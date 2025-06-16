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
    String TEST_TRADES_CSV_FILE_HEADER = "Long,Short,Profit %,LongCh %,ShortCh %,Z,Corr,MaxProfit %,TimeToMax min,MinProfit %,TimeToMin min,Timestamp";

    String LONG_DCA_BOT_NAME = "stat_long";
    String SHORT_DCA_BOT_NAME = "stat_short";

}
