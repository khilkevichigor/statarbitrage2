package com.example.statarbitrage.bot;

public enum BotMenu {
    FIND("/find"),
    START_TEST_TRADE("/start_test_trade"),
    START_SIMULATION("/start_simulation"),
    START_TEST_TRADE_L_A_S_B("/start_test_trade_l_a_s_b"),
    START_TEST_TRADE_L_B_S_A("/start_test_trade_l_b_s_a"),
    STOP_TEST_TRADE("/stop_test_trade"),
    GET_SETTINGS("/get_settings"),
    RESET_SETTINGS("/reset_settings"),
    DELETE_FILES("/delete_files");

    BotMenu(String name) {
        this.name = name;
    }

    private final String name;

    public String getName() {
        return name;
    }
}
