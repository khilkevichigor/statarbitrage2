-- Создание схемы базы данных для StatArbitrage микросервисов
-- Создано на основе анализа @Entity классов в shared/models

-- Создание типов для enum значений
-- CREATE TYPE trade_status AS ENUM ('SELECTED', 'TRADING', 'OBSERVED', 'CLOSED', 'ERROR');
-- CREATE TYPE position_type AS ENUM ('LONG', 'SHORT');
-- CREATE TYPE position_status AS ENUM ('PENDING', 'OPEN', 'CLOSING', 'CLOSED', 'FAILED');

-- Таблица настроек приложения
CREATE TABLE IF NOT EXISTS settings
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    timeframe
    VARCHAR
(
    255
),
    candle_limit DOUBLE PRECISION,
    min_z DOUBLE PRECISION,
    min_window_size DOUBLE PRECISION,
    max_p_value DOUBLE PRECISION,
    max_adf_value DOUBLE PRECISION,
    min_r_squared DOUBLE PRECISION,
    min_correlation DOUBLE PRECISION,
    min_volume DOUBLE PRECISION,
    check_interval DOUBLE PRECISION,
    max_long_margin_size DOUBLE PRECISION,
    max_short_margin_size DOUBLE PRECISION,
    leverage DOUBLE PRECISION,
    exit_take DOUBLE PRECISION,
    exit_stop DOUBLE PRECISION,
    exit_z_min DOUBLE PRECISION,
    exit_z_max DOUBLE PRECISION,
    exit_z_max_percent DOUBLE PRECISION,
    exit_time_minutes DOUBLE PRECISION,
    exit_break_even_percent DOUBLE PRECISION,
    exit_negative_z_min_profit_percent DOUBLE PRECISION,
    use_pairs DOUBLE PRECISION,
    auto_trading_enabled BOOLEAN DEFAULT FALSE,
    use_min_z_filter BOOLEAN DEFAULT TRUE,
    use_min_r_squared_filter BOOLEAN DEFAULT TRUE,
    use_max_p_value_filter BOOLEAN DEFAULT TRUE,
    use_max_adf_value_filter BOOLEAN DEFAULT TRUE,
    use_min_correlation_filter BOOLEAN DEFAULT TRUE,
    use_min_volume_filter BOOLEAN DEFAULT TRUE,
    use_exit_take BOOLEAN DEFAULT TRUE,
    use_exit_stop BOOLEAN DEFAULT TRUE,
    use_exit_z_min BOOLEAN DEFAULT TRUE,
    use_exit_z_max BOOLEAN DEFAULT TRUE,
    use_exit_z_max_percent BOOLEAN DEFAULT TRUE,
    use_exit_time_minutes BOOLEAN DEFAULT TRUE,
    use_exit_break_even_percent BOOLEAN DEFAULT TRUE,
    use_exit_negative_z_min_profit_percent BOOLEAN DEFAULT TRUE,
    use_cointegration_stability_filter BOOLEAN DEFAULT TRUE,
    minimum_lot_blacklist VARCHAR
(
    1000
) DEFAULT 'ETH-USDT-SWAP,BTC-USDT-SWAP',
    observed_pairs VARCHAR
(
    1000
) DEFAULT '',
    use_z_score_scoring BOOLEAN DEFAULT TRUE,
    use_pixel_spread_scoring BOOLEAN DEFAULT TRUE,
    use_cointegration_scoring BOOLEAN DEFAULT TRUE,
    use_model_quality_scoring BOOLEAN DEFAULT TRUE,
    use_statistics_scoring BOOLEAN DEFAULT TRUE,
    use_bonus_scoring BOOLEAN DEFAULT TRUE,
    z_score_scoring_weight DOUBLE PRECISION DEFAULT 40.0,
    pixel_spread_scoring_weight DOUBLE PRECISION DEFAULT 25.0,
    cointegration_scoring_weight DOUBLE PRECISION DEFAULT 25.0,
    model_quality_scoring_weight DOUBLE PRECISION DEFAULT 20.0,
    statistics_scoring_weight DOUBLE PRECISION DEFAULT 10.0,
    bonus_scoring_weight DOUBLE PRECISION DEFAULT 5.0,
    auto_averaging_enabled BOOLEAN DEFAULT FALSE,
    averaging_drawdown_threshold DOUBLE PRECISION DEFAULT 5.0,
    averaging_volume_multiplier DOUBLE PRECISION DEFAULT 1.5
    );

