package com.example.statarbitrage.common.dto;

import java.math.BigDecimal;

public record ProfitExtremum(BigDecimal maxProfit, BigDecimal minProfit, long timeToMax, long timeToMin, BigDecimal currentProfit) {
}