package com.example.core.processors;

import com.example.core.client.CandlesFeignClient;
import com.example.core.services.PairService;
import com.example.core.services.SettingsService;
import com.example.core.services.StablePairsService;
import com.example.core.services.ZScoreService;
import com.example.shared.utils.StringUtils;
import com.example.shared.dto.Candle;
import com.example.shared.dto.ExtendedCandlesRequest;
import com.example.shared.dto.FetchPairsRequest;
import com.example.shared.dto.ZScoreData;
import com.example.shared.enums.TradeStatus;
import com.example.shared.models.Pair;
import com.example.shared.models.Settings;
import com.example.shared.utils.NumberFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

        // Проверяем, нужно ли использовать стабильные пары из постоянного списка мониторинга
        if (settings.isUseStablePairsForMonitoring()) {
            log.info("🔄 Проверяем стабильные пары из постоянного списка мониторинга");
            if (stablePairsService.hasStablePairsInMonitoring()) {
                log.info("✅ Используем стабильные пары из постоянного списка мониторинга");
                return fetchPairsFromStableMonitoring(settings, request);
            } else {
                log.warn("⚠️ В постоянном списке мониторинга нет подходящих стабильных пар");
                
                // Если включен fallback к найденным парам
                if (settings.isUseFoundStablePairs()) {
                    log.info("🔄 Переключаемся на найденные стабильные пары как fallback");
                    return fetchPairsFromFoundStablePairs(settings, request);
                } else {
                    throw new IllegalStateException("❌ Постоянный список для мониторинга пуст. Добавьте стабильные пары в мониторинг или включите опцию 'Искать из Найденные стабильные пары' как fallback");
                }
            }
        }

        // Проверяем, нужно ли использовать найденные стабильные пары
        if (settings.isUseFoundStablePairs()) {
            log.info("🔍 Используем найденные стабильные пары (не в мониторинге)");
            return fetchPairsFromFoundStablePairs(settings, request);
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
     * Получение пар из постоянного списка стабильных пар для мониторинга
     *
     * @param settings настройки
     * @param request  запрос
     * @return список пар для анализа
     */
    private List<Pair> fetchPairsFromStableMonitoring(Settings settings, FetchPairsRequest request) {
        long start = System.currentTimeMillis();

        log.info("🔄 Получение пар из постоянного списка мониторинга...");

        // Получаем названия пар для загрузки свечей
        List<String> pairNames = stablePairsService.getPairNamesForZScoreAnalysis();
        log.info("📊 Получены пары для анализа: {}", pairNames);

        // Извлекаем уникальные тикеры из названий пар
        Set<String> uniqueTickers = new HashSet<>();
        for (String pairName : pairNames) {
            String[] tickers = pairName.split("/");
            if (tickers.length == 2) {
                uniqueTickers.add(tickers[0]);
                uniqueTickers.add(tickers[1]);
            }
        }

        log.info("📊 Извлечено {} уникальных тикеров для загрузки свечей: {}",
                uniqueTickers.size(), uniqueTickers);

        // Получаем свечи только для нужных тикеров
        Map<String, List<Candle>> candlesMap = getCandlesForSpecificTickers(settings, new ArrayList<>(uniqueTickers));

        if (candlesMap.isEmpty()) {
            log.warn("⚠️ Данные свечей для стабильных пар не получены — пропуск поиска.");
            throw new IllegalStateException("❌ Не удалось получить данные свечей для стабильных пар из мониторинга");
        }

        int count = Optional.ofNullable(request.getCountOfPairs())
                .orElse((int) settings.getUsePairs());

        // Рассчитываем Z-Score только для пар из мониторинга
        List<ZScoreData> zScoreDataList = computeZScoreDataForStablePairs(settings, candlesMap, pairNames, count);
        if (zScoreDataList.isEmpty()) {
            log.warn("⚠️ Z-Score данные для стабильных пар не получены");
            return Collections.emptyList();
        }

        logZScoreResults(zScoreDataList);

        List<Pair> pairs = createPairs(zScoreDataList, candlesMap);

        log.info("✅ Создано {} пар из стабильного мониторинга", pairs.size());
        pairs.forEach(p -> log.info("📈 {}", p.getPairName()));
        log.info("🕒 Время выполнения (стабильные пары): {} сек",
                String.format("%.2f", (System.currentTimeMillis() - start) / 1000.0));

        return pairs;
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
            log.info("⏳ Отправка запроса к candles микросервису для стабильных пар...");
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
     * Вычисление Z-Score данных для стабильных пар
     *
     * @param settings   настройки
     * @param candlesMap карта свечей
     * @param pairNames  названия пар
     * @param count      количество пар
     * @return список Z-Score данных
     */
    private List<ZScoreData> computeZScoreDataForStablePairs(Settings settings, Map<String, List<Candle>> candlesMap,
                                                             List<String> pairNames, int count) {
        try {
            log.info("📊 Расчет Z-Score для {} стабильных пар из мониторинга", pairNames.size());
            // Используем существующий метод getTopNZScoreData - он автоматически фильтрует по доступным тикерам
            return zScoreService.getTopNZScoreData(settings, candlesMap, count);
        } catch (Exception e) {
            log.error("❌ Ошибка при расчете Z-Score для стабильных пар: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Получение пар из найденных стабильных пар (не в мониторинге)
     *
     * @param settings настройки системы
     * @param request  запрос на получение пар
     * @return список торговых пар
     */
    private List<Pair> fetchPairsFromFoundStablePairs(Settings settings, FetchPairsRequest request) {
        long start = System.currentTimeMillis();

        log.info("🔍 Получение найденных стабильных пар для поиска торговых возможностей...");

        // Получаем все найденные стабильные пары (не в мониторинге)
        List<Pair> foundStablePairs = pairService.getAllFoundPairs();

        if (foundStablePairs.isEmpty()) {
            log.warn("⚠️ Нет найденных стабильных пар для анализа");
            return Collections.emptyList();
        }

        log.info("📋 Найдено {} стабильных пар для анализа", foundStablePairs.size());

        // Извлекаем названия пар и уникальные тикеры
        List<String> pairNames = foundStablePairs.stream()
                .map(Pair::getPairName)
                .toList();

        Set<String> uniqueTickersSet = new HashSet<>();
        for (Pair pair : foundStablePairs) {
            if (pair.getLongTicker() != null) uniqueTickersSet.add(pair.getLongTicker());
            if (pair.getShortTicker() != null) uniqueTickersSet.add(pair.getShortTicker());
        }

        List<String> uniqueTickers = new ArrayList<>(uniqueTickersSet);

        if (uniqueTickers.isEmpty()) {
            log.warn("⚠️ Не удалось извлечь тикеры из найденных стабильных пар");
            return Collections.emptyList();
        }

        log.info("📊 Извлечено {} уникальных тикеров для загрузки свечей: {}",
                uniqueTickers.size(), uniqueTickers);

        // Получаем свечи только для нужных тикеров
        Map<String, List<Candle>> candlesMap = getCandlesForSpecificTickers(settings, uniqueTickers);

        if (candlesMap.isEmpty()) {
            log.warn("⚠️ Данные свечей для найденных стабильных пар не получены — пропуск поиска.");
            throw new IllegalStateException("❌ Не удалось получить данные свечей для найденных стабильных пар");
        }

        int count = Optional.ofNullable(request.getCountOfPairs())
                .orElse((int) settings.getUsePairs());

        // Рассчитываем Z-Score только для найденных стабильных пар
        List<ZScoreData> zScoreDataList = computeZScoreDataForFoundStablePairs(settings, candlesMap, pairNames, count);
        if (zScoreDataList.isEmpty()) {
            log.warn("⚠️ Z-Score данные для найденных стабильных пар не получены");
            return Collections.emptyList();
        }

        logZScoreResults(zScoreDataList);

        List<Pair> pairs = createPairs(zScoreDataList, candlesMap);

        log.info("✅ Создано {} пар из найденных стабильных пар", pairs.size());
        pairs.forEach(p -> log.info("📈 {}", p.getPairName()));
        log.info("🕒 Время выполнения (найденные стабильные пары): {} сек",
                String.format("%.2f", (System.currentTimeMillis() - start) / 1000.0));

        return pairs;
    }

    /**
     * Вычисление Z-Score данных для найденных стабильных пар
     *
     * @param settings   настройки
     * @param candlesMap карта свечей
     * @param pairNames  названия пар
     * @param count      количество пар
     * @return список Z-Score данных
     */
    private List<ZScoreData> computeZScoreDataForFoundStablePairs(Settings settings, Map<String, List<Candle>> candlesMap,
                                                                  List<String> pairNames, int count) {
        try {
            log.info("📊 Расчет Z-Score для {} найденных стабильных пар", pairNames.size());
            // Используем существующий метод getTopNZScoreData - он автоматически фильтрует по доступным тикерам
            return zScoreService.getTopNZScoreData(settings, candlesMap, count);
        } catch (Exception e) {
            log.error("❌ Ошибка при расчете Z-Score для найденных стабильных пар: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