-- Таблица настроек графиков
CREATE TABLE IF NOT EXISTS chart_settings
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    chart_type
    VARCHAR
(
    255
) UNIQUE NOT NULL,
    show_z_score BOOLEAN DEFAULT TRUE,
    show_combined_price BOOLEAN DEFAULT TRUE,
    show_pixel_spread BOOLEAN DEFAULT FALSE,
    show_ema BOOLEAN DEFAULT FALSE,
    show_stoch_rsi BOOLEAN DEFAULT FALSE,
    show_profit BOOLEAN DEFAULT FALSE,
    show_entry_point BOOLEAN DEFAULT TRUE
    );

-- Таблица торговых пар
CREATE TABLE IF NOT EXISTS trading_pair
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    uuid
    UUID
    NOT
    NULL,
    version
    BIGINT,
    status
    TEXT
    DEFAULT
    'SELECTED',
    error_description
    TEXT,
    long_ticker_candles_json
    TEXT,
    short_ticker_candles_json
    TEXT,
    z_score_history_json
    TEXT,
    profit_history_json
    TEXT,
    pixel_spread_history_json
    TEXT,
    long_ticker
    VARCHAR
(
    255
),
    short_ticker VARCHAR
(
    255
),
    pair_name VARCHAR
(
    255
),
    long_ticker_entry_price DOUBLE PRECISION,
    long_ticker_current_price DOUBLE PRECISION,
    short_ticker_entry_price DOUBLE PRECISION,
    short_ticker_current_price DOUBLE PRECISION,
    mean_entry DOUBLE PRECISION,
    mean_current DOUBLE PRECISION,
    spread_entry DOUBLE PRECISION,
    spread_current DOUBLE PRECISION,
    z_score_entry DOUBLE PRECISION,
    z_score_current DOUBLE PRECISION,
    p_value_entry DOUBLE PRECISION,
    p_value_current DOUBLE PRECISION,
    adf_pvalue_entry DOUBLE PRECISION,
    adf_pvalue_current DOUBLE PRECISION,
    correlation_entry DOUBLE PRECISION,
    correlation_current DOUBLE PRECISION,
    alpha_entry DOUBLE PRECISION,
    alpha_current DOUBLE PRECISION,
    beta_entry DOUBLE PRECISION,
    beta_current DOUBLE PRECISION,
    std_entry DOUBLE PRECISION,
    std_current DOUBLE PRECISION,
    z_score_changes DECIMAL
(
    19,
    8
),
    long_usdt_changes DECIMAL
(
    19,
    8
),
    long_percent_changes DECIMAL
(
    19,
    8
),
    short_usdt_changes DECIMAL
(
    19,
    8
),
    short_percent_changes DECIMAL
(
    19,
    8
),
    portfolio_before_trade_usdt DECIMAL
(
    19,
    8
),
    profit_usdt_changes DECIMAL
(
    19,
    8
),
    portfolio_after_trade_usdt DECIMAL
(
    19,
    8
),
    profit_percent_changes DECIMAL
(
    19,
    8
),
    minutes_to_min_profit_percent BIGINT,
    minutes_to_max_profit_percent BIGINT,
    min_profit_percent_changes DECIMAL
(
    19,
    8
),
    max_profit_percent_changes DECIMAL
(
    19,
    8
),
    formatted_time_to_min_profit VARCHAR
(
    255
),
    formatted_time_to_max_profit VARCHAR
(
    255
),
    formatted_profit_long VARCHAR
(
    255
),
    formatted_profit_short VARCHAR
(
    255
),
    formatted_profit_common VARCHAR
