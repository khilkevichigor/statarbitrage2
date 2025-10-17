package com.example.core.services;

import com.example.core.client.CandlesFeignClient;
import com.example.shared.models.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Сервис для управления кэшем свечей через вызовы к candles микросервису
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandleCacheManagementService {

    private final SettingsService settingsService;
    private final CandlesFeignClient candlesFeignClient;

    private final ExecutorService executorService = Executors.newFixedThreadPool(3);

    /**
     * Получить статистику кэша свечей
     */
    public Map<String, Object> getCacheStatistics(String exchange) {
        try {
            log.info("📊 Запрос статистики кэша для биржи: {}", exchange);

            Map<String, Object> statistics = candlesFeignClient.getCacheStatistics(exchange);

            if (statistics != null && !statistics.isEmpty()) {
                log.info("✅ Получена статистика кэша: {} записей", statistics.size());
                return statistics;
            } else {
                log.warn("⚠️ Получен пустой ответ статистики кэша");
                return generateEmptyStatistics();
            }

        } catch (Exception e) {
            log.error("❌ Ошибка при получении статистики кэша: {}", e.getMessage());
            return generateErrorStatistics(e.getMessage());
        }
    }

    /**
     * Принудительная загрузка свечей
     */
    public void forceLoadCandles(String exchange, Set<String> timeframes,
                                 List<String> tickers, Integer threadCount, Integer periodDays) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("🚀 Запуск принудительной загрузки: биржа={}, таймфреймы={}, тикеров={}, потоков={}, период={} дней",
                        exchange, timeframes, tickers.size(), threadCount, periodDays);

                // Обновляем настройки если указаны
                if (threadCount != null && threadCount > 0) {
                    updateThreadCountSetting(threadCount);
                }

                if (periodDays != null && periodDays > 0) {
                    updateForceLoadPeriodSetting(periodDays);
                }

                // Подготавливаем данные для запроса
                Map<String, Object> requestData = new HashMap<>();
                requestData.put("exchange", exchange);
                requestData.put("timeframes", timeframes);
                requestData.put("threadCount", threadCount != null ? threadCount : 5);
                requestData.put("periodDays", periodDays != null ? periodDays : 365);

                if (tickers != null && !tickers.isEmpty()) {
                    requestData.put("tickers", tickers);
                }

                Map<String, String> response = candlesFeignClient.forceLoadCandles(requestData);

                if ("started".equals(response.get("status"))) {
                    log.info("✅ Принудительная загрузка запущена успешно");
                } else {
                    log.error("❌ Ошибка запуска принудительной загрузки: {}", response.get("message"));
                }

            } catch (Exception e) {
                log.error("❌ Критическая ошибка при принудительной загрузке свечей: {}", e.getMessage(), e);
            }
        }, executorService);
    }

    /**
     * Сохранить настройки расписаний шедуллеров
     */
    public void saveSchedulerSettings(String preloadSchedule, String dailyUpdateSchedule) {
        try {
            log.info("💾 Сохранение настроек расписаний: предзагрузка='{}', обновление='{}'",
                    preloadSchedule, dailyUpdateSchedule);

            Settings settings = settingsService.getSettings();
            settings.setCandleCachePreloadSchedule(preloadSchedule);
            settings.setCandleCacheDailyUpdateSchedule(dailyUpdateSchedule);
            settingsService.save(settings);

            // Уведомляем candles сервис о новых настройках
            notifyCandlesServiceAboutScheduleUpdate(preloadSchedule, dailyUpdateSchedule);

            log.info("✅ Настройки расписаний сохранены");

        } catch (Exception e) {
            log.error("❌ Ошибка при сохранении настроек расписаний: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка сохранения настроек: " + e.getMessage());
        }
    }

    /**
     * Сохранить основные настройки кэша свечей
     */
    public void saveCacheSettings(String exchange, Set<String> activeTimeframes, Integer threadCount) {
        try {
            log.info("💾 Сохранение настроек кэша: биржа={}, таймфреймы={}, потоки={}",
                    exchange, activeTimeframes, threadCount);

            Settings settings = settingsService.getSettings();

            if (exchange != null && !exchange.isEmpty()) {
                settings.setCandleCacheDefaultExchange(exchange);
            }

            if (activeTimeframes != null && !activeTimeframes.isEmpty()) {
                String timeframesString = String.join(",", activeTimeframes);
                settings.setCandleCacheActiveTimeframes(timeframesString);
            }

            if (threadCount != null && threadCount > 0) {
                settings.setCandleCacheThreadCount(threadCount);
            }

            settingsService.save(settings);

            log.info("✅ Настройки кэша сохранены");

        } catch (Exception e) {
            log.error("❌ Ошибка при сохранении настроек кэша: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка сохранения настроек кэша: " + e.getMessage());
        }
    }

    /**
     * Получить настройки кэша свечей из базы
     */
    public Map<String, Object> getCacheSettings() {
        try {
            Settings settings = settingsService.getSettings();

            Map<String, Object> cacheSettings = new HashMap<>();
            cacheSettings.put("enabled", settings.isCandleCacheEnabled());
            cacheSettings.put("defaultExchange", settings.getCandleCacheDefaultExchange());
            cacheSettings.put("threadCount", settings.getCandleCacheThreadCount());
            cacheSettings.put("preloadSchedule", settings.getCandleCachePreloadSchedule());
            cacheSettings.put("dailyUpdateSchedule", settings.getCandleCacheDailyUpdateSchedule());

            // Парсим активные таймфреймы
            String timeframesStr = settings.getCandleCacheActiveTimeframes();
            Set<String> activeTimeframes = new HashSet<>();
            if (timeframesStr != null && !timeframesStr.trim().isEmpty()) {
                activeTimeframes = Arrays.stream(timeframesStr.split(","))
                        .map(String::trim)
                        .filter(tf -> !tf.isEmpty())
                        .collect(Collectors.toSet());
            }
            cacheSettings.put("activeTimeframes", activeTimeframes);
            cacheSettings.put("forceLoadPeriodDays", settings.getCandleCacheForceLoadPeriodDays());

            return cacheSettings;

        } catch (Exception e) {
            log.error("❌ Ошибка при получении настроек кэша: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }

    /**
     * Запустить полную предзагрузку свечей
     */
    public void startFullPreload(String exchange) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("🚀 Запуск полной предзагрузки для биржи: {}", exchange);

                Map<String, Object> requestData = new HashMap<>();
                requestData.put("exchange", exchange);

                Map<String, String> response = candlesFeignClient.startFullPreload(requestData);

                if ("started".equals(response.get("status"))) {
                    log.info("✅ Полная предзагрузка запущена для биржи: {}", exchange);
                } else {
                    log.error("❌ Ошибка запуска полной предзагрузки: {}", response.get("message"));
                }

            } catch (Exception e) {
                log.error("❌ Критическая ошибка при запуске полной предзагрузки: {}", e.getMessage(), e);
            }
        }, executorService);
    }

    /**
     * Запустить ежедневное обновление свечей
     */
    public void startDailyUpdate(String exchange) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("🔄 Запуск ежедневного обновления для биржи: {}", exchange);

                Map<String, Object> requestData = new HashMap<>();
                requestData.put("exchange", exchange);

                Map<String, String> response = candlesFeignClient.startDailyUpdate(requestData);

                if ("started".equals(response.get("status"))) {
                    log.info("✅ Ежедневное обновление запущено для биржи: {}", exchange);
                } else {
                    log.error("❌ Ошибка запуска ежедневного обновления: {}", response.get("message"));
                }

            } catch (Exception e) {
                log.error("❌ Критическая ошибка при запуске ежедневного обновления: {}", e.getMessage(), e);
            }
        }, executorService);
    }

    /**
     * Очистить неактивные таймфреймы из кэша
     */
    public void cleanupInactiveTimeframes(String exchange, Set<String> activeTimeframes) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("🧹 Запуск очистки неактивных таймфреймов: биржа={}, активные ТФ={}",
                        exchange, activeTimeframes);

                Map<String, Object> requestData = new HashMap<>();
                requestData.put("exchange", exchange);
                requestData.put("activeTimeframes", activeTimeframes);

                Map<String, String> response = candlesFeignClient.cleanupInactiveTimeframes(requestData);

                if ("success".equals(response.get("status"))) {
                    log.info("✅ Очистка неактивных таймфреймов завершена: {}", response.get("message"));
                } else {
                    log.error("❌ Ошибка при очистке неактивных таймфреймов: {}", response.get("message"));
                }

            } catch (Exception e) {
                log.error("❌ Критическая ошибка при очистке неактивных таймфреймов: {}", e.getMessage(), e);
            }
        }, executorService);
    }

    /**
     * Обновить настройку количества потоков загрузки в candles сервисе
     */
    private void updateThreadCountSetting(int threadCount) {
        try {
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("threadCount", threadCount);

            candlesFeignClient.updateThreadCount(requestData);

            log.info("✅ Количество потоков загрузки обновлено: {}", threadCount);

        } catch (Exception e) {
            log.warn("⚠️ Не удалось обновить количество потоков в candles сервисе: {}", e.getMessage());
        }
    }

    /**
     * Уведомить candles сервис о новых настройках расписания
     */
    private void notifyCandlesServiceAboutScheduleUpdate(String preloadSchedule, String dailyUpdateSchedule) {
        try {
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("preloadSchedule", preloadSchedule);
            requestData.put("dailyUpdateSchedule", dailyUpdateSchedule);

            candlesFeignClient.updateSchedules(requestData);

            log.info("✅ Candles сервис уведомлен о новых настройках расписания");

        } catch (Exception e) {
            log.warn("⚠️ Не удалось уведомить candles сервис о новых настройках: {}", e.getMessage());
        }
    }

    /**
     * Обновить настройку периода принудительной загрузки в candles сервисе
     */
    private void updateForceLoadPeriodSetting(int periodDays) {
        try {
            Settings settings = settingsService.getSettings();
            settings.setCandleCacheForceLoadPeriodDays(periodDays);
            settingsService.save(settings);

            Map<String, Object> requestData = new HashMap<>();
            requestData.put("forceLoadPeriodDays", periodDays);

            candlesFeignClient.updateForceLoadPeriod(requestData);

            log.info("✅ Период принудительной загрузки обновлен: {} дней", periodDays);

        } catch (Exception e) {
            log.warn("⚠️ Не удалось обновить период принудительной загрузки: {}", e.getMessage());
        }
    }

    /**
     * Генерировать пустую статистику
     */
    private Map<String, Object> generateEmptyStatistics() {
        Map<String, Object> emptyStats = new HashMap<>();
        emptyStats.put("totalTickers", 0);
        emptyStats.put("byExchange", new HashMap<>());
        emptyStats.put("timeframeStats", new HashMap<>());
        emptyStats.put("message", "Данные недоступны");
        return emptyStats;
    }

    /**
     * Генерировать статистику с ошибкой
     */
    private Map<String, Object> generateErrorStatistics(String errorMessage) {
        Map<String, Object> errorStats = new HashMap<>();
        errorStats.put("error", errorMessage);
        errorStats.put("totalTickers", 0);
        errorStats.put("byExchange", new HashMap<>());
        errorStats.put("timeframeStats", new HashMap<>());
        return errorStats;
    }
}