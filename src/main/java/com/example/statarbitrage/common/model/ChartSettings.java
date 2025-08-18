package com.example.statarbitrage.common.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chart_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChartSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chart_type", unique = true, nullable = false)
    private String chartType; // Тип чарта, например "ZSCORE_CHART_DIALOG"

    // Основные чарты
    @Builder.Default
    private boolean showZScore = true;

    @Builder.Default
    private boolean showCombinedPrice = true;

    @Builder.Default
    private boolean showPixelSpread = false;

    // Дополнительные индикаторы на Z-Score чарте  
    @Builder.Default
    private boolean showEma = false;

    @Builder.Default
    private boolean showStochRsi = false;

    @Builder.Default
    private boolean showProfit = false;
}