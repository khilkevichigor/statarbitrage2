package com.example.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProfitHistoryItem {
    private long timestamp;
    private double profitPercent;
}
