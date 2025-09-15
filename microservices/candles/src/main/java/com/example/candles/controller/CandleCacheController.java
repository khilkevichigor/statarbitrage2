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
@RequestMapping("/api/candles/cache")
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
     * Ручной запуск полной предзагрузки
     */
    @PostMapping("/preload")
    public ResponseEntity<Map<String, String>> triggerFullPreload() {
        log.info("🎯 Ручной запуск полной предзагрузки через API");

        try {
            // Запускаем в отдельном потоке чтобы не блокировать HTTP запрос
            new Thread(() -> {
                try {
                    candleCacheScheduler.triggerFullPreload();
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
    @PostMapping("/update")
    public ResponseEntity<Map<String, String>> triggerDailyUpdate() {
        log.info("🎯 Ручной запуск ежедневного обновления через API");

        try {
            // Запускаем в отдельном потоке
            new Thread(() -> {
                try {
                    candleCacheScheduler.triggerDailyUpdate();
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