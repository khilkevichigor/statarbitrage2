package com.example.statarbitrage.model.threecommas;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class StrategyInfo {
    @JsonProperty("strategy_type")
    private String strategyType;

    private boolean payed;
    private boolean beta;
    private String name;

    private StrategyOptions options;

    @JsonProperty("accounts_whitelist")
    private List<String> accountsWhitelist;
}