package com.example.statarbitrage.model.threecommas;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
public class StrategyOptions {
    private Map<String, String> time;
    private Object points;

    @JsonProperty("trigger_condition")
    private Map<String, String> triggerCondition;

    @JsonProperty("time_period")
    private Object timePeriod;
}