package com.example.core.services;

import com.example.core.client.CandlesFeignClient;
import com.example.core.experemental.stability.dto.StabilityResponseDto;
import com.example.core.repositories.PairRepository;
import com.example.core.services.chart.ChartService;
import com.example.shared.dto.*;
import com.example.shared.enums.PairType;
import com.example.shared.enums.TradeStatus;
import com.example.shared.models.Pair;
import com.example.shared.models.Settings;
import com.example.shared.models.StablePairsScreenerSettings;
import com.example.shared.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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
    private final PythonAnalysisService pythonAnalysisService;
    private final ChartService chartService;
    private final StablePairsScreenerSettingsService stablePairsScreenerSettingsService;
    private final StablePairsService stablePairsService;

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
            // Теперь stat[0] может быть StabilityRating enum, конвертируем в строку
            String ratingString = stat[0] != null ? stat[0].toString() : "UNKNOWN";
            ratingStats.put(ratingString, (Long) stat[1]);
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
            log.debug("🧮 Расчет Z-Score для стабильной пары {}", stablePair.getPairName());

            // Получаем настройки системы
            Settings settings = settingsService.getSettings();

            // ИСПРАВЛЕНИЕ: Используем точно те же параметры что и при поиске стабильных пар
            String timeframe = stablePair.getTimeframe() != null ? stablePair.getTimeframe() : settings.getTimeframe();
            String period = stablePair.getPeriod() != null ? stablePair.getPeriod() : settings.calculateCurrentPeriod();
            // КРИТИЧНО: Используем реальное количество свечей из найденной пары!
            int candleLimit = stablePair.getCandleCount() != null ? stablePair.getCandleCount() : 1000; //todo 1000???

            log.debug("🔧 ИСПРАВЛЕНИЕ: Используем точно те же параметры что и при поиске - timeframe: {}, period: {}, candleCount: {}",
                    timeframe, period, candleLimit);

            // Создаем запрос для получения свечей конкретной пары
            ExtendedCandlesRequest extendedRequest = ExtendedCandlesRequest.builder()
                    .timeframe(timeframe)
                    .candleLimit(candleLimit)
                    .minVolume(settings.getMinVolume() != 0.0 ? settings.getMinVolume() * 1_000_000 : 50_000_000)
                    .tickers(List.of(stablePair.getTickerA(), stablePair.getTickerB()))
                    .period(period)
                    .untilDate(StringUtils.getCurrentDateTimeWithZ())
                    .excludeTickers(null)
                    .exchange("OKX")
                    .useCache(true)
                    .useMinVolumeFilter(true)
                    .minimumLotBlacklist(null)
                    .sorted(true)
                    .build();

            // Получаем свечи для пары
            Map<String, List<Candle>> candlesMap = candlesFeignClient.getValidatedCacheExtended(extendedRequest);

            if (candlesMap == null || candlesMap.isEmpty()) {
                log.warn("⚠️ Не удалось получить данные свечей для пары {}", stablePair.getPairName());
                return null;
            }

            // КРИТИЧЕСКАЯ ПРОВЕРКА: Для конкретной пары должны быть ОБА тикера!
            if (candlesMap.size() != 2) {
                log.error("❌ CANDLES ФИЛЬТРАЦИЯ: Candles-сервис вернул {} тикеров вместо 2 для пары {}!",
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

            log.debug("✅ ВАЛИДАЦИЯ СВЕЧЕЙ: Пара {} имеет одинаковое количество свечей: {}",
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
            log.debug("🔍 Вызываем pythonAnalysisService.calculateZScoreData для пары {}", stablePair.getPairName());
            ZScoreData zScoreData = pythonAnalysisService.calculateZScoreData(settings, candlesMap);
            log.debug("📊 Результат calculateZScoreData для пары {}: {}", stablePair.getPairName(), zScoreData != null ? "OK" : "NULL");

            if (zScoreData != null) {
                // Обновляем Z-Score данные в TradingPair
                updateZScoreDataCurrent(tradingPair, zScoreData);

                // Инициализируем пиксельный спред
                chartService.calculatePixelSpreadIfNeeded(tradingPair);
                chartService.addCurrentPixelSpreadPoint(tradingPair);

                log.debug("✅ Z-Score рассчитан для пары {}. Latest Z-Score: {}",
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

        // Сохраняем изначальный скор при добавлении в мониторинг (если ещё не сохранён)
        if (pair.getTotalScoreEntry() == null && pair.getTotalScore() != null) {
            pair.setTotalScoreEntry(pair.getTotalScore());
            log.info("📊 Сохранён изначальный скор {} для пары {}/{}", pair.getTotalScore(), pair.getTickerA(), pair.getTickerB());
        }

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

    public List<Pair> createPairDataList(List<ZScoreData> zScoreDataList, Map<String, List<Candle>> candlesMap) {
        List<Pair> pairs = createPairDataService.createPairs(zScoreDataList, candlesMap);
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

    public Pair createPair(ZScoreData zScoreData, Map<String, List<Candle>> candlesMap) {
        Pair pair = createPairDataService.buildPairData(zScoreData, candlesMap);
        //todo здесь можно на всякий получать пару FETCHED по pairName и удалять ее если она есть, ну или обновлять
        save(pair);
        return pair;
    }

    public void updateZScoreDataCurrent(Pair pair, ZScoreData zScoreData) {
        updateZScoreDataCurrentService.updateCurrent(pair, zScoreData);
    }

    public void save(Pair pair) {
        pair.setUpdatedTime(LocalDateTime.now()); //перед сохранением обновляем время
        pairRepository.save(pair);
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

    /**
     * Найти пары с ошибками которые действительно торговались (имеют результат торговли)
     */
    public List<Pair> findTradedErrorPairs() {
        return pairRepository.findTradedPairsByStatusOrderByUpdatedTime(TradeStatus.ERROR);
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

    public void addEntryPoints(Pair pair, ZScoreData zScoreData, TradeResult openLongTradeResult, TradeResult openShortTradeResult) {
        entryPointService.addEntryPoints(pair, zScoreData, openLongTradeResult, openShortTradeResult);
    }

    public void addChanges(Pair pair) {
        ChangesData changes = calculateChangesServiceImpl.getChanges(pair);
        updateChangesService.update(pair, changes);
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

    /**
     * Обновить пару в мониторинге
     * Пересчитывает показатели стабильности для пары
     */
    @Async
    @Transactional
    public CompletableFuture<Boolean> updateMonitoringPairAsync(Long pairId) {
        boolean result = updateMonitoringPairSync(pairId);
        return CompletableFuture.completedFuture(result);
    }

    @Transactional
    public boolean updateMonitoringPairSync(Long pairId) {
        try {
            log.info("🔄 Начало обновления пары ID: {}", pairId);

            Pair pair = pairRepository.findById(pairId)
                    .orElseThrow(() -> new RuntimeException("Пара не найдена: " + pairId));

            if (!pair.getType().isStable()) {
                log.error("❌ Попытка обновления не-стабильной пары ID: {}", pairId);
                return false;
            }

            if (!pair.isInMonitoring()) {
                log.error("❌ Пара ID: {} не находится в мониторинге", pairId);
                return false;
            }

            log.info("📊 Обновление пары {}/{} [{}][{}]",
                    pair.getTickerA(), pair.getTickerB(),
                    pair.getTimeframe(), pair.getPeriod());

            // Получаем настройки системы
            Settings settings = settingsService.getSettings();

            // Используем те же параметры что были при поиске пары
            String timeframe = pair.getTimeframe() != null ? pair.getTimeframe() : settings.getTimeframe();
            String period = pair.getPeriod() != null ? pair.getPeriod() : settings.calculateCurrentPeriod();

            // Рассчитываем правильное количество свечей на основе периода и таймфрейма
            int candleLimit;
            if (pair.getCandleCount() != null) {
                candleLimit = pair.getCandleCount();
            } else {
                // Используем централизованный расчет вместо хардкода 35040
                candleLimit = com.example.core.ui.utils.PeriodOptions.calculateCandleLimit(timeframe, period);
            }

            log.info("🔧 Параметры обновления: timeframe={}, period={}, candleCount={}",
                    timeframe, period, candleLimit);

            // Создаем запрос для получения свежих свечей
            ExtendedCandlesRequest request = ExtendedCandlesRequest.builder()
                    .timeframe(timeframe)
                    .candleLimit(candleLimit)
                    .minVolume(settings.getMinVolume() != 0.0 ? settings.getMinVolume() * 1_000_000 : 50_000_000)
                    .tickers(List.of(pair.getTickerA(), pair.getTickerB()))
                    .period(period)
                    .untilDate(StringUtils.getCurrentDateTimeWithZ())
                    .excludeTickers(null)
                    .exchange("OKX")
                    .useCache(true)
                    .useMinVolumeFilter(true)
                    .minimumLotBlacklist(null)
                    .sorted(true)
                    .build();

            // Получаем свежие данные свечей
            Map<String, List<Candle>> candlesMap = candlesFeignClient.getValidatedCacheExtended(request);

            if (candlesMap == null || candlesMap.size() != 2) {
                log.error("❌ Не удалось получить данные для обоих тикеров пары {}. Получено: {}",
                        pair.getPairName(), candlesMap != null ? candlesMap.keySet() : "null");
                return false;
            }

            List<Candle> longCandles = candlesMap.get(pair.getTickerA());
            List<Candle> shortCandles = candlesMap.get(pair.getTickerB());

            if (longCandles == null || longCandles.isEmpty() ||
                    shortCandles == null || shortCandles.isEmpty()) {
                log.error("❌ Получены пустые данные свечей для пары {}", pair.getPairName());
                return false;
            }

            if (longCandles.size() != shortCandles.size()) {
                log.error("❌ Несоответствие количества свечей для пары {}: {} vs {}",
                        pair.getPairName(), longCandles.size(), shortCandles.size());
                return false;
            }

            log.info("✅ Получено {} свечей для обоих тикеров пары {}",
                    longCandles.size(), pair.getPairName());

            // Пересчитываем показатели стабильности через Python API, используя настройки системы
            StablePairsScreenerSettings screenerSettings = stablePairsScreenerSettingsService.getDefaultSettings();
            Map<String, Object> searchSettings = stablePairsScreenerSettingsService.buildSearchSettingsMap(screenerSettings);

            // Добавляем фильтр по конкретным тикерам для обновления только нужной пары
            searchSettings.put("searchTickers", List.of(pair.getTickerA(), pair.getTickerB()));

            log.info("🔄 Обновление пары {} с настройками: minVolume={}",
                    pair.getPairName(), searchSettings.get("minVolume"));

            try {
                // Используем существующий сервис для поиска стабильных пар
                StabilityResponseDto response = searchStablePairService.searchStablePairs( //todo почему ищем все когда обновление только для конкретной
                        Set.of(timeframe), Set.of(period), searchSettings);

                if (response != null && response.getSuccess() && response.getResults() != null) {
                    // Ищем обновленные данные для нашей пары
                    var updatedResult = response.getResults().stream()
                            .filter(result ->
                                    (result.getTickerA().equals(pair.getTickerA()) &&
                                            result.getTickerB().equals(pair.getTickerB())) ||
                                            (result.getTickerA().equals(pair.getTickerB()) &&
                                                    result.getTickerB().equals(pair.getTickerA())))
                            .findFirst();

                    if (updatedResult.isPresent()) {
                        var result = updatedResult.get();

                        // Обновляем метрики стабильности
                        pair.setTotalScore(result.getTotalScore());
                        pair.setStabilityRating(result.getStabilityRating());
                        pair.setTradeable(result.getIsTradeable());
                        pair.setDataPoints(result.getDataPoints());
                        pair.setCandleCount(longCandles.size());
                        pair.setAnalysisTimeSeconds(result.getAnalysisTimeSeconds());

                        // Обновляем время последнего обновления
                        pair.setUpdatedTime(LocalDateTime.now());

                        // Сохраняем обновленную пару
                        pairRepository.save(pair);

                        log.info("✅ Пара {} успешно обновлена. Новые метрики: score={}, rating={}, tradeable={}",
                                pair.getPairName(), result.getTotalScore(), result.getStabilityRating(), result.getIsTradeable());

                        return true;
                    } else {
                        log.warn("⚠️ Пара {} больше не найдена в результатах анализа стабильности", pair.getPairName());
                        return false;
                    }
                } else {
                    log.error("❌ Анализ стабильности завершился неуспешно для пары {}", pair.getPairName());
                    return false;
                }

            } catch (Exception analysisEx) {
                log.error("❌ Ошибка при анализе стабильности пары {}: {}",
                        pair.getPairName(), analysisEx.getMessage(), analysisEx);
                return false;
            }

        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении пары ID {}: {}", pairId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Очистить все найденные стабильные пары (не в мониторинге)
     * Используется перед сохранением новых результатов поиска
     */
    @Transactional
    public int clearFoundStablePairs() {
        try {
            int deletedCount = pairRepository.deleteAllFoundStablePairs();
            log.debug("🧹 Удалено {} найденных стабильных пар", deletedCount);
            return deletedCount;
        } catch (Exception e) {
            log.error("❌ Ошибка при очистке найденных стабильных пар: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Создает зеркальную пару с положительным Z-Score из пары с отрицательным Z-Score
     * Объединяет создание зеркальной пары и инверсию Z-Score данных
     *
     * @param originalPair       исходная пара с отрицательным Z-Score
     * @param originalZScoreData исходные Z-Score данные с отрицательным значением
     * @param candlesMap         карта свечей
     * @return зеркальная пара с положительным Z-Score или null если не удалось создать
     */
    public Pair createMirrorPairWithPositiveZScore(
            Pair originalPair,
            ZScoreData originalZScoreData,
            Map<String, List<Candle>> candlesMap) {

        if (originalPair == null || originalZScoreData == null) {
            log.warn("⚠️ Не удается создать зеркальную пару: originalPair или originalZScoreData равны null");
            return null;
        }

        try {
            log.debug("🪞 Создание зеркальной пары с положительным Z-Score для {}", originalPair.getPairName());

            // Инвертируем Z-Score данные
//            ZScoreData invertedZScoreData = invertZScoreData(originalZScoreData);

//            if (invertedZScoreData == null ||
//                    invertedZScoreData.getLatestZScore() == null ||
//                    invertedZScoreData.getLatestZScore() <= 0) {
//
//                log.debug("⚠️ Не удалось создать положительный Z-Score для зеркальной пары {}",
//                        originalPair.getPairName());
//                return null;
//            }

            // Создаем зеркальную пару
            Pair mirrorPair = stablePairsService.createMirrorPair(originalPair);

            // Обновляем зеркальную пару с инвертированными Z-Score данными
//            updateZScoreDataCurrent(mirrorPair, invertedZScoreData);

            // Получаем свечи для пары (меняем местами тикеры для зеркальной пары)
//            String tickerA = mirrorPair.getTickerA();
//            String tickerB = mirrorPair.getTickerB();

//            if (candlesMap.containsKey(tickerA) && candlesMap.containsKey(tickerB)) {
//                mirrorPair.setLongTickerCandles(candlesMap.get(tickerB));
//                mirrorPair.setShortTickerCandles(candlesMap.get(tickerA));
//            } else {
//                log.warn("⚠️ Не удалось найти данные свечей для зеркальной пары {}", mirrorPair.getPairName());
//            }

            // Устанавливаем Z-Score
            if (originalZScoreData.getLatestZScore() != null) {
                mirrorPair.setZScoreCurrent(BigDecimal.valueOf(originalZScoreData.getLatestZScore()).multiply(BigDecimal.valueOf(-1)));
            }

            log.debug("✅ Зеркальная пара {}/{} успешно создана с положительным Z-Score: {}",
                    mirrorPair.getTickerA(), mirrorPair.getTickerB(), mirrorPair.getZScoreCurrent());

            return mirrorPair;

        } catch (Exception e) {
            log.error("❌ Ошибка при создании зеркальной пары для {}: {}",
                    originalPair.getPairName(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Обогащает пару данными из стабильных пар с учетом зеркальности
     *
     * @param pair        пара для обогащения
     * @param stablePairs список стабильных пар для поиска
     */
    private void enrichSinglePairWithStableData(Pair pair, List<Pair> stablePairs) {
        try {
            log.debug("🔄 Обогащение пары {} данными из стабильных пар", pair.getPairName());

            String pairName = pair.getPairName();

            // Ищем соответствующую стабильную пару
            Pair matchingStablePair = findMatchingStablePair(pairName, stablePairs);

            if (matchingStablePair != null) {
                // Переносим данные из стабильной пары
                pair.setTotalScore(matchingStablePair.getTotalScore());
                pair.setTotalScoreEntry(matchingStablePair.getTotalScoreEntry());
                pair.setStabilityRating(matchingStablePair.getStabilityRating());

                log.debug("✅ Пара {} обогащена данными: score={}, scoreEntry={}, rating={}",
                        pairName,
                        matchingStablePair.getTotalScore(),
                        matchingStablePair.getTotalScoreEntry(),
                        matchingStablePair.getStabilityRating());
            } else {
                log.debug("⚠️ Не найдена соответствующая стабильная пара для: {}", pairName);
            }

        } catch (Exception e) {
            log.error("❌ Ошибка при обогащении пары {} данными: {}",
                    pair.getPairName(), e.getMessage(), e);
        }
    }

    /**
     * Находит соответствующую стабильную пару с учетом зеркальности
     *
     * @param pairName    название пары (например "BTC/ETH")
     * @param stablePairs список стабильных пар для поиска
     * @return найденная стабильная пара или null
     */
    private Pair findMatchingStablePair(String pairName, List<Pair> stablePairs) {
        if (pairName == null || !pairName.contains("/")) {
            return null;
        }

        // Разбиваем название пары
        String[] parts = pairName.split("/");
        if (parts.length != 2) {
            return null;
        }

        String tickerA = parts[0];
        String tickerB = parts[1];

        // Ищем прямое соответствие или зеркальное
        for (Pair stablePair : stablePairs) {
            String stablePairName = stablePair.getPairName();

            if (pairName.equals(stablePairName)) {
                // Прямое соответствие
                return stablePair;
            }

            // Проверяем зеркальное соответствие (A/B == B/A)
            String mirrorPairName = tickerB + "/" + tickerA;
            if (mirrorPairName.equals(stablePairName)) {
                return stablePair;
            }
        }

        return null;
    }

    /**
     * Создает инвертированную копию ZScoreData для зеркальной пары
     * Меняет знак Z-Score на противоположный и меняет местами тикеры
     *
     * @param originalZScoreData исходные данные Z-Score
     * @return инвертированная копия данных для зеркальной пары
     */
    private ZScoreData invertZScoreData(ZScoreData originalZScoreData) {
        if (originalZScoreData == null) {
            return null;
        }

        try {
            ZScoreData invertedData = new ZScoreData();

            // Меняем местами тикеры
            invertedData.setUnderValuedTicker(originalZScoreData.getOverValuedTicker());
            invertedData.setOverValuedTicker(originalZScoreData.getUnderValuedTicker());

            // Инвертируем Z-Score (меняем знак)
            if (originalZScoreData.getLatestZScore() != null) {
                invertedData.setLatestZScore(-originalZScoreData.getLatestZScore());
            }

            // Копируем остальные параметры без изменений
            invertedData.setPearsonCorr(originalZScoreData.getPearsonCorr());
            invertedData.setPearsonCorrPValue(originalZScoreData.getPearsonCorrPValue());
            invertedData.setJohansenCointPValue(originalZScoreData.getJohansenCointPValue());
            invertedData.setAvgAdfPvalue(originalZScoreData.getAvgAdfPvalue());
            invertedData.setAvgRSquared(originalZScoreData.getAvgRSquared());
            invertedData.setTotalObservations(originalZScoreData.getTotalObservations());
            invertedData.setZScoreHistory(originalZScoreData.getZScoreHistory()); // История остается та же

            log.debug("🔄 Создана инвертированная ZScoreData: {}/{} -> {}/{}, Z-Score: {} -> {}",
                    originalZScoreData.getUnderValuedTicker(), originalZScoreData.getOverValuedTicker(),
                    invertedData.getUnderValuedTicker(), invertedData.getOverValuedTicker(),
                    originalZScoreData.getLatestZScore(), invertedData.getLatestZScore());

            return invertedData;

        } catch (Exception e) {
            log.error("❌ Ошибка при создании инвертированной ZScoreData: {}", e.getMessage(), e);
            return null;
        }
    }
}