(
    255
),
    timestamp BIGINT,
    entry_time BIGINT,
    updated_time BIGINT,
    max_z DECIMAL
(
    19,
    8
),
    min_z DECIMAL
(
    19,
    8
),
    max_long DECIMAL
(
    19,
    8
),
    min_long DECIMAL
(
    19,
    8
),
    max_short DECIMAL
(
    19,
    8
),
    min_short DECIMAL
(
    19,
    8
),
    max_corr DECIMAL
(
    19,
    8
),
    min_corr DECIMAL
(
    19,
    8
),
    exit_reason VARCHAR
(
    255
),
    close_at_breakeven BOOLEAN,
    settings_timeframe VARCHAR
(
    255
),
    settings_candle_limit DOUBLE PRECISION,
    settings_min_z DOUBLE PRECISION,
    settings_min_window_size DOUBLE PRECISION,
    settings_min_p_value DOUBLE PRECISION,
    settings_max_adf_value DOUBLE PRECISION,
    settings_min_r_squared DOUBLE PRECISION,
    settings_min_correlation DOUBLE PRECISION,
    settings_min_volume DOUBLE PRECISION,
    settings_check_interval DOUBLE PRECISION,
    settings_max_long_margin_size DOUBLE PRECISION,
    settings_max_short_margin_size DOUBLE PRECISION,
    settings_leverage DOUBLE PRECISION,
    settings_exit_take DOUBLE PRECISION,
    settings_exit_stop DOUBLE PRECISION,
    settings_exit_z_min DOUBLE PRECISION,
    settings_exit_z_max DOUBLE PRECISION,
    settings_exit_z_max_percent DOUBLE PRECISION,
    settings_exit_time_minutes DOUBLE PRECISION,
    settings_exit_breakeven_percent DOUBLE PRECISION,
    settings_use_pairs DOUBLE PRECISION,
    settings_auto_trading_enabled BOOLEAN,
    settings_use_min_z_filter BOOLEAN,
    settings_use_min_r_squared_filter BOOLEAN,
    settings_use_min_p_value_filter BOOLEAN,
    settings_use_max_adf_value_filter BOOLEAN,
    settings_use_min_correlation_filter BOOLEAN,
    settings_use_min_volume_filter BOOLEAN,
    settings_use_exit_take BOOLEAN,
    settings_use_exit_stop BOOLEAN,
    settings_use_exit_z_min BOOLEAN,
    settings_use_exit_z_max BOOLEAN,
    settings_use_exit_z_max_percent BOOLEAN,
    settings_use_exit_time_hours BOOLEAN,
    settings_use_exit_break_even_percent BOOLEAN,
    settings_minimum_lot_blacklist VARCHAR
(
    255
),
    use_z_score_scoring BOOLEAN,
    z_score_scoring_weight DOUBLE PRECISION,
    use_pixel_spread_scoring BOOLEAN,
    pixel_spread_scoring_weight DOUBLE PRECISION,
    use_cointegration_scoring BOOLEAN,
    cointegration_scoring_weight DOUBLE PRECISION,
    use_model_quality_scoring BOOLEAN,
    model_quality_scoring_weight DOUBLE PRECISION,
    use_statistics_scoring BOOLEAN,
    statistics_scoring_weight DOUBLE PRECISION,
    use_bonus_scoring BOOLEAN,
    bonus_scoring_weight DOUBLE PRECISION,
    settings_use_exit_negative_z_min_profit_percent BOOLEAN,
    settings_exit_negative_z_min_profit_percent DOUBLE PRECISION,
    settings_auto_averaging_enabled BOOLEAN,
    settings_averaging_drawdown_threshold DOUBLE PRECISION,
    settings_averaging_volume_multiplier DOUBLE PRECISION,
    averaging_count INTEGER DEFAULT 0,
    last_averaging_timestamp BIGINT
    );

