package com.example.statarbitrage.model.threecommas;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TradePayload {
    @JsonProperty("account_id")
    private long accountId;

    private String pair;
    private Order order;
    private Units units;
    private Leverage leverage;

    @JsonProperty("is_enabled")
    private boolean isEnabled;

    private Flag conditional;
    private Flag trailing;
    private Flag timeout;
}