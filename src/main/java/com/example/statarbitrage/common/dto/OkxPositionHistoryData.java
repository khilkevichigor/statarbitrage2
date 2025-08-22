package com.example.statarbitrage.common.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OkxPositionHistoryData {
    
    @SerializedName("instType")
    private String instrumentType;
    
    @SerializedName("instId")
    private String instrumentId;
    
    @SerializedName("posId")
    private String positionId;
    
    @SerializedName("posType")
    private String positionType; // 1: long, 2: short, 3: net
    
    @SerializedName("openSize")
    private BigDecimal openSize;
    
    @SerializedName("closeSize") 
    private BigDecimal closeSize;
    
    @SerializedName("avgOpenPrice")
    private BigDecimal averageOpenPrice;
    
    @SerializedName("avgClosePrice")
    private BigDecimal averageClosePrice;
    
    @SerializedName("realizedPnl")
    private BigDecimal realizedPnl;
    
    @SerializedName("pnl")
    private BigDecimal pnl;
    
    @SerializedName("pnlRatio")
    private BigDecimal pnlRatio;
    
    @SerializedName("openTime")
    private String openTime;
    
    @SerializedName("closeTime")
    private String closeTime;
    
    @SerializedName("ccy")
    private String currency;
    
    @SerializedName("lever")
    private BigDecimal leverage;
    
    @SerializedName("margin")
    private BigDecimal margin;
    
    @SerializedName("fee")
    private BigDecimal fee;
    
    @SerializedName("fundingFee")
    private BigDecimal fundingFee;
}