-- Таблица коинтеграционных пар (аналогичная структура с trading_pair)
CREATE TABLE IF NOT EXISTS coint_pair
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    uuid
    UUID
    NOT
    NULL,
    version
    BIGINT,
    status
    TEXT
    DEFAULT
    'SELECTED',
    error_description
    TEXT,
    long_ticker_candles_json
    TEXT,
    short_ticker_candles_json
    TEXT,
    z_score_history_json
    TEXT,
    profit_history_json
    TEXT,
    pixel_spread_history_json
    TEXT,
    long_ticker
    VARCHAR
(
    255
),
    short_ticker VARCHAR
(
    255
),
    pair_name VARCHAR
(
    255
),
    long_ticker_entry_price DOUBLE PRECISION,
    long_ticker_current_price DOUBLE PRECISION,
    short_ticker_entry_price DOUBLE PRECISION,
    short_ticker_current_price DOUBLE PRECISION,
    mean_entry DOUBLE PRECISION,
    mean_current DOUBLE PRECISION,
    spread_entry DOUBLE PRECISION,
    spread_current DOUBLE PRECISION,
    z_score_entry DOUBLE PRECISION,
    z_score_current DOUBLE PRECISION,
    p_value_entry DOUBLE PRECISION,
    p_value_current DOUBLE PRECISION,
    adf_pvalue_entry DOUBLE PRECISION,
    adf_pvalue_current DOUBLE PRECISION,
    correlation_entry DOUBLE PRECISION,
    correlation_current DOUBLE PRECISION,
    alpha_entry DOUBLE PRECISION,
    alpha_current DOUBLE PRECISION,
    beta_entry DOUBLE PRECISION,
    beta_current DOUBLE PRECISION,
    std_entry DOUBLE PRECISION,
    std_current DOUBLE PRECISION,
    z_score_changes DECIMAL
(
    19,
    8
),
    long_usdt_changes DECIMAL
(
    19,
    8
),
    long_percent_changes DECIMAL
(
    19,
    8
),
    short_usdt_changes DECIMAL
(
    19,
    8
),
    short_percent_changes DECIMAL
(
    19,
    8
),
    portfolio_before_trade_usdt DECIMAL
(
    19,
    8
),
    profit_usdt_changes DECIMAL
(
    19,
    8
),
    portfolio_after_trade_usdt DECIMAL
(
    19,
    8
),
    profit_percent_changes DECIMAL
(
    19,
    8
),
    minutes_to_min_profit_percent BIGINT,
    minutes_to_max_profit_percent BIGINT,
    min_profit_percent_changes DECIMAL
(
    19,
    8
),
    max_profit_percent_changes DECIMAL
(
    19,
    8
),
    formatted_time_to_min_profit VARCHAR
(
    255
),
    formatted_time_to_max_profit VARCHAR
(
    255
),
    formatted_profit_long VARCHAR
(
    255
),
    formatted_profit_short VARCHAR
(
    255
),
    formatted_profit_common VARCHAR
(
    255
),
    timestamp BIGINT,
    entry_time BIGINT,
    updated_time BIGINT,
    max_z DECIMAL
(
    19,
    8
),
    min_z DECIMAL
(
    19,
    8
),
    max_long DECIMAL
(
    19,
    8
),
    min_long DECIMAL
(
    19,
    8
),
    max_short DECIMAL
(
    19,
    8
),
    min_short DECIMAL
(
    19,
    8
),
    max_corr DECIMAL
(
    19,
    8
),
    min_corr DECIMAL
(
    19,
    8
),
    exit_reason VARCHAR
(
    255
),
    close_at_breakeven BOOLEAN,
    settings_timeframe VARCHAR
