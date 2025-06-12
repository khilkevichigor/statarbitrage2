package com.example.statarbitrage.model.threecommas;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class StrategyListResponse {

    private Map<String, StrategyInfo> strategies = new HashMap<>();

    @JsonAnySetter
    public void addStrategy(String key, StrategyInfo value) {
        strategies.put(key, value);
    }

    @Data
    public static class StrategyInfo {
        @JsonProperty("strategy_type")
        private String strategyType;

        private boolean payed;
        private boolean beta;
        private String name;

        private StrategyOptions options;

        @JsonProperty("accounts_whitelist")
        private List<String> accountsWhitelist;
    }

    @Data
    public static class StrategyOptions {
        private Map<String, String> time;
        private Object points;

        @JsonProperty("trigger_condition")
        private Map<String, String> triggerCondition;

        @JsonProperty("time_period")
        private Object timePeriod;
    }
}
