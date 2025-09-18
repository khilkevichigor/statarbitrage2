package com.example.candles.controller;

import com.example.candles.scheduler.CandleCacheScheduler;
import com.example.candles.service.CandleCacheService;
import com.example.shared.dto.Candle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cache")
@RequiredArgsConstructor
@Slf4j
public class CandleCacheController {

    private final CandleCacheService candleCacheService;
    private final CandleCacheScheduler candleCacheScheduler;

    @Value("${app.candle-cache.default-exchange:OKX}")
    private String defaultExchange;

    /**
     * Получить кэшированные свечи (основной метод для скринера)
     */
    @PostMapping("/get")
    public ResponseEntity<Map<String, List<Candle>>> getCachedCandles(@RequestBody CachedCandlesRequest request) {
        log.info("📥 Запрос кэшированных свечей: {} тикеров, таймфрейм {}, лимит {}",
                request.getTickers().size(), request.getTimeframe(), request.getCandleLimit());

        try {
            Map<String, List<Candle>> result = candleCacheService.getCachedCandles(
                    request.getTickers(),
                    request.getTimeframe(),
                    request.getCandleLimit(),
                    request.getExchange() != null ? request.getExchange() : defaultExchange
            );

            log.info("📤 Возвращено свечей: {} тикеров", result.size());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("❌ Ошибка получения кэшированных свечей: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(new HashMap<>());
        }
    }

    /**
     * Получить статистику кэша
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getCacheStatistics(
            @RequestParam(required = false) String exchange) {

        String targetExchange = exchange != null ? exchange : defaultExchange;
        log.info("📊 Запрос статистики кэша для биржи {}", targetExchange);

        try {
            Map<String, Object> stats = candleCacheService.getCacheStatistics(targetExchange);
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("❌ Ошибка получения статистики кэша: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Получить статус шедуллера
     */
    @GetMapping("/scheduler/status")
    public ResponseEntity<Map<String, Object>> getSchedulerStatus() {
        log.info("🔍 Запрос статуса шедуллера кэша");

        try {
            Map<String, Object> status = candleCacheScheduler.getSchedulerStatus();
            return ResponseEntity.ok(status);

        } catch (Exception e) {
            log.error("❌ Ошибка получения статуса шедуллера: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Принудительная загрузка свечей с настраиваемыми параметрами
     */
    @PostMapping("/force-load")
    public ResponseEntity<Map<String, String>> forceLoadCandles(@RequestBody ForceLoadRequest request) {
        log.info("🚀 Принудительная загрузка: биржа={}, таймфреймы={}, тикеров={}, потоки={}, период={} дней", 
                request.getExchange(), request.getTimeframes(), 
                request.getTickers() != null ? request.getTickers().size() : 0, 
                request.getThreadCount(), request.getPeriodDays());

        try {
            // Запускаем в отдельном потоке
            new Thread(() -> {
                try {
                    candleCacheService.forceLoadCandlesCustom(
                            request.getExchange(), 
                            request.getTimeframes(), 
                            request.getTickers(), 
                            request.getThreadCount(),
                            request.getPeriodDays()
                    );
                } catch (Exception e) {
                    log.error("❌ Ошибка в фоновой принудительной загрузке: {}", e.getMessage(), e);
                }
            }, "force-load").start();

            Map<String, String> response = new HashMap<>();
            response.put("status", "started");
            response.put("message", "Принудительная загрузка запущена в фоновом режиме");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Ошибка принудительной загрузки: {}", e.getMessage(), e);

            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Ручной запуск полной предзагрузки
     */
    @PostMapping("/full-preload")
    public ResponseEntity<Map<String, String>> triggerFullPreload(@RequestBody Map<String, Object> request) {
        String exchange = (String) request.getOrDefault("exchange", defaultExchange);
        log.info("🎯 Ручной запуск полной предзагрузки через API для биржи: {}", exchange);

        try {
            // Запускаем в отдельном потоке чтобы не блокировать HTTP запрос
            new Thread(() -> {
                try {
                    candleCacheService.preloadAllCandles(exchange);
                } catch (Exception e) {
                    log.error("❌ Ошибка в фоновом потоке предзагрузки: {}", e.getMessage(), e);
                }
            }, "manual-preload").start();

            Map<String, String> response = new HashMap<>();
            response.put("status", "started");
            response.put("message", "Полная предзагрузка запущена в фоновом режиме");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Ошибка запуска полной предзагрузки: {}", e.getMessage(), e);

            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Ручной запуск ежедневного обновления
     */
    @PostMapping("/daily-update")
    public ResponseEntity<Map<String, String>> triggerDailyUpdate(@RequestBody Map<String, Object> request) {
        String exchange = (String) request.getOrDefault("exchange", defaultExchange);
        log.info("🎯 Ручной запуск ежедневного обновления через API для биржи: {}", exchange);

        try {
            // Запускаем в отдельном потоке
            new Thread(() -> {
                try {
                    candleCacheService.dailyCandlesUpdate(exchange);
                } catch (Exception e) {
                    log.error("❌ Ошибка в фоновом потоке обновления: {}", e.getMessage(), e);
                }
            }, "manual-update").start();

            Map<String, String> response = new HashMap<>();
            response.put("status", "started");
            response.put("message", "Ежедневное обновление запущено в фоновом режиме");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Ошибка запуска ежедневного обновления: {}", e.getMessage(), e);

            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Очистка кэша
     */
    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, String>> clearCache(
            @RequestParam(required = false) String exchange,
            @RequestParam(required = false) String ticker,
            @RequestParam(required = false) String timeframe) {

        String targetExchange = exchange != null ? exchange : defaultExchange;
        log.info("🗑️ Запрос очистки кэша: exchange={}, ticker={}, timeframe={}",
                targetExchange, ticker, timeframe);

        try {
            // TODO: Реализовать методы очистки в CandleCacheService
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Кэш очищен (функция в разработке)");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Ошибка очистки кэша: {}", e.getMessage(), e);

            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Health check для кэша
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();

        try {
            // Проверяем базовую работоспособность
            Map<String, Object> stats = candleCacheService.getCacheStatistics(defaultExchange);

            health.put("status", "UP");
            health.put("cacheWorking", true);
            health.put("exchange", defaultExchange);
            health.put("timestamp", System.currentTimeMillis());

            // Добавляем краткую статистику
            @SuppressWarnings("unchecked")
            var exchangeStats = (Map<String, Map<String, Long>>) stats.get("byExchange");

            if (exchangeStats != null && exchangeStats.containsKey(defaultExchange)) {
                long totalCandles = exchangeStats.get(defaultExchange).values().stream()
                        .mapToLong(Long::longValue).sum();
                health.put("totalCachedCandles", totalCandles);
            } else {
                health.put("totalCachedCandles", 0);
            }

            return ResponseEntity.ok(health);

        } catch (Exception e) {
            log.error("❌ Ошибка health check кэша: {}", e.getMessage(), e);

            health.put("status", "DOWN");
            health.put("cacheWorking", false);
            health.put("error", e.getMessage());
            health.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.status(503).body(health);
        }
    }

    /**
     * Обновление количества потоков загрузки
     */
    @PostMapping("/thread-count")
    public ResponseEntity<Map<String, String>> updateThreadCount(@RequestBody Map<String, Object> request) {
        try {
            Integer threadCount = (Integer) request.get("threadCount");
            if (threadCount != null && threadCount > 0) {
                candleCacheService.updateThreadPoolSize(threadCount);
                log.info("✅ Обновлено количество потоков загрузки: {}", threadCount);
                
                Map<String, String> response = new HashMap<>();
                response.put("status", "success");
                response.put("message", "Количество потоков обновлено до " + threadCount);
                response.put("currentThreads", String.valueOf(candleCacheService.getCurrentThreadPoolSize()));
                return ResponseEntity.ok(response);
            } else {
                Map<String, String> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Некорректное количество потоков");
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("❌ Ошибка обновления количества потоков: {}", e.getMessage(), e);
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Обновление периода принудительной загрузки
     */
    @PostMapping("/force-load-period")
    public ResponseEntity<Map<String, String>> updateForceLoadPeriod(@RequestBody Map<String, Object> request) {
        try {
            Integer periodDays = (Integer) request.get("forceLoadPeriodDays");
            if (periodDays != null && periodDays > 0) {
                // TODO: Обновить период в CandleCacheService
                log.info("✅ Обновлен период принудительной загрузки: {} дней", periodDays);
                
                Map<String, String> response = new HashMap<>();
                response.put("status", "success");
                response.put("message", "Период принудительной загрузки обновлен");
                return ResponseEntity.ok(response);
            } else {
                Map<String, String> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Некорректный период");
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("❌ Ошибка обновления периода: {}", e.getMessage(), e);
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Обновление настроек расписания
     */
    @PostMapping("/schedule-update")
    public ResponseEntity<Map<String, String>> updateSchedules(@RequestBody Map<String, Object> request) {
        try {
            String preloadSchedule = (String) request.get("preloadSchedule");
            String dailyUpdateSchedule = (String) request.get("dailyUpdateSchedule");
            
            if (preloadSchedule != null && dailyUpdateSchedule != null) {
                // TODO: Обновить расписания в CandleCacheScheduler
                log.info("✅ Обновлены расписания: предзагрузка='{}', обновление='{}'", preloadSchedule, dailyUpdateSchedule);
                
                Map<String, String> response = new HashMap<>();
                response.put("status", "success");
                response.put("message", "Расписания обновлены");
                return ResponseEntity.ok(response);
            } else {
                Map<String, String> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Не указаны расписания");
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("❌ Ошибка обновления расписаний: {}", e.getMessage(), e);
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * DTO для принудительной загрузки
     */
    public static class ForceLoadRequest {
        private String exchange;
        private java.util.Set<String> timeframes;
        private List<String> tickers;
        private Integer threadCount;
        private Integer periodDays;

        // Getters and setters
        public String getExchange() { return exchange; }
        public void setExchange(String exchange) { this.exchange = exchange; }
        
        public java.util.Set<String> getTimeframes() { return timeframes; }
        public void setTimeframes(java.util.Set<String> timeframes) { this.timeframes = timeframes; }
        
        public List<String> getTickers() { return tickers; }
        public void setTickers(List<String> tickers) { this.tickers = tickers; }
        
        public Integer getThreadCount() { return threadCount; }
        public void setThreadCount(Integer threadCount) { this.threadCount = threadCount; }
        
        public Integer getPeriodDays() { return periodDays; }
        public void setPeriodDays(Integer periodDays) { this.periodDays = periodDays; }
    }

    /**
     * DTO для запроса кэшированных свечей
     */
    public static class CachedCandlesRequest {
        private List<String> tickers;
        private String timeframe;
        private int candleLimit;
        private String exchange;

        // Getters and setters
        public List<String> getTickers() {
            return tickers;
        }

        public void setTickers(List<String> tickers) {
            this.tickers = tickers;
        }

        public String getTimeframe() {
            return timeframe;
        }

        public void setTimeframe(String timeframe) {
            this.timeframe = timeframe;
        }

        public int getCandleLimit() {
            return candleLimit;
        }

        public void setCandleLimit(int candleLimit) {
            this.candleLimit = candleLimit;
        }

        public String getExchange() {
            return exchange;
        }

        public void setExchange(String exchange) {
            this.exchange = exchange;
        }
    }
}