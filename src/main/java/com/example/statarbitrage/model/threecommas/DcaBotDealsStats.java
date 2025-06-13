package com.example.statarbitrage.model.threecommas;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DcaBotDealsStats {
    private Integer active;
    private Integer completed;

    @JsonProperty("panic_sold")
    private Integer panicSold;

    @JsonProperty("from_currency_is_dollars")
    private Boolean fromCurrencyIsDollars;

    @JsonProperty("completed_deals_usd_profit")
    private String completedDealsUsdProfit;

    @JsonProperty("completed_deals_btc_profit")
    private String completedDealsBtcProfit;

    @JsonProperty("funds_locked_in_active_deals")
    private String fundsLockedInActiveDeals;

    @JsonProperty("btc_funds_locked_in_active_deals")
    private String btcFundsLockedInActiveDeals;

    @JsonProperty("active_deals_usd_profit")
    private String activeDealsUsdProfit;

    @JsonProperty("active_deals_btc_profit")
    private String activeDealsBtcProfit;
}
