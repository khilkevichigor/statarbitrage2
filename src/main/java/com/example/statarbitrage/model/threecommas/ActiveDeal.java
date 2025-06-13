package com.example.statarbitrage.model.threecommas;

import lombok.Data;

import java.util.List;

@Data
public class ActiveDeal {
    private long id;
    private long botId;
    private String pair;
    private String status;
    private String strategy;
    private String takeProfit;
    private String baseOrderVolume;
    private String boughtAmount;
    private String soldAmount;
    private String boughtAveragePrice;
    private String finalProfit;
    private String actualProfit;
    private String actualUsdProfit;
    private String currentPrice;
    private List<StrategyItem> closeStrategyList;
}
