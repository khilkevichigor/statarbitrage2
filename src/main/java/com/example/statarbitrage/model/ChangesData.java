package com.example.statarbitrage.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ChangesData {
    private BigDecimal longReturnRounded;
    private BigDecimal shortReturnRounded;

    private BigDecimal profitRounded;

    private BigDecimal maxProfitRounded;
    private BigDecimal minProfitRounded;

    private long timeInMinutesSinceEntryToMin;
    private long timeInMinutesSinceEntryToMax;

    private BigDecimal zScoreRounded;

    private BigDecimal totalCapital;

    private String chartProfitMessage;
    private String allLogMessage;

}
