package com.example.statarbitrage.common.constant;

public interface Constants {
    String PAIR_DATA_FILE_NAME = "pair_data.json";
    String CANDLES_FILE_NAME = "candles.json";
    String SETTINGS_FILE_NAME = "settings.json";
    String CHARTS_DIR = "charts";

    String TEST_TRADES_CSV_FILE = "logs/test_trade.csv";
    String TEST_TRADES_CSV_FILE_HEADER =
            "long_ticker,short_ticker," +
                    "latest_profit_percent,min_profit_percent,min_profit_minutes,max_profit_percent,max_profit_minutes," +
                    "latest_long_percent,min_long_percent,max_long_percent," +
                    "latest_short_percent,min_short_percent,max_short_percent," +
                    "latest_z,min_z,max_z," +
                    "latest_correlation,min_correlation,max_correlation," +
                    "settings_exit_stop," +
                    "settings_exit_take," +
                    "settings_exit_z_min," +
                    "settings_exit_z_max," +
                    "settings_exit_time_hours," +
                    "exit_reason," +
                    "entry_time," +
                    "timestamp";

    String GET_CSV_COMMAND = "/get_csv";
    String GET_STATISTIC_COMMAND = "/get_statistic";

    String EXIT_REASON_BY_TAKE = "EXIT_REASON_BY_TAKE";
    String EXIT_REASON_BY_STOP = "EXIT_REASON_BY_STOP";
    String EXIT_REASON_BY_Z_MIN = "EXIT_REASON_BY_Z_MIN";
    String EXIT_REASON_BY_Z_MAX = "EXIT_REASON_BY_Z_MAX";
    String EXIT_REASON_BY_TIME = "EXIT_REASON_BY_TIME";
    String EXIT_REASON_MANUALLY = "EXIT_REASON_MANUALLY";
}
