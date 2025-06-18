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
    String TEST_TRADES_CSV_FILE_HEADER = "Long Ticker,Short Ticker,Profit %,ProfitMin %,ProfitMin Time min,ProfitMax %,ProfitMax Time min,Long %,LongMin %,LongMax %,Short %,ShortMin %,ShortMax %,Z,ZMin,ZMax,Corr,CorrMin,CorrMax,Timestamp";
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

}
