package com.example.statarbitrage.bot;

public enum BotMenu {
    SCAN_BTC("/scan_btc"),
    SCAN_ALL("/scan_all"),
    START_AUTOSCAN("/start_autoscan"),
    STOP_AUTOSCAN("/stop_autoscan"),
    GET_SETTINGS("/get_settings"),
    RESET_SETTINGS("/reset_settings");

    BotMenu(String name) {
        this.name = name;
    }

    private final String name;

    public String getName() {
        return name;
    }
}
