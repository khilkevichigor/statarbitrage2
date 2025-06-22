package com.example.statarbitrage.vaadin.services;

public enum TradeStatus {
    SELECTED("Отобрано"),
    TRADING("Торгуется"),
    BLACKLISTED("Черный список"),
    CLOSED("Закрыто");

    private final String displayName;

    TradeStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}