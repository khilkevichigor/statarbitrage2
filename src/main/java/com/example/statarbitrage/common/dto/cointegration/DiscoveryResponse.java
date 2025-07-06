package com.example.statarbitrage.common.dto.cointegration;

import com.example.statarbitrage.common.dto.ZScoreData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DiscoveryResponse {
    private boolean success;
    private int pairs_found;
    private List<ZScoreData> results;
}