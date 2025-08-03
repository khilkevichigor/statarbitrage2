package com.example.statarbitrage.common.dto;

import com.example.statarbitrage.common.dto.cointegration.CointegrationDetails;
import com.example.statarbitrage.common.dto.cointegration.DataQuality;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZScoreData {
    private List<ZScoreParam> zscoreParams;

    // Новые четкие поля из Python API
    private String overvaluedTicker;
    private String undervaluedTicker;

    // Статистические данные
    private Double correlation;
    private Double correlation_pvalue;
    private Double cointegration_pvalue;
    private Map<String, Object> cointegration_details; //todo уже есть ниже!
    private Double latest_zscore;
    private Integer total_observations;
    private Double avg_r_squared;

    private Boolean isCointegrated;
    private DataQuality dataQuality;
    private CointegrationDetails cointegrationDetails;

    public ZScoreParam getLastZScoreParam() {
        if (zscoreParams == null || zscoreParams.isEmpty()) {
            // Если zscoreParams отсутствуют, создаем синтетический ZScoreParam из новых полей API
            if (latest_zscore != null) {
                return ZScoreParam.builder()
                        .zscore(latest_zscore)
                        .correlation(correlation != null ? correlation : 0.0)
                        .pvalue(correlation_pvalue != null ? correlation_pvalue : 0.0)
                        .adfpvalue(cointegration_pvalue != null ? cointegration_pvalue : 0.0)
                        .timestamp(System.currentTimeMillis())
                        .build();
            }
            return null;
        }
        return zscoreParams.get(zscoreParams.size() - 1);
    }
}
