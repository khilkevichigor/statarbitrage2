package com.example.core.services;

import com.example.core.client.CandlesFeignClient;
import com.example.core.experemental.stability.dto.StabilityRequestDto;
import com.example.core.experemental.stability.dto.StabilityResponseDto;
import com.example.core.experemental.stability.dto.StabilityResultDto;
import com.example.core.experemental.stability.service.StabilityAnalysisService;
import com.example.core.repositories.PairRepository;
import com.example.shared.dto.Candle;
import com.example.shared.dto.ExtendedCandlesRequest;
import com.example.shared.models.Pair;
import com.example.shared.models.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Сервис для работы с унифицированной моделью Pair
 * Заменяет функциональность StablePairService с возможностью работы с разными типами пар
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchStablePairService {

    private final PairRepository pairRepository;
    private final StabilityAnalysisService stabilityAnalysisService;
    private final CandlesFeignClient candlesFeignClient;
    private final SettingsService settingsService;

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

            // Извлекаем searchTickers из настроек для фильтрации
            Set<String> searchTickers = extractSearchTickers(searchSettings);

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
                        // Получаем свечи для конкретной комбинации с учетом фильтра тикеров
                        Map<String, List<Candle>> candlesMap = getCandlesForAnalysis(settings, timeframe, period, searchTickers, searchSettings);

                        if (candlesMap.isEmpty()) {
                            log.warn("⚠️ Не удалось получить данные свечей для timeframe={}, period={}", timeframe, period);
                            continue;
                        }

                        // Проверяем, что у нас достаточно инструментов для анализа стабильности
                        if (candlesMap.size() < 2) {
                            if (searchTickers != null && !searchTickers.isEmpty()) {
                                log.warn("⚠️ Недостаточно инструментов для анализа стабильности: получено {} из {} запрошенных. " +
                                                "Возможно, некоторые инструменты исключены валидацией консистентности данных.",
                                        candlesMap.size(), searchTickers.size());
                                log.warn("💡 Попробуйте выбрать инструменты с более консистентными историческими данными или увеличить список инструментов.");
                            } else {
                                log.warn("⚠️ Недостаточно инструментов для анализа стабильности: получено только {}", candlesMap.size());
                            }
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
                // Формируем детальное описание только для различающихся параметров
                List<String> differences = new ArrayList<>();
                
                if (candles.size() != referenceCount) {
                    differences.add(String.format("свечей:%d≠%d", candles.size(), referenceCount));
                }
                if (candles.get(0).getTimestamp() != referenceStart) {
                    differences.add(String.format("начало:%s≠%s", 
                        formatTimestamp(candles.get(0).getTimestamp()), formatTimestamp(referenceStart)));
                }
                if (candles.get(candles.size() - 1).getTimestamp() != referenceEnd) {
                    differences.add(String.format("конец:%s≠%s", 
                        formatTimestamp(candles.get(candles.size() - 1).getTimestamp()), formatTimestamp(referenceEnd)));
                }
                
                String reason = !differences.isEmpty() ? 
                    "(" + String.join(", ", differences) + ")" : 
                    "(неизвестная причина)";
                    
                invalidTickers.add(ticker + reason);
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

    private Map<String, List<Candle>> getCandlesForAnalysis(Settings settings, String timeframe, String period, Set<String> searchTickers, Map<String, Object> searchSettings) {
        try {
            // Рассчитываем количество свечей для запрошенного периода
            int candleLimit = calculateCandleLimit(timeframe, period);

            if (searchTickers != null && !searchTickers.isEmpty()) {
                log.info("📊 Запрашиваем {} свечей для таймфрейма {} и периода {} с фильтром по {} тикерам: {}",
                        candleLimit, timeframe, period, searchTickers.size(), searchTickers);
            } else {
                log.info("📊 Запрашиваем {} свечей для таймфрейма {} и периода {} без фильтра тикеров",
                        candleLimit, timeframe, period);
            }

            // Всегда используем расширенный запрос к candles микросервису с пагинацией
            return getCandlesExtended(settings, timeframe, candleLimit, searchTickers, period, searchSettings);

        } catch (Exception e) {
            log.error("❌ Ошибка при получении свечей: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }

    private Map<String, List<Candle>> getCandlesExtended(Settings settings, String timeframe, int candleLimit, Set<String> searchTickers, String period, Map<String, Object> searchSettings) {
        try {
            if (searchTickers != null && !searchTickers.isEmpty()) {
                log.info("📊 Расширенный запрос {} свечей для таймфрейма {} через candles микросервис с фильтром по {} тикерам",
                        candleLimit, timeframe, searchTickers.size());
            } else {
                log.info("📊 Расширенный запрос {} свечей для таймфрейма {} через candles микросервис без фильтра тикеров",
                        candleLimit, timeframe);
            }

            // Извлекаем параметр useCache из searchSettings
            Boolean useCache = searchSettings != null ? (Boolean) searchSettings.get("useCache") : null;

            ExtendedCandlesRequest request = ExtendedCandlesRequest.builder()
                    .timeframe(timeframe)
                    .candleLimit(candleLimit)
                    .minVolume(settings.getMinVolume())
//                    .useMinVolumeFilter(settings.isUseMinVolumeFilter())
//                    .minimumLotBlacklist(settings.getMinimumLotBlacklist())
                    .tickers(searchTickers != null && !searchTickers.isEmpty() ? searchTickers.stream().toList() : null) // Передаем полные названия инструментов
                    .excludeTickers(Arrays.asList(settings.getMinimumLotBlacklist().split(",")))
//                    .useCache(useCache != null ? useCache : true) // По умолчанию используем кэш
                    .period(period)
                    .build();

            Map<String, List<Candle>> result = candlesFeignClient.getValidatedCacheExtended(request);

            if (result != null && !result.isEmpty()) {
                int totalCandles = result.values().stream().mapToInt(List::size).sum();
                int avgCandles = result.values().stream().mapToInt(List::size).sum() / result.size();
                if (searchTickers != null && !searchTickers.isEmpty()) {
                    log.info("✅ Получено {} тикеров из {} запрошенных со средним количеством {} свечей (всего {} свечей)",
                            result.size(), searchTickers.size(), avgCandles, totalCandles);
                } else {
                    log.info("✅ Получено {} тикеров со средним количеством {} свечей (всего {} свечей)",
                            result.size(), avgCandles, totalCandles);
                }
            }

            return result != null ? result : new HashMap<>();

        } catch (Exception e) {
            log.error("❌ Ошибка при расширенном получении свечей: {}", e.getMessage(), e);
            throw new RuntimeException(e);
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

//        return Math.max(100, idealLimit); //todo пока отрубил тк для 1М и 3 года достаточно 36 свечей, 100 свечей для BTC тупо нет на окх почему-то
        return idealLimit;
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

    // ======== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ДЛЯ ПОИСКА ========

    /**
     * Извлекает Set тикеров из настроек поиска
     */
    @SuppressWarnings("unchecked")
    private Set<String> extractSearchTickers(Map<String, Object> searchSettings) {
        if (searchSettings == null || searchSettings.isEmpty()) {
            return null;
        }

        Object searchTickersObj = searchSettings.get("searchTickers");
        if (searchTickersObj == null) {
            return null;
        }

        if (searchTickersObj instanceof Set<?>) {
            Set<?> tickersSet = (Set<?>) searchTickersObj;
            if (tickersSet.isEmpty()) {
                return null;
            }

            // Конвертируем в Set<String> с валидацией
            Set<String> result = new HashSet<>();
            for (Object ticker : tickersSet) {
                if (ticker instanceof String tickerStr) {
                    String trimmed = tickerStr.trim().toUpperCase();
                    if (!trimmed.isEmpty()) {
                        result.add(trimmed);
                    }
                }
            }

            if (result.isEmpty()) {
                log.debug("🔍 Фильтр тикеров пуст после валидации");
                return null;
            }

            log.info("🎯 Применяется фильтр по {} тикерам: {}", result.size(), result);
            return result;
        }

        // Поддержка строк для обратной совместимости
        if (searchTickersObj instanceof String tickersStr) {
            String trimmedStr = tickersStr.trim();
            if (trimmedStr.isEmpty()) {
                return null;
            }

            Set<String> result = new HashSet<>();
            String[] tickerArray = trimmedStr.split(",");
            for (String ticker : tickerArray) {
                String trimmed = ticker.trim().toUpperCase();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }

            if (result.isEmpty()) {
                log.debug("🔍 Фильтр тикеров пуст после парсинга строки");
                return null;
            }

            log.info("🎯 Применяется фильтр по {} тикерам из строки: {}", result.size(), result);
            return result;
        }

        log.warn("⚠️ searchTickers имеет неожиданный тип: {}", searchTickersObj.getClass());
        return null;
    }
}