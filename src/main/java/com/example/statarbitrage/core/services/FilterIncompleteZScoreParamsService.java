package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FilterIncompleteZScoreParamsService {
    private final PairDataService pairDataService;

    /**
     * Оптимизированная фильтрация коинтегрированных пар для парного трейдинга
     * Правильная последовательность фильтров для максимальной эффективности
     * Поддержка Johansen теста и новой структуры данных из Python API
     */
    public void filter(PairData pairData, List<ZScoreData> zScoreDataList, Settings settings) {
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
                // Удаляем PairData только при массовой фильтрации (когда pairData == null)
                // При единичной фильтрации (для новых трейдов) PairData не удаляем
                log.debug("⚠️ Отфильтровано {}/{} — {}",
                        data.getUndervaluedTicker(), data.getOvervaluedTicker(), reason);
                return true;
            }
            return false;
        });

        int after = zScoreDataList.size();
        log.info("✅ Фильтрация завершена: {} → {} пар", before, after);

        // Статистика по причинам фильтрации
        filterStats.forEach((reason, count) ->
                log.debug("📊 {}: {} пар", reason, count));

        // Детальная статистика фильтрации
        logFilteringStatistics(originalList, zScoreDataList, settings);
    }

    /**
     * Анализирует входящие данные и определяет формат API
     */
    private void analyzeInputData(List<ZScoreData> zScoreDataList) {
        if (zScoreDataList.isEmpty()) return;

        ZScoreData sample = zScoreDataList.get(0);
        boolean hasOldFormat = sample.getZscoreHistory() != null && !sample.getZscoreHistory().isEmpty();
        boolean hasNewFormat = sample.getLatestZscore() != null;
        boolean hasJohansenData = sample.getCointegrationPvalue() != null;

        log.info("📋 Анализ формата данных:");
        log.info("   📊 Старый формат (zscoreParams): {}", hasOldFormat ? "✅" : "❌");
        log.info("   🆕 Новый формат (latest_zscore): {}", hasNewFormat ? "✅" : "❌");
        log.info("   🔬 Johansen тест: {}", hasJohansenData ? "✅ ДОСТУПЕН" : "❌");

        if (hasJohansenData) {
            double minJohansenPValue = zScoreDataList.stream()
                    .filter(d -> d.getCointegrationPvalue() != null)
                    .mapToDouble(ZScoreData::getCointegrationPvalue)
                    .min()
                    .orElse(1.0);
            log.info("   📈 Минимальный Johansen p-value: {}", String.format("%.6f", minJohansenPValue));
        }
    }

    /**
     * Определяет должна ли быть отфильтрована пара
     * Возвращает причину фильтрации или null если пара прошла все фильтры
     */
    private String shouldFilterPair(ZScoreData data, Settings settings, double expectedSize) {
        List<ZScoreParam> params = data.getZscoreHistory();

        // ====== ЭТАП 1: БЫСТРЫЕ ПРОВЕРКИ (дешевые операции) ======

        // 1. Проверка наличия данных
        if (isDataMissing(data, params)) {
            return "Отсутствуют данные Z-score";
        }

        // 2. Проверка тикеров
        if (isTickersInvalid(data)) {
            return "Некорректные тикеры";
        }

        // 3. Проверка размера выборки (только для старого API)
        if (params != null && !params.isEmpty()) {
            int actualSize = params.size();
            if (actualSize < expectedSize) {
                return String.format("Недостаточно наблюдений: %d < %.0f", actualSize, expectedSize);
            }
        }

        // ====== ЭТАП 2: КОИНТЕГРАЦИЯ (критически важно!) ======

        // 4. Johansen/ADF тест на коинтеграцию (ПРИОРИТЕТ!)
        if (settings.isUseMaxAdfValueFilter()) {
            String cointegrationReason = checkCointegration(data, params, settings);
            if (cointegrationReason != null) return cointegrationReason;
        }

        // ====== ЭТАП 3: КАЧЕСТВО СТАТИСТИЧЕСКОЙ МОДЕЛИ ======

        // 5. R-squared (объяснительная способность модели)
        if (settings.isUseMinRSquaredFilter()) {
            String rSquaredReason = checkRSquared(data, settings);
            if (rSquaredReason != null) return rSquaredReason;
        }

        // 6. Стабильность коинтеграции в времени
        if (settings.isUseCointegrationStabilityFilter()) {
            String stabilityReason = checkCointegrationStability(data, params, settings);
            if (stabilityReason != null) return stabilityReason;
        }

        // ====== ЭТАП 4: СТАТИСТИЧЕСКАЯ ЗНАЧИМОСТЬ ======

        // 7. P-value корреляции
        if (settings.isUseMinPValueFilter()) {
            String pValueReason = checkCorrelationSignificance(data, params, settings);
            if (pValueReason != null) return pValueReason;
        }

        // 8. Корреляция (осторожно - может быть ложной!)
        if (settings.isUseMinCorrelationFilter()) {
            String correlationReason = checkCorrelation(data, settings);
            if (correlationReason != null) return correlationReason;
        }

        // ====== ЭТАП 5: ТОРГОВЫЕ СИГНАЛЫ (в последнюю очередь!) ======

        // 9. Z-Score фильтр (торговый сигнал)
        if (settings.isUseMinZFilter()) {
            String zScoreReason = checkZScoreSignal(data, params, settings);
            if (zScoreReason != null) return zScoreReason;
        }

        // 10. Дополнительные торговые фильтры
        String tradingReason = checkAdditionalTradingFilters(data, params, settings);
        if (tradingReason != null) return tradingReason;

        return null; // Пара прошла все фильтры
    }

    // ============ ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ФИЛЬТРАЦИИ ============

    private boolean isDataMissing(ZScoreData data, List<ZScoreParam> params) {
        // Проверяем наличие Z-score данных в любом формате
        if (params != null && !params.isEmpty()) {
            return false; // Старый формат - есть данные
        }
        return data.getLatestZscore() == null; // Новый формат - проверяем latest_zscore
    }

    private boolean isTickersInvalid(ZScoreData data) {
        return data.getUndervaluedTicker() == null ||
                data.getOvervaluedTicker() == null ||
                data.getUndervaluedTicker().isEmpty() ||
                data.getOvervaluedTicker().isEmpty() ||
                data.getUndervaluedTicker().equals(data.getOvervaluedTicker());
    }

    private String checkCointegration(ZScoreData data, List<ZScoreParam> params, Settings settings) {
        // ПРИОРИТЕТ 1: Johansen тест (если доступен)
        if (data.getCointegrationPvalue() != null) {
            Double johansenPValue = data.getCointegrationPvalue();
            log.debug("🔬 Johansen p-value: {} для пары {}/{}",
                    String.format("%.6f", johansenPValue),
                    data.getUndervaluedTicker(),
                    data.getOvervaluedTicker());

            if (johansenPValue > settings.getMaxAdfValue()) {
                return String.format("НЕ коинтегрированы (Johansen): p-value=%.6f > %.6f",
                        johansenPValue, settings.getMaxAdfValue());
            }

            // Дополнительная проверка качества Johansen теста
            if (data.getTraceStatistic() != null) {
                if (data.getError() != null) {
                    return "Ошибка в Johansen тесте: " + data.getError();
                }

                // Проверяем trace statistic
                if (data.getTraceStatistic() != null && data.getCriticalValue95() != null) {
                    if (data.getTraceStatistic() <= data.getCriticalValue95()) {
                        return String.format("Слабая коинтеграция (Johansen): trace=%.2f ≤ critical=%.2f",
                                data.getTraceStatistic(), data.getCriticalValue95());
                    }
                }
            }

            log.debug("✅ Пара {}/{} прошла Johansen тест",
                    data.getUndervaluedTicker(), data.getOvervaluedTicker());
            return null; // Прошли Johansen тест
        }

        // ПРИОРИТЕТ 2: Fallback к ADF если нет Johansen данных
        Double adfPValue = getAdfPValue(data, params);
        if (adfPValue == null) {
            return "Отсутствует cointegration p-value";
        }

        // Для криптовалют используем более мягкие критерии ADF
        double adfThreshold = Math.max(settings.getMaxAdfValue(), 0.15); // Минимум 0.15 для crypto

        if (adfPValue > adfThreshold) {
            return String.format("Слабая коинтеграция (ADF): p-value=%.6f > %.6f",
                    adfPValue, adfThreshold);
        }

        log.debug("✅ Пара {}/{} прошла ADF тест",
                data.getUndervaluedTicker(), data.getOvervaluedTicker());
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

        if (pValue > settings.getMinPValue()) {
            return String.format("Незначимая корреляция: p-value=%.6f > %.6f",
                    pValue, settings.getMinPValue());
        }

        return null;
    }

    private String checkCorrelation(ZScoreData data, Settings settings) {
        Double correlation = data.getCorrelation();
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

        // Используем абсолютное значение Z-Score для торгового сигнала
        double absZScore = Math.abs(lastZScore);
        if (absZScore < settings.getMinZ()) {
            return String.format("Слабый сигнал: |Z-score|=%.2f < %.2f",
                    absZScore, settings.getMinZ());
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
            // Новый формат API - ADF может быть в cointegration_pvalue если нет Johansen
            return data.getCointegrationPvalue();
        }
    }

    private Double getCorrelationPValue(ZScoreData data, List<ZScoreParam> params) {
        if (params != null && !params.isEmpty()) {
            // Старый формат API
            ZScoreParam lastParam = params.get(params.size() - 1);
            return lastParam.getPvalue();
        } else {
            // Новый формат API
            return data.getCorrelationPvalue();
        }
    }

    private double getLatestZScore(ZScoreData data, List<ZScoreParam> params) {
        if (params != null && !params.isEmpty()) {
            // Старый формат API
            return params.get(params.size() - 1).getZscore();
        } else if (data.getLatestZscore() != null) {
            // Новый формат API
            return data.getLatestZscore();
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

        log.info("⚙️ Активные фильтры:");
        if (settings.isUseMaxAdfValueFilter())
            log.info("   🔬 Коинтеграция: p-value < {}", settings.getMaxAdfValue());
        if (settings.isUseMinRSquaredFilter())
            log.info("   📈 R-squared: > {}", settings.getMinRSquared());
        if (settings.isUseMinCorrelationFilter())
            log.info("   🔗 Корреляция: > {}", settings.getMinCorrelation());
        if (settings.isUseMinPValueFilter())
            log.info("   📊 P-value корреляции: < {}", settings.getMinPValue());
        if (settings.isUseMinZFilter())
            log.info("   ⚡ Z-Score: > {}", settings.getMinZ());
    }

    /**
     * Анализирует качество оставшихся пар после фильтрации
     */
    private void analyzeRemainingPairs(List<ZScoreData> filteredList) {
        // Статистика Z-Score
        double avgZScore = filteredList.stream()
                .mapToDouble(d -> Math.abs(getLatestZScore(d, d.getZscoreHistory())))
                .average().orElse(0.0);

        // Статистика корреляции
        double avgCorrelation = filteredList.stream()
                .filter(d -> d.getCorrelation() != null)
                .mapToDouble(d -> Math.abs(d.getCorrelation()))
                .average().orElse(0.0);

        // Статистика R-squared
        double avgRSquared = filteredList.stream()
                .map(this::getRSquared)
                .filter(r -> r != null)
                .mapToDouble(Double::doubleValue)
                .average().orElse(0.0);

        // Подсчет пар с Johansen тестом
        long johansenPairs = filteredList.stream()
                .filter(d -> d.getCointegrationPvalue() != null)
                .count();

        log.info("📋 Качество отобранных пар:");
        log.info("   📊 Средний |Z-Score|: {}", String.format("%.2f", avgZScore));
        log.info("   🔗 Средняя |корреляция|: {}", String.format("%.3f", avgCorrelation));
        log.info("   📈 Средний R²: {}", String.format("%.3f", avgRSquared));
        log.info("   🔬 Пары с Johansen тестом: {}/{} ({}%)",
                johansenPairs, filteredList.size(), String.format("%.1f", (johansenPairs * 100.0 / filteredList.size())));
    }
}
