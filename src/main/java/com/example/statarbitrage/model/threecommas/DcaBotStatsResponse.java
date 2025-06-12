package com.example.statarbitrage.model.threecommas;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DcaBotStatsResponse {
    @JsonProperty("completed_deals_count")
    private int completedDealsCount;

    @JsonProperty("total_usd_profit")
    private double totalUsdProfit;

    @JsonProperty("total_btc_profit")
    private double totalBtcProfit;

    // Геттеры и сеттеры

    public int getCompletedDealsCount() {
        return completedDealsCount;
    }

    public void setCompletedDealsCount(int completedDealsCount) {
        this.completedDealsCount = completedDealsCount;
    }

    public double getTotalUsdProfit() {
        return totalUsdProfit;
    }

    public void setTotalUsdProfit(double totalUsdProfit) {
        this.totalUsdProfit = totalUsdProfit;
    }

    public double getTotalBtcProfit() {
        return totalBtcProfit;
    }

    public void setTotalBtcProfit(double totalBtcProfit) {
        this.totalBtcProfit = totalBtcProfit;
    }
}
