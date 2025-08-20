-- Fix timestamp columns to use BIGINT instead of INTEGER to match Java long type

-- Update timestamp-related columns to BIGINT
-- SQLite doesn't support ALTER COLUMN type changes, so we need to recreate the table

-- Create new pair_data table with correct column types
CREATE TABLE pair_data_new AS
SELECT *
FROM pair_data;

-- Drop old table
DROP TABLE pair_data;

-- Recreate pair_data table with correct BIGINT types for timestamp columns
CREATE TABLE pair_data
(
    id                                              BIGINT PRIMARY KEY,
    uuid                                            TEXT NOT NULL UNIQUE,
    version                                         INTEGER,
    status                                          TEXT,
    error_description                               TEXT,
    z_score_history_json                            TEXT,
    profit_history_json                             TEXT,
    pixel_spread_history_json                       TEXT,
    long_ticker                                     TEXT,
    short_ticker                                    TEXT,
    pair_name                                       TEXT,
    long_ticker_entry_price                         REAL,
    long_ticker_current_price                       REAL,
    short_ticker_entry_price                        REAL,
    short_ticker_current_price                      REAL,
    mean_entry                                      REAL,
    mean_current                                    REAL,
    spread_entry                                    REAL,
    spread_current                                  REAL,
    z_score_entry                                   REAL,
    z_score_current                                 REAL,
    p_value_entry                                   REAL,
    p_value_current                                 REAL,
    adf_pvalue_entry                                REAL,
    adf_pvalue_current                              REAL,
    correlation_entry                               REAL,
    correlation_current                             REAL,
    alpha_entry                                     REAL,
    alpha_current                                   REAL,
    beta_entry                                      REAL,
    beta_current                                    REAL,
    std_entry                                       REAL,
    std_current                                     REAL,
    long_qty_entry                                  DECIMAL,
    long_qty_current                                DECIMAL,
    short_qty_entry                                 DECIMAL,
    short_qty_current                               DECIMAL,
    long_margin_entry                               DECIMAL,
    long_margin_current                             DECIMAL,
    short_margin_entry                              DECIMAL,
    short_margin_current                            DECIMAL,
    profit_usdt_current                             DECIMAL,
    profit_percent_current                          DECIMAL,
    profit_percent_changes                          DECIMAL,
    profit_percent_entry                            DECIMAL,
    profit_percent_max                              DECIMAL,
    profit_percent_min                              DECIMAL,
    total_position_size_usdt_current                DECIMAL,
    total_position_size_usdt_entry                  DECIMAL,
    total_position_size_usdt_max                    DECIMAL,
    total_position_size_usdt_min                    DECIMAL,
    trading_amount_usdt                             DECIMAL,
    timestamp                                       BIGINT,
    entry_time                                      BIGINT,
    updated_time                                    BIGINT,
    candle_check_time                               INTEGER,
    settings_timeframe                              TEXT,
    settings_candle_limit                           REAL,
    settings_min_z                                  REAL,
    settings_min_window_size                        REAL,
    settings_max_p_value                            REAL,
    settings_max_adf_value                          REAL,
    settings_min_r_squared                          REAL,
    settings_min_correlation                        REAL,
    settings_min_volume                             REAL,
    settings_check_interval                         REAL,
    settings_max_long_margin_size                   REAL,
    settings_max_short_margin_size                  REAL,
    settings_leverage                               REAL,
    settings_exit_take                              REAL,
    settings_exit_stop                              REAL,
    settings_exit_z_min                             REAL,
    settings_exit_z_max                             REAL,
    settings_exit_z_max_percent                     REAL,
    settings_exit_time_minutes                      REAL,
    settings_exit_break_even_percent                REAL,
    settings_use_pairs                              REAL,
    settings_auto_trading_enabled                   BOOLEAN,
    settings_use_min_z_filter                       BOOLEAN,
    settings_use_min_r_squared_filter               BOOLEAN,
    settings_use_max_p_value_filter                 BOOLEAN,
    settings_use_max_adf_value_filter               BOOLEAN,
    settings_use_min_correlation_filter             BOOLEAN,
    settings_use_min_volume_filter                  BOOLEAN,
    settings_use_exit_take                          BOOLEAN,
    settings_use_exit_stop                          BOOLEAN,
    settings_use_exit_z_min                         BOOLEAN,
    settings_use_exit_z_max                         BOOLEAN,
    settings_use_exit_z_max_percent                 BOOLEAN,
    settings_use_exit_time_hours                    BOOLEAN,
    settings_use_exit_break_even_percent            BOOLEAN,
    settings_minimum_lot_blacklist                  TEXT,
    use_z_score_scoring                             BOOLEAN,
    z_score_scoring_weight                          REAL,
    use_pixel_spread_scoring                        BOOLEAN,
    pixel_spread_scoring_weight                     REAL,
    use_cointegration_scoring                       BOOLEAN,
    cointegration_scoring_weight                    REAL,
    use_model_quality_scoring                       BOOLEAN,
    model_quality_scoring_weight                    REAL,
    use_statistics_scoring                          BOOLEAN,
    statistics_scoring_weight                       REAL,
    use_bonus_scoring                               BOOLEAN,
    bonus_scoring_weight                            REAL,
    settings_use_exit_negative_z_min_profit_percent BOOLEAN,
    settings_exit_negative_z_min_profit_percent     REAL,
    -- Columns added in V4
    z_score_changes                                 DECIMAL,
    long_usdt_changes                               DECIMAL,
    long_percent_changes                            DECIMAL,
    short_usdt_changes                              DECIMAL,
    short_percent_changes                           DECIMAL,
    portfolio_before_trade_usdt                     DECIMAL,
    profit_usdt_changes                             DECIMAL,
    portfolio_after_trade_usdt                      DECIMAL,
    minutes_to_min_profit_percent                   INTEGER,
    minutes_to_max_profit_percent                   INTEGER,
    min_profit_percent_changes                      DECIMAL,
    max_profit_percent_changes                      DECIMAL,
    formatted_time_to_min_profit                    TEXT,
    formatted_time_to_max_profit                    TEXT,
    formatted_profit_long                           TEXT,
    formatted_profit_short                          TEXT,
    formatted_profit_common                         TEXT,
    max_z                                           DECIMAL,
    min_z                                           DECIMAL,
    max_long                                        DECIMAL,
    min_long                                        DECIMAL,
    max_short                                       DECIMAL,
    min_short                                       DECIMAL,
    max_corr                                        DECIMAL,
    min_corr                                        DECIMAL,
    exit_reason                                     TEXT,
    close_at_breakeven                              BOOLEAN DEFAULT FALSE
);

-- Copy data back from the temporary table
INSERT INTO pair_data
SELECT *
FROM pair_data_new;

-- Drop temporary table
DROP TABLE pair_data_new;

-- Recreate indexes
CREATE INDEX idx_pairdata_uuid ON pair_data (uuid);
CREATE INDEX idx_pairdata_status ON pair_data (status);
CREATE INDEX idx_pairdata_timestamp ON pair_data (timestamp);