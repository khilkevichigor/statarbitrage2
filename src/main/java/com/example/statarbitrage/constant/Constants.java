package com.example.statarbitrage.constant;

public interface Constants {
    String SET_SETTINGS = "/set_settings";
    String SET_SETTINGS_SHORT = "/ss";

    String Z_SCORE_FILE_NAME = "z_score.json";
    String PAIR_DATA_FILE_NAME = "pair_data.json";
    String CANDLES_FILE_NAME = "candles.json";
    String SETTINGS_FILE_NAME = "settings.json";
    String CHARTS_DIR = "charts";

    String TEST_TRADES_CSV_FILE = "logs/test_trade.csv";
    String TEST_TRADES_CSV_FILE_HEADER =
            "long_ticker,short_ticker," +
                    "current_profit_percent,min_profit_percent,min_profit_minutes,max_profit_percent,max_profit_minutes," +
                    "current_long_percent,min_long_percent,max_long_percent," +
                    "current_short_percent,min_short_percent,max_short_percent," +
                    "current_z,min_z,max_z," +
                    "current_corr,min_corr,max_corr," +
                    "exit_reason," +
                    "date";
    int TEST_TRADES_CSV_EXISTING_ROW_VALIDITY_HOURS = 8;

    String LONG_DCA_BOT_NAME = "stat_long";
    String SHORT_DCA_BOT_NAME = "stat_short";

    String FIND_COMMAND = "/find";
    String START_TEST_TRADE_COMMAND = "/start_test_trade";
    String STOP_TEST_TRADE_COMMAND = "/stop_test_trade";
    String GET_SETTINGS_COMMAND = "/get_settings";
    String RESET_SETTINGS_COMMAND = "/reset_settings";
    String FIND_AND_TEST_TRADE_COMMAND = "/find_and_test_trade";
    String START_REAL_TRADE_COMMAND = "/start_real_trade";
    String STOP_REAL_TRADE_COMMAND = "/stop_real_trade";
    String TEST_3COMMAS_API_COMMAND = "/test_3commas_api";
    String DELETE_FILES_COMMAND = "/delete_files";
    String START_SIMULATION_COMMAND = "/start_simulation";
    String GET_CSV_COMMAND = "/get_csv";


    String EXIT_REASON_BY_TAKE = "exit_by_take";
    String EXIT_REASON_BY_STOP = "exit_by_stop";
    String EXIT_REASON_BY_Z = "exit_by_z";
    String EXIT_REASON_BY_TIME = "exit_by_time";

}
