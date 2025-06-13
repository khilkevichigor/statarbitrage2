package com.example.statarbitrage.model.threecommas;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@ToString
public class DcaBot {
    private long id;
    @JsonProperty("account_id")
    private long accountId;
    @JsonProperty("is_enabled")
    private boolean isEnabled;
    @JsonProperty("max_safety_orders")
    private int maxSafetyOrders;
    @JsonProperty("active_safety_orders_count")
    private int activeSafetyOrdersCount;
    private List<String> pairs;
    @JsonProperty("strategy_list")
    private List<StrategyItem> strategyList;
    @JsonProperty("close_strategy_list")
    private List<StrategyItem> closeStrategyList;
    @JsonProperty("safety_strategy_list")
    private List<StrategyItem> safetyStrategyList;
    @JsonProperty("max_active_deals")
    private int maxActiveDeals;
    @JsonProperty("active_deals_count")
    private int activeDealsCount;
    @JsonProperty("deletable?")
    private boolean deletable;
    @JsonProperty("created_at")
    private String createdAt;
    @JsonProperty("updated_at")
    private String updatedAt;
    @JsonProperty("trailing_enabled")
    private Boolean trailingEnabled;
    @JsonProperty("tsl_enabled")
    private boolean tslEnabled;
    @JsonProperty("deal_start_delay_seconds")
    private Integer dealStartDelaySeconds;
    @JsonProperty("stop_loss_timeout_enabled")
    private boolean stopLossTimeoutEnabled;
    @JsonProperty("stop_loss_timeout_in_seconds")
    private int stopLossTimeoutInSeconds;
    @JsonProperty("disable_after_deals_count")
    private Integer disableAfterDealsCount;
    @JsonProperty("deals_counter")
    private Integer dealsCounter;
    @JsonProperty("allowed_deals_on_same_pair")
    private Integer allowedDealsOnSamePair;
    @JsonProperty("easy_form_supported")
    private boolean easyFormSupported;
    @JsonProperty("close_deals_timeout")
    private Integer closeDealsTimeout;
    @JsonProperty("url_secret")
    private String urlSecret;
    @JsonProperty("take_profit_steps")
    private List<Object> takeProfitSteps;
    private String name;
    @JsonProperty("take_profit")
    private String takeProfit;
    @JsonProperty("min_profit_percentage")
    private Double minProfitPercentage;
    @JsonProperty("base_order_volume")
    private String baseOrderVolume;
    @JsonProperty("safety_order_volume")
    private String safetyOrderVolume;
    @JsonProperty("safety_order_step_percentage")
    private String safetyOrderStepPercentage;
    @JsonProperty("safety_order_calculation_mode")
    private String safetyOrderCalculationMode;
    @JsonProperty("take_profit_type")
    private String takeProfitType;
    @JsonProperty("min_profit_type")
    private String minProfitType;
    private String type;
    @JsonProperty("martingale_volume_coefficient")
    private String martingaleVolumeCoefficient;
    @JsonProperty("martingale_step_coefficient")
    private String martingaleStepCoefficient;
    @JsonProperty("stop_loss_percentage")
    private String stopLossPercentage;
    private String cooldown;
    @JsonProperty("btc_price_limit")
    private String btcPriceLimit;
    private String strategy;
    @JsonProperty("min_volume_btc_24h")
    private String minVolumeBtc24h;
    @JsonProperty("profit_currency")
    private String profitCurrency;
    @JsonProperty("min_price")
    private Double minPrice;
    @JsonProperty("max_price")
    private Double maxPrice;
    @JsonProperty("stop_loss_type")
    private String stopLossType;
    @JsonProperty("safety_order_volume_type")
    private String safetyOrderVolumeType;
    @JsonProperty("base_order_volume_type")
    private String baseOrderVolumeType;
    @JsonProperty("account_name")
    private String accountName;
    @JsonProperty("trailing_deviation")
    private String trailingDeviation;
    @JsonProperty("finished_deals_profit_usd")
    private String finishedDealsProfitUsd;
    @JsonProperty("finished_deals_count")
    private String finishedDealsCount;
    @JsonProperty("leverage_type")
    private String leverageType;
    @JsonProperty("leverage_custom_value")
    private Double leverageCustomValue;
    @JsonProperty("start_order_type")
    private String startOrderType;
    @JsonProperty("active_deals_usd_profit")
    private String activeDealsUsdProfit;
    @JsonProperty("reinvesting_percentage")
    private String reinvestingPercentage;
    @JsonProperty("risk_reduction_percentage")
    private String riskReductionPercentage;
    @JsonProperty("reinvested_volume_usd")
    private Double reinvestedVolumeUsd;
    @JsonProperty("min_price_percentage")
    private Double minPricePercentage;
    @JsonProperty("max_price_percentage")
    private Double maxPricePercentage;
    @JsonProperty("active_deals")
    private List<Object> activeDeals;
}
