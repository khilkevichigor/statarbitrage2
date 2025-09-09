package com.example.core.experemental;

import com.example.core.client.CandlesFeignClient;
import com.example.core.experemental.stability.dto.StabilityRequestDto;
import com.example.core.experemental.stability.dto.StabilityResponseDto;
import com.example.core.experemental.stability.service.StabilityAnalysisService;
import com.example.core.services.SettingsService;
import com.example.shared.dto.Candle;
import com.example.shared.dto.CandlesRequest;
import com.example.shared.models.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stability")
@RequiredArgsConstructor
@Slf4j
public class StabilityAnalysisController {

    private final StabilityAnalysisService stabilityAnalysisService;
    private final CandlesFeignClient candlesFeignClient;
    private final SettingsService settingsService;

    /**
     * Эндпоинт для анализа стабильности всех возможных пар
     * Получает все тикеры из системы и отправляет их на анализ стабильности в Python
     */
    @GetMapping("/analyze-all-pairs")
    public ResponseEntity<StabilityResponseDto> analyzeAllPairsStability() {
        log.info("🔍 Начало анализа стабильности всех пар...");
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Получаем настройки из базы
            Settings settings = settingsService.getSettings();
            log.info("📋 Получены настройки: minWindowSize={}, minCorr={}", 
                    settings.getMinWindowSize(), settings.getMinCorrelation());
            
            // Получаем все свечи для всех доступных тикеров (без исключений)
            Map<String, List<Candle>> candlesMap = getAllAvailableCandles(settings);
            
            if (candlesMap.isEmpty()) {
                log.warn("⚠️ Не удалось получить данные свечей");
                return ResponseEntity.badRequest().build();
            }
            
            log.info("📊 Получены данные для {} тикеров: {}", 
                    candlesMap.size(), String.join(", ", candlesMap.keySet()));
            
            // Конвертируем настройки в Map для Python API
            Map<String, Object> settingsMap = convertSettingsToMap(settings);
            
            // Создаем запрос для Python API
            StabilityRequestDto request = new StabilityRequestDto(candlesMap, settingsMap);
            
            // Отправляем запрос на анализ стабильности
            StabilityResponseDto response = stabilityAnalysisService.analyzeStability(request);
            
            long totalTime = System.currentTimeMillis() - startTime;
            log.info("✅ Анализ стабильности завершен за {} секунд. Найдено торгуемых пар: {}/{}", 
                    totalTime / 1000.0, 
                    response.getTradeablePairsFound(), 
                    response.getTotalPairsAnalyzed());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            log.error("❌ Ошибка при анализе стабильности (время: {}с): {}", 
                    totalTime / 1000.0, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Получает все доступные свечи из системы без исключений (как в FetchPairsProcessor)
     */
    private Map<String, List<Candle>> getAllAvailableCandles(Settings settings) {
        try {
            // Создаем запрос БЕЗ указания конкретных тикеров - получаем ВСЕ доступные
            CandlesRequest candlesRequest = new CandlesRequest(settings, null);
            
            long start = System.currentTimeMillis();
            Map<String, List<Candle>> candlesMap = candlesFeignClient.getApplicableCandlesMap(candlesRequest);
            long elapsed = System.currentTimeMillis() - start;
            
            log.info("📈 Загружено свечей для {} тикеров за {} сек", 
                    candlesMap.size(), String.format("%.2f", elapsed / 1000.0));
            
            return candlesMap;
            
        } catch (Exception e) {
            log.error("❌ Ошибка при получении свечей: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }
    
    /**
     * Конвертирует объект Settings в Map для Python API анализа стабильности
     * Python StabilityAnalyzer использует только minWindowSize из настроек
     */
    private Map<String, Object> convertSettingsToMap(Settings settings) {
        Map<String, Object> map = new HashMap<>();
        
        // Основной параметр который использует Python StabilityAnalyzer
        map.put("minWindowSize", (int) settings.getMinWindowSize());
        
        // Добавляем дополнительные параметры, которые могут понадобиться
        map.put("minCorrelation", settings.getMinCorrelation());
        map.put("maxPValue", settings.getMaxPValue());
        map.put("maxAdfValue", settings.getMaxAdfValue());
        map.put("minRSquared", settings.getMinRSquared());
        map.put("minZ", settings.getMinZ());
        map.put("candleLimit", (int) settings.getCandleLimit());
        map.put("timeframe", settings.getTimeframe());
        
        log.info("🔧 Настройки для Python анализа стабильности: minWindowSize={}, остальные параметры включены", 
                (int) settings.getMinWindowSize());
        return map;
    }
}