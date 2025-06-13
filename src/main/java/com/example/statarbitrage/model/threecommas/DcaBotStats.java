package com.example.statarbitrage.model.threecommas;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DcaBotStats {
    @JsonProperty("completed_deals_count")
    private int completedDealsCount;

    @JsonProperty("total_usd_profit")
    private double totalUsdProfit;

    @JsonProperty("total_btc_profit")
    private double totalBtcProfit;
}
