package com.example.core.common.dto;

import java.math.BigDecimal;

public record ProfitExtremum(BigDecimal maxProfit, BigDecimal minProfit, long timeToMax, long timeToMin, BigDecimal currentProfit) {
}