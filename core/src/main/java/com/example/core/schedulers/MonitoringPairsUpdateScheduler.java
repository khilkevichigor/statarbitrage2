package com.example.core.schedulers;

import com.example.core.services.PairService;
import com.example.core.services.SchedulerControlService;
import com.example.core.services.SettingsService;
import com.example.shared.models.Pair;
import com.example.shared.models.Settings;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Шедуллер для автоматического обновления пар в мониторинге
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringPairsUpdateScheduler {

    private final PairService pairService;
    private final SchedulerControlService schedulerControlService;
    private final SettingsService settingsService;

    // Пул потоков для параллельной обработки
    private final ExecutorService executorService = Executors.newFixedThreadPool(3,
            r -> {
                Thread t = new Thread(r, "MonitoringPairsUpdate-");
                t.setDaemon(true);
                return t;
            });

    // Максимальное время ожидания завершения всех задач (2 часа)
    private static final int MAX_EXECUTION_TIME_HOURS = 2;

    /**
     * Автоматическое обновление пар в мониторинге
     * Запускается по расписанию из настроек (по умолчанию каждую ночь в 01:00)
     */
    @Scheduled(cron = "${app.scheduler.monitoring-pairs-update.cron:0 0 1 * * *}")
    public void updateMonitoringPairsScheduled() {
        try {
            Settings settings = settingsService.getSettings();

            // Проверяем включен ли шедуллер через настройки
            if (!settings.getSchedulerMonitoringPairsUpdateEnabled()) {
                log.debug("📅 MonitoringPairsUpdateScheduler отключен в настройках");
                return;
            }

            // Проверяем глобальное состояние шедуллеров
            if (!schedulerControlService.isSchedulingEnabled()) {
                log.debug("⏸️ Глобальное планирование задач отключено");
                return;
            }
        } catch (Exception e) {
            log.error("❌ Ошибка проверки настроек шедуллера: {}", e.getMessage());
            return;
        }

        log.info("");
        log.info("🌙 Запуск автоматического обновления пар в мониторинге в {}", LocalDateTime.now());

        try {
            // Получаем все пары в мониторинге
            List<Pair> monitoringPairs = pairService.getMonitoringPairs();

            if (monitoringPairs.isEmpty()) {
                log.info("📭 Нет пар в мониторинге для обновления");
                return;
            }

            log.info("📋 Найдено {} пар в мониторинге для обновления", monitoringPairs.size());
            log.info("🔧 Используем {} потоков для параллельной обработки", 3);

            // Атомарные счетчики для статистики
            AtomicInteger successfulUpdates = new AtomicInteger(0);
            AtomicInteger failedUpdates = new AtomicInteger(0);

            // Создаем список задач для параллельного выполнения
            List<CompletableFuture<Void>> futures = monitoringPairs.stream()
                    .map(pair -> CompletableFuture.runAsync(() ->
                            updateSinglePair(pair, successfulUpdates, failedUpdates), executorService))
                    .toList();

            // Ждем завершения всех задач
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0]));

            try {
                allFutures.get(MAX_EXECUTION_TIME_HOURS, TimeUnit.HOURS);
                log.info("✅ Все задачи обновления завершены успешно");
            } catch (TimeoutException e) {
                log.warn("⏰ Превышен лимит времени выполнения ({} часов). Принудительно завершаем задачи",
                        MAX_EXECUTION_TIME_HOURS);
                allFutures.cancel(true);
            }

            // Итоговая статистика
            int totalPairs = monitoringPairs.size();
            log.info("🏁 Автоматическое обновление пар в мониторинге завершено:");
            log.info("   📊 Успешно обновлено: {}/{}", successfulUpdates.get(), totalPairs);
            log.info("   ❌ Ошибок при обновлении: {}", failedUpdates.get());
            log.info("   ⚡ Использовано потоков: 3");

        } catch (Exception e) {
            log.error("💥 Критическая ошибка при автоматическом обновлении пар в мониторинге: {}",
                    e.getMessage(), e);
        }
    }

    /**
     * Обновление одной пары в отдельном потоке
     */
    private void updateSinglePair(Pair pair, AtomicInteger successfulUpdates, AtomicInteger failedUpdates) {
        String threadName = Thread.currentThread().getName();
        String pairName = pair.getPairName();

        log.info("🧵 Поток {}: Начало обновления пары {}", threadName, pairName);

        try {
            boolean success = pairService.updateMonitoringPairSync(pair.getId());

            if (success) {
                successfulUpdates.incrementAndGet();
                log.info("✅ Поток {}: Пара {} успешно обновлена", threadName, pairName);
            } else {
                failedUpdates.incrementAndGet();
                log.error("❌ Поток {}: Не удалось обновить пару {}", threadName, pairName);
            }

        } catch (Exception e) {
            failedUpdates.incrementAndGet();
            log.error("💥 Поток {}: Ошибка при обновлении пары {}: {}",
                    threadName, pairName, e.getMessage(), e);
        }
    }

    /**
     * Ручное обновление всех пар в мониторинге
     * Может быть вызвано из UI или другого сервиса
     */
    public void manualUpdateMonitoringPairs() {
        log.info("🔄 Запуск ручного обновления пар в мониторинге...");
        updateMonitoringPairsScheduled();
    }

    /**
     * Тестовый метод для проверки автоматического обновления
     * Можно запустить вручную для тестирования
     */
    public void testScheduledUpdate() {
        log.info("🧪 Тестовый запуск автоматического обновления пар в мониторинге");
        updateMonitoringPairsScheduled();
    }

    /**
     * Получить статус шедуллера
     */
    public boolean isSchedulerEnabled() {
        try {
            Settings settings = settingsService.getSettings();
            return settings.getSchedulerMonitoringPairsUpdateEnabled() &&
                    schedulerControlService.isSchedulingEnabled();
        } catch (Exception e) {
            log.error("❌ Ошибка получения статуса шедуллера: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Получить CRON выражение шедуллера
     */
    public String getSchedulerCron() {
        try {
            Settings settings = settingsService.getSettings();
            return settings.getSchedulerMonitoringPairsUpdateCron();
        } catch (Exception e) {
            log.error("❌ Ошибка получения CRON выражения: {}", e.getMessage());
            return "0 0 1 * * *"; // дефолтное значение
        }
    }

    /**
     * Graceful shutdown при остановке приложения
     */
    @PreDestroy
    public void shutdown() {
        log.info("🛑 Завершение работы шедуллера обновления пар в мониторинге");

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    log.error("❌ Пул потоков не завершился корректно");
                }
            }
        } catch (InterruptedException ie) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("✅ Шедуллер обновления пар в мониторинге остановлен");
    }
}