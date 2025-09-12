package com.example.candles.controller;

import com.example.candles.client.OkxFeignClient;
import com.example.candles.dto.ExtendedCandlesRequest;
import com.example.candles.service.CandlesService;
import com.example.shared.dto.Candle;
import com.example.shared.dto.CandlesRequest;
import com.example.shared.models.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    /**
     * Расширенный эндпоинт для получения большого количества свечей с пагинацией
     * Поддерживает получение более 300 свечей через несколько запросов к OKX API
     */
    @PostMapping("/all-extended")
    public Map<String, List<Candle>> getAllCandlesExtended(@RequestBody ExtendedCandlesRequest request) {
        log.info("🔍 Расширенный запрос {} свечей для таймфрейма {}...",
                request.getCandleLimit(), request.getTimeframe());

        long startTime = System.currentTimeMillis();

        // Конвертируем ExtendedCandlesRequest в Settings
        Settings settings = convertToSettings(request);

        // Получаем все доступные тикеры
        List<String> swapTickers = okxFeignClient.getAllSwapTickers(true);

        // Используем расширенный сервис для получения большого количества свечей
        Map<String, List<Candle>> result = candlesService.getCandlesExtended(settings, swapTickers, request.getCandleLimit());

        long elapsed = System.currentTimeMillis() - startTime;

        if (result != null && !result.isEmpty()) {
            int totalCandles = result.values().stream().mapToInt(List::size).sum();
            int avgCandles = totalCandles / result.size();
            log.info("✅ Расширенный запрос завершен за {} сек. Получено {} тикеров со средним количеством {} свечей (всего {} свечей)",
                    String.format("%.2f", elapsed / 1000.0), result.size(), avgCandles, totalCandles);
        } else {
            log.warn("⚠️ Расширенный запрос не вернул данных");
        }

        return result != null ? result : Map.of();
    }

    /**
     * Конвертация ExtendedCandlesRequest в Settings
     */
    private Settings convertToSettings(ExtendedCandlesRequest request) {
        Settings settings = new Settings();
        settings.setTimeframe(request.getTimeframe());
        settings.setCandleLimit(request.getCandleLimit());
        settings.setMinVolume(request.getMinVolume());
        settings.setUseMinVolumeFilter(request.isUseMinVolumeFilter());
        settings.setMinimumLotBlacklist(request.getMinimumLotBlacklist() != null ? request.getMinimumLotBlacklist() : "");

        // Устанавливаем разумные значения по умолчанию для остальных полей
        settings.setMinCorrelation(0.1);
        settings.setMinWindowSize(100);
        settings.setMaxPValue(1.0);
        settings.setMaxAdfValue(1.0);
        settings.setMinRSquared(0.1);

        return settings;
    }
}