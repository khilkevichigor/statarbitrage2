package com.example.candles.controller;

import com.example.candles.client.OkxFeignClient;
import com.example.candles.service.CandlesService;
import com.example.shared.dto.Candle;
import com.example.shared.dto.CandlesRequest;
import com.example.shared.models.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/candles")
@RequiredArgsConstructor
@Slf4j
public class CandlesController {

    private final CandlesService candlesService;
    private final OkxFeignClient okxFeignClient;

    @PostMapping("/applicable-map")
    public Map<String, List<Candle>> getApplicableCandlesMap(@RequestBody CandlesRequest request) {
        if (request.isUsePairData()) {
            return candlesService.getApplicableCandlesMap(request.getTradingPair(), request.getSettings());
        } else {
            return candlesService.getApplicableCandlesMap(request.getSettings(), request.getTradingTickers());
        }
    }

    /**
     * Новый эндпоинт для анализа стабильности - получает ВСЕ доступные свечи
     * Использует пустой список тикеров для получения всех доступных тикеров
     */
    @PostMapping("/all")
    public Map<String, List<Candle>> getAllCandles(@RequestBody Settings settings) {
        log.info("🔍 Получение всех доступных свечей для анализа стабильности...");
        
        long startTime = System.currentTimeMillis();

        List<String> swapTickers = okxFeignClient.getAllSwapTickers(true);

        // Передаем пустой список тикеров для получения ВСЕХ доступных
        Map<String, List<Candle>> result = candlesService.getCandles(settings, swapTickers, true);
        
        long elapsed = System.currentTimeMillis() - startTime;
        
        log.info("✅ Получено {} тикеров с свечами за {} сек для анализа стабильности", 
                result.size(), String.format("%.2f", elapsed / 1000.0));
        
        if (!result.isEmpty()) {
            log.info("📊 Первые тикеры: {}", 
                    result.keySet().stream()
                            .limit(10)
                            .reduce((a, b) -> a + ", " + b)
                            .orElse(""));
        }
        
        return result;
    }
}