package com.example.statarbitrage.model.threecommas;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ProfitData {
    @JsonProperty("s_date")
    private String sDate;

    @JsonProperty("unix_timestamp")
    private long unixTimestamp;

    private Profit profit;
}