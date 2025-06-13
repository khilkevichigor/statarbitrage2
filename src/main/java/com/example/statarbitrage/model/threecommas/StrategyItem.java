package com.example.statarbitrage.model.threecommas;

import lombok.Data;

import java.util.Map;

@Data
public class StrategyItem {
    private String strategy;
    private Map<String, String> options;
}
