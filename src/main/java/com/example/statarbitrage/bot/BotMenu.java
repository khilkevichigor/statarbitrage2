package com.example.statarbitrage.bot;

public enum BotMenu {
    FIND("/find"),
    START_TEST_TRADE("/start_test_trade"),
    STOP_TEST_TRADE("/stop_test_trade"),
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
