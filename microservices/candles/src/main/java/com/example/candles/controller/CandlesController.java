package com.example.candles.controller;

import com.example.candles.client.OkxFeignClient;
import com.example.candles.service.CandleCacheService;
import com.example.candles.service.CandlesService;
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

import java.util.ArrayList;
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

    @Deprecated
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
     * Эндпоинт для анализа стабильности - получает ВСЕ доступные свечи
     * Использует пустой список тикеров для получения всех доступных тикеров
     */
    @Deprecated
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
        final List<String> originalRequestedTickers; // Сохраняем оригинальный список для фильтрации результата

        if (request.getTickers() != null && !request.getTickers().isEmpty()) {
            log.info("📝 Используем переданный список из {} тикеров", request.getTickers().size());
            originalRequestedTickers = new ArrayList<>(request.getTickers()); // Сохраняем оригинальный список
            swapTickers = new ArrayList<>(request.getTickers());

            // Добавляем BTC-USDT-SWAP как эталон если его нет в списке
            if (!swapTickers.contains("BTC-USDT-SWAP")) {
                swapTickers.add("BTC-USDT-SWAP");
                log.info("🎯 Добавлен BTC-USDT-SWAP как эталон для валидации (всего {} тикеров для загрузки)", swapTickers.size());
            }
        } else {
            log.info("🌐 Получаем все доступные тикеры");
            originalRequestedTickers = null; // При загрузке всех тикеров фильтрация не нужна
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

        Map<String, List<Candle>> result;

        result = candleCacheService.getCachedCandles(
                swapTickers,
                request.getTimeframe(),
                request.getCandleLimit(),
                "OKX"
        );

        long elapsed = System.currentTimeMillis() - startTime;

        if (result != null && !result.isEmpty()) {
            int totalCandles = result.values().stream().mapToInt(List::size).sum();
            int avgCandles = totalCandles / result.size();
            log.info("⚡ Запрос ИЗ КЭША завершен за {} мс! Получено {} тикеров со средним количеством {} свечей (всего {} свечей)",
                    elapsed, result.size(), avgCandles, totalCandles);

            // Если были переданы конкретные тикеры, возвращаем только их (исключаем добавленный BTC эталон)
            if (originalRequestedTickers != null) {
                Map<String, List<Candle>> filteredResult = result.entrySet().stream()
                        .filter(entry -> originalRequestedTickers.contains(entry.getKey()))
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue
                        ));

                log.info("🎯 Отфильтрованы результаты: возвращаем {} из {} тикеров (исключен BTC эталон)",
                        filteredResult.size(), result.size());
                return filteredResult;
            }
        } else {
            log.warn("⚠️ Кэш не содержит данных - проверьте работу предзагрузки!");
        }

        return result != null ? result : Map.of();
    }
}