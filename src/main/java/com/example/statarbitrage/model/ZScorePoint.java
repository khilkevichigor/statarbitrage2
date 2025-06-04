package com.example.statarbitrage.model;

import java.math.BigDecimal;

public record ZScorePoint(long timestamp, BigDecimal zScoreChanges, BigDecimal profit) {
}
