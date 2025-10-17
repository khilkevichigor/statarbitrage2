-- Добавление полей TradingPair в унифицированную таблицу pairs
-- Эти поля будут null для типов STABLE и частично заполнены для COINTEGRATED

-- Торговые изменения и статистика
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS z_score_changes DECIMAL(18,8);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS long_usdt_changes DECIMAL(18,8);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS long_percent_changes DECIMAL(18,8);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS short_usdt_changes DECIMAL(18,8);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS short_percent_changes DECIMAL(18,8);

-- Портфель и прибыль
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS portfolio_before_trade_usdt DECIMAL(18,8);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS profit_usdt_changes DECIMAL(18,8);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS portfolio_after_trade_usdt DECIMAL(18,8);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS profit_percent_changes DECIMAL(18,8);

-- Время до минимальной и максимальной прибыли
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS minutes_to_min_profit_percent BIGINT;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS minutes_to_max_profit_percent BIGINT;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS min_profit_percent_changes DECIMAL(18,8);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS max_profit_percent_changes DECIMAL(18,8);

-- Форматированные поля времени и прибыли
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS formatted_time_to_min_profit TEXT;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS formatted_time_to_max_profit TEXT;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS formatted_profit_long TEXT;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS formatted_profit_short TEXT;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS formatted_profit_common TEXT;

-- Экстремальные значения Z-Score, Long и Short
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS max_z DECIMAL(18,8);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS min_z DECIMAL(18,8);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS max_long DECIMAL(18,8);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS min_long DECIMAL(18,8);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS max_short DECIMAL(18,8);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS min_short DECIMAL(18,8);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS max_corr DECIMAL(18,8);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS min_corr DECIMAL(18,8);

-- Торговая информация
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS exit_reason TEXT;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS close_at_breakeven BOOLEAN DEFAULT FALSE;

-- Настройки торговли (сохранены для каждой торгуемой пары)
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_candle_limit DECIMAL(10,2);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_min_z DECIMAL(10,4);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_min_window_size DECIMAL(10,2);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_min_p_value DECIMAL(10,8);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_max_adf_value DECIMAL(10,4);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_min_r_squared DECIMAL(10,8);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_min_correlation DECIMAL(10,8);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_min_volume DECIMAL(18,8);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_check_interval DECIMAL(10,2);

-- Настройки маржинальной торговли
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_max_long_margin_size DECIMAL(18,8);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_max_short_margin_size DECIMAL(18,8);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_leverage DECIMAL(10,2);

-- Настройки выхода
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_exit_take DECIMAL(10,4);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_exit_stop DECIMAL(10,4);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_exit_z_min DECIMAL(10,4);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_exit_z_max DECIMAL(10,4);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_exit_z_max_percent DECIMAL(10,4);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_exit_time_minutes DECIMAL(10,2);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_exit_breakeven_percent DECIMAL(10,4);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_use_pairs DECIMAL(10,2);

-- Булевые настройки торговли
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_auto_trading_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_use_min_z_filter BOOLEAN DEFAULT FALSE;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_use_min_r_squared_filter BOOLEAN DEFAULT FALSE;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_use_min_p_value_filter BOOLEAN DEFAULT FALSE;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_use_max_adf_value_filter BOOLEAN DEFAULT FALSE;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_use_min_correlation_filter BOOLEAN DEFAULT FALSE;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_use_min_volume_filter BOOLEAN DEFAULT FALSE;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_use_exit_take BOOLEAN DEFAULT FALSE;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_use_exit_stop BOOLEAN DEFAULT FALSE;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_use_exit_z_min BOOLEAN DEFAULT FALSE;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_use_exit_z_max BOOLEAN DEFAULT FALSE;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_use_exit_z_max_percent BOOLEAN DEFAULT FALSE;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_use_exit_time_hours BOOLEAN DEFAULT FALSE;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_use_exit_breakeven_percent BOOLEAN DEFAULT FALSE;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_minimum_lot_blacklist TEXT;

-- Настройки скоринга
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS use_z_score_scoring BOOLEAN DEFAULT FALSE;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS z_score_scoring_weight DECIMAL(10,4);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS use_pixel_spread_scoring BOOLEAN DEFAULT FALSE;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS pixel_spread_scoring_weight DECIMAL(10,4);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS use_cointegration_scoring BOOLEAN DEFAULT FALSE;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS cointegration_scoring_weight DECIMAL(10,4);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS use_model_quality_scoring BOOLEAN DEFAULT FALSE;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS model_quality_scoring_weight DECIMAL(10,4);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS use_statistics_scoring BOOLEAN DEFAULT FALSE;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS statistics_scoring_weight DECIMAL(10,4);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS use_bonus_scoring BOOLEAN DEFAULT FALSE;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS bonus_scoring_weight DECIMAL(10,4);

-- Дополнительные настройки выхода
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_use_exit_negative_z_min_profit_percent BOOLEAN DEFAULT FALSE;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_exit_negative_z_min_profit_percent DECIMAL(10,4);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_auto_averaging_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_averaging_drawdown_threshold DECIMAL(10,4);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_averaging_volume_multiplier DECIMAL(10,4);

-- Усреднение
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS averaging_count INTEGER DEFAULT 0;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS last_averaging_timestamp BIGINT;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS last_averaging_profit_percent DECIMAL(10,4);

-- Нормализованные цены и пересечения (из более поздних версий)
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS normalized_long_prices_json TEXT;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS normalized_short_prices_json TEXT;
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS intersections_count INTEGER DEFAULT 0;

-- Создание дополнительных индексов для торговых полей
CREATE INDEX IF NOT EXISTS idx_pair_profit_percent ON pairs(profit_percent_changes);
CREATE INDEX IF NOT EXISTS idx_pair_max_z ON pairs(max_z);
CREATE INDEX IF NOT EXISTS idx_pair_min_z ON pairs(min_z);
CREATE INDEX IF NOT EXISTS idx_pair_exit_reason ON pairs(exit_reason);
CREATE INDEX IF NOT EXISTS idx_pair_averaging_count ON pairs(averaging_count);

-- Комментарии к новым полям
COMMENT ON COLUMN pairs.z_score_changes IS 'Изменения Z-Score с момента входа';
COMMENT ON COLUMN pairs.profit_percent_changes IS 'Процентная прибыль от сделки';
COMMENT ON COLUMN pairs.exit_reason IS 'Причина закрытия позиции';
COMMENT ON COLUMN pairs.close_at_breakeven IS 'Закрыта ли позиция в безубыточности';
COMMENT ON COLUMN pairs.averaging_count IS 'Количество усреднений позиции';
COMMENT ON COLUMN pairs.intersections_count IS 'Количество пересечений нормализованных цен';