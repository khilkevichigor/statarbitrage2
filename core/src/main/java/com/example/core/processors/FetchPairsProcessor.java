package com.example.core.processors;

import com.example.core.client.CandlesFeignClient;
import com.example.core.services.PairService;
import com.example.core.services.SettingsService;
import com.example.core.services.StablePairsService;
import com.example.core.services.ZScoreService;
import com.example.shared.dto.Candle;
import com.example.shared.dto.ExtendedCandlesRequest;
import com.example.shared.dto.FetchPairsRequest;
import com.example.shared.dto.ZScoreData;
import com.example.shared.enums.TradeStatus;
import com.example.shared.models.Pair;
import com.example.shared.models.Settings;
import com.example.shared.utils.NumberFormatter;
import com.example.shared.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FetchPairsProcessor {
    private final PairService pairService;
    private final ZScoreService zScoreService;
    private final CandlesFeignClient candlesFeignClient;
    private final SettingsService settingsService;
    private final StablePairsService stablePairsService;

    public List<Pair> fetchPairs(FetchPairsRequest request) {

        if (request == null) {
            throw new IllegalArgumentException("❌ FetchPairsRequest не может быть null");
        }

        long start = System.currentTimeMillis();
        log.info("");
        log.info("🔎 Начало поиска пар...");

        Settings settings = settingsService.getSettings();

        // Проверяем использование стабильных пар
        boolean useMonitoring = settings.isUseStablePairsForMonitoring();
        boolean useFound = settings.isUseFoundStablePairs();

        if (useMonitoring || useFound) {
            log.info("🔍 Используем стабильные пары: мониторинг={}, найденные={}", useMonitoring, useFound);
            return fetchPairsFromStablePairs(settings, request, useMonitoring, useFound);
        }

        // Стандартный путь - получение всех тикеров и их анализ
        List<String> usedTickers = getUsedTickers();
        Map<String, List<Candle>> candlesMap = getCandles(settings, usedTickers);

        if (candlesMap.isEmpty()) {
            log.warn("⚠️ Данные свечей не получены — пропуск поиска.");
            return Collections.emptyList();
        }

        int count = Optional.ofNullable(request.getCountOfPairs())
                .orElse((int) settings.getUsePairs());

        List<ZScoreData> zScoreDataList = computeZScoreData(settings, candlesMap, count);
        if (zScoreDataList.isEmpty()) {
            return Collections.emptyList();
        }

        logZScoreResults(zScoreDataList);

        List<Pair> pairs = createPairs(zScoreDataList, candlesMap);

        log.debug("✅ Создано {} пар", pairs.size());
        pairs.forEach(p -> log.debug("📈 {}", p.getPairName()));
        log.debug("🕒 Время выполнения: {} сек", String.format("%.2f", (System.currentTimeMillis() - start) / 1000.0));

        return pairs;
    }

    private List<String> getUsedTickers() {
        List<Pair> activePairs = pairService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
        List<String> tickers = new ArrayList<>();
        for (Pair pair : activePairs) {
            tickers.add(pair.getLongTicker());
            tickers.add(pair.getShortTicker());
        }
        return tickers;
    }

    private Map<String, List<Candle>> getCandles(Settings settings, List<String> tradingTickers) {
        long start = System.currentTimeMillis();

        log.info("📊 Запрос свечей: таймфрейм={}, лимит={}, исключить_тикеров={}",
                settings.getTimeframe(), (int) settings.getCandleLimit(),
                tradingTickers != null ? tradingTickers.size() : 0);

        List<String> blacklistItems = Arrays.asList(settings.getMinimumLotBlacklist().split(","));
        List<String> excludedTickers = new ArrayList<>();
        excludedTickers.addAll(tradingTickers);
        excludedTickers.addAll(blacklistItems);

        // Создаем ExtendedCandlesRequest для получения свечей через пагинацию
        ExtendedCandlesRequest request = ExtendedCandlesRequest.builder()
                .timeframe(settings.getTimeframe())
                .candleLimit((int) settings.getCandleLimit())
                .minVolume(settings.getMinVolume() != 0.0 ? settings.getMinVolume() * 1_000_000 : 50_000_000)
                .tickers(null) // Получаем все доступные тикеры
                .excludeTickers(excludedTickers)
                .period(settings.calculateCurrentPeriod())
                .untilDate(StringUtils.getCurrentDateTimeWithZ())
                .exchange("OKX")
                .useCache(true)
                .useMinVolumeFilter(true)
                .minimumLotBlacklist(null)
                .build();

        try {
            log.info("⏳ Отправка запроса к candles микросервису...");
            Map<String, List<Candle>> map = candlesFeignClient.getValidatedCacheExtended(request);

            double elapsed = (System.currentTimeMillis() - start) / 1000.0;
            if (map != null && !map.isEmpty()) {
                log.info("✅ Свечи загружены за {} сек. Получено {} тикеров",
                        String.format("%.2f", elapsed), map.size());
            } else {
                log.warn("⚠️ Получен пустой результат за {} сек", String.format("%.2f", elapsed));
            }

            return map != null ? map : new HashMap<>();

        } catch (Exception e) {
            double elapsed = (System.currentTimeMillis() - start) / 1000.0;
            log.error("❌ Ошибка при получении свечей за {} сек: {}",
                    String.format("%.2f", elapsed), e.getMessage());
            return new HashMap<>();
        }
    }

    private List<ZScoreData> computeZScoreData(Settings settings, Map<String, List<Candle>> candlesMap, int count) {
        try {
            return zScoreService.getTopNZScoreData(settings, candlesMap, count);
        } catch (Exception e) {
            log.error("❌ Ошибка при расчете Z-Score: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private void logZScoreResults(List<ZScoreData> dataList) {
        int index = 1;
        for (ZScoreData data : dataList) {
            // Use NumberFormatter.format which handles nulls and returns "N/A"
            String cointegrationPValue = NumberFormatter.format(data.getJohansenCointPValue(), 5);
            String avgAdfPValue = NumberFormatter.format(data.getAvgAdfPvalue(), 5);
            String latestZscore = NumberFormatter.format(data.getLatestZScore(), 2);
            String correlation = NumberFormatter.format(data.getPearsonCorr(), 2);

            log.info(String.format("%d. Пара: underValuedTicker=%s overValuedTicker=%s | p=%s | adf=%s | z=%s | corr=%s",
                    index++, data.getUnderValuedTicker(), data.getOverValuedTicker(),
                    cointegrationPValue, avgAdfPValue, latestZscore, correlation));
        }
    }

    private List<Pair> createPairs(List<ZScoreData> zScoreDataList, Map<String, List<Candle>> candlesMap) {
        try {
            return pairService.createPairDataList(zScoreDataList, candlesMap);
        } catch (Exception e) {
            log.error("❌ Ошибка при создании PairData: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Получение пар из стабильных пар с фильтрами
     *
     * @param settings      настройки
     * @param request       запрос
     * @param useMonitoring использовать ли пары в мониторинге
     * @param useFound      использовать ли найденные пары
     * @return список пар для анализа
     */
    private List<Pair> fetchPairsFromStablePairs(Settings settings, FetchPairsRequest request,
                                                 boolean useMonitoring, boolean useFound) {
        long start = System.currentTimeMillis();

        log.info("🔍 Получение стабильных пар с фильтрами: мониторинг={}, найденные={}", useMonitoring, useFound);

        // Получаем хорошие стабильные пары с учетом настроек
        List<Pair> stablePairs = stablePairsService.getGoodStablePairsBySettings(
                useMonitoring, useFound,
                settings.isUseScoreFiltering(), settings.getMinStabilityScore());

        if (stablePairs.isEmpty()) {
            log.warn("⚠️ Не найдено стабильных пар с указанными фильтрами - возвращаем пустой список");
            return Collections.emptyList();
        }

        log.info("📋 Найдено {} стабильных пар для анализа", stablePairs.size());

        Set<String> uniqueTickersSet = new HashSet<>();
        for (Pair pair : stablePairs) {
            if (pair.getLongTicker() != null) uniqueTickersSet.add(pair.getLongTicker());
            if (pair.getShortTicker() != null) uniqueTickersSet.add(pair.getShortTicker());
        }

        List<String> uniqueTickers = new ArrayList<>(uniqueTickersSet);

        if (uniqueTickers.isEmpty()) {
            log.warn("⚠️ Не удалось извлечь тикеры из стабильных пар");
            return Collections.emptyList();
        }

        log.info("📊 Извлечено {} уникальных тикеров для загрузки свечей: {}",
                uniqueTickers.size(), uniqueTickers);

        // Получаем свечи только для нужных тикеров
        Map<String, List<Candle>> candlesMap = getCandlesForSpecificTickers(settings, uniqueTickers);

        if (candlesMap.isEmpty()) {
            log.warn("⚠️ Данные свечей для стабильных пар не получены — пропуск поиска.");
            throw new IllegalStateException("❌ Не удалось получить данные свечей для стабильных пар");
        }

        // Анализируем исходные стабильные пары и создаем зеркальные при необходимости
        List<Pair> updatedPairs = analyzeAndUpdatePairs(stablePairs, candlesMap, settings, stablePairs);

        if (updatedPairs.isEmpty()) {
            log.warn("⚠️ Не найдено пар с положительным Z-Score");
            return Collections.emptyList();
        }

        log.info("✅ Обновлено {} пар из стабильных источников", updatedPairs.size());
        updatedPairs.forEach(p -> log.info("📈 {}", p.getPairName()));
        log.info("🕒 Время выполнения (стабильные пары): {} сек",
                String.format("%.2f", (System.currentTimeMillis() - start) / 1000.0));

        return updatedPairs;
    }

    /**
     * Получение свечей для конкретных тикеров
     *
     * @param settings настройки
     * @param tickers  список тикеров
     * @return карта свечей по тикерам
     */
    private Map<String, List<Candle>> getCandlesForSpecificTickers(Settings settings, List<String> tickers) {
        long start = System.currentTimeMillis();

        log.info("📊 Запрос свечей для стабильных пар: таймфрейм={}, лимит={}, тикеров={}",
                settings.getTimeframe(), (int) settings.getCandleLimit(), tickers.size());

        // Создаем ExtendedCandlesRequest для получения свечей только нужных тикеров
        ExtendedCandlesRequest request = ExtendedCandlesRequest.builder()
                .timeframe(settings.getTimeframe())
                .candleLimit((int) settings.getCandleLimit())
                .minVolume(settings.getMinVolume() != 0.0 ? settings.getMinVolume() * 1_000_000 : 50_000_000)
                .tickers(tickers) // Передаем конкретные тикеры
                .excludeTickers(Collections.emptyList())
                .period(settings.calculateCurrentPeriod())
                .untilDate(StringUtils.getCurrentDateTimeWithZ())
                .exchange("OKX")
                .useCache(true)
                .useMinVolumeFilter(true)
                .minimumLotBlacklist(null)
                .build();

        try {
            log.debug("⏳ Отправка запроса к candles микросервису для стабильных пар...");
            Map<String, List<Candle>> map = candlesFeignClient.getValidatedCacheExtended(request);

            double elapsed = (System.currentTimeMillis() - start) / 1000.0;
            if (map != null && !map.isEmpty()) {
                log.info("✅ Свечи для стабильных пар загружены за {} сек. Получено {} тикеров",
                        String.format("%.2f", elapsed), map.size());
            } else {
                log.warn("⚠️ Получен пустой результат для стабильных пар за {} сек",
                        String.format("%.2f", elapsed));
            }

            return map != null ? map : new HashMap<>();

        } catch (Exception e) {
            double elapsed = (System.currentTimeMillis() - start) / 1000.0;
            log.error("❌ Ошибка при получении свечей для стабильных пар за {} сек: {}",
                    String.format("%.2f", elapsed), e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Анализирует и обновляет конкретные стабильные пары с Z-Score данными
     *
     * @param allPairsWithMirrors все пары включая зеркальные
     * @param candlesMap          карта свечей
     * @param settings            настройки
     * @param originalStablePairs исходные стабильные пары для обогащения
     * @return список обновленных пар с положительным Z-Score
     */
    private List<Pair> analyzeAndUpdatePairs(List<Pair> allPairsWithMirrors, Map<String, List<Candle>> candlesMap,
                                             Settings settings, List<Pair> originalStablePairs) {
        try {
            log.info("📊 Анализ и обновление {} пар (включая зеркальные)", allPairsWithMirrors.size());

            List<Pair> updatedPairs = new ArrayList<>();

            for (Pair pair : allPairsWithMirrors) {
                try {
                    // Создаем карту свечей только для конкретной пары
                    Map<String, List<Candle>> pairCandlesMap = new HashMap<>();

                    String tickerA = pair.getTickerA();
                    String tickerB = pair.getTickerB();

                    if (candlesMap.containsKey(tickerA) && candlesMap.containsKey(tickerB)) {
                        pairCandlesMap.put(tickerA, candlesMap.get(tickerA));
                        pairCandlesMap.put(tickerB, candlesMap.get(tickerB));

                        log.debug("🔍 Анализ пары: {}/{}", tickerA, tickerB);

                        // Рассчитываем Z-Score для конкретной пары
                        ZScoreData zScoreData = zScoreService.calculateZScoreData(settings, pairCandlesMap);

                        if (zScoreData != null) {
                            double zScore = zScoreData.getLatestZScore() != null ? zScoreData.getLatestZScore() : 0.0;
                            if (zScore > 0) {
                                // Обновляем пару с Z-Score данными
                                updatePairWithZScoreData(pair, zScoreData, candlesMap);

                                // Обогащаем данными из стабильных пар
                                enrichSinglePairWithStableData(pair, originalStablePairs);

                                updatedPairs.add(pair);
                                log.info("✅ Пара {}/{} обновлена, Z-Score: {} - добавлена в результаты",
                                        tickerA, tickerB, zScore);
                            } else {
                                // ИСПРАВЛЕНИЕ: Создаем зеркальную пару с положительным Z-Score
                                log.debug("🪞 Пара {}/{} имеет отрицательный Z-Score: {}, создаем зеркальную пару",
                                        tickerA, tickerB, zScore);

                                // Создаем инвертированную ZScoreData для зеркальной пары
                                ZScoreData invertedZScoreData = createInvertedZScoreData(zScoreData);

                                if (invertedZScoreData != null && invertedZScoreData.getLatestZScore() != null && invertedZScoreData.getLatestZScore() > 0) {
                                    // Создаем зеркальную пару
                                    Pair mirrorPair = stablePairsService.createMirrorPair(pair);

                                    // Обновляем зеркальную пару с инвертированными Z-Score данными
                                    updatePairWithZScoreData(mirrorPair, invertedZScoreData, candlesMap);

                                    // Обогащаем данными из стабильных пар
                                    enrichSinglePairWithStableData(mirrorPair, originalStablePairs);

                                    updatedPairs.add(mirrorPair);
                                    log.info("✅ Зеркальная пара {}/{} создана и обновлена, Z-Score: {} - добавлена в результаты",
                                            mirrorPair.getTickerA(), mirrorPair.getTickerB(), invertedZScoreData.getLatestZScore());
                                } else {
                                    log.debug("⚠️ Не удалось создать зеркальную пару для {}/{} - инвертированный Z-Score отрицательный или null",
                                            tickerA, tickerB);
                                }
                            }
                        } else {
                            log.debug("⚠️ Пара {}/{} не получила Z-Score данные", tickerA, tickerB);
                        }
                    } else {
                        log.warn("⚠️ Отсутствуют данные свечей для пары {}/{}", tickerA, tickerB);
                    }
                } catch (Exception e) {
                    log.error("❌ Ошибка при анализе пары {}/{}: {}",
                            pair.getTickerA(), pair.getTickerB(), e.getMessage());
                }
            }

            // Сортируем по Z-Score в убывающем порядке
            updatedPairs.sort((a, b) -> Double.compare(
                    b.getZScoreCurrent() != null ? b.getZScoreCurrent().doubleValue() : 0.0,
                    a.getZScoreCurrent() != null ? a.getZScoreCurrent().doubleValue() : 0.0
            ));

            log.info("✅ Проанализировано {} пар, получено {} с положительным Z-Score",
                    allPairsWithMirrors.size(), updatedPairs.size());

            return updatedPairs;

        } catch (Exception e) {
            log.error("❌ Ошибка при анализе и обновлении стабильных пар: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

//    /**
//     * Анализирует конкретные стабильные пары, не ища случайные комбинации тикеров
//     *
//     * @param stablePairs стабильные пары для анализа
//     * @param candlesMap  карта свечей
//     * @param settings    настройки
//     * @return список Z-Score данных для конкретных стабильных пар
//     */
//    private List<ZScoreData> getZScoreDataForPairs(List<Pair> stablePairs, Map<String, List<Candle>> candlesMap,
//                                                   Settings settings) {
//        try {
//            log.info("📊 Анализ {} конкретных стабильных пар (включая зеркальные)", stablePairs.size());
//
//            List<ZScoreData> results = new ArrayList<>();
//
//            for (Pair stablePair : stablePairs) {
//                try {
//                    // Создаем карту свечей только для конкретной пары
//                    Map<String, List<Candle>> pairCandlesMap = new HashMap<>();
//
//                    String tickerA = stablePair.getTickerA();
//                    String tickerB = stablePair.getTickerB();
//
//                    if (candlesMap.containsKey(tickerA) && candlesMap.containsKey(tickerB)) {
//                        pairCandlesMap.put(tickerA, candlesMap.get(tickerA));
//                        pairCandlesMap.put(tickerB, candlesMap.get(tickerB));
//
//                        log.debug("🔍 Анализ пары: {}/{}", tickerA, tickerB);
//
//                        // Рассчитываем Z-Score для конкретной пары
//                        ZScoreData zScoreData = zScoreService.calculateZScoreData(settings, pairCandlesMap);
//
//                        if (zScoreData != null) {
//                            double zScore = zScoreData.getLatestZScore() != null ? zScoreData.getLatestZScore() : 0.0;
//                            if (zScore > 0) {
//                                results.add(zScoreData);
//                                log.info("✅ Пара {}/{} проанализирована, Z-Score: {} - добавлена в результаты",
//                                        tickerA, tickerB, zScore);
//                            } else {
//                                log.debug("⚠️ Пара {}/{} имеет отрицательный Z-Score: {} - пропускаем",
//                                        tickerA, tickerB, zScore);
//                            }
//                        } else {
//                            log.debug("⚠️ Пара {}/{} не получила Z-Score данные", tickerA, tickerB);
//                        }
//                    } else {
//                        log.warn("⚠️ Отсутствуют данные свечей для пары {}/{}", tickerA, tickerB);
//                    }
//                } catch (Exception e) {
//                    log.error("❌ Ошибка при анализе пары {}/{}: {}",
//                            stablePair.getTickerA(), stablePair.getTickerB(), e.getMessage());
//                }
//            }
//
//            // Сортируем по Z-Score в убывающем порядке
//            results.sort((a, b) -> Double.compare(
//                    b.getLatestZScore() != null ? b.getLatestZScore() : 0.0,
//                    a.getLatestZScore() != null ? a.getLatestZScore() : 0.0
//            ));
//
//            log.info("✅ Проанализировано {} стабильных пар, получено {} результатов",
//                    stablePairs.size(), results.size());
//
//            return results;
//
//        } catch (Exception e) {
//            log.error("❌ Ошибка при анализе стабильных пар: {}", e.getMessage(), e);
//            return Collections.emptyList();
//        }
//    }
//
//    /**
//     * Вычисление Z-Score данных для стабильных пар (УСТАРЕВШИЙ МЕТОД)
//     *
//     * @deprecated Использовать analyzeSpecificStablePairs() вместо этого метода
//     */
//    @Deprecated
//    private List<ZScoreData> computeZScoreDataForStablePairs(Settings settings, Map<String, List<Candle>> candlesMap,
//                                                             List<String> pairNames, int count) {
//        try {
//            log.info("📊 Расчет Z-Score для {} стабильных пар", pairNames.size());
//            // Используем существующий метод getTopNZScoreData - он автоматически фильтрует по доступным тикерам
//            return zScoreService.getTopNZScoreData(settings, candlesMap, count);
//        } catch (Exception e) {
//            log.error("❌ Ошибка при расчете Z-Score для стабильных пар: {}", e.getMessage());
//            return Collections.emptyList();
//        }
//    }
//
//    /**
//     * Обогащает созданные пары данными из стабильных пар
//     * Переносит: totalScore (факт), totalScoreEntry, stabilityRating с учетом зеркальности
//     */
//    private void enrichPairsWithStableData(List<Pair> pairs, List<Pair> stablePairs) {
//        log.info("🔄 Обогащение {} пар данными из {} стабильных пар", pairs.size(), stablePairs.size());
//
//        int enrichedCount = 0;
//
//        for (Pair pair : pairs) {
//            String pairName = pair.getPairName();
//
//            // Ищем соответствующую стабильную пару (включая зеркальные варианты)
//            Pair matchingStablePair = findMatchingStablePair(pairName, stablePairs);
//
//            if (matchingStablePair != null) {
//                // Переносим данные из стабильной пары
//                pair.setTotalScore(matchingStablePair.getTotalScore()); // Скор факт (текущий)
//                pair.setTotalScoreEntry(matchingStablePair.getTotalScoreEntry()); // Скор entry (изначальный)
//                pair.setStabilityRating(matchingStablePair.getStabilityRating()); // Рейтинг
//
//                log.info("📋 Перенесены данные для {}: scoreFact={}, scoreEntry={}, rating={}",
//                        pairName,
//                        matchingStablePair.getTotalScore(),
//                        matchingStablePair.getTotalScoreEntry(),
//                        matchingStablePair.getStabilityRating());
//
//                enrichedCount++;
//            } else {
//                log.warn("⚠️ Не найдена стабильная пара для: {}", pairName);
//            }
//        }
//
//        log.info("✅ Обогащено {} из {} пар данными из стабильных пар", enrichedCount, pairs.size());
//    }

    /**
     * Обновляет пару с данными Z-Score
     *
     * @param pair       пара для обновления
     * @param zScoreData данные Z-Score
     * @param candlesMap карта свечей
     */
    private void updatePairWithZScoreData(Pair pair, ZScoreData zScoreData, Map<String, List<Candle>> candlesMap) {
        try {
            log.debug("🔄 Обновление пары {} с Z-Score данными", pair.getPairName());

            // Устанавливаем Z-Score
            if (zScoreData.getLatestZScore() != null) {
                pair.setZScoreCurrent(BigDecimal.valueOf(zScoreData.getLatestZScore()));
            }

            // Обновляем пару с Z-Score данными через PairService (если доступен метод)
            pairService.updateZScoreDataCurrent(pair, zScoreData);

            // Получаем свечи для пары
            String tickerA = pair.getTickerA();
            String tickerB = pair.getTickerB();

            if (candlesMap.containsKey(tickerA) && candlesMap.containsKey(tickerB)) {
                pair.setLongTickerCandles(candlesMap.get(tickerA));
                pair.setShortTickerCandles(candlesMap.get(tickerB));

                log.debug("✅ Пара {} обновлена с Z-Score: {}",
                        pair.getPairName(), zScoreData.getLatestZScore());
            } else {
                log.warn("⚠️ Не удалось найти данные свечей для пары {}", pair.getPairName());
            }

        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении пары {} с Z-Score данными: {}",
                    pair.getPairName(), e.getMessage(), e);
        }
    }

    /**
     * Обогащает одну пару данными из стабильных пар
     *
     * @param pair                пара для обогащения
     * @param originalStablePairs исходные стабильные пары
     */
    private void enrichSinglePairWithStableData(Pair pair, List<Pair> originalStablePairs) {
        try {
            log.debug("🔄 Обогащение пары {} данными из стабильных пар", pair.getPairName());

            String pairName = pair.getPairName();

            // Ищем соответствующую стабильную пару
            Pair matchingStablePair = findMatchingStablePair(pairName, originalStablePairs);

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
     * Создает инвертированную копию ZScoreData для зеркальной пары
     * Меняет знак Z-Score на противоположный и меняет местами тикеры
     *
     * @param originalZScoreData исходные данные Z-Score
     * @return инвертированная копия данных для зеркальной пары
     */
    private ZScoreData createInvertedZScoreData(ZScoreData originalZScoreData) {
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

}
