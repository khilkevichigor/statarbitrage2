package com.example.core.common.dto.cointegration;

import com.example.core.common.dto.ZScoreData;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DiscoveryResponse {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("pairs_found")
    private int pairs_found;

    @JsonProperty("results")
    private List<ZScoreData> results;
}