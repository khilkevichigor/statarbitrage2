package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.common.utils.NumberFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class FilterIncompleteZScoreParamsServiceV2 {

    private final PairDataService pairDataService;
    private final PixelSpreadService pixelSpreadService;

    /**
     * Новая версия фильтрации на основе системы очков
     * <p>
     * РЕВОЛЮЦИОННОЕ ИЗМЕНЕНИЕ:
     * - Минимальная фильтрация (только критические проверки)
     * - Расчет качественного скора для каждой пары (0-100 очков)
     * - ObtainBestPairServiceV2 выбирает лучшую на основе скора
     * - НЕТ приоритета Johansen - комплексная оценка!
     */
    public void filter(List<ZScoreData> zScoreDataList, Settings settings) {
        double expected = settings.getExpectedZParamsCount();

        // Анализируем входящие данные
        analyzeInputData(zScoreDataList);

        log.info("🔍 Ожидаемое количество наблюдений: {}, всего пар для анализа: {}", expected, zScoreDataList.size());

        // Сохраняем копию оригинального списка для статистики
        List<ZScoreData> originalList = List.copyOf(zScoreDataList);
        int before = zScoreDataList.size();
        Map<String, Integer> filterStats = new HashMap<>();

        zScoreDataList.removeIf(data -> {
            String reason = shouldFilterPair(data, settings, expected);
            if (reason != null) {
                filterStats.merge(reason, 1, Integer::sum);
                log.info("⚠️ Отфильтровано {}/{} — {}. Детали: Z-Score={}, ADF p-value={}, R²={}",
                        data.getUnderValuedTicker(), data.getOverValuedTicker(), reason,
                        NumberFormatter.format(getLatestZScore(data, data.getZScoreHistory()), 2),
                        getAdfPValue(data, data.getZScoreHistory()) != null ? NumberFormatter.format(getAdfPValue(data, data.getZScoreHistory()), 4) : "N/A",
                        getRSquared(data) != null ? NumberFormatter.format(getRSquared(data), 3) : "N/A"
                );
                return true;
            }
            // НОВАЯ СИСТЕМА: Рассчитываем качественный скор для каждой пары
            double qualityScore = calculatePairQualityScoreInternal(data, settings);
            log.info("📊 Пара {}/{} прошла базовую фильтрацию. Качественный скор: {}",
                    data.getUnderValuedTicker(), data.getOverValuedTicker(),
                    NumberFormatter.format(qualityScore, 2));
            return false;
        });

        int after = zScoreDataList.size();
        log.info("✅ Фильтрация завершена: {} → {} пар", before, after);

        // Статистика по причинам фильтрации
        filterStats.forEach((reason, count) ->
                log.debug("📊 Статистика по фильтрации - {}: {} пар", reason, count));

        // Детальная статистика фильтрации
        logFilteringStatistics(originalList, zScoreDataList, settings);
    }

    /**
     * Анализирует входящие данные и определяет формат API
     */
    private void analyzeInputData(List<ZScoreData> zScoreDataList) {
        if (zScoreDataList.isEmpty()) return;

        ZScoreData sample = zScoreDataList.get(0);
        boolean hasOldFormat = sample.getZScoreHistory() != null && !sample.getZScoreHistory().isEmpty();
        boolean hasNewFormat = sample.getLatestZScore() != null;
        boolean hasJohansenData = sample.getJohansenCointPValue() != null;

        log.info("📋 Анализ формата данных:");
        log.info("   📊 Старый формат (zscoreParams): {}", hasOldFormat ? "✅" : "❌");
        log.info("   🆕 Новый формат (latest_zscore): {}", hasNewFormat ? "✅" : "❌");
        log.info("   🔬 Johansen тест: {}", hasJohansenData ? "✅ ДОСТУПЕН" : "❌");

        if (hasJohansenData) {
            double minJohansenPValue = zScoreDataList.stream()
                    .filter(d -> d.getJohansenCointPValue() != null)
                    .mapToDouble(ZScoreData::getJohansenCointPValue)
                    .min()
                    .orElse(1.0);
            log.info("   📈 Минимальный Johansen p-value: {}", String.format("%.6f", minJohansenPValue));
        }
    }

    /**
     * Определяет должна ли быть отфильтрована пара (МИНИМАЛЬНАЯ ФИЛЬТРАЦИЯ)
     * Остались только критические проверки - остальное через скоринг!
     * Возвращает причину фильтрации или null если пара прошла критичные фильтры
     */
    private String shouldFilterPair(ZScoreData data, Settings settings, double expectedSize) {
        List<ZScoreParam> params = data.getZScoreHistory();
        String pairName = data.getUnderValuedTicker() + "/" + data.getOverValuedTicker();

        log.debug("⚙️ МИНИМАЛЬНАЯ фильтрация пары {} (основная оценка через скоринг):", pairName);

        // ====== КРИТИЧЕСКИЕ ПРОВЕРКИ (только обязательные!) ======

        // 1. Проверка наличия данных
        String reason = isDataMissing(data, params) ? "Отсутствуют данные Z-score" : null;
        if (reason != null) {
            log.debug("   ❌ {}: {}", pairName, reason);
            return reason;
        }

        // 2. Проверка тикеров
        reason = isTickersInvalid(data) ? "Некорректные тикеры" : null;
        if (reason != null) {
            log.debug("   ❌ {}: {}", pairName, reason);
            return reason;
        }

        // 3. Проверка Z-Score на положительность (ключевой фильтр!)
        double currentZScore = getLatestZScore(data, params);
        if (currentZScore <= 0) {
            reason = String.format("Отрицательный/нулевой Z-score: %.2f (нет сигнала)", currentZScore);
            log.debug("   ❌ {}: {}", pairName, reason);
            return reason;
        }
        log.debug("   ✅ {}: Положительный Z-score: {}", pairName, NumberFormatter.format(currentZScore, 2));


        return null; // Пара прошла критические проверки
    }

    // ============ ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ФИЛЬТРАЦИИ ============

    private boolean isDataMissing(ZScoreData data, List<ZScoreParam> params) {
        // Проверяем наличие Z-score данных в любом формате
        if (params != null && !params.isEmpty()) {
            return false; // Старый формат - есть данные
        }
        return data.getLatestZScore() == null; // Новый формат - проверяем latest_zscore
    }

    private boolean isTickersInvalid(ZScoreData data) {
        return data.getUnderValuedTicker() == null ||
                data.getOverValuedTicker() == null ||
                data.getUnderValuedTicker().isEmpty() ||
                data.getOverValuedTicker().isEmpty() ||
                data.getUnderValuedTicker().equals(data.getOverValuedTicker());
    }

    private String checkCointegration(ZScoreData data, List<ZScoreParam> params, Settings settings) {
        // ПРИОРИТЕТ 1: Johansen тест (если доступен)
        if (data.getJohansenCointPValue() != null) {
            Double johansenPValue = data.getJohansenCointPValue();
            log.debug("🔬 Johansen p-value: {} для пары {}/{}",
                    NumberFormatter.format(johansenPValue, 6), // Use NumberFormatter
                    data.getUnderValuedTicker(),
                    data.getOverValuedTicker());

            // Для Johansen теста используем более строгий порог (0.05)
            double johansenThreshold = 0.05; //todo вынести в настройки
            if (johansenPValue > johansenThreshold) {
                return String.format("НЕ коинтегрированы (Johansen): p-value=%s > %.6f",
                        NumberFormatter.format(johansenPValue, 6), johansenThreshold);
            }

            // Дополнительная проверка качества Johansen теста
            if (data.getJohansenTraceStatistic() != null && data.getJohansenCriticalValue95() != null) {
                if (data.getJohansenError() != null) {
                    return "Ошибка в Johansen тесте: " + data.getJohansenError();
                }

                // Проверяем trace statistic - должен быть больше критического значения
                if (data.getJohansenTraceStatistic() <= data.getJohansenCriticalValue95()) {
                    return String.format("Слабая коинтеграция (Johansen): trace=%.2f ≤ critical=%.2f",
                            data.getJohansenTraceStatistic(), data.getJohansenCriticalValue95());
                }
            }

            log.debug("✅ Пара {}/{} прошла Johansen тест (p-value={})",
                    data.getUnderValuedTicker(), data.getOverValuedTicker(),
                    NumberFormatter.format(johansenPValue, 6)); // Use NumberFormatter
            return null; // Прошли Johansen тест
        }

        // ПРИОРИТЕТ 2: Fallback к ADF если нет Johansen данных
        Double adfPValue = getAdfPValue(data, params);
        if (adfPValue == null) {
            log.debug("⚠️ Отсутствует ADF p-value для пары {}/{}", data.getUnderValuedTicker(), data.getOverValuedTicker());
            return "Отсутствует cointegration p-value"; // Return reason for filtering
        }

        // Для ADF теста используем настроечное значение с минимумом для криптовалют
        double adfThreshold = Math.max(settings.getMaxAdfValue(), 0.1); // Минимум 0.1 для crypto

        if (adfPValue > adfThreshold) {
            return String.format("Слабая коинтеграция (ADF): p-value=%s > %.6f",
                    NumberFormatter.format(adfPValue, 6), adfThreshold); // Use NumberFormatter
        }

        log.debug("✅ Пара {}/{} прошла ADF тест (p-value={})",
                data.getUnderValuedTicker(), data.getOverValuedTicker(),
                NumberFormatter.format(adfPValue, 6)); // Use NumberFormatter
        return null;
    }

    private String checkRSquared(ZScoreData data, Settings settings) {
        Double rSquared = getRSquared(data);
        if (rSquared == null) {
            return "Отсутствует R-squared";
        }

        if (rSquared < settings.getMinRSquared()) {
            return String.format("Слабая модель: R²=%.4f < %.4f", rSquared, settings.getMinRSquared());
        }

        return null;
    }

    private String checkCointegrationStability(ZScoreData data, List<ZScoreParam> params, Settings settings) {
        // Используем данные из DataQuality если доступны
        if (data.getStablePeriods() != null && data.getTotalObservations() != null) {
            if (data.getTotalObservations() > 0) {
                double stabilityRatio = (double) data.getStablePeriods() / data.getTotalObservations();
                double minStabilityRatio = 0.7; // 70% стабильных периодов

                if (stabilityRatio < minStabilityRatio) {
                    return String.format("Нестабильная коинтеграция: %.1f%% < %.1f%%",
                            stabilityRatio * 100, minStabilityRatio * 100);
                }
            }
            return null;
        }

        // Fallback к старой логике для старого формата
        if (params == null || params.size() < 100) {
            return null; // Недостаточно данных для проверки стабильности
        }

        int windowSize = (int) settings.getMinWindowSize();
        int stableWindows = 0;
        int totalWindows = 0;

        for (int i = windowSize; i <= params.size(); i += windowSize / 2) {
            List<ZScoreParam> window = params.subList(Math.max(0, i - windowSize), i);

            double avgAdfPValue = window.stream()
                    .filter(p -> p.getAdfpvalue() != 0)
                    .mapToDouble(ZScoreParam::getAdfpvalue)
                    .average()
                    .orElse(1.0);

            totalWindows++;
            if (avgAdfPValue < 0.05) {
                stableWindows++;
            }
        }

        double stabilityRatio = (double) stableWindows / totalWindows;
        double minStabilityRatio = 0.7;

        if (stabilityRatio < minStabilityRatio) {
            return String.format("Нестабильная коинтеграция: %.2f%% < %.2f%%",
                    stabilityRatio * 100, minStabilityRatio * 100);
        }

        return null;
    }

    private String checkCorrelationSignificance(ZScoreData data, List<ZScoreParam> params, Settings settings) {
        Double pValue = getCorrelationPValue(data, params);

        if (pValue == null) {
            return "Отсутствует correlation p-value";
        }

        if (pValue > settings.getMaxPValue()) {
            return String.format("Незначимая корреляция: p-value=%.6f > %.6f",
                    pValue, settings.getMaxPValue());
        }

        return null;
    }

    private String checkCorrelation(ZScoreData data, Settings settings) {
        Double correlation = data.getPearsonCorr();
        if (correlation == null) {
            return "Отсутствует корреляция";
        }

        // Используем абсолютное значение корреляции
        double absCorrelation = Math.abs(correlation);
        if (absCorrelation < settings.getMinCorrelation()) {
            return String.format("Слабая корреляция: |%.4f| < %.4f",
                    correlation, settings.getMinCorrelation());
        }

        return null;
    }

    private String checkZScoreSignal(ZScoreData data, List<ZScoreParam> params, Settings settings) {
        double lastZScore = getLatestZScore(data, params);

        // Фильтруем отрицательные Z-Score - они указывают на неправильное определение ролей тикеров
        if (lastZScore < 0) {
            return String.format("Отрицательный Z-score: %.2f (роли тикеров перепутаны)", lastZScore);
        }

        // Проверяем минимальный порог Z-Score для торгового сигнала
        if (lastZScore < settings.getMinZ()) {
            return String.format("Слабый сигнал: Z-score=%.2f < %.2f", lastZScore, settings.getMinZ());
        }

        return null;
    }

    private String checkAdditionalTradingFilters(ZScoreData data, List<ZScoreParam> params, Settings settings) {
        // Дополнительные фильтры для улучшения качества торговли

        // 1. Проверка на экстремальные Z-Score (могут быть выбросами)
        double lastZScore = getLatestZScore(data, params);
        if (Math.abs(lastZScore) > 5.0) {
            return String.format("Экстремальный Z-score: %.2f", lastZScore);
        }

        // 2. Проверка тренда Z-Score (не должен быть слишком волатильным)
        if (params != null && params.size() >= 10) {
            double zScoreVolatility = calculateZScoreVolatility(params);
            if (zScoreVolatility > 10.0) {
                return String.format("Высокая волатильность Z-score: %.2f", zScoreVolatility);
            }
        }

        return null;
    }

    // ============ УТИЛИТНЫЕ МЕТОДЫ ============

    private Double getAdfPValue(ZScoreData data, List<ZScoreParam> params) {
        if (params != null && !params.isEmpty()) {
            // Старый формат API
            ZScoreParam lastParam = params.get(params.size() - 1);
            return lastParam.getAdfpvalue();
        } else {
            // Новый формат API - используем avgAdfPvalue для ADF теста
            return data.getAvgAdfPvalue();
        }
    }

    private Double getCorrelationPValue(ZScoreData data, List<ZScoreParam> params) {
        if (params != null && !params.isEmpty()) {
            // Старый формат API
            ZScoreParam lastParam = params.get(params.size() - 1);
            return lastParam.getPvalue();
        } else {
            // Новый формат API
            return data.getPearsonCorrPValue();
        }
    }

    private double getLatestZScore(ZScoreData data, List<ZScoreParam> params) {
        if (params != null && !params.isEmpty()) {
            // Старый формат API
            return params.get(params.size() - 1).getZscore();
        } else if (data.getLatestZScore() != null) {
            // Новый формат API
            return data.getLatestZScore();
        } else {
            return 0.0;
        }
    }

    private Double getRSquared(ZScoreData data) {
        // Приоритет новому формату
        if (data.getAvgRSquared() != null) {
            return data.getAvgRSquared();
        }

        return null;
    }

    private double calculateZScoreVolatility(List<ZScoreParam> params) {
        if (params.size() < 10) return 0.0;

        // Берем последние 10 значений Z-Score
        List<Double> recentZScores = params.subList(params.size() - 10, params.size())
                .stream()
                .map(ZScoreParam::getZscore)
                .toList();

        // Рассчитываем стандартное отклонение
        double mean = recentZScores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = recentZScores.stream()
                .mapToDouble(z -> Math.pow(z - mean, 2))
                .average()
                .orElse(0.0);

        return Math.sqrt(variance);
    }

    // ============ НОВАЯ СИСТЕМА ОЦЕНКИ КАЧЕСТВА ПАР ============

    /**
     * ПУБЛИЧНЫЙ МЕТОД для ObtainBestPairServiceV2
     * Рассчитывает качественный скор пары с КОНФИГУРИРУЕМЫМИ ВЕСАМИ из Settings
     * 
     * Компоненты скора (веса настраиваются через UI):
     * - Z-Score сила - основной торговый сигнал
     * - Пиксельный спред - важность раздвижки цен  
     * - Коинтеграция - Johansen + ADF тесты
     * - Качество модели - R-squared + стабильность
     * - Статистика - P-values и корреляции
     * - Бонусы - за полноту данных
     */
    public double calculatePairQualityScore(ZScoreData data, Settings settings) {
        return calculatePairQualityScoreInternal(data, settings);
    }

    /**
     * Внутренняя реализация расчета скора
     */
    private double calculatePairQualityScoreInternal(ZScoreData data, Settings settings) {
        double totalScore = 0.0;
        List<ZScoreParam> params = data.getZScoreHistory();
        String pairName = data.getUnderValuedTicker() + "/" + data.getOverValuedTicker();

        log.info("🎯 Рассчет качественного скора для {} с НАСТРАИВАЕМЫМИ весами", pairName);

        // ====== 1. Z-SCORE СИЛА (настраиваемый вес) ======
        if (settings.isUseZScoreScoring()) {
            double zScore = getLatestZScore(data, params);
            double maxWeight = settings.getZScoreScoringWeight();
            double zScorePoints = Math.min(Math.abs(zScore) * (maxWeight / 5.0), maxWeight); // Нормализуем по весу
            totalScore += zScorePoints;
            log.info("  🎯 Z-Score компонент: {} очков (Z-score={}, вес={})",
                    NumberFormatter.format(zScorePoints, 1), NumberFormatter.format(zScore, 2), maxWeight);
        }

        // ====== 2. ПИКСЕЛЬНЫЙ СПРЕД (настраиваемый вес, высокий приоритет!) ======
        if (settings.isUsePixelSpreadScoring()) {
            double pixelSpreadScore = calculatePixelSpreadScoreComponent(data, settings);
            totalScore += pixelSpreadScore;
            log.info("  📏 Пиксельный спред: {} очков (вес={})",
                    NumberFormatter.format(pixelSpreadScore, 1), settings.getPixelSpreadScoringWeight());
        }

        // ====== 3. КОИНТЕГРАЦИЯ (настраиваемый вес) ======
        if (settings.isUseCointegrationScoring()) {
            double cointegrationScore = calculateCointegrationScoreComponent(data, params, settings);
            totalScore += cointegrationScore;
            log.info("  🔬 Коинтеграция: {} очков (вес={})",
                    NumberFormatter.format(cointegrationScore, 1), settings.getCointegrationScoringWeight());
        }

        // ====== 4. КАЧЕСТВО МОДЕЛИ (настраиваемый вес) ======
        if (settings.isUseModelQualityScoring()) {
            double modelQualityScore = calculateModelQualityScoreComponent(data, params, settings);
            totalScore += modelQualityScore;
            log.info("  📊 Качество модели: {} очков (вес={})",
                    NumberFormatter.format(modelQualityScore, 1), settings.getModelQualityScoringWeight());
        }

        // ====== 5. СТАТИСТИЧЕСКАЯ ЗНАЧИМОСТЬ (настраиваемый вес) ======
        if (settings.isUseStatisticsScoring()) {
            double statisticalScore = calculateStatisticalSignificanceScoreComponent(data, params, settings);
            totalScore += statisticalScore;
            log.info("  📊 Статистика: {} очков (вес={})",
                    NumberFormatter.format(statisticalScore, 1), settings.getStatisticsScoringWeight());
        }

        // ====== 6. БОНУСЫ (настраиваемый вес) ======
        if (settings.isUseBonusScoring()) {
            double bonusScore = calculateBonusScoreComponent(data, settings);
            totalScore += bonusScore;
            log.info("  🎁 Бонусы: {} очков (вес={})",
                    NumberFormatter.format(bonusScore, 1), settings.getBonusScoringWeight());
        }

        log.info("🏆 Итоговый скор для {}: {} очков (НАСТРАИВАЕМЫЕ ВЕСА)", pairName, NumberFormatter.format(totalScore, 1));
        return totalScore; // Убираем ограничение в 100 очков - теперь сумма весов настраивается
    }

    /**
     * НОВЫЙ КОМПОНЕНТ: Расчет скора пиксельного спреда
     * Использует полный вес из настроек (по умолчанию 25 очков = равно Johansen/ADF)
     */
    private double calculatePixelSpreadScoreComponent(ZScoreData data, Settings settings) {
        try {
            // Ищем существующую PairData по тикерам
            String longTicker = data.getUnderValuedTicker();  // undervalued = long
            String shortTicker = data.getOverValuedTicker(); // overvalued = short

            // Получаем PairData из базы (если существует)
            var existingPairs = pairDataService.findByTickers(longTicker, shortTicker);

            if (!existingPairs.isEmpty()) {
                var pairData = existingPairs.get(0);

                // Получаем статистику пиксельного спреда
                double avgSpread = pixelSpreadService.getAveragePixelSpread(pairData);
                double maxSpread = pixelSpreadService.getMaxPixelSpread(pairData);

                if (avgSpread > 0) {
                    double maxWeight = settings.getPixelSpreadScoringWeight();
                    
                    // Логика начисления баллов (нормализуем на полный вес):
                    // avg < 20px: 0% от веса
                    // avg 20-40px: 25-50% от веса  
                    // avg 40-80px: 50-75% от веса
                    // avg > 80px: 75-100% от веса
                    double scoreRatio;
                    if (avgSpread < 20) {
                        scoreRatio = 0.0;
                    } else if (avgSpread < 40) {
                        scoreRatio = 0.25 + (avgSpread - 20) / 20 * 0.25; // 25-50%
                    } else if (avgSpread < 80) {
                        scoreRatio = 0.50 + (avgSpread - 40) / 40 * 0.25; // 50-75%
                    } else {
                        scoreRatio = 0.75 + Math.min((avgSpread - 80) / 40, 1.0) * 0.25; // 75-100%
                    }

                    // Бонус за высокий максимум (дополнительная волатильность)
                    if (maxSpread > 100) {
                        scoreRatio = Math.min(scoreRatio + 0.1, 1.0); // +10% бонус
                    }

                    double totalScore = maxWeight * scoreRatio;

                    log.info("    📏 Пиксельный спред: avg={:.1f}px, max={:.1f}px → {:.1f} баллов ({:.0f}% от {})",
                            avgSpread, maxSpread, totalScore, scoreRatio * 100, maxWeight);

                    return totalScore;
                }
            }

            return 0.0; // Нет данных о пиксельном спреде

        } catch (Exception e) {
            log.warn("    📏 Ошибка расчета скора пиксельного спреда: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * ДИНАМИЧЕСКАЯ СИСТЕМА ВЕСОВ для коинтеграции (настраиваемый вес)
     * <p>
     * Принцип: равные веса когда доступны оба теста, полный вес единственному доступному
     * - Johansen + ADF доступны: 50% + 50% от веса
     * - Только Johansen: 100% от веса
     * - Только ADF: 100% от веса
     * - Нет данных: 0 очков
     */
    private double calculateCointegrationScoreComponent(ZScoreData data, List<ZScoreParam> params, Settings settings) {
        boolean hasJohansen = data.getJohansenCointPValue() != null && data.getJohansenCointPValue() > 0;
        boolean hasAdf = getAdfPValue(data, params) != null && getAdfPValue(data, params) > 0;

        String pairName = data.getUnderValuedTicker() + "/" + data.getOverValuedTicker();

        if (!hasJohansen && !hasAdf) {
            log.info("  🔬 {}: Нет данных коинтеграции", pairName);
            return 0.0;
        }

        double maxWeight = settings.getCointegrationScoringWeight();
        double score = 0.0;

        if (hasJohansen && hasAdf) {
            // ОБА ТЕСТА ДОСТУПНЫ - равные веса по 50% от полного веса
            log.info("  🔬 {}: Динамические веса - оба теста ({}+{})", pairName, maxWeight/2, maxWeight/2);

            // Johansen (50% от веса)
            double johansenPValue = data.getJohansenCointPValue();
            double johansenScore = Math.max(0, (0.05 - johansenPValue) / 0.05) * (maxWeight / 2.0);
            score += johansenScore;

            // ADF (50% от веса)
            Double adfPValue = getAdfPValue(data, params);
            double adfScore = Math.max(0, (0.05 - Math.min(adfPValue, 0.05)) / 0.05) * (maxWeight / 2.0);
            score += adfScore;

            log.info("    Johansen: {} очков (p-value={})",
                    NumberFormatter.format(johansenScore, 1),
                    NumberFormatter.format(johansenPValue, 6));
            log.info("    ADF: {} очков (p-value={})",
                    NumberFormatter.format(adfScore, 1),
                    NumberFormatter.format(adfPValue, 6));

        } else if (hasJohansen) {
            // ТОЛЬКО JOHANSEN - полный вес
            log.info("  🔬 {}: Динамические веса - только Johansen ({})", pairName, maxWeight);

            double johansenPValue = data.getJohansenCointPValue();
            double johansenScore = Math.max(0, (0.05 - johansenPValue) / 0.05) * maxWeight;
            score += johansenScore;

            log.info("    Johansen: {} очков (p-value={})",
                    NumberFormatter.format(johansenScore, 1),
                    NumberFormatter.format(johansenPValue, 6));

        } else if (hasAdf) {
            // ТОЛЬКО ADF - полный вес
            log.info("  🔬 {}: Динамические веса - только ADF ({})", pairName, maxWeight);

            Double adfPValue = getAdfPValue(data, params);
            double adfScore = Math.max(0, (0.05 - Math.min(adfPValue, 0.05)) / 0.05) * maxWeight;
            score += adfScore;

            log.info("    ADF: {} очков (p-value={})",
                    NumberFormatter.format(adfScore, 1),
                    NumberFormatter.format(adfPValue, 6));
        }

        // Небольшой бонус за trace statistic (только если есть Johansen) - 5% от веса
        if (hasJohansen && data.getJohansenTraceStatistic() != null && data.getJohansenCriticalValue95() != null) {
            if (data.getJohansenTraceStatistic() > data.getJohansenCriticalValue95()) {
                double traceBonus = maxWeight * 0.05; // 5% от основного веса
                score += traceBonus;
                log.info("    Бонус trace statistic: +{} очков", NumberFormatter.format(traceBonus, 1));
            }
        }

        return score; // Возвращаем полный скор без ограничений
    }

    private double calculateModelQualityScoreComponent(ZScoreData data, List<ZScoreParam> params, Settings settings) {
        double maxWeight = settings.getModelQualityScoringWeight();
        double score = 0.0;

        // R-squared (75% от веса)
        Double rSquared = getRSquared(data);
        if (rSquared != null && rSquared > 0) {
            score += rSquared * (maxWeight * 0.75); // 75% от веса при R² = 1.0
        }

        // Стабильность (25% от веса)
        if (data.getStablePeriods() != null && data.getTotalObservations() != null && data.getTotalObservations() > 0) {
            double stabilityRatio = (double) data.getStablePeriods() / data.getTotalObservations();
            score += stabilityRatio * (maxWeight * 0.25); // 25% от веса
        }

        return score;
    }

    private double calculateStatisticalSignificanceScoreComponent(ZScoreData data, List<ZScoreParam> params, Settings settings) {
        double maxWeight = settings.getStatisticsScoringWeight();
        double score = 0.0;

        // Pearson корреляция P-value (50% от веса)
        Double pearsonPValue = getCorrelationPValue(data, params);
        if (pearsonPValue != null && pearsonPValue >= 0) {
            score += Math.max(0, (0.05 - Math.min(pearsonPValue, 0.05)) / 0.05) * (maxWeight * 0.5);
        }

        // Корреляция сила (50% от веса)
        if (data.getPearsonCorr() != null) {
            double absCorr = Math.abs(data.getPearsonCorr());
            score += Math.min(absCorr, 1.0) * (maxWeight * 0.5);
        }

        return score;
    }

    private double calculateBonusScoreComponent(ZScoreData data, Settings settings) {
        double maxWeight = settings.getBonusScoringWeight();
        double bonusScore = 0.0;

        // Бонус за полноту данных Johansen (30% от веса)
        if (data.getJohansenCointPValue() != null && data.getJohansenTraceStatistic() != null) {
            bonusScore += maxWeight * 0.3;
        }

        // Бонус за стабильность (20% от веса)
        if (data.getStablePeriods() != null && data.getTotalObservations() != null) {
            bonusScore += maxWeight * 0.2;
        }

        // Бонус за качество модели (30% от веса)
        if (data.getAvgRSquared() != null && data.getAvgRSquared() > 0.8) {
            bonusScore += maxWeight * 0.3;
        }

        // ЗАМЕТКА: Пиксельный спред теперь отдельный полноценный компонент!
        // Он больше не в бонусах, а имеет собственный вес равный Johansen/ADF

        return bonusScore;
    }


    /**
     * Логирует детальную статистику по фильтрации
     */
    private void logFilteringStatistics(List<ZScoreData> originalList, List<ZScoreData> filteredList, Settings settings) {
        int total = originalList.size();
        int remaining = filteredList.size();
        int filtered = total - remaining;

        log.info("📈 === СТАТИСТИКА ФИЛЬТРАЦИИ ПАРЫ ===");
        log.info("📊 Всего пар: {}", total);
        log.info("✅ Прошли фильтры: {} ({}%)", remaining, String.format("%.1f", (remaining * 100.0 / total)));
        log.info("❌ Отфильтровано: {} ({}%)", filtered, String.format("%.1f", (filtered * 100.0 / total)));

        // Анализ качества оставшихся пар
        if (!filteredList.isEmpty()) {
            analyzeRemainingPairs(filteredList);
        }

        log.info("⚙️ Активные фильтры (централизованная фильтрация):");
        if (settings.isUseMaxAdfValueFilter())
            log.info("   🔬 Коинтеграция: Johansen p-value < 0.05, ADF p-value < {}", settings.getMaxAdfValue()); //todo выпилить хардкод 0.05
        if (settings.isUseMinRSquaredFilter())
            log.info("   📈 R-squared: > {}", settings.getMinRSquared());
        if (settings.isUseMinCorrelationFilter())
            log.info("   🔗 Корреляция: |значение| > {}", settings.getMinCorrelation());
        if (settings.isUseMaxPValueFilter())
            log.info("   📊 P-value корреляции: < {}", settings.getMaxPValue());
        if (settings.isUseMinZFilter())
            log.info("   ⚡ Z-Score: положительный и > {}", settings.getMinZ());
        log.info("   🚫 Отрицательные Z-Score отфильтровываются автоматически");
        log.info("   🎯 Ранжирование в ObtainBestPairServiceV2 по качественному скору (вместо приоритетов)");
    }

    /**
     * Анализирует качество оставшихся пар после фильтрации
     */
    private void analyzeRemainingPairs(List<ZScoreData> filteredList) {
        // Статистика Z-Score
        double avgZScore = filteredList.stream()
                .mapToDouble(d -> Math.abs(getLatestZScore(d, d.getZScoreHistory())))
                .average().orElse(0.0);

        // Статистика корреляции
        double avgCorrelation = filteredList.stream()
                .filter(d -> d.getPearsonCorr() != null)
                .mapToDouble(d -> Math.abs(d.getPearsonCorr()))
                .average().orElse(0.0);

        // Статистика R-squared
        double avgRSquared = filteredList.stream()
                .map(this::getRSquared)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average().orElse(0.0);

        // Подсчет пар с Johansen тестом
        long johansenPairs = filteredList.stream()
                .filter(d -> d.getJohansenCointPValue() != null)
                .count();

        log.info("📋 Качество отобранных пар:");
        log.info("   📊 Средний |Z-Score|: {}", String.format("%.2f", avgZScore));
        log.info("   🔗 Средняя |корреляция|: {}", String.format("%.3f", avgCorrelation));
        log.info("   📈 Средний R²: {}", String.format("%.3f", avgRSquared));
        log.info("   🔬 Пары с Johansen тестом: {}/{} ({}%)",
                johansenPairs, filteredList.size(), String.format("%.1f", (johansenPairs * 100.0 / filteredList.size())));
    }
}
