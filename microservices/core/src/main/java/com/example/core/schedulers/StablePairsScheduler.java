package com.example.core.schedulers;

import com.example.core.experemental.stability.dto.StabilityResponseDto;
import com.example.core.services.PairService;
import com.example.core.services.StablePairsScreenerSettingsService;
import com.example.shared.models.StablePairsScreenerSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Шедуллер для автоматического поиска стабильных пар
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StablePairsScheduler {

    private final StablePairsScreenerSettingsService settingsService;
    private final PairService pairService;

    // Пул потоков для параллельной обработки
    private final ExecutorService executorService = Executors.newFixedThreadPool(5,
            r -> {
                Thread t = new Thread(r, "StablePairsScheduler-");
                t.setDaemon(true);
                return t;
            });

    // Максимальное время ожидания завершения всех задач (6 часов)
    private static final int MAX_EXECUTION_TIME_HOURS = 6;

    /**
     * Автоматический поиск стабильных пар по расписанию (многопоточный)
     * Запускается каждую ночь в 2:00 по местному времени
     */
//    @Scheduled(cron = "0 10 1 * * *") // каждый день в 2:00
    public void searchStablePairsScheduled() {
        log.info("🌙 Запуск многопоточного автоматического поиска стабильных пар в {}", LocalDateTime.now());

        try {
            // Получаем все настройки с включенным автоматическим запуском
            List<StablePairsScreenerSettings> scheduledSettings = settingsService.getScheduledSettings();

            if (scheduledSettings.isEmpty()) {
                log.info("⏰ Нет настроек с включенным автоматическим поиском");
                return;
            }

            // Считаем общее количество задач
            int totalTasks = scheduledSettings.stream()
                    .mapToInt(settings ->
                            settings.getSelectedTimeframesSet().size() *
                                    settings.getSelectedPeriodsSet().size())
                    .sum();

            log.info("📋 Найдено {} настроек для автоматического поиска", scheduledSettings.size());
            log.info("🧮 Общее количество комбинаций для обработки: {}", totalTasks);
            log.info("🔧 Используем {} потоков для параллельной обработки", 5);

            // Атомарные счетчики для статистики
            AtomicInteger totalPairsFound = new AtomicInteger(0);
            AtomicInteger totalPairsAnalyzed = new AtomicInteger(0);
            AtomicInteger successfulTasks = new AtomicInteger(0);
            AtomicInteger failedTasks = new AtomicInteger(0);

            // Создаем список задач для параллельного выполнения
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // Выполняем поиск по каждой настройке
            for (StablePairsScreenerSettings settings : scheduledSettings) {
                try {
                    Set<String> timeframes = settings.getSelectedTimeframesSet();
                    Set<String> periods = settings.getSelectedPeriodsSet();

                    if (timeframes.isEmpty() || periods.isEmpty()) {
                        log.warn("⚠️ Пропуск настроек {} - не выбраны таймфреймы или периоды",
                                settings.getName());
                        continue;
                    }

                    Map<String, Object> searchSettings = settingsService.buildSearchSettingsMap(settings);

                    // Создаем задачи для каждой комбинации timeframe + period
                    for (String timeframe : timeframes) {
                        for (String period : periods) {
                            CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
                                    processTimeframePeriodCombination(
                                            settings, timeframe, period, searchSettings,
                                            totalPairsFound, totalPairsAnalyzed,
                                            successfulTasks, failedTasks
                                    ), executorService);

                            futures.add(future);
                        }
                    }

                } catch (Exception e) {
                    log.error("💥 Ошибка при создании задач для настроек '{}': {}",
                            settings.getName(), e.getMessage(), e);
                }
            }

            // Ждем завершения всех задач
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0]));

            try {
                allFutures.get(MAX_EXECUTION_TIME_HOURS, TimeUnit.HOURS);
                log.info("✅ Все задачи завершены успешно");
            } catch (TimeoutException e) {
                log.warn("⏰ Превышен лимит времени выполнения ({} часов). Принудительно завершаем задачи",
                        MAX_EXECUTION_TIME_HOURS);
                allFutures.cancel(true);
            }

            // Отмечаем все настройки как использованные
            for (StablePairsScreenerSettings settings : scheduledSettings) {
                try {
                    settingsService.markAsUsed(settings.getId());
                } catch (Exception e) {
                    log.warn("⚠️ Не удалось отметить настройки '{}' как использованные: {}",
                            settings.getName(), e.getMessage());
                }
            }

            // Итоговая статистика
            log.info("🏁 Многопоточный автоматический поиск завершен:");
            log.info("   📊 Успешных задач: {}/{}", successfulTasks.get(), totalTasks);
            log.info("   ❌ Неудачных задач: {}", failedTasks.get());
            log.info("   🔍 Всего проанализировано пар: {}", totalPairsAnalyzed.get());
            log.info("   ✅ Всего найдено торгуемых пар: {}", totalPairsFound.get());
            log.info("   ⚡ Использовано потоков: 5");

        } catch (Exception e) {
            log.error("💥 Критическая ошибка при автоматическом поиске стабильных пар: {}",
                    e.getMessage(), e);
        }
    }

    /**
     * Обработка одной комбинации timeframe + period в отдельном потоке
     */
    private void processTimeframePeriodCombination(
            StablePairsScreenerSettings settings,
            String timeframe,
            String period,
            Map<String, Object> searchSettings,
            AtomicInteger totalPairsFound,
            AtomicInteger totalPairsAnalyzed,
            AtomicInteger successfulTasks,
            AtomicInteger failedTasks) {

        String taskId = String.format("%s[%s-%s]", settings.getName(), timeframe, period);
        String threadName = Thread.currentThread().getName();

        log.info("🧵 Поток {}: Начало обработки {}", threadName, taskId);

        try {
            // Выполняем поиск для конкретной комбинации
            StabilityResponseDto response = pairService.searchStablePairs(
                    Set.of(timeframe), Set.of(period), searchSettings);

            if (response.getSuccess()) {
                int pairsFound = response.getTradeablePairsFound();
                int pairsAnalyzed = response.getTotalPairsAnalyzed();

                // Атомарно обновляем счетчики
                totalPairsFound.addAndGet(pairsFound);
                totalPairsAnalyzed.addAndGet(pairsAnalyzed);
                successfulTasks.incrementAndGet();

                log.info("✅ Поток {}: {} завершен успешно - найдено {} торгуемых пар из {} проанализированных",
                        threadName, taskId, pairsFound, pairsAnalyzed);

            } else {
                failedTasks.incrementAndGet();
                log.error("❌ Поток {}: {} завершился неудачно", threadName, taskId);
            }

        } catch (Exception e) {
            failedTasks.incrementAndGet();
            log.error("💥 Поток {}: Ошибка при обработке {}: {}",
                    threadName, taskId, e.getMessage(), e);
        }
    }

    /**
     * Тестовый метод для проверки автоматического поиска
     * Можно запустить вручную для тестирования
     */
    public void testScheduledSearch() {
        log.info("🧪 Тестовый запуск многопоточного автоматического поиска стабильных пар");
        searchStablePairsScheduled();
    }

    /**
     * Graceful shutdown при остановке приложения
     */
    @jakarta.annotation.PreDestroy
    public void shutdown() {
        log.info("🛑 Завершение работы шедуллера стабильных пар");

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

        log.info("✅ Шедуллер стабильных пар остановлен");
    }
}