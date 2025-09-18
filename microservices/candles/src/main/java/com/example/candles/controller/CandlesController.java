package com.example.candles.controller;

import com.example.candles.client.OkxFeignClient;
import com.example.candles.service.CandlesService;
import com.example.candles.service.CandleCacheService;
import com.example.shared.dto.Candle;
import com.example.shared.dto.CandlesRequest;
import com.example.shared.dto.ExtendedCandlesRequest;
import com.example.shared.models.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/candles")
@RequiredArgsConstructor
@Slf4j
public class CandlesController {

    private final CandlesService candlesService;
    private final CandleCacheService candleCacheService;
    private final OkxFeignClient okxFeignClient;

    @PostMapping("/applicable-map")
    public Map<String, List<Candle>> getApplicableCandlesMap(@RequestBody CandlesRequest request) {
        try {
            if (request.isUsePairData()) {
                log.debug("📊 Запрос свечей для пары: {} (лимит: {})",
                        request.getTradingPair().getPairName(), request.getSettings().getCandleLimit());
                return candlesService.getApplicableCandlesMap(request.getTradingPair(), request.getSettings());
            } else {
                log.debug("📊 Запрос свечей для {} тикеров (лимит: {})",
                        request.getTradingTickers().size(), request.getSettings().getCandleLimit());
                return candlesService.getApplicableCandlesMap(request.getSettings(), request.getTradingTickers());
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при получении свечей: {}", e.getMessage(), e);

            // Возвращаем пустую карту вместо выброса исключения
            // Это предотвратит падение Python API запросов
            return Map.of();
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
     * ИСПРАВЛЕНО: Быстрый эндпоинт получения свечей ИЗ КЭША - АК-47 подход! 
     * Больше никаких медленных запросов к OKX API - только из локальной базы
     */
    @PostMapping("/all-extended")
    public Map<String, List<Candle>> getAllCandlesExtended(@RequestBody ExtendedCandlesRequest request) {
        log.info("⚡ АК-47: Быстрый запрос {} свечей для таймфрейма {} ИЗ КЭША",
                request.getCandleLimit(), request.getTimeframe());

        long startTime = System.currentTimeMillis();

        // Получаем тикеры: используем переданный список или все доступные
        List<String> swapTickers;
        if (request.getTickers() != null && !request.getTickers().isEmpty()) {
            log.info("📝 Используем переданный список из {} тикеров", request.getTickers().size());
            swapTickers = request.getTickers();
        } else {
            log.info("🌐 Получаем все доступные тикеры");
            swapTickers = okxFeignClient.getAllSwapTickers(true);

            // Исключаем тикеры из excludeTickers если они указаны
            if (request.getExcludeTickers() != null && !request.getExcludeTickers().isEmpty()) {
                log.info("❌ Исключаем {} тикеров из результата", request.getExcludeTickers().size());
                swapTickers = swapTickers.stream()
                        .filter(ticker -> !request.getExcludeTickers().contains(ticker))
                        .toList();
                log.info("✅ После исключения осталось {} тикеров", swapTickers.size());
            }
        }

        // ИСПРАВЛЕНО: Используем ТОЛЬКО кэш - АК-47 подход!
        Map<String, List<Candle>> result = candleCacheService.getCachedCandles(
                swapTickers, 
                request.getTimeframe(), 
                request.getCandleLimit(), 
                "OKX"
        );

        long elapsed = System.currentTimeMillis() - startTime;

        if (result != null && !result.isEmpty()) {
            int totalCandles = result.values().stream().mapToInt(List::size).sum();
            int avgCandles = totalCandles / result.size();
            log.info("⚡ АК-47: Запрос ИЗ КЭША завершен за {} мс! Получено {} тикеров со средним количеством {} свечей (всего {} свечей)",
                    elapsed, result.size(), avgCandles, totalCandles);
        } else {
            log.warn("⚠️ АК-47: Кэш не содержит данных - проверьте работу предзагрузки!");
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