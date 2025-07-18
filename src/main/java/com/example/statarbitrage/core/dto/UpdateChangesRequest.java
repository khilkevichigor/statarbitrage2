package com.example.statarbitrage.core.dto;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.core.processors.UpdateChangesType;
import com.example.statarbitrage.trading.model.ArbitragePairTradeInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateChangesRequest {
    private PairData pairData;
    private ArbitragePairTradeInfo closeResult;
    private UpdateChangesType updateChangesType;
}
