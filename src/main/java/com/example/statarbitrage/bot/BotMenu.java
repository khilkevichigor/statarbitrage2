package com.example.statarbitrage.bot;

public enum BotMenu {
    FIND("/find"),
    START_TEST_TRADE("/start_test_trade"),
    FIND_AND_START_TEST_TRADE("/find_and_test_trade"),
    START_SIMULATION("/start_simulation"),
    STOP_TEST_TRADE("/stop_test_trade"),
    START_REAL_TRADE("/start_real_trade"),
    STOP_REAL_TRADE("/stop_real_trade"),
    TEST_3COMMAS_API("/test_3commas_api"),
    GET_SETTINGS("/get_settings"),
    RESET_SETTINGS("/reset_settings"),
    GET_CSV("/get_csv"),
    DELETE_FILES("/delete_files");

    BotMenu(String name) {
        this.name = name;
    }

    private final String name;

    public String getName() {
        return name;
    }
}
