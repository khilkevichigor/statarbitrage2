package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.common.utils.NumberFormatter;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ObtainBestPairByCriteriaService {

    /**
     * Улучшенный метод выбора лучшей пары для торговли
     * Использует композитный скор вместо простого максимального Z-Score
     */
    public Optional<ZScoreData> getBestByCriteria(Settings settings, List<ZScoreData> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return Optional.empty();
        }

        log.debug("🎯 Выбираем лучшую пару из {} кандидатов", dataList.size());

        List<PairCandidate> candidates = new ArrayList<>();

        for (ZScoreData z : dataList) {
            PairCandidate candidate = evaluatePair(z, settings);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }

        if (candidates.isEmpty()) {
            log.warn("❌ Нет подходящих пар после финальной фильтрации");
            return Optional.empty();
        }

        // Сортируем по композитному скору (лучший первый)
        candidates.sort(Comparator.comparingDouble(PairCandidate::getCompositeScore).reversed());

        PairCandidate best = candidates.get(0);
        log.info("🏆 Выбрана лучшая пара: {}/{} со скором {}. Детали: Z-Score={}, Корр={}, P-Value(corr)={}, P-Value(coint)={}, R²={}",
                best.getData().getUndervaluedTicker(),
                best.getData().getOvervaluedTicker(),
                NumberFormatter.format(best.getCompositeScore(), 2),
                NumberFormatter.format(best.getZScore(), 2),
                NumberFormatter.format(best.getCorrelation(), 3),
                NumberFormatter.format(best.getPValue(), 4),
                NumberFormatter.format(best.getAdfValue(), 4),
                NumberFormatter.format(best.getRSquared(), 3)
        );

        // Логируем топ-3 для анализа
        logTopCandidates(candidates);

        return Optional.of(best.getData());
    }

    /**
     * Оценивает пару и возвращает кандидата с композитным скором
     */
    private PairCandidate evaluatePair(ZScoreData z, Settings settings) {
        List<ZScoreParam> params = z.getZscoreHistory();

        double zVal, pValue, adf, corr, rSquared;

        if (params != null && !params.isEmpty()) {
            // Старый формат с детальными параметрами
            ZScoreParam last = params.get(params.size() - 1);
            zVal = last.getZscore();
            pValue = last.getPvalue();
            adf = last.getAdfpvalue();
            corr = last.getCorrelation();
            rSquared = z.getAvgRSquared() != null ? z.getAvgRSquared() : 0.0;
        } else {
            // Новый формат с агрегированными данными
            if (z.getLatestZscore() == null || z.getCorrelation() == null) {
                log.debug("⚠️ Пропускаем пару с отсутствующими данными: {}/{}",
                        z.getUndervaluedTicker(), z.getOvervaluedTicker());
                return null;
            }

            zVal = z.getLatestZscore();
            corr = z.getCorrelation();
            pValue = z.getCorrelationPvalue() != null ? z.getCorrelationPvalue() : 0.0;
            adf = z.getCointegrationPvalue() != null ? z.getCointegrationPvalue() : 0.0;
            rSquared = z.getAvgRSquared() != null ? z.getAvgRSquared() : 0.0;
        }

        // ====== ТОЛЬКО КОМПОЗИТНЫЙ СКОР (без фильтрации) ======
        // Вся фильтрация уже выполнена в FilterIncompleteZScoreParamsService

        double compositeScore = calculateCompositeScore(zVal, corr, adf, pValue, rSquared, z, settings);

        return new PairCandidate(z, compositeScore, zVal, corr, adf, pValue, rSquared);
    }

    /**
     * Рассчитывает композитный скор для ранжирования пар
     * Учитывает множественные факторы с весами
     */
    private double calculateCompositeScore(double zVal, double corr, double adf,
                                           double pValue, double rSquared,
                                           ZScoreData data, Settings settings) {
        double score = 0.0;

        // 1. Z-Score компонент (40% веса) - основной торговый сигнал
        double zScoreComponent = Math.abs(zVal) * 40.0;

        // 2. Качество коинтеграции (25% веса) - Учитывает Johansen и ADF
        double cointegrationComponent = 0.0;
        double johansenWeight = 0.6; // 60% вес для Johansen
        double adfWeight = 0.4;      // 40% вес для ADF

        boolean hasJohansen = data.getCointegrationPvalue() != null && data.getCointegrationPvalue() > 0;
        boolean hasAdf = adf > 0;

        if (hasJohansen && hasAdf) {
            // Оба теста доступны: используем взвешенную оценку
            double johansenScore = (1.0 - data.getCointegrationPvalue());
            double adfScore = (1.0 - Math.min(adf, 1.0));
            cointegrationComponent = (johansenScore * johansenWeight + adfScore * adfWeight) * 25.0;
            log.trace("Расчет коинтеграции для {}/{}: Johansen ({}) и ADF ({}) -> {}",
                    data.getUndervaluedTicker(), data.getOvervaluedTicker(),
                    NumberFormatter.format(johansenScore, 2), NumberFormatter.format(adfScore, 2),
                    NumberFormatter.format(cointegrationComponent, 2));

        } else if (hasJohansen) {
            // Только Johansen
            cointegrationComponent = (1.0 - data.getCointegrationPvalue()) * 25.0;
        } else if (hasAdf) {
            // Только ADF
            cointegrationComponent = (1.0 - Math.min(adf, 1.0)) * 25.0; // Используем полный вес
        }

        // 3. R-squared компонент (20% веса) - качество модели
        double rSquaredComponent = rSquared * 20.0;

        // 4. Корреляция компонент (10% веса)
        double correlationComponent = Math.abs(corr) * 10.0;

        // 5. Статистическая значимость (5% веса)
        double significanceComponent = (1.0 - Math.min(pValue, 1.0)) * 5.0;

        score = zScoreComponent + cointegrationComponent + rSquaredComponent +
                correlationComponent + significanceComponent;

        // БОНУСЫ за особые качества:

        // Бонус за использование Johansen теста (более надежный)
        if (data.getCointegrationPvalue() != null && data.getTraceStatistic() != null) {
            score += 5.0; // Бонус за Johansen

            // Дополнительный бонус за сильную коинтеграцию (trace >> critical)
            if (data.getCriticalValue95() != null &&
                    data.getTraceStatistic() > data.getCriticalValue95() * 1.5) {
                score += 3.0;
            }
        }

        // Бонус за стабильность (если есть данные)
        if (data.getStablePeriods() != null && data.getTotalObservations() != null) {
            double stabilityRatio = (double) data.getStablePeriods() / data.getTotalObservations();
            if (stabilityRatio > 0.8) {
                score += 2.0; // Бонус за высокую стабильность
            }
        }

        // ШТРАФЫ за риски:

        // Штраф за слишком высокую корреляцию (может быть ложной)
        if (Math.abs(corr) > 0.95) {
            score -= 3.0; // Подозрительно высокая корреляция
        }

        // Штраф за волатильность Z-Score (если есть история)
        if (data.getZscoreHistory() != null && data.getZscoreHistory().size() >= 10) {
            double volatility = calculateZScoreVolatility(data.getZscoreHistory());
            if (volatility > 2.0) {
                score -= volatility; // Штраф за высокую волатильность
            }
        }

        return Math.max(0.0, score); // Не даем отрицательным скорам
    }

    /**
     * Рассчитывает волатильность Z-Score
     */
    private double calculateZScoreVolatility(List<ZScoreParam> params) {
        if (params.size() < 10) return 0.0;

        List<Double> recentZScores = params.subList(params.size() - 10, params.size())
                .stream()
                .map(ZScoreParam::getZscore)
                .toList();

        double mean = recentZScores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = recentZScores.stream()
                .mapToDouble(z -> Math.pow(z - mean, 2))
                .average()
                .orElse(0.0);

        return Math.sqrt(variance);
    }

    /**
     * Логирует топ кандидатов для анализа
     */
    private void logTopCandidates(List<PairCandidate> candidates) {
        log.info("🏅 Топ-3 кандидата:");

        for (int i = 0; i < Math.min(3, candidates.size()); i++) {
            PairCandidate candidate = candidates.get(i);
            ZScoreData data = candidate.getData();

            log.info("   {}. {}/{} - Скор: {}, Z: {}, Корр: {}, Johansen: {}",
                    i + 1,
                    data.getUndervaluedTicker(),
                    data.getOvervaluedTicker(),
                    com.example.statarbitrage.common.utils.NumberFormatter.format(candidate.getCompositeScore(), 1),
                    com.example.statarbitrage.common.utils.NumberFormatter.format(candidate.getZScore(), 2),
                    com.example.statarbitrage.common.utils.NumberFormatter.format(candidate.getCorrelation(), 3),
                    data.getCointegrationPvalue() != null ? "✅" : "❌");
        }
    }

    /**
     * Класс для хранения кандидата с метриками
     */
    @Data
    private static class PairCandidate {
        // Getters
        private final ZScoreData data;
        private final double compositeScore;
        private final double zScore;
        private final double correlation;
        private final double adfValue;
        private final double pValue;
        private final double rSquared;

        public PairCandidate(ZScoreData data, double compositeScore, double zScore,
                             double correlation, double adfValue, double pValue, double rSquared) {
            this.data = data;
            this.compositeScore = compositeScore;
            this.zScore = zScore;
            this.correlation = correlation;
            this.adfValue = adfValue;
            this.pValue = pValue;
            this.rSquared = rSquared;
        }
    }
}
