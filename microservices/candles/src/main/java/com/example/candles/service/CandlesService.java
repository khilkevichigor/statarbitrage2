//package com.example.candles.service;
//
//import com.example.candles.client.OkxFeignClient;
//import com.example.candles.service.CandleCacheService;
//import com.example.shared.dto.Candle;
//import com.example.shared.dto.ExtendedCandlesRequest;
//import com.example.shared.models.Settings;
//import com.example.shared.models.Pair;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//import org.springframework.web.bind.annotation.RequestBody;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.stream.Collectors;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class CandlesService {
//
//    private final OkxFeignClient okxFeignClient;
//    private final CandleCacheService candleCacheService;
//    private final DirectCandlesService directCandlesService;
//
////    public Map<String, List<Candle>> getAllCandlesExtended(ExtendedCandlesRequest request) {
////        // Проверяем, использовать ли кэш (по умолчанию true)
////        boolean useCache = request.getUseCache() != null ? request.getUseCache() : true;
////
////        if (useCache) {
////            log.info("💾 Запрос {} свечей для таймфрейма {} ИЗ КЭША",
////                    request.getCandleLimit(), request.getTimeframe());
////            return getAllCandlesFromCache(request);
////        } else {
////            log.info("🚀 Запрос {} свечей для таймфрейма {} НАПРЯМУЮ С OKX (без кэша)",
////                    request.getCandleLimit(), request.getTimeframe());
////            return directCandlesService.loadCandlesDirectly(request);
////        }
////    }
//
////    /**
////     * Получение свечей из кэша (старый метод)
////     */
////    private Map<String, List<Candle>> getAllCandlesFromCache(ExtendedCandlesRequest request) {
////        long startTime = System.currentTimeMillis();
////
////        // Получаем тикеры: используем переданный список или все доступные
////        List<String> swapTickers;
////        final List<String> originalRequestedTickers; // Сохраняем оригинальный список для фильтрации результата
////
////        if (request.getTickers() != null && !request.getTickers().isEmpty()) {
////            log.info("📝 Используем переданный список из {} тикеров", request.getTickers().size());
////            originalRequestedTickers = new ArrayList<>(request.getTickers()); // Сохраняем оригинальный список
////            swapTickers = new ArrayList<>(request.getTickers());
////
////            // Добавляем BTC-USDT-SWAP как эталон если его нет в списке
////            if (!swapTickers.contains("BTC-USDT-SWAP")) {
////                swapTickers.add("BTC-USDT-SWAP");
////                log.info("🎯 Добавлен BTC-USDT-SWAP как эталон для валидации (всего {} тикеров для загрузки)", swapTickers.size());
////            }
////        } else {
////            log.info("🌐 Получаем все доступные тикеры");
////            originalRequestedTickers = null; // При загрузке всех тикеров фильтрация не нужна
////            swapTickers = okxFeignClient.getAllSwapTickers(true);
////
////            // Исключаем тикеры из excludeTickers если они указаны
////            if (request.getExcludeTickers() != null && !request.getExcludeTickers().isEmpty()) {
////                log.info("❌ Исключаем {} тикеров из результата", request.getExcludeTickers().size());
////                swapTickers = swapTickers.stream()
////                        .filter(ticker -> !request.getExcludeTickers().contains(ticker))
////                        .toList();
////                log.info("✅ После исключения осталось {} тикеров", swapTickers.size());
////            }
////        }
////
////        Map<String, List<Candle>> result;
////
////        // Идем в КЭШ
////        result = candleCacheService.getCachedCandles(
////                swapTickers,
////                request.getTimeframe(),
////                request.getCandleLimit(),
////                "OKX"
////        );
////
////        long elapsed = System.currentTimeMillis() - startTime;
////
////        if (result != null && !result.isEmpty()) {
////            int totalCandles = result.values().stream().mapToInt(List::size).sum();
////            int avgCandles = totalCandles / result.size();
////            log.info("💾 Запрос ИЗ КЭША завершен за {} мс! Получено {} тикеров со средним количеством {} свечей (всего {} свечей)",
////                    elapsed, result.size(), avgCandles, totalCandles);
////
////            // Если были переданы конкретные тикеры, возвращаем только их (исключаем добавленный BTC эталон)
////            if (originalRequestedTickers != null) {
////                Map<String, List<Candle>> filteredResult = result.entrySet().stream()
////                        .filter(entry -> originalRequestedTickers.contains(entry.getKey()))
////                        .collect(Collectors.toMap(
////                                Map.Entry::getKey,
////                                Map.Entry::getValue
////                        ));
////
////                log.info("🎯 Отфильтрованы результаты: возвращаем {} из {} тикеров (исключен BTC эталон)",
////                        filteredResult.size(), result.size());
////                return filteredResult;
////            }
////        } else {
////            log.warn("⚠️ Кэш не содержит данных - проверьте работу предзагрузки!");
////        }
////
////        return result != null ? result : Map.of();
////    }
//
//}
