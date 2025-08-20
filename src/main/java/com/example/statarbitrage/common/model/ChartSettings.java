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
    @Column(name = "id")
    private Long id;

    @Column(name = "chart_type", unique = true, nullable = false)
    private String chartType; // Тип чарта, например "ZSCORE_CHART_DIALOG"

    // Основные чарты
    @Column(name = "show_z_score")
    @Builder.Default
    private boolean showZScore = true;

    @Column(name = "show_combined_price")
    @Builder.Default
    private boolean showCombinedPrice = true;

    @Column(name = "show_pixel_spread")
    @Builder.Default
    private boolean showPixelSpread = false;

    // Дополнительные индикаторы на Z-Score чарте  
    @Column(name = "show_ema")
    @Builder.Default
    private boolean showEma = false;

    @Column(name = "show_stoch_rsi")
    @Builder.Default
    private boolean showStochRsi = false;

    @Column(name = "show_profit")
    @Builder.Default
    private boolean showProfit = false;

    // Показать точку входа (вертикальная и горизонтальная линии)
    @Column(name = "show_entry_point")
    @Builder.Default
    private boolean showEntryPoint = true;
}