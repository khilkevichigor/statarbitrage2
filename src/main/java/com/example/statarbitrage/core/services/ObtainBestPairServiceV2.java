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
public class ObtainBestPairServiceV2 {

    /**
     * Новая версия получения лучшей пары (ОБНОВЛЕНО!)
     * <p>
     * Использует НОВУЮ систему оценки качества пар:
     * - Нет приоритета Johansen тесту - все комплексно!
     * - Z-Score(40p) + Коинтеграция(25p) + Качество(20p) + Статистика(10p) + Бонус(5p)
     * - Максимальный скор: 100 очков
     */
    public Optional<ZScoreData> getBestPair(Settings settings, List<ZScoreData> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return Optional.empty();
        }

        log.info("🎯 НОВАЯ СИСТЕМА: Выбираем лучшую пару из {} кандидатов по скору качества (без приоритета Johansen)", dataList.size());

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
        log.info("🏆 НОВАЯ СИСТЕМА: Выбрана лучшая пара {}/{} с упрощенным скором {}. Основной скоринг в Filter! Детали: Z-Score={}, Корр={}, P-Value(corr)={}, P-Value(coint)={}, R²={}",
                best.getData().getUnderValuedTicker(),
                best.getData().getOverValuedTicker(),
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
     * Оценивает пару с использованием НОВОЙ системы оценки качества
     * Упрощенный метод - вся логика скоринга в FilterIncompleteZScoreParamsServiceV2
     */
    private PairCandidate evaluatePair(ZScoreData z, Settings settings) {
        List<ZScoreParam> params = z.getZScoreHistory();

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
            if (z.getLatestZScore() == null || z.getPearsonCorr() == null) {
                log.warn("⚠️ Пропускаем пару с отсутствующими данными: {}/{}",
                        z.getUnderValuedTicker(), z.getOverValuedTicker());
                return null;
            }

            zVal = z.getLatestZScore();
            corr = z.getPearsonCorr();
            pValue = z.getPearsonCorrPValue() != null ? z.getPearsonCorrPValue() : 0.0;
            adf = z.getJohansenCointPValue() != null ? z.getJohansenCointPValue() : 0.0;
            rSquared = z.getAvgRSquared() != null ? z.getAvgRSquared() : 0.0;
        }

        // ====== ПРОСТОЙ КАЛКУЛЯТОР СКОРА (основной в Filter) ======
        // Основная логика скоринга вынесена в FilterIncompleteZScoreParamsServiceV2

        double simplifiedScore = calculateSimplifiedScore(zVal, z);

        return new PairCandidate(z, simplifiedScore, zVal, corr, adf, pValue, rSquared);
    }

    /**
     * УПРОЩЕННЫЙ калкулятор скора (основной скоринг в Filter)
     * Основная логика скоринга перенесена в FilterIncompleteZScoreParamsServiceV2.calculatePairQualityScore()
     */
    private double calculateSimplifiedScore(double zVal, ZScoreData data) {
        // Простое вычисление скора - основная логика в FilterIncompleteZScoreParamsServiceV2
        double score = 0.0;

        // Z-Score - главный компонент
        score += Math.abs(zVal) * 10.0; // Базовые очки за Z-Score

        // Простые бонусы
        if (data.getJohansenCointPValue() != null) {
            score += 5.0; // Бонус за Johansen тест
        }

        if (data.getAvgRSquared() != null && data.getAvgRSquared() > 0.8) {
            score += 3.0; // Бонус за высокий R-squared
        }

        if (data.getPearsonCorr() != null) {
            score += Math.abs(data.getPearsonCorr()) * 2.0; // Бонус за корреляцию
        }

        return Math.max(0.0, score);
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

            String johansenStatus = "❌";
            if (data.getJohansenCointPValue() != null && data.getJohansenCointPValue() > 0) {
                johansenStatus = String.format("✅ (p=%.4f)", data.getJohansenCointPValue());
            }

            log.info("   {}. {}/{} -> Скор: {}, Z: {}, Корр: {}, R²: {}, Johansen: {}, ADF: {}",
                    i + 1,
                    data.getUnderValuedTicker(),
                    data.getOverValuedTicker(),
                    NumberFormatter.format(candidate.getCompositeScore(), 2),
                    NumberFormatter.format(candidate.getZScore(), 2),
                    NumberFormatter.format(candidate.getCorrelation(), 3),
                    NumberFormatter.format(candidate.getRSquared(), 3),
                    johansenStatus,
                    NumberFormatter.format(candidate.getAdfValue(), 4)
            );
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
