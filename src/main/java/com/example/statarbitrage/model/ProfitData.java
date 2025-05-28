package com.example.statarbitrage.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ProfitData {
    private BigDecimal longReturnRounded;
    private BigDecimal shortReturnRounded;
    private BigDecimal profitRounded;
    private String profitStr;
    private BigDecimal totalCapital;
}
