package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.common.utils.NumberFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FilterIncompleteZScoreParamsService {

    private final PairDataService pairDataService;

    /**
     * Старая версия фильтрации
     *
     * @param pairData
     * @param zScoreDataList
     * @param settings
     */
    public void filterV1(PairData pairData, List<ZScoreData> zScoreDataList, Settings settings) {
        double expected = settings.getExpectedZParamsCount();
        double maxZScore = zScoreDataList.stream()
                .map(data -> (data.getZScoreHistory() != null && !data.getZScoreHistory().isEmpty()) ? data.getZScoreHistory().get(data.getZScoreHistory().size() - 1) : null)
                .filter(Objects::nonNull)
                .map(ZScoreParam::getZscore)
                .max(Comparator.naturalOrder())
                .orElse(0d);
        log.info("🔍 Ожидаемое количество наблюдений по настройкам: {}, максимальный Z-скор: {}", expected, maxZScore);

        int before = zScoreDataList.size();

        zScoreDataList.removeIf(data -> {
            // Проверяем размер данных (используем новые поля API если zscoreParams отсутствуют)
            List<ZScoreParam> params = data.getZScoreHistory();
            int actualSize = params != null ? params.size() :
                    (data.getTotalObservations() != null ? data.getTotalObservations() : 0);

            // Для нового API не проверяем количество наблюдений - данные уже агрегированы
            boolean isIncompleteBySize = false;
            if (params != null && !params.isEmpty()) {
                // Только для старого формата проверяем количество наблюдений
                isIncompleteBySize = actualSize < expected;
                if (isIncompleteBySize) {
                    if (pairData != null) {
                        pairDataService.delete(pairData);
                        log.warn("⚠️ Удалили пару {}/{} — наблюдений {} (ожидалось {})",
                                data.getUnderValuedTicker(), data.getOverValuedTicker(), actualSize, expected);
                    }
                }
            }

            // Получаем последний Z-score (используем новые поля API если zscoreParams отсутствуют)
            double lastZScore;
            if (params != null && !params.isEmpty()) {
                lastZScore = params.get(params.size() - 1).getZscore(); //todo
            } else if (data.getLatestZScore() != null) {
                lastZScore = data.getLatestZScore();
            } else {
                if (pairData != null) {
                    pairDataService.delete(pairData);
                    log.warn("⚠️ Удалили пару {}/{} — отсутствует информация о Z-score",
                            data.getUnderValuedTicker(), data.getOverValuedTicker());
                }
                return true;
            }

            boolean isIncompleteByZ = settings.isUseMinZFilter() && lastZScore < settings.getMinZ();
            if (isIncompleteByZ) {
                if (pairData != null) {
                    pairDataService.delete(pairData);
                    log.warn("⚠️ Удалили пару {}/{} — Z-скор={} < Z-скор Min={}",
                            data.getUnderValuedTicker(), data.getOverValuedTicker(), lastZScore, settings.getMinZ());
                }
            }

            // Фильтрация по R-squared
            boolean isIncompleteByRSquared = false;
            if (settings.isUseMinRSquaredFilter() && data.getAvgRSquared() != null && data.getAvgRSquared() < settings.getMinRSquared()) {
                isIncompleteByRSquared = true;
                if (pairData != null) {
                    pairDataService.delete(pairData);
                    log.warn("⚠️ Удалили пару {}/{} — RSquared={} < MinRSquared={}",
                            data.getUnderValuedTicker(), data.getOverValuedTicker(), data.getAvgRSquared(), settings.getMinRSquared());
                }
            }

            // Фильтрация по Correlation
            boolean isIncompleteByCorrelation = false;
            if (settings.isUseMinCorrelationFilter() && data.getPearsonCorr() != null && data.getPearsonCorr() < settings.getMinCorrelation()) {
                isIncompleteByCorrelation = true;
                if (pairData != null) {
                    pairDataService.delete(pairData);
                    log.warn("⚠️ Удалили пару {}/{} — Correlation={} < MinCorrelation={}",
                            data.getUnderValuedTicker(), data.getOverValuedTicker(), data.getPearsonCorr(), settings.getMinCorrelation());
                }
            }

            // Фильтрация по pValue
            boolean isIncompleteByPValue = false;
            if (settings.isUseMaxPValueFilter()) {
                Double pValue = null;
                if (params != null && !params.isEmpty()) {
                    // Для старого формата используем pValue из последнего параметра
                    pValue = params.get(params.size() - 1).getPvalue();
                } else if (data.getPearsonCorrPValue() != null) {
                    // Для нового формата используем correlation_pvalue
                    pValue = data.getPearsonCorrPValue();
                }

                if (pValue != null && pValue > settings.getMaxPValue()) {
                    isIncompleteByPValue = true;
                    if (pairData != null) {
                        pairDataService.delete(pairData);
                        log.warn("⚠️ Удалили пару {}/{} — pValue={} > MinPValue={}",
                                data.getUnderValuedTicker(), data.getOverValuedTicker(), pValue, settings.getMaxPValue());
                    }
                }
            }

            // Фильтрация по adfValue
            boolean isIncompleteByAdfValue = false;
            if (settings.isUseMaxAdfValueFilter()) {
                Double adfValue = null;
                if (params != null && !params.isEmpty()) {
                    // Для старого формата используем adfpvalue из последнего параметра
                    adfValue = params.get(params.size() - 1).getAdfpvalue(); //todo здесь смесь старой и новой логики! актуализировать!!!
                } else if (data.getJohansenCointPValue() != null) {
                    // Для нового формата используем cointegration_pvalue
                    adfValue = data.getJohansenCointPValue(); //todo проверить это одно и то же???
                }

                if (adfValue != null && adfValue > settings.getMaxAdfValue()) {
                    isIncompleteByAdfValue = true;
                    if (pairData != null) {
                        pairDataService.delete(pairData);
                        log.warn("⚠️ Удалили пару {}/{} — adfValue={} > MaxAdfValue={}",
                                data.getUnderValuedTicker(), data.getOverValuedTicker(), adfValue, settings.getMaxAdfValue());
                    }
                }
            }

            return isIncompleteBySize || isIncompleteByZ || isIncompleteByRSquared || isIncompleteByCorrelation || isIncompleteByPValue || isIncompleteByAdfValue;
        });

        int after = zScoreDataList.size();
        log.debug("✅ После фильтрации осталось {} из {} пар", after, before);
    }

    /**
     * Новая версия фильтрации
     * <p>
     * Оптимизированная фильтрация коинтегрированных пар для парного трейдинга
     * Правильная последовательность фильтров для максимальной эффективности
     * Поддержка Johansen теста и новой структуры данных из Python API
     */
    public void filterV2(List<ZScoreData> zScoreDataList, Settings settings) {
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
            log.info("✅ Пара {}/{} прошла все фильтры.", data.getUnderValuedTicker(), data.getOverValuedTicker());
            return false;
        });

        int after = zScoreDataList.size();
        log.info("✅ Фильтрация завершена: {} → {} пар", before, after);

        // Статистика по причинам фильтрации
        filterStats.forEach((reason, count) ->
                log.info("📊 Статистика по фильтрации - {}: {} пар", reason, count));

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
     * Определяет должна ли быть отфильтрована пара
     * Возвращает причину фильтрации или null если пара прошла все фильтры
     */
    private String shouldFilterPair(ZScoreData data, Settings settings, double expectedSize) {
        List<ZScoreParam> params = data.getZScoreHistory();
        String pairName = data.getUnderValuedTicker() + "/" + data.getOverValuedTicker();

        log.info("⚙️ Проверка пары {} по критериям фильтрации:", pairName);

        // ====== ЭТАП 1: БЫСТРЫЕ ПРОВЕРКИ (дешевые операции) ======

        // 1. Проверка наличия данных
        String reason = isDataMissing(data, params) ? "Отсутствуют данные Z-score" : null;
        if (reason != null) {
            log.info("   ❌ {}: {}", pairName, reason);
            return reason;
        }
        log.info("   ✅ {}: Данные Z-score присутствуют.", pairName);

        // 2. Проверка тикеров
        reason = isTickersInvalid(data) ? "Некорректные тикеры" : null;
        if (reason != null) {
            log.info("   ❌ {}: {}", pairName, reason);
            return reason;
        }
        log.info("   ✅ {}: Тикеры корректны.", pairName);

        // 3. Проверка размера выборки (только для старого API)
        if (params != null && !params.isEmpty()) {
            int actualSize = params.size();
            if (actualSize < expectedSize) {
                reason = String.format("Недостаточно наблюдений: %d < %.0f", actualSize, expectedSize);
                log.info("   ❌ {}: {}", pairName, reason);
                return reason;
            }
            log.info("   ✅ {}: Достаточно наблюдений ({} >= {}).", pairName, actualSize, expectedSize);
        } else {
            log.info("   ℹ️ {}: Проверка размера выборки пропущена (новый формат API).", pairName);
        }


        // ====== ЭТАП 2: КОИНТЕГРАЦИЯ (критически важно!) ======

        // 4. Johansen/ADF тест на коинтеграцию (ПРИОРИТЕТ!)
        if (settings.isUseMaxAdfValueFilter()) {
            reason = checkCointegration(data, params, settings);
            if (reason != null) {
                log.info("   ❌ {}: {}", pairName, reason);
                return reason;
            }
            log.info("   ✅ {}: Прошла тест на коинтеграцию.", pairName);
        } else {
            log.info("   ℹ️ {}: Фильтр коинтеграции отключен.", pairName);
        }


        // ====== ЭТАП 3: КАЧЕСТВО СТАТИСТИЧЕСКОЙ МОДЕЛИ ======

        // 5. R-squared (объяснительная способность модели)
        if (settings.isUseMinRSquaredFilter()) {
            reason = checkRSquared(data, settings);
            if (reason != null) {
                log.info("   ❌ {}: {}", pairName, reason);
                return reason;
            }
            log.info("   ✅ {}: Прошла фильтр R-squared (R²={}).", pairName, com.example.statarbitrage.common.utils.NumberFormatter.format(getRSquared(data), 3));
        } else {
            log.info("   ℹ️ {}: Фильтр R-squared отключен.", pairName);
        }

        // 6. Стабильность коинтеграции в времени
        if (settings.isUseCointegrationStabilityFilter()) {
            reason = checkCointegrationStability(data, params, settings);
            if (reason != null) {
                log.info("   ❌ {}: {}", pairName, reason);
                return reason;
            }
            log.info("   ✅ {}: Прошла фильтр стабильности коинтеграции.", pairName);
        } else {
            log.info("   ℹ️ {}: Фильтр стабильности коинтеграции отключен.", pairName);
        }


        // ====== ЭТАП 4: СТАТИСТИЧЕСКАЯ ЗНАЧИМОСТЬ ======

        // 7. P-value корреляции
        if (settings.isUseMaxPValueFilter()) {
            reason = checkCorrelationSignificance(data, params, settings);
            if (reason != null) {
                log.info("   ❌ {}: {}", pairName, reason);
                return reason;
            }
            log.info("   ✅ {}: Прошла фильтр P-value корреляции (P-value={}).", pairName, com.example.statarbitrage.common.utils.NumberFormatter.format(getCorrelationPValue(data, params), 4));
        } else {
            log.info("   ℹ️ {}: Фильтр P-value корреляции отключен.", pairName);
        }

        // 8. Корреляция (осторожно - может быть ложной!)
        if (settings.isUseMinCorrelationFilter()) {
            reason = checkCorrelation(data, settings);
            if (reason != null) {
                log.info("   ❌ {}: {}", pairName, reason);
                return reason;
            }
            log.info("   ✅ {}: Прошла фильтр корреляции (Корр={}).", pairName, com.example.statarbitrage.common.utils.NumberFormatter.format(data.getPearsonCorr(), 3));
        } else {
            log.info("   ℹ️ {}: Фильтр корреляции отключен.", pairName);
        }


        // ====== ЭТАП 5: ТОРГОВЫЕ СИГНАЛЫ (в последнюю очередь!) ======

        // 9. Z-Score фильтр (торговый сигнал)
        if (settings.isUseMinZFilter()) {
            reason = checkZScoreSignal(data, params, settings);
            if (reason != null) {
                log.info("   ❌ {}: {}", pairName, reason);
                return reason;
            }
            log.info("   ✅ {}: Прошла фильтр Z-Score (Z={}).", pairName, com.example.statarbitrage.common.utils.NumberFormatter.format(getLatestZScore(data, params), 2));
        } else {
            log.info("   ℹ️ {}: Фильтр Z-Score отключен.", pairName);
        }

        // 10. Дополнительные торговые фильтры
        reason = checkAdditionalTradingFilters(data, params, settings);
        if (reason != null) {
            log.info("   ❌ {}: {}", pairName, reason);
            return reason;
        }
        log.info("   ✅ {}: Прошла дополнительные торговые фильтры.", pairName);

        return null; // Пара прошла все фильтры
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
                    com.example.statarbitrage.common.utils.NumberFormatter.format(johansenPValue, 6)); // Use NumberFormatter
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
                    com.example.statarbitrage.common.utils.NumberFormatter.format(adfPValue, 6), adfThreshold); // Use NumberFormatter
        }

        log.debug("✅ Пара {}/{} прошла ADF тест (p-value={})",
                data.getUnderValuedTicker(), data.getOverValuedTicker(),
                com.example.statarbitrage.common.utils.NumberFormatter.format(adfPValue, 6)); // Use NumberFormatter
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
        log.info("   🎯 Ранжирование в ObtainBestPairByCriteriaService по композитному скору");
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
                .filter(r -> r != null)
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