(
    255
),
    settings_candle_limit DOUBLE PRECISION,
    settings_min_z DOUBLE PRECISION,
    settings_min_window_size DOUBLE PRECISION,
    settings_min_p_value DOUBLE PRECISION,
    settings_max_adf_value DOUBLE PRECISION,
    settings_min_r_squared DOUBLE PRECISION,
    settings_min_correlation DOUBLE PRECISION,
    settings_min_volume DOUBLE PRECISION,
    settings_check_interval DOUBLE PRECISION,
    settings_max_long_margin_size DOUBLE PRECISION,
    settings_max_short_margin_size DOUBLE PRECISION,
    settings_leverage DOUBLE PRECISION,
    settings_exit_take DOUBLE PRECISION,
    settings_exit_stop DOUBLE PRECISION,
    settings_exit_z_min DOUBLE PRECISION,
    settings_exit_z_max DOUBLE PRECISION,
    settings_exit_z_max_percent DOUBLE PRECISION,
    settings_exit_time_minutes DOUBLE PRECISION,
    settings_exit_breakeven_percent DOUBLE PRECISION,
    settings_use_pairs DOUBLE PRECISION,
    settings_auto_trading_enabled BOOLEAN,
    settings_use_min_z_filter BOOLEAN,
    settings_use_min_r_squared_filter BOOLEAN,
    settings_use_min_p_value_filter BOOLEAN,
    settings_use_max_adf_value_filter BOOLEAN,
    settings_use_min_correlation_filter BOOLEAN,
    settings_use_min_volume_filter BOOLEAN,
    settings_use_exit_take BOOLEAN,
    settings_use_exit_stop BOOLEAN,
    settings_use_exit_z_min BOOLEAN,
    settings_use_exit_z_max BOOLEAN,
    settings_use_exit_z_max_percent BOOLEAN,
    settings_use_exit_time_hours BOOLEAN,
    settings_use_exit_break_even_percent BOOLEAN,
    settings_minimum_lot_blacklist VARCHAR
(
    255
),
    use_z_score_scoring BOOLEAN,
    z_score_scoring_weight DOUBLE PRECISION,
    use_pixel_spread_scoring BOOLEAN,
    pixel_spread_scoring_weight DOUBLE PRECISION,
    use_cointegration_scoring BOOLEAN,
    cointegration_scoring_weight DOUBLE PRECISION,
    use_model_quality_scoring BOOLEAN,
    model_quality_scoring_weight DOUBLE PRECISION,
    use_statistics_scoring BOOLEAN,
    statistics_scoring_weight DOUBLE PRECISION,
    use_bonus_scoring BOOLEAN,
    bonus_scoring_weight DOUBLE PRECISION,
    settings_use_exit_negative_z_min_profit_percent BOOLEAN,
    settings_exit_negative_z_min_profit_percent DOUBLE PRECISION,
    settings_auto_averaging_enabled BOOLEAN,
    settings_averaging_drawdown_threshold DOUBLE PRECISION,
    settings_averaging_volume_multiplier DOUBLE PRECISION,
    averaging_count INTEGER DEFAULT 0,
    last_averaging_timestamp BIGINT
    );

-- Таблица позиций
CREATE TABLE IF NOT EXISTS positions
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    position_id
    VARCHAR
(
    255
),
    trading_pair_id BIGINT,
    symbol VARCHAR
(
    255
),
    type TEXT,
    size DECIMAL
(
    19,
    8
),
    entry_price DECIMAL
(
    19,
    8
),
    closing_price DECIMAL
(
    19,
    8
),
    current_price DECIMAL
(
    19,
    8
),
    leverage DECIMAL
(
    19,
    8
),
    allocated_amount DECIMAL
(
    19,
    8
),
    unrealized_pnl_usdt DECIMAL
(
    19,
    8
),
    unrealized_pnl_percent DECIMAL
(
    19,
    8
),
    realized_pnl_usdt DECIMAL
(
    19,
    8
),
    realized_pnl_percent DECIMAL
(
    19,
    8
),
    opening_fees DECIMAL
(
    19,
    8
),
    funding_fees DECIMAL
(
    19,
    8
),
    closing_fees DECIMAL
(
    19,
    8
),
    open_close_fees DECIMAL
(
    19,
    8
),
    open_close_funding_fees DECIMAL
(
    19,
    8
),
    status TEXT,
    open_time TIMESTAMP,
    last_updated TIMESTAMP,
    metadata TEXT,
    external_order_id VARCHAR
(
    255
)
    );

