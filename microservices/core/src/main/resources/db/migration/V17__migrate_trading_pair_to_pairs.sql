-- Миграция данных из trading_pair в унифицированную таблицу pairs
-- Переносим все данные из trading_pair как тип TRADING

INSERT INTO pairs (
    -- Основные поля
    uuid,
    type,
    status,
    error_description,
    
    -- Базовая информация о паре
    ticker_a,
    ticker_b, 
    pair_name,
    
    -- Временные метки
    timestamp,
    entry_time,
    updated_time,
    created_at,
    search_date,
    
    -- JSON поля
    long_ticker_candles_json,
    short_ticker_candles_json,
    z_score_history_json,
    profit_history_json,
    pixel_spread_history_json,
    
    -- Торговые цены
    long_ticker_entry_price,
    long_ticker_current_price,
    short_ticker_entry_price,
    short_ticker_current_price,
    
    -- Статистические данные
    mean_entry,
    mean_current,
    spread_entry,
    spread_current,
    z_score_entry,
    z_score_current,
    p_value_entry,
    p_value_current,
    adf_pvalue_entry,
    adf_pvalue_current,
    correlation_entry,
    correlation_current,
    alpha_entry,
    alpha_current,
    beta_entry,
    beta_current,
    std_entry,
    std_current,
    
    -- Торговые изменения
    z_score_changes,
    long_usdt_changes,
    long_percent_changes,
    short_usdt_changes,
    short_percent_changes,
    
    -- Портфель и прибыль
    portfolio_before_trade_usdt,
    profit_usdt_changes,
    portfolio_after_trade_usdt,
    profit_percent_changes,
    
    -- Время до экстремумов
    minutes_to_min_profit_percent,
    minutes_to_max_profit_percent,
    min_profit_percent_changes,
    max_profit_percent_changes,
    
    -- Форматированные поля
    formatted_time_to_min_profit,
    formatted_time_to_max_profit,
    formatted_profit_long,
    formatted_profit_short,
    formatted_profit_common,
    
    -- Экстремальные значения
    max_z,
    min_z,
    max_long,
    min_long,
    max_short,
    min_short,
    max_corr,
    min_corr,
    
    -- Информация о выходе
    exit_reason,
    close_at_breakeven,
    
    -- Торговые настройки
    settings_candle_limit,
    settings_min_z,
    
    -- Усреднение (если поля существуют)
    averaging_count,
    last_averaging_timestamp,
    
    -- Нормализованные цены (если поля существуют)
    normalized_long_prices_json,
    normalized_short_prices_json,
    intersections_count
)
SELECT 
    -- Основные поля
    COALESCE(uuid, gen_random_uuid()) as uuid,
    'TRADING' as type,
    COALESCE(status, 'TRADING') as status,
    error_description,
    
    -- Базовая информация о паре  
    long_ticker as ticker_a,
    short_ticker as ticker_b,
    pair_name,
    
    -- Временные метки - преобразуем long в LocalDateTime если нужно
    timestamp,
    CASE 
        WHEN entry_time IS NOT NULL AND entry_time > 0 
        THEN to_timestamp(entry_time / 1000.0) AT TIME ZONE 'UTC'
        ELSE NOW()
    END as entry_time,
    CASE 
        WHEN updated_time IS NOT NULL AND updated_time > 0 
        THEN to_timestamp(updated_time / 1000.0) AT TIME ZONE 'UTC' 
        ELSE NOW()
    END as updated_time,
    COALESCE(
        CASE 
            WHEN entry_time IS NOT NULL AND entry_time > 0 
            THEN to_timestamp(entry_time / 1000.0) AT TIME ZONE 'UTC'
            ELSE NULL
        END,
        NOW()
    ) as created_at,
    COALESCE(
        CASE 
            WHEN entry_time IS NOT NULL AND entry_time > 0 
            THEN to_timestamp(entry_time / 1000.0) AT TIME ZONE 'UTC'
            ELSE NULL
        END,
        NOW()
    ) as search_date,
    
    -- JSON поля
    long_ticker_candles_json,
    short_ticker_candles_json,
    z_score_history_json,
    profit_history_json,
    pixel_spread_history_json,
    
    -- Торговые цены - преобразуем double в DECIMAL
    CASE WHEN long_ticker_entry_price IS NOT NULL THEN long_ticker_entry_price::DECIMAL(18,8) END,
    CASE WHEN long_ticker_current_price IS NOT NULL THEN long_ticker_current_price::DECIMAL(18,8) END,
    CASE WHEN short_ticker_entry_price IS NOT NULL THEN short_ticker_entry_price::DECIMAL(18,8) END,
    CASE WHEN short_ticker_current_price IS NOT NULL THEN short_ticker_current_price::DECIMAL(18,8) END,
    
    -- Статистические данные
    CASE WHEN mean_entry IS NOT NULL THEN mean_entry::DECIMAL(18,8) END,
    CASE WHEN mean_current IS NOT NULL THEN mean_current::DECIMAL(18,8) END,
    CASE WHEN spread_entry IS NOT NULL THEN spread_entry::DECIMAL(18,8) END,
    CASE WHEN spread_current IS NOT NULL THEN spread_current::DECIMAL(18,8) END,
    CASE WHEN z_score_entry IS NOT NULL THEN z_score_entry::DECIMAL(10,4) END,
    CASE WHEN z_score_current IS NOT NULL THEN z_score_current::DECIMAL(10,4) END,
    CASE WHEN p_value_entry IS NOT NULL THEN p_value_entry::DECIMAL(10,8) END,
    CASE WHEN p_value_current IS NOT NULL THEN p_value_current::DECIMAL(10,8) END,
    CASE WHEN adf_pvalue_entry IS NOT NULL THEN adf_pvalue_entry::DECIMAL(10,8) END,
    CASE WHEN adf_pvalue_current IS NOT NULL THEN adf_pvalue_current::DECIMAL(10,8) END,
    CASE WHEN correlation_entry IS NOT NULL THEN correlation_entry::DECIMAL(10,8) END,
    CASE WHEN correlation_current IS NOT NULL THEN correlation_current::DECIMAL(10,8) END,
    CASE WHEN alpha_entry IS NOT NULL THEN alpha_entry::DECIMAL(18,8) END,
    CASE WHEN alpha_current IS NOT NULL THEN alpha_current::DECIMAL(18,8) END,
    CASE WHEN beta_entry IS NOT NULL THEN beta_entry::DECIMAL(18,8) END,
    CASE WHEN beta_current IS NOT NULL THEN beta_current::DECIMAL(18,8) END,
    CASE WHEN std_entry IS NOT NULL THEN std_entry::DECIMAL(18,8) END,
    CASE WHEN std_current IS NOT NULL THEN std_current::DECIMAL(18,8) END,
    
    -- Торговые изменения
    z_score_changes,
    long_usdt_changes,
    long_percent_changes,
    short_usdt_changes,
    short_percent_changes,
    
    -- Портфель и прибыль
    portfolio_before_trade_usdt,
    profit_usdt_changes,
    portfolio_after_trade_usdt,
    profit_percent_changes,
    
    -- Время до экстремумов
    minutes_to_min_profit_percent,
    minutes_to_max_profit_percent,
    min_profit_percent_changes,
    max_profit_percent_changes,
    
    -- Форматированные поля
    formatted_time_to_min_profit,
    formatted_time_to_max_profit,
    formatted_profit_long,
    formatted_profit_short,
    formatted_profit_common,
    
    -- Экстремальные значения
    max_z,
    min_z,
    max_long,
    min_long,
    max_short,
    min_short,
    max_corr,
    min_corr,
    
    -- Информация о выходе
    exit_reason,
    close_at_breakeven,
    
    -- Торговые настройки
    CASE WHEN settings_candle_limit IS NOT NULL THEN settings_candle_limit::DECIMAL(10,2) END,
    CASE WHEN settings_min_z IS NOT NULL THEN settings_min_z::DECIMAL(10,4) END,
    
    -- Усреднение (проверяем существование полей)
    CASE 
        WHEN EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'trading_pair' AND column_name = 'averaging_count'
        ) THEN averaging_count 
        ELSE 0 
    END,
    CASE 
        WHEN EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'trading_pair' AND column_name = 'last_averaging_timestamp'
        ) THEN last_averaging_timestamp 
        ELSE NULL 
    END,
    
    -- Нормализованные цены (проверяем существование полей)
    CASE 
        WHEN EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'trading_pair' AND column_name = 'normalized_long_prices_json'
        ) THEN normalized_long_prices_json 
        ELSE NULL 
    END,
    CASE 
        WHEN EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'trading_pair' AND column_name = 'normalized_short_prices_json'
        ) THEN normalized_short_prices_json 
        ELSE NULL 
    END,
    CASE 
        WHEN EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'trading_pair' AND column_name = 'intersections_count'
        ) THEN intersections_count 
        ELSE 0 
    END

FROM trading_pair
-- Исключаем записи с некорректными данными
WHERE long_ticker IS NOT NULL 
  AND short_ticker IS NOT NULL;

-- Проверяем результат миграции
DO $$
DECLARE
    trading_count INTEGER;
    pairs_trading_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO trading_count FROM trading_pair;
    SELECT COUNT(*) INTO pairs_trading_count FROM pairs WHERE type = 'TRADING';
    
    RAISE NOTICE 'Миграция TradingPair завершена:';
    RAISE NOTICE '- Записей в trading_pair: %', trading_count;
    RAISE NOTICE '- Записей в pairs (TRADING): %', pairs_trading_count;
    
    IF pairs_trading_count != trading_count THEN
        RAISE WARNING 'Количество записей не совпадает! Проверьте данные.';
    END IF;
END $$;