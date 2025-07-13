package com.example.statarbitrage.ui.dto;

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
public class FetchPairsRequest {
    private Integer countOfPairs;
}