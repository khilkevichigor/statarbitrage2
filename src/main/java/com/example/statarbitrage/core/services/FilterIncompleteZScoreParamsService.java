package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FilterIncompleteZScoreParamsService {
    private final PairDataService pairDataService;

    /**
     * Оптимизированная фильтрация коинтегрированных пар для парного трейдинга
     * Правильная последовательность фильтров для максимальной эффективности
     */
    public void filter(PairData pairData, List<ZScoreData> zScoreDataList, Settings settings) {
        double expected = settings.getExpectedZParamsCount();
        double maxZScore = zScoreDataList.stream()
                .map(ZScoreData::getLastZScoreParam)
                .map(ZScoreParam::getZscore)
                .max(Comparator.naturalOrder())
                .orElse(0d);

        log.info("🔍 Ожидаемое количество наблюдений: {}, максимальный Z-скор: {}", expected, maxZScore);

        int before = zScoreDataList.size();
        Map<String, Integer> filterStats = new HashMap<>();

        zScoreDataList.removeIf(data -> {
            String reason = shouldFilterPair(data, settings, expected);
            if (reason != null) {
                filterStats.merge(reason, 1, Integer::sum);
                if (pairData != null) {
                    pairDataService.delete(pairData);
                }
                log.debug("⚠️ Отфильтровано {}/{} — {}",
                        data.getUndervaluedTicker(), data.getOvervaluedTicker(), reason);
                return true;
            }
            return false;
        });

        int after = zScoreDataList.size();
        log.info("✅ Фильтрация завершена: {} → {} пар", before, after);

        // Статистика фильтрации
        filterStats.forEach((reason, count) ->
                log.info("📊 {}: {} пар", reason, count));
    }

    /**
     * Определяет должна ли быть отфильтрована пара
     * Возвращает причину фильтрации или null если пара прошла все фильтры
     */
    private String shouldFilterPair(ZScoreData data, Settings settings, double expectedSize) {
        List<ZScoreParam> params = data.getZscoreParams();

        // ====== ЭТАП 1: БЫСТРЫЕ ПРОВЕРКИ (дешевые операции) ======

        // 1. Проверка наличия данных
        if (isDataMissing(data, params)) {
            return "Отсутствуют данные Z-score";
        }

        // 2. Проверка размера выборки (только для старого API)
        if (params != null && !params.isEmpty()) {
            int actualSize = params.size();
            if (actualSize < expectedSize) {
                return String.format("Недостаточно наблюдений: %d < %.0f", actualSize, expectedSize);
            }
        }

        // 3. Проверка объема торгов (если включена) //проверяем когда берем тикеры
//        if (settings.isUseMinVolumeFilter()) {
//            String volumeReason = checkVolumeFilter(data, settings);
//            if (volumeReason != null) return volumeReason;
//        }

        // ====== ЭТАП 2: КОИНТЕГРАЦИЯ (критически важно!) ======

        // 4. ADF тест на коинтеграцию (ПРИОРИТЕТ!)
        if (settings.isUseMaxAdfValueFilter()) {
            String adfReason = checkCointegration(data, params, settings);
            if (adfReason != null) return adfReason;
        }

        // ====== ЭТАП 3: КАЧЕСТВО СТАТИСТИЧЕСКОЙ МОДЕЛИ ======

        // 5. R-squared (объяснительная способность модели)
        if (settings.isUseMinRSquaredFilter()) {
            String rSquaredReason = checkRSquared(data, settings);
            if (rSquaredReason != null) return rSquaredReason;
        }

        // 6. Стабильность коинтеграции в времени
        if (settings.isUseCointegrationStabilityFilter()) {
            String stabilityReason = checkCointegrationStability(params, settings);
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
        // Проверяем наличие Z-score данных
        if (params != null && !params.isEmpty()) {
            return false; // Старый формат - есть данные
        }
        return data.getLatest_zscore() == null; // Новый формат - проверяем latest_zscore
    }

//    private String checkVolumeFilter(ZScoreData data, Settings settings) { //todo проверяем преде получением тикеров
//        // Проверка минимального объема торгов (если такие данные есть)
//        Double volume24h = data.getVolume_24h(); // Предполагаем что такое поле есть
//        if (volume24h != null && volume24h < settings.getMinVolume()) {
//            return String.format("Низкий объем: %.2f < %.2f", volume24h, settings.getMinVolume());
//        }
//        return null;
//    }

    private String checkCointegration(ZScoreData data, List<ZScoreParam> params, Settings settings) {
        Double adfPValue = getAdfPValue(data, params);

        if (adfPValue == null) {
            return "Отсутствует ADF p-value";
        }

        // КРИТИЧНО: ADF p-value должно быть < 0.05 для коинтеграции!
        // Нулевая гипотеза ADF: НЕТ коинтеграции
        // p-value < 0.05 = отвергаем H0 = ЕСТЬ коинтеграция
        if (adfPValue > settings.getMaxAdfValue()) {
            return String.format("НЕ коинтегрированы: ADF p-value=%.6f > %.6f",
                    adfPValue, settings.getMaxAdfValue());
        }

        return null;
    }

    private String checkRSquared(ZScoreData data, Settings settings) {
        Double rSquared = data.getAvg_r_squared();
        if (rSquared == null) {
            return "Отсутствует R-squared";
        }

        if (rSquared < settings.getMinRSquared()) {
            return String.format("Слабая модель: R²=%.4f < %.4f", rSquared, settings.getMinRSquared());
        }

        return null;
    }

    private String checkCointegrationStability(List<ZScoreParam> params, Settings settings) {
        if (params == null || params.size() < 100) {
            return null; // Недостаточно данных для проверки стабильности
        }

        int windowSize = (int) settings.getMinWindowSize(); // Размер окна для проверки
        int stableWindows = 0;
        int totalWindows = 0;

        // Проверяем стабильность коинтеграции в скользящих окнах
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
//        double minStabilityRatio = settings.getMinCointegrationStability(); // Например, 0.7
        double minStabilityRatio = 0.7; // Например, 0.7

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
//        if (Math.abs(lastZScore) > settings.getMaxZForEntry()) { // Например, 5.0
        if (Math.abs(lastZScore) > 5.0) { // Например, 5.0
            return String.format("Экстремальный Z-score: %.2f", lastZScore);
        }

        // 2. Проверка тренда Z-Score (не должен быть слишком волатильным)
        if (params != null && params.size() >= 10) {
            double zScoreVolatility = calculateZScoreVolatility(params);
//            if (zScoreVolatility > settings.getMaxZScoreVolatility()) {
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
            // Новый формат API
            return data.getCointegration_pvalue();
        }
    }

    private Double getCorrelationPValue(ZScoreData data, List<ZScoreParam> params) {
        if (params != null && !params.isEmpty()) {
            // Старый формат API
            ZScoreParam lastParam = params.get(params.size() - 1);
            return lastParam.getPvalue();
        } else {
            // Новый формат API
            return data.getCorrelation_pvalue();
        }
    }

    private double getLatestZScore(ZScoreData data, List<ZScoreParam> params) {
        if (params != null && !params.isEmpty()) {
            // Старый формат API
            return params.get(params.size() - 1).getZscore();
        } else if (data.getLatest_zscore() != null) {
            // Новый формат API
            return data.getLatest_zscore();
        } else {
            return 0.0;
        }
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
        log.info("✅ Прошли фильтры: {} ({:.1f}%)", remaining, (remaining * 100.0 / total));
        log.info("❌ Отфильтровано: {} ({:.1f}%)", filtered, (filtered * 100.0 / total));
        log.info("⚙️ Активные фильтры:");

        if (settings.isUseMaxAdfValueFilter())
            log.info("   🔬 Коинтеграция (ADF): p-value < {}", settings.getMaxAdfValue());
        if (settings.isUseMinRSquaredFilter())
            log.info("   📈 R-squared: > {}", settings.getMinRSquared());
        if (settings.isUseMinCorrelationFilter())
            log.info("   🔗 Корреляция: > {}", settings.getMinCorrelation());
        if (settings.isUseMinPValueFilter())
            log.info("   📊 P-value корреляции: < {}", settings.getMinPValue());
        if (settings.isUseMinZFilter())
            log.info("   ⚡ Z-Score: > {}", settings.getMinZ());
    }
}
