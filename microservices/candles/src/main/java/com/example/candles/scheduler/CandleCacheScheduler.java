package com.example.candles.scheduler;

import com.example.candles.service.CandleCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
@Slf4j
public class CandleCacheScheduler {

    private final CandleCacheService candleCacheService;

    @Value("${app.candle-cache.default-exchange:OKX}")
    private String defaultExchange;

    @Value("${app.candle-cache.preload-enabled:true}")
    private boolean preloadEnabled;

    @Value("${app.candle-cache.daily-update-enabled:true}")
    private boolean dailyUpdateEnabled;

    @Value("${app.candle-cache.startup-check:true}")
    private boolean startupCheckEnabled;

    private boolean isFirstPreloadCompleted = false;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (startupCheckEnabled) {
            log.info("🚀 Приложение готово. Проверяем состояние кэша свечей...");

            try {
                var stats = candleCacheService.getCacheStatistics(defaultExchange);
                log.info("📊 Статистика кэша при запуске: {}", stats);

                // Проверяем есть ли данные в кэше
                @SuppressWarnings("unchecked")
                var exchangeStats = (java.util.Map<String, java.util.Map<String, Long>>) stats.get("byExchange");

                boolean hasCachedData = exchangeStats != null &&
                        exchangeStats.containsKey(defaultExchange) &&
                        exchangeStats.get(defaultExchange).values().stream()
                                .anyMatch(count -> count > 0);

                if (!hasCachedData) {
                    log.info("⚠️ Кэш пуст. Запланируем полную предзагрузку при следующем запуске шедуллера.");
                    isFirstPreloadCompleted = false;
                } else {
                    log.info("✅ Кэш содержит данные. Полная предзагрузка не требуется.");
                    isFirstPreloadCompleted = true;
                }

            } catch (Exception e) {
                log.error("❌ Ошибка проверки состояния кэша при запуске: {}", e.getMessage(), e);
                isFirstPreloadCompleted = false;
            }
        }
    }

    /**
     * Ежедневное обновление кэша в 3:00 утра по местному времени
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduledCacheUpdate() {
        String currentTime = LocalDateTime.now().format(formatter);
        log.info("⏰ Запуск шедуллера обновления кэша в {}", currentTime);

        try {
            if (!isFirstPreloadCompleted && preloadEnabled) {
                // Первый запуск - полная предзагрузка
                log.info("🔄 Первый запуск шедуллера. Запускаем полную предзагрузку...");
                performFullPreload();
                isFirstPreloadCompleted = true;
            } else if (dailyUpdateEnabled) {
                // Обычное ежедневное обновление
                log.info("🔄 Запуск ежедневного обновления кэша...");
                performDailyUpdate();
            } else {
                log.info("⏸️ Обновление кэша отключено в настройках");
            }

        } catch (Exception e) {
            log.error("❌ Ошибка в шедуллере обновления кэша: {}", e.getMessage(), e);

            // Отправляем уведомление об ошибке (можно добавить Telegram/Email уведомления)
            notifySchedulerError(e);
        }
    }

    /**
     * Дополнительное обновление каждые 4 часа (только новые свечи)
     */
    @Scheduled(fixedRate = 4 * 60 * 60 * 1000, initialDelay = 30 * 60 * 1000) // 4 часа, старт через 30 мин
    public void scheduledQuickUpdate() {
        if (!dailyUpdateEnabled || !isFirstPreloadCompleted) {
            return; // Пропускаем если основное обновление отключено или еще не было первой загрузки
        }

        String currentTime = LocalDateTime.now().format(formatter);
        log.info("🔄 Быстрое обновление кэша в {}", currentTime);

        try {
            candleCacheService.dailyCandlesUpdate(defaultExchange);
            log.info("✅ Быстрое обновление кэша завершено");

        } catch (Exception e) {
            log.warn("⚠️ Ошибка при быстром обновлении кэша: {}", e.getMessage());
        }
    }

    /**
     * Статистика кэша каждый час
     */
    @Scheduled(cron = "0 0 * * * ?") // Каждый час в начале часа
    public void hourlyStatistics() {
        try {
            var stats = candleCacheService.getCacheStatistics(defaultExchange);

            @SuppressWarnings("unchecked")
            var totalTickers = (Integer) stats.get("totalTickers");

            if (totalTickers != null && totalTickers > 0) {
                log.info("📊 Статистика кэша: {} тикеров кэшировано", totalTickers);
            }

        } catch (Exception e) {
            log.debug("⚠️ Ошибка получения статистики кэша: {}", e.getMessage());
        }
    }

    /**
     * Ручной запуск полной предзагрузки
     */
    public void triggerFullPreload() {
        log.info("🎯 Ручной запуск полной предзагрузки кэша");

        try {
            performFullPreload();
            isFirstPreloadCompleted = true;
            log.info("✅ Ручная полная предзагрузка завершена успешно");

        } catch (Exception e) {
            log.error("❌ Ошибка при ручной предзагрузке: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка полной предзагрузки: " + e.getMessage(), e);
        }
    }

    /**
     * Ручной запуск ежедневного обновления
     */
    public void triggerDailyUpdate() {
        log.info("🎯 Ручной запуск ежедневного обновления кэша");

        try {
            performDailyUpdate();
            log.info("✅ Ручное ежедневное обновление завершено успешно");

        } catch (Exception e) {
            log.error("❌ Ошибка при ручном обновлении: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка ежедневного обновления: " + e.getMessage(), e);
        }
    }

    /**
     * Получение статуса шедуллера
     */
    public java.util.Map<String, Object> getSchedulerStatus() {
        java.util.Map<String, Object> status = new java.util.HashMap<>();

        status.put("preloadEnabled", preloadEnabled);
        status.put("dailyUpdateEnabled", dailyUpdateEnabled);
        status.put("startupCheckEnabled", startupCheckEnabled);
        status.put("isFirstPreloadCompleted", isFirstPreloadCompleted);
        status.put("defaultExchange", defaultExchange);
        status.put("currentTime", LocalDateTime.now().format(formatter));

        // Добавляем статистику кэша
        try {
            var cacheStats = candleCacheService.getCacheStatistics(defaultExchange);
            status.put("cacheStatistics", cacheStats);
        } catch (Exception e) {
            status.put("cacheStatisticsError", e.getMessage());
        }

        return status;
    }

    private void performFullPreload() {
        long startTime = System.currentTimeMillis();
        log.info("🚀 Начинаем полную предзагрузку кэша для биржи {}", defaultExchange);

        candleCacheService.preloadAllCandles(defaultExchange);

        long duration = (System.currentTimeMillis() - startTime) / 1000;
        log.info("✅ Полная предзагрузка завершена за {} секунд", duration);

        // Логируем финальную статистику
        try {
            var stats = candleCacheService.getCacheStatistics(defaultExchange);
            log.info("📈 Финальная статистика после предзагрузки: {}", stats);
        } catch (Exception e) {
            log.warn("⚠️ Не удалось получить финальную статистику: {}", e.getMessage());
        }
    }

    private void performDailyUpdate() {
        long startTime = System.currentTimeMillis();
        log.info("🔄 Начинаем ежедневное обновление кэша для биржи {}", defaultExchange);

        candleCacheService.dailyCandlesUpdate(defaultExchange);

        long duration = (System.currentTimeMillis() - startTime) / 1000;
        log.info("✅ Ежедневное обновление завершено за {} секунд", duration);

        // Логируем статистику после обновления
        try {
            var stats = candleCacheService.getCacheStatistics(defaultExchange);
            log.info("📊 Статистика после обновления: {}", stats);
        } catch (Exception e) {
            log.warn("⚠️ Не удалось получить статистику после обновления: {}", e.getMessage());
        }
    }

    private void notifySchedulerError(Exception e) {
        // TODO: Здесь можно добавить отправку уведомлений в Telegram или по email
        log.error("🔔 КРИТИЧЕСКАЯ ОШИБКА ШЕДУЛЛЕРА КЭША: {}", e.getMessage());
        log.error("📧 Рекомендуется настроить уведомления для мониторинга состояния кэша");
    }
}