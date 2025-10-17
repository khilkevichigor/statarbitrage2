package com.example.shared.dto;

import com.example.shared.models.Pair;
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
public class UpdateTradeRequest {
    private Pair tradingPair;
    private boolean closeManually;
}