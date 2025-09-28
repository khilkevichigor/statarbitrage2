package com.example.candles.controller;

import com.example.candles.service.CacheValidatedCandlesProcessor;
import com.example.candles.service.CandlesLoaderProcessor;
import com.example.shared.dto.Candle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Контроллер для тестирования новых сервисов-процессоров
 */
@RestController
@RequestMapping("/api/candles-processor")
@RequiredArgsConstructor
@Slf4j
public class CandlesProcessorController {
    
    private final CacheValidatedCandlesProcessor cacheValidatedCandlesProcessor;
    private final CandlesLoaderProcessor candlesLoaderProcessor;
    
    /**
     * Получить валидированные свечи из кэша
     * 
     * GET /api/candles-processor/validated-cache?exchange=OKX&ticker=BTC-USDT-SWAP&timeframe=1H&period=1year
     */
    @GetMapping("/validated-cache")
    public ResponseEntity<?> getValidatedCandlesFromCache(
            @RequestParam(defaultValue = "OKX") String exchange,
            @RequestParam String ticker,
            @RequestParam(defaultValue = "1H") String timeframe,
            @RequestParam(defaultValue = "1 год") String period
    ) {
        log.info("🔍 API ЗАПРОС: Получение валидированных свечей из кэша");
        log.info("📊 ПАРАМЕТРЫ: exchange={}, ticker={}, timeframe={}, period={}", exchange, ticker, timeframe, period);
        
        try {
            // Генерируем дату "до" как начало текущего дня
            String untilDate = generateUntilDate();
            log.info("📅 ДАТА ДО: {}", untilDate);
            
            // Получаем валидированные свечи
            List<Candle> candles = cacheValidatedCandlesProcessor.getValidatedCandlesFromCache(
                    exchange, ticker, untilDate, timeframe, period);
            
            if (candles.isEmpty()) {
                log.warn("⚠️ API РЕЗУЛЬТАТ: Не удалось получить валидированные свечи");
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "Не удалось получить валидированные свечи",
                        "candlesCount", 0,
                        "candles", List.of()
                ));
            }
            
            log.info("✅ API РЕЗУЛЬТАТ: Возвращаем {} валидированных свечей", candles.size());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Валидированные свечи получены успешно",
                    "candlesCount", candles.size(),
                    "parameters", Map.of(
                            "exchange", exchange,
                            "ticker", ticker,
                            "timeframe", timeframe,
                            "period", period,
                            "untilDate", untilDate
                    ),
                    "timeRange", Map.of(
                            "oldest", candles.get(0).getTimestamp(),
                            "newest", candles.get(candles.size() - 1).getTimestamp()
                    ),
                    "candles", candles
            ));
            
        } catch (Exception e) {
            log.error("❌ API ОШИБКА: Ошибка при получении валидированных свечей: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Ошибка при получении валидированных свечей: " + e.getMessage(),
                    "candlesCount", 0
            ));
        }
    }
    
    /**
     * Загрузить и сохранить свечи с OKX
     * 
     * POST /api/candles-processor/load-and-save
     * Body: {"exchange": "OKX", "ticker": "BTC-USDT-SWAP", "timeframe": "1H", "period": "1year"}
     */
    @PostMapping("/load-and-save")
    public ResponseEntity<?> loadAndSaveCandles(@RequestBody Map<String, String> request) {
        String exchange = request.getOrDefault("exchange", "OKX");
        String ticker = request.get("ticker");
        String timeframe = request.getOrDefault("timeframe", "1H");
        String period = request.getOrDefault("period", "1year");
        
        log.info("🚀 API ЗАПРОС: Загрузка и сохранение свечей");
        log.info("📊 ПАРАМЕТРЫ: exchange={}, ticker={}, timeframe={}, period={}", exchange, ticker, timeframe, period);
        
        if (ticker == null || ticker.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Параметр ticker обязателен"
            ));
        }
        
        try {
            // Генерируем дату "до"
            String untilDate = generateUntilDate();
            log.info("📅 ДАТА ДО: {}", untilDate);
            
            // Загружаем и сохраняем свечи
            int savedCount = candlesLoaderProcessor.loadAndSaveCandles(exchange, ticker, untilDate, timeframe, period);
            
            if (savedCount > 0) {
                log.info("✅ API РЕЗУЛЬТАТ: Успешно загружено и сохранено {} свечей", savedCount);
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Свечи успешно загружены и сохранены",
                        "savedCount", savedCount,
                        "parameters", Map.of(
                                "exchange", exchange,
                                "ticker", ticker,
                                "timeframe", timeframe,
                                "period", period,
                                "untilDate", untilDate
                        )
                ));
            } else {
                log.warn("⚠️ API РЕЗУЛЬТАТ: Не удалось загрузить свечи");
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "Не удалось загрузить свечи",
                        "savedCount", 0
                ));
            }
            
        } catch (Exception e) {
            log.error("❌ API ОШИБКА: Ошибка при загрузке свечей: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Ошибка при загрузке свечей: " + e.getMessage(),
                    "savedCount", 0
            ));
        }
    }
    
    /**
     * Генерирует дату "до" как начало текущего дня в формате 2025-09-27T00:00:00Z
     */
    private String generateUntilDate() {
        return LocalDate.now().atStartOfDay().toString() + ":00.000Z";
    }
}