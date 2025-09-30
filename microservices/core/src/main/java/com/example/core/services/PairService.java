package com.example.core.services;

import com.example.core.client.CandlesFeignClient;
import com.example.core.experemental.stability.dto.StabilityResponseDto;
import com.example.core.repositories.PairRepository;
import com.example.shared.dto.*;
import com.example.shared.enums.PairType;
import com.example.shared.enums.TradeStatus;
import com.example.shared.models.Pair;
import com.example.shared.models.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class PairService {
    private final PairRepository pairRepository;
    private final CalculateChangesService calculateChangesServiceImpl;
    private final EntryPointService entryPointService;
    private final UpdateZScoreDataCurrentService updateZScoreDataCurrentService;
    private final ExcludeExistingTradingPairsService excludeExistingTradingPairsService;
    private final UpdateChangesService updateChangesService;
    private final CreatePairDataService createPairDataService;
    private final CalculateUnrealizedProfitTotalService calculateUnrealizedProfitTotalService;
    private final PortfolioService portfolioServiceImpl;
    private final UpdateSettingsParamService updateSettingsParamService;
    private final SearchStablePairService searchStablePairService;
    private final CandlesFeignClient candlesFeignClient;
    private final SettingsService settingsService;
    private final PythonAnalysisService pythonAnalysisService; // Заменили ZScoreService на PythonAnalysisService
    private final ChartService chartService;

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
                    .build();

            // Получаем свечи для пары
            Map<String, List<Candle>> candlesMap = candlesFeignClient.getValidatedCandlesExtended(extendedRequest);

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
                updateZScoreDataCurrent(tradingPair, zScoreData);

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
    public void deletePair(Pair pair) {
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
     * Получить пары в мониторинге
     */
    public List<Pair> getMonitoringPairs() {
        return pairRepository.findStablePairsInMonitoring();
    }

    /**
     * Получить все найденные стабильные пары (не в мониторинге)
     */
    public List<Pair> getAllFoundPairs() {
        return pairRepository.findFoundStablePairs();
    }

    /**
     * Поиск стабильных пар с множественными таймфреймами и периодами
     */
    public StabilityResponseDto searchStablePairs(Set<String> timeframes, Set<String> periods,
                                                  Map<String, Object> searchSettings) {
        return searchStablePairService.searchStablePairs(timeframes, periods, searchSettings);
    }

    public List<Pair> createPairDataList(List<ZScoreData> top, Map<String, List<Candle>> candlesMap) {
        List<Pair> pairs = createPairDataService.createPairs(top, candlesMap);
        // Сохраняем с обработкой конфликтов
        List<Pair> savedPairs = new ArrayList<>();
        for (Pair pair : pairs) {
            try {
                save(pair);
                savedPairs.add(pair);
            } catch (RuntimeException e) {
                log.warn("⚠️ Не удалось сохранить пару {}/{}: {} - пропускаем",
                        pair.getLongTicker(), pair.getShortTicker(), e.getMessage());
                // Продолжаем обработку остальных пар
            }
        }

        log.debug("✅ Успешно сохранено {}/{} пар", savedPairs.size(), pairs.size());

        return pairs;
    }

    public void updateZScoreDataCurrent(Pair tradingPair, ZScoreData zScoreData) {
        updateZScoreDataCurrentService.updateCurrent(tradingPair, zScoreData);
    }

    public void save(Pair tradingPair) {
        tradingPair.setUpdatedTime(LocalDateTime.now()); //перед сохранением обновляем время
        pairRepository.save(tradingPair);
    }

    public void saveAll(List<Pair> tradingPairList) {
        tradingPairList.forEach(pairData -> pairData.setUpdatedTime(LocalDateTime.now()));
        pairRepository.saveAll(tradingPairList);
    }

    public Pair findById(Long id) {
        return pairRepository.findById(id).orElse(null);
    }

    public List<Pair> findAllByStatusOrderByEntryTimeTodayDesc(TradeStatus status) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        return pairRepository.findTradingPairsByStatusAndEntryTimeAfter(status, startOfDay);
    }

    public List<Pair> findAllByStatusOrderByEntryTimeDesc(TradeStatus status) {
        return pairRepository.findTradingPairsByStatus(status);
    }

    public List<Pair> findAllByStatusOrderByUpdatedTimeDesc(TradeStatus status) {
        return pairRepository.findTradingPairsByStatus(status);
    }

    public List<Pair> findAllByStatusIn(List<TradeStatus> statuses) {
        List<Pair> result = new ArrayList<>();
        for (TradeStatus status : statuses) {
            result.addAll(pairRepository.findTradingPairsByStatus(status));
        }
        return result;
    }

    public List<Pair> findByTickers(String longTicker, String shortTicker) {
        return pairRepository.findByTickerAAndTickerB(longTicker, shortTicker)
                .stream()
                .filter(pair -> pair.getType() == PairType.TRADING)
                .toList();
    }

    @Transactional
    public int deleteAllByStatus(TradeStatus status) {
        return pairRepository.deleteByTypeAndStatus(PairType.TRADING, status);
    }

    public void delete(Pair tradingPair) {
        pairRepository.delete(tradingPair);
    }

    public void excludeExistingTradingPairs(List<ZScoreData> zScoreDataList) {
        excludeExistingTradingPairsService.exclude(zScoreDataList);
    }

    public BigDecimal getUnrealizedProfitPercentTotal() {
        return calculateUnrealizedProfitTotalService.getUnrealizedProfitPercentTotal();
    }

    public BigDecimal getUnrealizedProfitUSDTTotal() {
        return calculateUnrealizedProfitTotalService.getUnrealizedProfitUSDTTotal();
    }

    public void addEntryPoints(Pair tradingPair, ZScoreData zScoreData, TradeResult openLongTradeResult, TradeResult openShortTradeResult) {
        entryPointService.addEntryPoints(tradingPair, zScoreData, openLongTradeResult, openShortTradeResult);
    }

    public void addChanges(Pair tradingPair) {
        ChangesData changes = calculateChangesServiceImpl.getChanges(tradingPair);
        updateChangesService.update(tradingPair, changes);
    }

    public void updatePortfolioBalanceBeforeTradeUSDT(Pair tradingPair) {
        portfolioServiceImpl.updatePortfolioBalanceBeforeTradeUSDT(tradingPair);
    }

    public void updatePortfolioBalanceAfterTradeUSDT(Pair tradingPair) {
        portfolioServiceImpl.updatePortfolioBalanceAfterTradeUSDT(tradingPair);
    }

    public void updateSettingsParam(Pair tradingPair, Settings settings) {
        updateSettingsParamService.updateSettingsParam(tradingPair, settings);
    }
}
