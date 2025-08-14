package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class FilterIncompleteZScoreParamsServiceV1 {

    private final PairDataService pairDataService;

    /**
     * Старая версия фильтрации
     *
     * @param pairData
     * @param zScoreDataList
     * @param settings
     */
    public void filter(PairData pairData, List<ZScoreData> zScoreDataList, Settings settings) {
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
}
