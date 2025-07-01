package com.example.statarbitrage.services;

import lombok.Getter;

@Getter
public enum TradeStatus {
    SELECTED("Отобрано"),
    TRADING("Торгуется"),
    CLOSED("Закрыто");

    private final String displayName;

    TradeStatus(String displayName) {
        this.displayName = displayName;
    }

}