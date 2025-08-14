package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ObtainBestPairServiceV1 {

    /**
     * Старая версия получения лучшей пары
     *
     * @param settings
     * @param dataList
     * @return
     */
    public Optional<ZScoreData> getBestPair(Settings settings, List<ZScoreData> dataList) {
        ZScoreData best = null;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (ZScoreData z : dataList) {
            List<ZScoreParam> params = z.getZScoreHistory();

            double zVal, pValue, adf, corr;

            if (params != null && !params.isEmpty()) {
                // Используем старый формат с детальными параметрами
                ZScoreParam last = params.get(params.size() - 1);
                zVal = last.getZscore();
                pValue = last.getPvalue();
                adf = last.getAdfpvalue();
                corr = last.getCorrelation();
            } else {
                // Используем новый формат с агрегированными данными
                if (z.getLatestZScore() == null || z.getPearsonCorr() == null) continue;

                zVal = z.getLatestZScore();
                corr = z.getPearsonCorr();

                // Для новых полей используем разумные значения по умолчанию
                pValue = z.getPearsonCorrPValue() != null ? z.getPearsonCorrPValue() : 0.0;
                adf = z.getJohansenCointPValue() != null ? z.getJohansenCointPValue() : 0.0;
            }

            // 1. Z >= minZ (только положительные Z-score, исключаем зеркальные пары)
            if (settings.isUseMinZFilter() && zVal < settings.getMinZ()) continue;

            // 2. pValue <= minPValue
            if (settings.isUseMaxPValueFilter() && pValue > settings.getMaxPValue()) continue;

            // 3. adfValue <= maxAdfValue
            if (settings.isUseMaxAdfValueFilter() && adf > settings.getMaxAdfValue()) continue;

            // 4. corr >= minCorr
            if (settings.isUseMinCorrelationFilter() && corr < settings.getMinCorrelation()) continue;

            // 5. Выбираем с максимальным Z (только положительные)
            if (zVal > maxZ) {
                maxZ = zVal;
                best = z;
            }
        }

        return Optional.ofNullable(best);
    }
}
