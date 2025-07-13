package com.example.statarbitrage.ui.dto;

import com.example.statarbitrage.common.model.PairData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Модель свечи для тестирования
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartNewTradeRequest {
    private PairData pairData;
    private boolean checkAutoTrading;
}