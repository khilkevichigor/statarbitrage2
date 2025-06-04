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
    private BigDecimal zScoreRounded;
    private String zScoreStr;
    private String profitStr;
    private String chartProfitMessage;
    private BigDecimal totalCapital;
    private String logMessage;
}
