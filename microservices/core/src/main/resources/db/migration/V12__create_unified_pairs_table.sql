-- Создание унифицированной таблицы pairs для замены StablePair, CointPair, TradingPair
CREATE TABLE pairs (
    -- Основные поля
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    version BIGINT DEFAULT 0,
    
    -- Тип и статус
    type VARCHAR(20) NOT NULL DEFAULT 'STABLE' CHECK (type IN ('STABLE', 'COINTEGRATED', 'TRADING')),
    status VARCHAR(20) DEFAULT 'SELECTED' CHECK (status IN ('SELECTED', 'IN_PROCESS', 'SUCCESS', 'FAILED', 'OBSERVED')),
    error_description TEXT,
    
    -- Базовая информация о паре  
    ticker_a VARCHAR(20) NOT NULL,
    ticker_b VARCHAR(20) NOT NULL,
    pair_name VARCHAR(50),
    
    -- Поля из StablePair
    total_score INTEGER,
    stability_rating VARCHAR(20) CHECK (stability_rating IN ('EXCELLENT', 'GOOD', 'MARGINAL', 'POOR', 'REJECTED', 'FAILED')),
    is_tradeable BOOLEAN,
    data_points INTEGER,
    candle_count INTEGER,
    analysis_time_seconds DECIMAL(10,2),
    timeframe VARCHAR(10),
    period VARCHAR(20),
    search_date TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    is_in_monitoring BOOLEAN NOT NULL DEFAULT FALSE,
    search_settings TEXT, -- JSON
    analysis_results TEXT, -- JSON
    
    -- JSON поля для исторических данных (для типов COINTEGRATED и TRADING)
    long_ticker_candles_json TEXT,
    short_ticker_candles_json TEXT,
    z_score_history_json TEXT,
    profit_history_json TEXT,
    pixel_spread_history_json TEXT,
    
    -- Торговые цены (nullable для STABLE типа)
    long_ticker_entry_price DECIMAL(18,8),
    long_ticker_current_price DECIMAL(18,8),
    short_ticker_entry_price DECIMAL(18,8),
    short_ticker_current_price DECIMAL(18,8),
    
    -- Статистические данные  
    mean_entry DECIMAL(18,8),
    mean_current DECIMAL(18,8),
    spread_entry DECIMAL(18,8),
    spread_current DECIMAL(18,8),
    z_score_entry DECIMAL(10,4),
    z_score_current DECIMAL(10,4),
    p_value_entry DECIMAL(10,8),
    p_value_current DECIMAL(10,8),
    adf_pvalue_entry DECIMAL(10,8),
    adf_pvalue_current DECIMAL(10,8),
    correlation_entry DECIMAL(10,8),
    correlation_current DECIMAL(10,8),
    alpha_entry DECIMAL(18,8),
    alpha_current DECIMAL(18,8),
    beta_entry DECIMAL(18,8),
    beta_current DECIMAL(18,8),
    std_entry DECIMAL(18,8),
    std_current DECIMAL(18,8),
    
    -- Временные метки
    timestamp BIGINT,
    entry_time TIMESTAMP WITH TIME ZONE,
    updated_time TIMESTAMP WITH TIME ZONE
);

-- Создание индексов для производительности
CREATE INDEX idx_pair_uuid ON pairs(uuid);
CREATE INDEX idx_pair_type ON pairs(type);
CREATE INDEX idx_pair_tickers ON pairs(ticker_a, ticker_b);
CREATE INDEX idx_pair_monitoring ON pairs(is_in_monitoring);
CREATE INDEX idx_pair_search_date ON pairs(search_date);
CREATE INDEX idx_pair_status ON pairs(status);
CREATE INDEX idx_pair_type_status ON pairs(type, status);
CREATE INDEX idx_pair_tradeable ON pairs(is_tradeable);
CREATE INDEX idx_pair_stability_rating ON pairs(stability_rating);

-- Комментарии к таблице
COMMENT ON TABLE pairs IS 'Унифицированная таблица торговых пар, объединяющая функциональность StablePair, CointPair и TradingPair';
COMMENT ON COLUMN pairs.type IS 'Тип пары: STABLE (найденная), COINTEGRATED (анализированная), TRADING (торгуемая)';
COMMENT ON COLUMN pairs.ticker_a IS 'Первый тикер пары (A в StablePair, Long в TradingPair)';
COMMENT ON COLUMN pairs.ticker_b IS 'Второй тикер пары (B в StablePair, Short в TradingPair)';
COMMENT ON COLUMN pairs.total_score IS 'Общий балл стабильности (StablePair)';
COMMENT ON COLUMN pairs.stability_rating IS 'Рейтинг стабильности: EXCELLENT, GOOD, MARGINAL, POOR, REJECTED, FAILED';
COMMENT ON COLUMN pairs.is_tradeable IS 'Подходит ли пара для торговли';
COMMENT ON COLUMN pairs.data_points IS 'Количество точек данных для анализа';
COMMENT ON COLUMN pairs.analysis_time_seconds IS 'Время анализа в секундах';
COMMENT ON COLUMN pairs.search_settings IS 'Настройки поиска в JSON формате';
COMMENT ON COLUMN pairs.analysis_results IS 'Результаты анализа в JSON формате';
COMMENT ON COLUMN pairs.is_in_monitoring IS 'Находится ли пара в мониторинге';