-- Таблица истории торговли
CREATE TABLE IF NOT EXISTS trade_history
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    long_ticker
    VARCHAR
(
    255
),
    short_ticker VARCHAR
(
    255
),
    pair_uuid VARCHAR
(
    255
),
    min_profit_minutes VARCHAR
(
    255
),
    max_profit_minutes VARCHAR
(
    255
),
    min_profit_percent DECIMAL
(
    19,
    8
),
    max_profit_percent DECIMAL
(
    19,
    8
),
    current_profit_usdt DECIMAL
(
    19,
    8
),
    current_profit_percent DECIMAL
(
    19,
    8
),
    min_long_percent DECIMAL
(
    19,
    8
),
    max_long_percent DECIMAL
(
    19,
    8
),
    current_long_percent DECIMAL
(
    19,
    8
),
    min_short_percent DECIMAL
(
    19,
    8
),
    max_short_percent DECIMAL
(
    19,
    8
),
    current_short_percent DECIMAL
(
    19,
    8
),
    min_z DECIMAL
(
    19,
    8
),
    max_z DECIMAL
(
    19,
    8
),
    current_z DOUBLE PRECISION,
    min_corr DECIMAL
(
    19,
    8
),
    max_corr DECIMAL
(
    19,
    8
),
    current_corr DOUBLE PRECISION,
    exit_take DOUBLE PRECISION,
    exit_stop DOUBLE PRECISION,
    exit_z_min DOUBLE PRECISION,
    exit_z_max DOUBLE PRECISION,
    exit_time_minutes DOUBLE PRECISION,
    exit_reason VARCHAR
(
    255
),
    entry_time BIGINT,
    timestamp BIGINT
    );

-- Создание индексов для улучшения производительности
CREATE UNIQUE INDEX idx_trading_pair_uuid ON trading_pair (uuid);
CREATE UNIQUE INDEX idx_coint_pair_uuid ON coint_pair (uuid);
CREATE INDEX idx_trading_pair_status ON trading_pair (status);
CREATE INDEX idx_coint_pair_status ON coint_pair (status);
CREATE INDEX idx_trading_pair_timestamp ON trading_pair (timestamp);
CREATE INDEX idx_coint_pair_timestamp ON coint_pair (timestamp);
CREATE INDEX idx_positions_status ON positions (status);
CREATE INDEX idx_positions_symbol ON positions (symbol);
CREATE INDEX idx_positions_trading_pair_id ON positions (trading_pair_id);
CREATE INDEX idx_trade_history_pair_uuid ON trade_history (pair_uuid);
CREATE INDEX idx_trade_history_timestamp ON trade_history (timestamp);

-- Комментарии к таблицам
COMMENT
ON TABLE settings IS 'Настройки приложения для статистического арбитража';
COMMENT
ON TABLE chart_settings IS 'Настройки отображения графиков';
COMMENT
ON TABLE trading_pair IS 'Торговые пары для статистического арбитража';
COMMENT
ON TABLE coint_pair IS 'Коинтеграционные пары для анализа';
COMMENT
ON TABLE positions IS 'Открытые и закрытые торговые позиции';
COMMENT
ON TABLE trade_history IS 'История торговых операций';

-- Добавление комментариев к некоторым ключевым полям
COMMENT
ON COLUMN trading_pair.uuid IS 'Уникальный идентификатор торговой пары';
COMMENT
ON COLUMN trading_pair.status IS 'Статус торговой пары: SELECTED, TRADING, OBSERVED, CLOSED, ERROR';
COMMENT
ON COLUMN trading_pair.z_score_current IS 'Текущее значение Z-Score для пары';
COMMENT
ON COLUMN trading_pair.correlation_current IS 'Текущая корреляция между активами';
COMMENT
ON COLUMN positions.position_id IS 'ID позиции от биржи OKX';
COMMENT
ON COLUMN positions.unrealized_pnl_usdt IS 'Нереализованная прибыль/убыток в USDT';
COMMENT
ON COLUMN positions.realized_pnl_usdt IS 'Реализованная прибыль/убыток в USDT';