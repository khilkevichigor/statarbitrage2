package com.example.statarbitrage.bot;

import static com.example.statarbitrage.constant.Constants.*;

public enum BotMenu {
    FIND(FIND_COMMAND),
    FIND_AND_START_TEST_TRADE(FIND_AND_TEST_TRADE_COMMAND),

    START_TEST_TRADE(START_TEST_TRADE_COMMAND),
    STOP_TEST_TRADE(STOP_TEST_TRADE_COMMAND),

    START_SIMULATION(START_SIMULATION_COMMAND),

    START_REAL_TRADE(START_REAL_TRADE_COMMAND),
    STOP_REAL_TRADE(STOP_REAL_TRADE_COMMAND),

    TEST_3COMMAS_API(TEST_3COMMAS_API_COMMAND),

    GET_SETTINGS(GET_SETTINGS_COMMAND),
    RESET_SETTINGS(RESET_SETTINGS_COMMAND),

    GET_CSV(GET_CSV_COMMAND),
    DELETE_FILES(DELETE_FILES_COMMAND);

    BotMenu(String name) {
        this.name = name;
    }

    private final String name;

    public String getName() {
        return name;
    }
}
