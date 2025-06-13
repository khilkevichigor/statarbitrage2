package com.example.statarbitrage.model.threecommas;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class StrategyList {

    private Map<String, StrategyInfo> strategies = new HashMap<>();

    @JsonAnySetter
    public void addStrategy(String key, StrategyInfo value) {
        strategies.put(key, value);
    }


}
