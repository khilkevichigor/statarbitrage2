package com.example.core.services;

import com.example.core.client.CandlesFeignClient;
import com.example.core.experemental.stability.dto.StabilityRequestDto;
import com.example.core.experemental.stability.dto.StabilityResponseDto;
import com.example.core.experemental.stability.dto.StabilityResultDto;
import com.example.core.experemental.stability.service.StabilityAnalysisService;
import com.example.core.repositories.PairRepository;
import com.example.shared.dto.Candle;
import com.example.shared.dto.ExtendedCandlesRequest;
import com.example.shared.dto.ZScoreData;
import com.example.shared.enums.PairType;
import com.example.shared.enums.TradeStatus;
import com.example.shared.models.Pair;
import com.example.shared.models.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Сервис для работы с унифицированной моделью Pair
 * Заменяет функциональность StablePairService с возможностью работы с разными типами пар
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PairService {

    private final PairRepository pairRepository;
    private final StabilityAnalysisService stabilityAnalysisService;
    private final CandlesFeignClient candlesFeignClient;
    private final SettingsService settingsService;
    private final TradingPairService tradingPairService;
    private final PythonAnalysisService pythonAnalysisService; // Заменили ZScoreService на PythonAnalysisService
    private final ChartService chartService;

    // ======== МЕТОДЫ ДЛЯ ПОИСКА СТАБИЛЬНЫХ ПАР ========

    /**
     * Поиск стабильных пар с заданными параметрами (legacy метод)
     */
    @Transactional
    public StabilityResponseDto searchStablePairs(String timeframe, String period,
                                                  Map<String, Object> searchSettings) {
        // Конвертируем в Set для вызова нового метода
        return searchStablePairs(Set.of(timeframe), Set.of(period), searchSettings);
    }

    /**
     * Поиск стабильных пар с множественными таймфреймами и периодами
     */
    public StabilityResponseDto searchStablePairs(Set<String> timeframes, Set<String> periods,
                                                  Map<String, Object> searchSettings) {
        log.info("🔍 Начало поиска стабильных пар: timeframes={}, periods={}", timeframes, periods);

        try {
            // Применяем настройки поиска к параметрам анализа один раз
            Map<String, Object> analysisSettings = buildAnalysisSettings(searchSettings);
            Settings settings = settingsService.getSettings();
            
            // Результаты для аккумуляции
            StabilityResponseDto aggregatedResponse = new StabilityResponseDto();
            aggregatedResponse.setSuccess(true);
            aggregatedResponse.setResults(new ArrayList<>());
            int totalPairsFound = 0;
            int totalPairsAnalyzed = 0;
            
            // Выполняем поиск для каждой комбинации timeframe + period
            for (String timeframe : timeframes) {
                for (String period : periods) {
                    log.info("🔍 Поиск для комбинации: timeframe={}, period={}", timeframe, period);
                    
                    try {
                        // Получаем свечи для конкретной комбинации
                        Map<String, List<Candle>> candlesMap = getCandlesForAnalysis(settings, timeframe, period);

                        if (candlesMap.isEmpty()) {
                            log.warn("⚠️ Не удалось получить данные свечей для timeframe={}, period={}", timeframe, period);
                            continue;
                        }

                        // КРИТИЧЕСКАЯ ВАЛИДАЦИЯ
                        Map<String, List<Candle>> validatedCandlesMap = validateCandlesConsistency(candlesMap, timeframe);
                        
                        if (validatedCandlesMap.isEmpty()) {
                            log.warn("⚠️ После валидации не осталось валидных свечей для timeframe={}, period={}", timeframe, period);
                            continue;
                        }
                        
                        log.info("✅ ВАЛИДАЦИЯ: Из {} тикеров {} прошли валидацию для timeframe={}, period={}", 
                                candlesMap.size(), validatedCandlesMap.size(), timeframe, period);

                        // Создаем запрос для Python API
                        StabilityRequestDto request = new StabilityRequestDto(validatedCandlesMap, analysisSettings);

                        // Выполняем анализ стабильности
                        StabilityResponseDto response = stabilityAnalysisService.analyzeStability(request);

                        if (response.getSuccess() && response.getResults() != null) {
                            // Сохраняем результаты в базу данных с отдельной обработкой ошибок
                            try {
                                saveSearchResults(response, timeframe, period, searchSettings);
                            } catch (Exception saveEx) {
                                log.warn("⚠️ Проблема с сохранением результатов для timeframe={}, period={}: {}", 
                                        timeframe, period, saveEx.getMessage());
                                // Продолжаем работу - не прерываем общий процесс
                            }
                            
                            // Аккумулируем результаты
                            aggregatedResponse.getResults().addAll(response.getResults());
                            totalPairsFound += response.getTradeablePairsFound();
                            totalPairsAnalyzed += response.getTotalPairsAnalyzed();
                            
                            log.info("✅ Поиск для timeframe={}, period={} завершен. Найдено {} торгуемых пар из {}",
                                    timeframe, period, response.getTradeablePairsFound(), response.getTotalPairsAnalyzed());
                        } else {
                            log.warn("⚠️ Поиск для timeframe={}, period={} завершился неуспешно", 
                                    timeframe, period);
                        }
                        
                    } catch (Exception e) {
                        log.error("❌ Ошибка при поиске для timeframe={}, period={}: {}", 
                                timeframe, period, e.getMessage(), e);
                        // Продолжаем обработку других комбинаций
                    }
                }
            }
            
            // Устанавливаем итоговые результаты
            aggregatedResponse.setTradeablePairsFound(totalPairsFound);
            aggregatedResponse.setTotalPairsAnalyzed(totalPairsAnalyzed);
            
            if (totalPairsAnalyzed > 0) {
                log.info("🏁 Общий поиск завершен. Найдено {} торгуемых пар из {} по {} комбинациям",
                        totalPairsFound, totalPairsAnalyzed, timeframes.size() * periods.size());
            } else {
                log.warn("⚠️ Не найдено ни одной пары для анализа по заданным комбинациям");
                aggregatedResponse.setSuccess(false);
            }

            return aggregatedResponse;

        } catch (Exception e) {
            log.error("❌ Ошибка при поиске стабильных пар: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка при поиске стабильных пар: " + e.getMessage(), e);
        }
    }

    // ======== МЕТОДЫ ДЛЯ РАБОТЫ С НАЙДЕННЫМИ ПАРАМИ ========

    /**
     * Получить все найденные стабильные пары (не в мониторинге)
     */
    public List<Pair> getAllFoundPairs() {
        return pairRepository.findFoundStablePairs();
    }

    /**
     * Получить пары в мониторинге
     */
    public List<Pair> getMonitoringPairs() {
        return pairRepository.findStablePairsInMonitoring();
    }

    /**
     * Добавить пару в мониторинг
     */
    @Transactional
    public void addToMonitoring(Long pairId) {
        Pair pair = pairRepository.findById(pairId)
                .orElseThrow(() -> new RuntimeException("Пара не найдена: " + pairId));

        if (!pair.getType().isStable()) {
            throw new IllegalArgumentException("Только стабильные пары можно добавлять в мониторинг");
        }

        pair.setInMonitoring(true);
        pairRepository.save(pair);

        log.info("➕ Пара {}/{} добавлена в мониторинг", pair.getTickerA(), pair.getTickerB());
    }

    /**
     * Удалить пару из мониторинга
     */
    @Transactional
    public void removeFromMonitoring(Long pairId) {
        Pair pair = pairRepository.findById(pairId)
                .orElseThrow(() -> new RuntimeException("Пара не найдена: " + pairId));

        if (!pair.getType().isStable()) {
            throw new IllegalArgumentException("Только стабильные пары можно удалять из мониторинга");
        }

        pair.setInMonitoring(false);
        pairRepository.save(pair);

        log.info("➖ Пара {}/{} удалена из мониторинга", pair.getTickerA(), pair.getTickerB());
    }

    /**
     * Удалить найденную пару
     */
    @Transactional
    public void deleteFoundPair(Long pairId) {
        Pair pair = pairRepository.findById(pairId)
                .orElseThrow(() -> new RuntimeException("Пара не найдена: " + pairId));

        if (pair.getType().isStable() && pair.isInMonitoring()) {
            throw new IllegalArgumentException("Нельзя удалить пару, находящуюся в мониторинге");
        }

        pairRepository.deleteById(pairId);
        log.info("🗑️ Пара удалена: {}", pairId);
    }

    /**
     * Удаление пары по объекту
     */
    @Transactional
    public void delete(Pair pair) {
        if (pair == null) {
            log.warn("Попытка удаления null пары");
            return;
        }
        
        if (pair.getType() != null && pair.getType().isStable() && pair.isInMonitoring()) {
            throw new IllegalArgumentException("Нельзя удалить пару, находящуюся в мониторинге");
        }

        pairRepository.delete(pair);
        log.info("🗑️ Пара удалена: {} (ID: {})", pair.getPairName(), pair.getId());
    }


    /**
     * Очистить все найденные стабильные пары (не в мониторинге)
     */
    @Transactional
    public int clearAllFoundPairs() {
        List<Pair> pairsToDelete = pairRepository.findFoundStablePairs();
        int count = pairsToDelete.size();

        if (count > 0) {
            pairRepository.deleteAll(pairsToDelete);
            log.info("🧹 Очищено {} найденных пар", count);
        }

        return count;
    }

    /**
     * Очистка старых результатов поиска
     */
    @Transactional
    public int cleanupOldResults(int daysToKeep) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        int deletedCount = pairRepository.deleteOldStablePairs(cutoffDate);

        if (deletedCount > 0) {
            log.info("🧹 Удалено {} старых результатов поиска (старше {} дней)", deletedCount, daysToKeep);
        }

        return deletedCount;
    }

    // ======== СТАТИСТИКА И АНАЛИТИКА ========

    /**
     * Получить статистику по найденным парам
     */
    public Map<String, Object> getSearchStatistics() {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        List<Object[]> stats = pairRepository.getStabilityRatingStats(weekAgo);

        Map<String, Object> result = new HashMap<>();
        result.put("totalFound", pairRepository.findFoundStablePairs().size());
        result.put("totalInMonitoring", pairRepository.findStablePairsInMonitoring().size());

        Map<String, Long> ratingStats = new HashMap<>();
        for (Object[] stat : stats) {
            ratingStats.put((String) stat[0], (Long) stat[1]);
        }
        result.put("ratingDistribution", ratingStats);

        // Добавляем общую статистику по типам
        List<Object[]> typeStats = pairRepository.countPairsByType();
        Map<String, Long> typeDistribution = new HashMap<>();
        for (Object[] stat : typeStats) {
            typeDistribution.put(stat[0].toString(), (Long) stat[1]);
        }
        result.put("typeDistribution", typeDistribution);

        return result;
    }

    // ======== Z-SCORE РАСЧЕТЫ ========

    /**
     * Рассчитать Z-Score для стабильной пары и вернуть готовую Pair с данными
     * Используется для предпросмотра пары перед добавлением в мониторинг
     */
    public Pair calculateZScoreForStablePair(Pair stablePair) {
        if (!stablePair.getType().isStable()) {
            throw new IllegalArgumentException("Расчет Z-Score доступен только для стабильных пар");
        }

        try {
            log.info("🧮 Расчет Z-Score для стабильной пары {}", stablePair.getPairName());
            
            // Получаем настройки системы
            Settings settings = settingsService.getSettings();
            
            // ИСПРАВЛЕНИЕ: Используем точно те же параметры что и при поиске стабильных пар
            String timeframe = stablePair.getTimeframe() != null ? stablePair.getTimeframe() : settings.getTimeframe();
            String period = stablePair.getPeriod() != null ? stablePair.getPeriod() : "1 год";
            // КРИТИЧНО: Используем реальное количество свечей из найденной пары!
            int candleLimit = stablePair.getCandleCount() != null ? stablePair.getCandleCount() : 1000;
            
            log.info("🔧 ИСПРАВЛЕНИЕ: Используем точно те же параметры что и при поиске - timeframe: {}, period: {}, candleCount: {}", 
                    timeframe, period, candleLimit);
            
            // Создаем запрос для получения свечей конкретной пары
            ExtendedCandlesRequest extendedRequest = ExtendedCandlesRequest.builder()
                    .timeframe(timeframe)
                    .candleLimit(candleLimit)
                    .minVolume(settings.getMinVolume())
                    .useMinVolumeFilter(settings.isUseMinVolumeFilter())
                    .minimumLotBlacklist(settings.getMinimumLotBlacklist())
                    .tickers(List.of(stablePair.getTickerA(), stablePair.getTickerB()))
                    .excludeTickers(null)
                    .skipValidation(true) // Пропускаем валидацию для конкретных пар
                    .build();
            
            // Получаем свечи для пары
            Map<String, List<Candle>> candlesMap = candlesFeignClient.getAllCandlesExtended(extendedRequest);
            
            if (candlesMap == null || candlesMap.isEmpty()) {
                log.warn("⚠️ Не удалось получить данные свечей для пары {}", stablePair.getPairName());
                return null;
            }
            
            // КРИТИЧЕСКАЯ ПРОВЕРКА: Для конкретной пары должны быть ОБА тикера!
            if (candlesMap.size() != 2) {
                log.error("❌ CANDLES ФИЛЬТРАЦИЯ: Candles-сервис вернул {} тикеров вместо 2 для пары {} - один тикер отфильтрован!", 
                        candlesMap.size(), stablePair.getPairName());
                log.error("❌ ДОСТУПНЫЕ ТИКЕРЫ: {}", candlesMap.keySet());
                throw new IllegalStateException(String.format(
                    "Не удалось получить данные для обоих тикеров пары %s. Candles-сервис вернул только %d из 2 тикеров: %s", 
                    stablePair.getPairName(), candlesMap.size(), candlesMap.keySet()));
            }
            
            // Проверяем наличие свечей для обоих тикеров
            List<Candle> longCandles = candlesMap.get(stablePair.getTickerA());
            List<Candle> shortCandles = candlesMap.get(stablePair.getTickerB());
            
            if (longCandles == null || longCandles.isEmpty() || 
                shortCandles == null || shortCandles.isEmpty()) {
                log.warn("⚠️ Недостаточно данных свечей для пары {} (long: {}, short: {})", 
                        stablePair.getPairName(),
                        longCandles != null ? longCandles.size() : 0,
                        shortCandles != null ? shortCandles.size() : 0);
                return null;
            }
            
            // КРИТИЧЕСКАЯ ПРОВЕРКА: Количество свечей должно быть одинаковым!
            if (longCandles.size() != shortCandles.size()) {
                log.error("❌ НЕСООТВЕТСТВИЕ СВЕЧЕЙ: Пара {} имеет разное количество свечей: {} vs {} - БЛОКИРУЕМ расчет Z-Score!", 
                        stablePair.getPairName(), longCandles.size(), shortCandles.size());
                throw new IllegalStateException(String.format(
                    "Не удалось рассчитать Z-Score для пары %s: разное количество свечей (%d vs %d)", 
                    stablePair.getPairName(), longCandles.size(), shortCandles.size()));
            }
            
            log.info("✅ ВАЛИДАЦИЯ СВЕЧЕЙ: Пара {} имеет одинаковое количество свечей: {}", 
                    stablePair.getPairName(), longCandles.size());
            
            // Создаем временную Pair для расчетов
            Pair tradingPair = new Pair();
            tradingPair.setType(PairType.TRADING);
            tradingPair.setTickerA(stablePair.getTickerA());
            tradingPair.setTickerB(stablePair.getTickerB());
            tradingPair.setPairName(stablePair.getPairName());
            tradingPair.setStatus(TradeStatus.OBSERVED); // Статус "наблюдаемая"
            tradingPair.setLongTickerCandles(longCandles);
            tradingPair.setShortTickerCandles(shortCandles);
            
            // Рассчитываем Z-Score данные
            ZScoreData zScoreData = pythonAnalysisService.calculateZScoreData(settings, candlesMap);
            
            if (zScoreData != null) {
                // Обновляем Z-Score данные в TradingPair
                tradingPairService.updateZScoreDataCurrent(tradingPair, zScoreData);
                
                // Инициализируем пиксельный спред
                chartService.calculatePixelSpreadIfNeeded(tradingPair);
                chartService.addCurrentPixelSpreadPoint(tradingPair);
                
                log.info("✅ Z-Score рассчитан для пары {}. Latest Z-Score: {}", 
                        stablePair.getPairName(), zScoreData.getLatestZScore());
                
                return tradingPair;
            } else {
                log.warn("⚠️ Не удалось рассчитать Z-Score для пары {}", stablePair.getPairName());
                return null;
            }
            
        } catch (Exception e) {
            log.error("❌ Ошибка при расчете Z-Score для стабильной пары {}: {}", 
                    stablePair.getPairName(), e.getMessage(), e);
            return null;
        }
    }

    // ======== ОПЕРАЦИИ ПРЕОБРАЗОВАНИЯ ========

    /**
     * Конвертировать стабильную пару в коинтегрированную
     */
    @Transactional
    public Pair convertToCointegrated(Long pairId) {
        Pair pair = pairRepository.findById(pairId)
                .orElseThrow(() -> new RuntimeException("Пара не найдена: " + pairId));

        if (!pair.getType().canConvertTo(PairType.COINTEGRATED)) {
            throw new IllegalArgumentException("Данный тип пары нельзя конвертировать в коинтегрированную");
        }

        pair.setType(PairType.COINTEGRATED);
        pair.setStatus(TradeStatus.SELECTED);
        pair.setUpdatedTime(LocalDateTime.now());

        pairRepository.save(pair);
        log.info("🔄 Пара {} конвертирована в COINTEGRATED", pair.getPairName());

        return pair;
    }

    /**
     * Конвертировать коинтегрированную пару в торговую
     */
    @Transactional
    public Pair convertToTrading(Long pairId) {
        Pair pair = pairRepository.findById(pairId)
                .orElseThrow(() -> new RuntimeException("Пара не найдена: " + pairId));

        if (!pair.getType().canConvertTo(PairType.TRADING)) {
            throw new IllegalArgumentException("Данный тип пары нельзя конвертировать в торговую");
        }

        pair.setType(PairType.TRADING);
        pair.setStatus(TradeStatus.TRADING);
        pair.setEntryTime(LocalDateTime.now());
        pair.setUpdatedTime(LocalDateTime.now());

        pairRepository.save(pair);
        log.info("🔄 Пара {} конвертирована в TRADING", pair.getPairName());

        return pair;
    }

    // ======== МЕТОДЫ ДЛЯ ИСКЛЮЧЕНИЯ СУЩЕСТВУЮЩИХ ПАР ========

    /**
     * Исключает из списка ZScoreData те пары, которые уже торгуются
     * Используется для предотвращения создания дублирующих торговых пар
     */
    public void excludeExistingPairs(List<ZScoreData> zScoreDataList) {
        if (zScoreDataList == null || zScoreDataList.isEmpty()) {
            log.debug("Список ZScoreData пуст, пропускаем исключение торговых пар.");
            return;
        }

        // Получаем все активные торговые пары
        List<Pair> tradingPairs = pairRepository.findTradingPairsByStatus(TradeStatus.TRADING);
        if (tradingPairs.isEmpty()) {
            log.debug("Нет активных торговых пар, все ZScoreData будут использоваться.");
            return;
        }

        // Создаем набор ключей для быстрого поиска
        Set<String> existingKeys = tradingPairs.stream()
                .map(pair -> buildPairKey(pair.getTickerA(), pair.getTickerB()))
                .collect(Collectors.toSet());

        int beforeSize = zScoreDataList.size();

        // Удаляем ZScoreData для уже торгующихся пар
        zScoreDataList.removeIf(z ->
                existingKeys.contains(buildPairKey(z.getUnderValuedTicker(), z.getOverValuedTicker()))
        );

        int removed = beforeSize - zScoreDataList.size();
        if (removed > 0) {
            log.info("🚫 Исключено {} уже торгующихся пар из ZScoreData", removed);
        } else {
            log.debug("✅ Нет совпадений с активными торговыми парами — ничего не исключено.");
        }
    }

    /**
     * Строит уникальный ключ пары, не зависящий от порядка тикеров
     * Используется для сравнения пар независимо от того, какой тикер указан первым
     */
    private String buildPairKey(String ticker1, String ticker2) {
        return Stream.of(ticker1, ticker2)
                .sorted()
                .collect(Collectors.joining("-"));
    }

    // ======== ВАЛИДАЦИЯ КОНСИСТЕНТНОСТИ СВЕЧЕЙ ========
    
    /**
     * Валидирует консистентность свечей перед отправкой в Python API
     * Убирает тикеры с разным количеством свечей, разными таймштампами начала/конца
     */
    private Map<String, List<Candle>> validateCandlesConsistency(Map<String, List<Candle>> candlesMap, String timeframe) {
        if (candlesMap == null || candlesMap.isEmpty()) {
            return new HashMap<>();
        }
        
        log.info("🔍 ВАЛИДАЦИЯ КОНСИСТЕНТНОСТИ: Проверяем {} тикеров (таймфрейм: {})", 
                candlesMap.size(), timeframe);
        
        // Определяем эталонный тикер (BTC-USDT-SWAP или первый доступный)
        String referenceTicker = candlesMap.containsKey("BTC-USDT-SWAP") ? 
                "BTC-USDT-SWAP" : candlesMap.keySet().iterator().next();
        
        List<Candle> referenceCandles = candlesMap.get(referenceTicker);
        int referenceCount = referenceCandles.size();
        long referenceStart = referenceCandles.get(0).getTimestamp();
        long referenceEnd = referenceCandles.get(referenceCandles.size() - 1).getTimestamp();
        
        log.info("🎯 ЭТАЛОН: {} - {} свечей, {}-{}", 
                referenceTicker, referenceCount, 
                formatTimestamp(referenceStart), formatTimestamp(referenceEnd));
        
        Map<String, List<Candle>> validatedCandles = new HashMap<>();
        List<String> invalidTickers = new ArrayList<>();
        
        // Проверяем каждый тикер на соответствие эталону
        for (Map.Entry<String, List<Candle>> entry : candlesMap.entrySet()) {
            String ticker = entry.getKey();
            List<Candle> candles = entry.getValue();
            
            if (candles.size() == referenceCount &&
                candles.get(0).getTimestamp() == referenceStart &&
                candles.get(candles.size() - 1).getTimestamp() == referenceEnd) {
                
                validatedCandles.put(ticker, candles);
            } else {
                invalidTickers.add(String.format("%s(свечей:%d≠%d, начало:%s≠%s, конец:%s≠%s)", 
                        ticker, candles.size(), referenceCount,
                        formatTimestamp(candles.get(0).getTimestamp()), formatTimestamp(referenceStart),
                        formatTimestamp(candles.get(candles.size() - 1).getTimestamp()), formatTimestamp(referenceEnd)));
            }
        }
        
        int validCount = validatedCandles.size();
        double validPercent = (double) validCount / candlesMap.size() * 100;
        
        log.info("📊 ВАЛИДАЦИЯ РЕЗУЛЬТАТ:");
        log.info("   ✅ Валидные тикеры: {} из {} ({}%)", validCount, candlesMap.size(), String.format("%.1f", validPercent));
        
        if (!invalidTickers.isEmpty()) {
            log.warn("   ❌ Невалидные тикеры ({}): {}", invalidTickers.size(), String.join(", ", invalidTickers));
        }
        
        // Если валидных тикеров менее 100%, это критическая ошибка
        if (validPercent < 100.0) {
            log.error("💥 КРИТИЧЕСКАЯ ОШИБКА: Только {}% тикеров валидны - недостаточно для анализа стабильности!", validPercent);
            return new HashMap<>(); // Возвращаем пустую карту
        }
        
        if (validCount < candlesMap.size()) {
            log.warn("🗑️ ИСКЛЮЧЕНЫ: {} тикеров - {}", invalidTickers.size(), String.join(", ", invalidTickers));
        }
        
        return validatedCandles;
    }
    
    /**
     * Форматирует timestamp для лучшей читаемости в логах
     */
    private String formatTimestamp(long timestamp) {
        if (timestamp <= 0) return "неизвестно";
        
        try {
            // Проверяем формат timestamp: если слишком большой, то в миллисекундах, иначе в секундах
            if (timestamp > 9999999999L) { // больше чем 2001 год в секундах
                return java.time.Instant.ofEpochMilli(timestamp).toString();
            } else {
                return java.time.Instant.ofEpochSecond(timestamp).toString();
            }
        } catch (Exception e) {
            return "ошибка_формата";
        }
    }

    // ======== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ========

    private Map<String, List<Candle>> getCandlesForAnalysis(Settings settings, String timeframe, String period) {
        try {
            // Рассчитываем количество свечей для запрошенного периода
            int candleLimit = calculateCandleLimit(timeframe, period);
            log.info("📊 Запрашиваем {} свечей для таймфрейма {} и периода {}", candleLimit, timeframe, period);

            // Всегда используем расширенный запрос к candles микросервису с пагинацией
            return getCandlesExtended(settings, timeframe, candleLimit);

        } catch (Exception e) {
            log.error("❌ Ошибка при получении свечей: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }

    private Map<String, List<Candle>> getCandlesExtended(Settings settings, String timeframe, int candleLimit) {
        try {
            log.info("📊 Расширенный запрос {} свечей для таймфрейма {} через candles микросервис",
                    candleLimit, timeframe);

            ExtendedCandlesRequest request = ExtendedCandlesRequest.builder()
                    .timeframe(timeframe)
                    .candleLimit(candleLimit)
                    .minVolume(settings.getMinVolume())
                    .useMinVolumeFilter(settings.isUseMinVolumeFilter())
                    .minimumLotBlacklist(settings.getMinimumLotBlacklist())
                    .tickers(null) // Получаем все доступные тикеры
                    .excludeTickers(null) // Никого не исключаем
                    .skipValidation(false) // ВАЖНО: Включаем догрузку недостающих данных с OKX
                    .build();

            Map<String, List<Candle>> result = candlesFeignClient.getAllCandlesExtended(request);

            if (result != null && !result.isEmpty()) {
                int totalCandles = result.values().stream().mapToInt(List::size).sum();
                int avgCandles = result.values().stream().mapToInt(List::size).sum() / result.size();
                log.info("✅ Получено {} тикеров со средним количеством {} свечей (всего {} свечей)",
                        result.size(), avgCandles, totalCandles);
            }

            return result != null ? result : new HashMap<>();

        } catch (Exception e) {
            log.error("❌ Ошибка при расширенном получении свечей: {}", e.getMessage(), e);
            // Fallback к расширенному методу с ограничением
            log.warn("🔄 Используем fallback к расширенному методу с ограничением 300 свечей");
            ExtendedCandlesRequest fallbackRequest = ExtendedCandlesRequest.builder()
                    .timeframe(timeframe)
                    .candleLimit(300)
                    .minVolume(settings.getMinVolume())
                    .useMinVolumeFilter(settings.isUseMinVolumeFilter())
                    .minimumLotBlacklist(settings.getMinimumLotBlacklist())
                    .tickers(null)
                    .excludeTickers(null)
                    .build();
            return candlesFeignClient.getAllCandlesExtended(fallbackRequest);
        }
    }

    private int calculateCandleLimit(String timeframe, String period) {
        int multiplier = switch (period.toLowerCase()) {
            case "день" -> 1;
            case "неделя" -> 7;
            case "месяц" -> 30;
            case "6 месяцев" -> 180;  // 6 месяцев = 180 дней
            case "1 год" -> 365;
            case "2 года" -> 730;
            case "3 года" -> 1095;
            default -> 30;
        };

        int idealLimit = switch (timeframe) {
            case "1m" -> multiplier * 24 * 60;
            case "5m" -> multiplier * 24 * 12;
            case "15m" -> multiplier * 24 * 4;
            case "1H" -> multiplier * 24;
            case "4H" -> multiplier * 6;
            case "1D" -> multiplier;
            case "1W" -> multiplier / 7;
            case "1M" -> multiplier / 30;
            default -> multiplier * 24;
        };

        return Math.max(100, idealLimit);
    }

    private Map<String, Object> buildAnalysisSettings(Map<String, Object> searchSettings) {
        Map<String, Object> settings = new HashMap<>();

        // Устанавливаем значения по умолчанию
        settings.put("minWindowSize", 100);
        settings.put("minCorrelation", 0.1);
        settings.put("maxPValue", 1.0);
        settings.put("maxAdfValue", 1.0);
        settings.put("minRSquared", 0.1);

        // Переопределяем значениями из пользовательских настроек
        if (searchSettings != null) {
            searchSettings.forEach((key, value) -> {
                if (value != null) {
                    settings.put(key, value);
                }
            });
        }

        return settings;
    }

    public void saveSearchResults(StabilityResponseDto response, String timeframe,
                                  String period, Map<String, Object> searchSettings) {
        if (response.getResults() == null || response.getResults().isEmpty()) {
            return;
        }

        int savedCount = 0;
        int skippedCount = 0;

        // Упрощенное сохранение - убираем сложные проверки
        for (StabilityResultDto result : response.getResults()) {
            try {
                // Создаем пару БЕЗ проверки существующих (быстрее и безопаснее)
                Pair pair = Pair.fromStabilityResult(result, timeframe, period, searchSettings);
                if (pair != null && pair.getTickerA() != null && pair.getTickerB() != null) {
                    try {
                        // Сохраняем в отдельной транзакции
                        savePairSafely(pair);
                        savedCount++;
                    } catch (Exception saveEx) {
                        // Тихо игнорируем дубликаты
                        if (saveEx.getMessage() != null && (saveEx.getMessage().contains("unique constraint") || 
                                                           saveEx.getMessage().contains("duplicate key"))) {
                            skippedCount++;
                        } else {
                            log.debug("🔄 Пропускаем пару {}-{} [{}][{}]: {}", 
                                     result.getTickerA(), result.getTickerB(), timeframe, period, saveEx.getMessage());
                            skippedCount++;
                        }
                    }
                } else {
                    skippedCount++;
                }
            } catch (Exception e) {
                log.debug("🔄 Пропускаем пару {}-{} [{}][{}] из-за ошибки создания", 
                         result.getTickerA(), result.getTickerB(), timeframe, period);
                skippedCount++;
            }
        }

        if (savedCount > 0 || skippedCount > 0) {
            log.info("💾 Результаты сохранения [{}][{}]: {} новых пар, {} пропущено дубликатов", 
                    timeframe, period, savedCount, skippedCount);
        }
    }

    /**
     * Безопасное создание и сохранение пары в отдельной транзакции
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void savePairSafely(Pair pair) {
        try {
            // Проверяем существование пары перед сохранением для избежания SQL ошибок
            boolean exists = pairRepository.existsByTickerAAndTickerBAndTimeframeAndPeriodAndType(
                    pair.getTickerA(), pair.getTickerB(), pair.getTimeframe(), pair.getPeriod(), pair.getType());
            
            if (exists) {
                log.debug("🔄 Пара {}/{} уже существует - пропускаем", pair.getTickerA(), pair.getTickerB());
                return;
            }
            
            // Создаем новый объект для избежания проблем с Hibernate session
            Pair detachedPair = new Pair();
            
            // Копируем основные поля (используем реальные поля из Pair класса)
            detachedPair.setType(pair.getType());
            detachedPair.setStatus(pair.getStatus());
            detachedPair.setTickerA(pair.getTickerA());
            detachedPair.setTickerB(pair.getTickerB());
            detachedPair.setPairName(pair.getPairName());
            detachedPair.setTimeframe(pair.getTimeframe());
            detachedPair.setPeriod(pair.getPeriod());
            detachedPair.setSearchDate(pair.getSearchDate());
            detachedPair.setCreatedAt(pair.getCreatedAt());
            
            // Копируем StablePair поля
            detachedPair.setTotalScore(pair.getTotalScore());
            detachedPair.setStabilityRating(pair.getStabilityRating());
            detachedPair.setTradeable(pair.isTradeable());
            detachedPair.setDataPoints(pair.getDataPoints());
            detachedPair.setCandleCount(pair.getCandleCount());
            detachedPair.setAnalysisTimeSeconds(pair.getAnalysisTimeSeconds());
            detachedPair.setSearchSettings(pair.getSearchSettings());
            detachedPair.setAnalysisResults(pair.getAnalysisResults());
            
            // Копируем торговые данные если есть
            if (pair.getZScoreCurrent() != null) {
                detachedPair.setZScoreCurrent(pair.getZScoreCurrent());
            }
            if (pair.getCorrelationCurrent() != null) {
                detachedPair.setCorrelationCurrent(pair.getCorrelationCurrent());
            }
            
            // Сохраняем чистый объект
            pairRepository.save(detachedPair);
            
        } catch (Exception e) {
            // Проверяем на различные типы ошибок дубликатов
            String errorMessage = e.getMessage();
            if (errorMessage != null && (
                errorMessage.contains("duplicate key value violates unique constraint") ||
                errorMessage.contains("uk_stable_pairs_unique") ||
                errorMessage.contains("ConstraintViolationException") ||
                e.getCause() != null && e.getCause().getMessage() != null && 
                e.getCause().getMessage().contains("duplicate key"))) {
                
                log.info("🔄 Пара {}/{} уже существует - пропускаем дубликат",
                         pair.getTickerA(), pair.getTickerB());
                return; // Тихо игнорируем дубликаты
            }
            
            // Все остальные ошибки логируем и перебрасываем
            log.debug("❌ Ошибка сохранения пары: {}", errorMessage);
            throw e;
        }
    }

    // ======== МЕТОДЫ ДЛЯ РАБОТЫ С РАЗНЫМИ ТИПАМИ ПАР ========

    /**
     * Получить пары по типу
     */
    public List<Pair> getPairsByType(PairType type) {
        return pairRepository.findByTypeOrderByCreatedAtDesc(type);
    }

    /**
     * Получить активные торговые пары
     */
    public List<Pair> getActiveTradingPairs() {
        return pairRepository.findActiveTradingPairs();
    }

    /**
     * Получить коинтегрированные пары
     */
    public List<Pair> getCointegrationPairs() {
        return pairRepository.findCointegrationPairs();
    }
}