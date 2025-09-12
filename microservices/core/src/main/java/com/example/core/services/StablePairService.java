package com.example.core.services;

import com.example.core.client.CandlesFeignClient;
import com.example.core.experemental.stability.dto.StabilityRequestDto;
import com.example.core.experemental.stability.dto.StabilityResponseDto;
import com.example.core.experemental.stability.dto.StabilityResultDto;
import com.example.core.experemental.stability.service.StabilityAnalysisService;
import com.example.core.repositories.StablePairRepository;
import com.example.shared.dto.Candle;
import com.example.shared.dto.ExtendedCandlesRequest;
import com.example.shared.models.Settings;
import com.example.shared.models.StablePair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class StablePairService {

    private final StablePairRepository stablePairRepository;
    private final StabilityAnalysisService stabilityAnalysisService;
    private final CandlesFeignClient candlesFeignClient;
    private final SettingsService settingsService;

    /**
     * Поиск стабильных пар с заданными параметрами
     */
    @Transactional
    public StabilityResponseDto searchStablePairs(String timeframe, String period,
                                                  Map<String, Object> searchSettings) {
        log.info("🔍 Начало поиска стабильных пар: timeframe={}, period={}", timeframe, period);

        try {
            // Получаем все свечи для анализа
            Settings settings = settingsService.getSettings();
            Map<String, List<Candle>> candlesMap = getCandlesForAnalysis(settings, timeframe, period);

            if (candlesMap.isEmpty()) {
                log.warn("⚠️ Не удалось получить данные свечей для анализа");
                throw new RuntimeException("Не удалось получить данные свечей");
            }

            // Применяем настройки поиска к параметрам анализа
            Map<String, Object> analysisSettings = buildAnalysisSettings(searchSettings);

            // Создаем запрос для Python API
            StabilityRequestDto request = new StabilityRequestDto(candlesMap, analysisSettings);

            // Выполняем анализ стабильности
            StabilityResponseDto response = stabilityAnalysisService.analyzeStability(request);

            if (response.getSuccess() && response.getResults() != null) {
                // Сохраняем результаты в базу данных
                saveSearchResults(response, timeframe, period, searchSettings);
                log.info("✅ Поиск завершен. Найдено {} торгуемых пар из {}",
                        response.getTradeablePairsFound(), response.getTotalPairsAnalyzed());
            }

            return response;

        } catch (Exception e) {
            log.error("❌ Ошибка при поиске стабильных пар: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка при поиске стабильных пар: " + e.getMessage(), e);
        }
    }

    /**
     * Получение свечей для анализа в зависимости от периода и таймфрейма
     * Использует расширенный запрос к candles микросервису для больших периодов
     */
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

    /**
     * Получение большого количества свечей через candles микросервис
     * Микросервис сам будет делать пагинацию и собирать нужное количество свечей
     */
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
                    .tickers(null) // Получаем все доступные тикеры
                    .excludeTickers(null) // Никого не исключаем
                    .build();
            return candlesFeignClient.getAllCandlesExtended(fallbackRequest);
        }
    }

    /**
     * Получение свечей пачками для больших периодов
     */
    private Map<String, List<Candle>> getCandlesBatch(Settings settings, String timeframe, int totalLimit) {
        try {
            log.info("📊 Пачковая загрузка {} свечей для таймфрейма {}", totalLimit, timeframe);

            // Сначала получаем список всех доступных тикеров с обычными настройками (300 свечей)
            Settings tempSettings = new Settings();
            tempSettings.copyFrom(settings);
            tempSettings.setCandleLimit(300); // Максимум для OKX
            tempSettings.setTimeframe(timeframe);

            ExtendedCandlesRequest initialRequest = ExtendedCandlesRequest.builder()
                    .timeframe(timeframe)
                    .candleLimit(300)
                    .minVolume(settings.getMinVolume())
                    .useMinVolumeFilter(settings.isUseMinVolumeFilter())
                    .minimumLotBlacklist(settings.getMinimumLotBlacklist())
                    .tickers(null) // Получаем все доступные тикеры
                    .excludeTickers(null) // Никого не исключаем
                    .build();
            Map<String, List<Candle>> initialData = candlesFeignClient.getAllCandlesExtended(initialRequest);

            if (initialData.isEmpty()) {
                log.warn("⚠️ Не удалось получить начальные данные свечей");
                return new HashMap<>();
            }

            // Если нужно больше 300 свечей, получаем дополнительные данные
            if (totalLimit > 300) {
                int remainingCandles = totalLimit - 300;
                int batchSize = 300;
                int numberOfBatches = (remainingCandles + batchSize - 1) / batchSize; // Округление вверх

                log.info("🔄 Нужно загрузить еще {} свечей в {} пачках", remainingCandles, numberOfBatches);

                // Для каждого тикера получаем дополнительные исторические данные
                Map<String, List<Candle>> result = new HashMap<>();

                for (Map.Entry<String, List<Candle>> entry : initialData.entrySet()) {
                    String ticker = entry.getKey();
                    List<Candle> candles = new ArrayList<>(entry.getValue());

                    // Получаем самую старую свечу для определения точки начала
                    if (!candles.isEmpty()) {
                        long oldestTimestamp = candles.stream()
                                .mapToLong(Candle::getTimestamp)
                                .min()
                                .orElse(System.currentTimeMillis());

                        // Добавляем исторические данные пачками
                        for (int batch = 0; batch < numberOfBatches; batch++) {
                            try {
                                // Вычисляем временную точку для следующей пачки
                                long timeOffset = getTimeframeOffsetMs(timeframe) * 300 * (batch + 1);
                                long beforeTimestamp = oldestTimestamp - timeOffset;

                                // Получаем историческую пачку (здесь нужен отдельный метод API)
                                List<Candle> historicalBatch = getHistoricalCandlesBatch(
                                        ticker, timeframe, beforeTimestamp, batchSize);

                                if (!historicalBatch.isEmpty()) {
                                    // Добавляем в начало списка (более старые данные)
                                    candles.addAll(0, historicalBatch);
                                } else {
                                    log.debug("📉 Нет исторических данных для {} до timestamp {}",
                                            ticker, beforeTimestamp);
                                    break; // Нет больше данных
                                }

                                // Ограничиваем общее количество свечей
                                if (candles.size() >= totalLimit) {
                                    break;
                                }

                            } catch (Exception batchException) {
                                log.warn("⚠️ Ошибка при загрузке пачки {} для {}: {}",
                                        batch, ticker, batchException.getMessage());
                                break;
                            }
                        }

                        // Обрезаем до нужного размера
                        if (candles.size() > totalLimit) {
                            candles = candles.subList(candles.size() - totalLimit, candles.size());
                        }
                    }

                    result.put(ticker, candles);
                }

                log.info("✅ Пачковая загрузка завершена. Средний размер данных: {} свечей",
                        result.values().stream().mapToInt(List::size).average().orElse(0));

                return result;
            } else {
                return initialData;
            }

        } catch (Exception e) {
            log.error("❌ Ошибка при пачковой загрузке свечей: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }

    /**
     * Получение исторических свечей для конкретного тикера
     * ЗАГЛУШКА - нужно реализовать отдельный вызов API
     */
    private List<Candle> getHistoricalCandlesBatch(String ticker, String timeframe,
                                                   long beforeTimestamp, int batchSize) {
        try {
            // TODO: Здесь нужно реализовать вызов API для получения исторических данных
            // Пока возвращаем пустой список, чтобы не ломать функционал
            log.debug("🔍 Запрос исторических данных для {} до {}", ticker, beforeTimestamp);

            // Временная заглушка - в реальности здесь должен быть отдельный API вызов
            // к сервису свечей с параметрами: ticker, timeframe, before, limit

            return new ArrayList<>();

        } catch (Exception e) {
            log.error("❌ Ошибка при получении исторических свечей для {}: {}", ticker, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Получение смещения времени в миллисекундах для таймфрейма
     */
    private long getTimeframeOffsetMs(String timeframe) {
        return switch (timeframe) {
            case "1m" -> 60 * 1000L;
            case "5m" -> 5 * 60 * 1000L;
            case "15m" -> 15 * 60 * 1000L;
            case "1H" -> 60 * 60 * 1000L;
            case "4H" -> 4 * 60 * 60 * 1000L;
            case "1D" -> 24 * 60 * 60 * 1000L;
            case "1W" -> 7 * 24 * 60 * 60 * 1000L;
            case "1M" -> 30L * 24 * 60 * 60 * 1000L; // Примерно месяц
            default -> 60 * 60 * 1000L; // По умолчанию час
        };
    }

    /**
     * Расчет количества свечей для периода и таймфрейма
     * Теперь поддерживает большие периоды через candles микросервис
     */
    private int calculateCandleLimit(String timeframe, String period) {
        int multiplier = switch (period.toLowerCase()) {
            case "день" -> 1;
            case "неделя" -> 7;
            case "месяц" -> 30;
            case "1 год" -> 365;
            case "2 года" -> 730;
            case "3 года" -> 1095;
            default -> 30;
        };

        int idealLimit = switch (timeframe) {
            case "1m" -> multiplier * 24 * 60; // минуты в день
            case "5m" -> multiplier * 24 * 12; // 5-минутки в день
            case "15m" -> multiplier * 24 * 4; // 15-минутки в день
            case "1H" -> multiplier * 24; // часы в день
            case "4H" -> multiplier * 6; // 4-часовки в день
            case "1D" -> multiplier; // дни
            case "1W" -> multiplier / 7; // недели
            case "1M" -> multiplier / 30; // месяцы
            default -> multiplier * 24; // По умолчанию часовки
        };

        // Возвращаем полный расчет - candles микросервис справится с пагинацией
        return Math.max(100, idealLimit); // Минимум 100 свечей для качественного анализа
    }

    /**
     * Построение настроек для анализа на основе пользовательского ввода
     */
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

    /**
     * Сохранение результатов поиска в базу данных
     */
    @Transactional
    public void saveSearchResults(StabilityResponseDto response, String timeframe,
                                  String period, Map<String, Object> searchSettings) {
        if (response.getResults() == null || response.getResults().isEmpty()) {
            return;
        }

        List<StablePair> pairsToSave = new ArrayList<>();

        for (StabilityResultDto result : response.getResults()) {
            // Проверяем, нет ли уже похожих результатов
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(1); // Дубликаты за последний час
            List<StablePair> existing = stablePairRepository.findSimilarPairs(
                    result.getTickerA(), result.getTickerB(), timeframe, period, cutoffTime);

            if (existing.isEmpty()) {
                StablePair stablePair = StablePair.fromStabilityResult(result, timeframe, period, searchSettings);
                pairsToSave.add(stablePair);
            }
        }

        if (!pairsToSave.isEmpty()) {
            stablePairRepository.saveAll(pairsToSave);
            log.info("💾 Сохранено {} новых стабильных пар", pairsToSave.size());
        }
    }

    /**
     * Получить все найденные пары (не в мониторинге)
     */
    public List<StablePair> getAllFoundPairs() {
        return stablePairRepository.findByIsInMonitoringFalseOrderBySearchDateDesc();
    }

    /**
     * Получить пары в мониторинге
     */
    public List<StablePair> getMonitoringPairs() {
        return stablePairRepository.findByIsInMonitoringTrueOrderByCreatedAtDesc();
    }

    /**
     * Добавить пару в мониторинг
     */
    @Transactional
    public void addToMonitoring(Long pairId) {
        StablePair pair = stablePairRepository.findById(pairId)
                .orElseThrow(() -> new RuntimeException("Пара не найдена: " + pairId));

        pair.setIsInMonitoring(true);
        stablePairRepository.save(pair);

        log.info("➕ Пара {}/{} добавлена в мониторинг", pair.getTickerA(), pair.getTickerB());
    }

    /**
     * Удалить пару из мониторинга
     */
    @Transactional
    public void removeFromMonitoring(Long pairId) {
        StablePair pair = stablePairRepository.findById(pairId)
                .orElseThrow(() -> new RuntimeException("Пара не найдена: " + pairId));

        pair.setIsInMonitoring(false);
        stablePairRepository.save(pair);

        log.info("➖ Пара {}/{} удалена из мониторинга", pair.getTickerA(), pair.getTickerB());
    }

    /**
     * Удалить найденную пару
     */
    @Transactional
    public void deleteFoundPair(Long pairId) {
        stablePairRepository.deleteById(pairId);
        log.info("🗑️ Пара удалена: {}", pairId);
    }

    /**
     * Очистить все найденные пары (не в мониторинге)
     */
    @Transactional
    public int clearAllFoundPairs() {
        List<StablePair> pairsToDelete = stablePairRepository.findByIsInMonitoringFalseOrderBySearchDateDesc();
        int count = pairsToDelete.size();

        if (count > 0) {
            stablePairRepository.deleteAll(pairsToDelete);
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
        int deletedCount = stablePairRepository.deleteOldSearchResults(cutoffDate);

        if (deletedCount > 0) {
            log.info("🧹 Удалено {} старых результатов поиска (старше {} дней)", deletedCount, daysToKeep);
        }

        return deletedCount;
    }

    /**
     * Получить статистику по найденным парам
     */
    public Map<String, Object> getSearchStatistics() {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        List<Object[]> stats = stablePairRepository.getStabilityRatingStats(weekAgo);

        Map<String, Object> result = new HashMap<>();
        result.put("totalFound", stablePairRepository.findByIsInMonitoringFalseOrderBySearchDateDesc().size());
        result.put("totalInMonitoring", stablePairRepository.findByIsInMonitoringTrueOrderByCreatedAtDesc().size());

        Map<String, Long> ratingStats = new HashMap<>();
        for (Object[] stat : stats) {
            ratingStats.put((String) stat[0], (Long) stat[1]);
        }
        result.put("ratingDistribution", ratingStats);

        return result;
    }
}