package com.example.statarbitrage.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Полная структура данных для торговой пары
 * Заменяет сложную связку ZScoreData + ZScoreParam
 * Содержит ВСЕ поля для полной совместимости
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradingPair {
    
    // === ОСНОВНЫЕ ТОРГОВЫЕ ПОЛЯ ===
    
    @JsonProperty("undervaluedTicker")
    private String buyTicker;    // Что ПОКУПАЕМ (длинная позиция)
    
    @JsonProperty("overvaluedTicker") 
    private String sellTicker;   // Что ПРОДАЕМ (короткая позиция)
    
    @JsonProperty("latest_zscore")
    private Double zscore;       // Текущий Z-score
    
    private Double correlation;  // Корреляция пары
    
    @JsonProperty("cointegration_pvalue")
    private Double pValue;       // P-value коинтеграции
    
    @JsonProperty("total_observations")
    private Integer observations; // Количество наблюдений
    
    @JsonProperty("avg_r_squared")
    private Double rSquared;     // Качество модели
    
    private Long timestamp;      // Время анализа
    
    // === ДОПОЛНИТЕЛЬНЫЕ ПОЛЯ ИЗ ZSCORЕПARAM ===
    // Для полной совместимости со старым кодом
    
    private Double adfpvalue;    // ADF p-value для стационарности
    private Double alpha;        // Альфа коэффициент регрессии
    private Double beta;         // Бета коэффициент регрессии  
    private Double spread;       // Текущий спред
    private Double mean;         // Среднее значение спреда
    private Double std;          // Стандартное отклонение спреда
    
    // === ОБРАТНАЯ СОВМЕСТИМОСТЬ ===
    
    @Deprecated
    public String getLongTicker() { 
        return buyTicker; 
    }
    
    @Deprecated
    public String getShortTicker() { 
        return sellTicker; 
    }
    
    // === БИЗНЕС-ЛОГИКА ===
    
    /**
     * Проверка валидности пары для торговли
     */
    public boolean isValidForTrading(double minCorrelation, double maxPValue, double minZScore) {
        return correlation != null && Math.abs(correlation) >= minCorrelation &&
               pValue != null && pValue <= maxPValue &&
               zscore != null && Math.abs(zscore) >= minZScore;
    }
    
    /**
     * Отображаемое имя пары
     */
    public String getDisplayName() {
        return String.format("%s/%s (z=%.2f, r=%.2f)", 
            buyTicker, sellTicker, 
            zscore != null ? zscore : 0.0,
            correlation != null ? correlation : 0.0);
    }
    
    /**
     * Направление торговли на основе Z-score
     */
    public String getTradeDirection() {
        if (zscore == null) return "UNKNOWN";
        return zscore > 0 ? "MEAN_REVERSION" : "TREND_FOLLOWING";
    }
    
    /**
     * Сила сигнала (абсолютное значение Z-score)
     */
    public double getSignalStrength() {
        return zscore != null ? Math.abs(zscore) : 0.0;
    }
    
    // === МЕТОДЫ ДЛЯ СОВМЕСТИМОСТИ ===
    
    /**
     * Проверка на валидность торговой пары с расширенными критериями
     */
    public boolean isValidForTradingExtended(double minCorrelation, double maxPValue, double minZScore, double maxAdfPValue) {
        return isValidForTrading(minCorrelation, maxPValue, minZScore) &&
               (adfpvalue == null || adfpvalue <= maxAdfPValue);
    }
    
    /**
     * Получение всех статистических параметров как строки (для логирования)
     */
    public String getStatisticsString() {
        return String.format(
            "Z=%.2f, Corr=%.2f, P=%.3f, ADF=%.3f, R²=%.2f, α=%.2f, β=%.2f", 
            zscore != null ? zscore : 0.0,
            correlation != null ? correlation : 0.0,
            pValue != null ? pValue : 0.0,
            adfpvalue != null ? adfpvalue : 0.0,
            rSquared != null ? rSquared : 0.0,
            alpha != null ? alpha : 0.0,
            beta != null ? beta : 0.0
        );
    }
    
    /**
     * Копирование статистических данных в другой TradingPair
     */
    public void copyStatisticsTo(TradingPair target) {
        if (target == null) return;
        target.setZscore(this.zscore);
        target.setCorrelation(this.correlation);
        target.setPValue(this.pValue);
        target.setAdfpvalue(this.adfpvalue);
        target.setAlpha(this.alpha);
        target.setBeta(this.beta);
        target.setSpread(this.spread);
        target.setMean(this.mean);
        target.setStd(this.std);
        target.setTimestamp(this.timestamp);
        target.setObservations(this.observations);
        target.setRSquared(this.rSquared);
    }
}