-- Добавление новых полей усреднения в таблицу settings и trading_pair

-- Добавление новых полей в settings
ALTER TABLE settings
    ADD COLUMN IF NOT EXISTS averaging_drawdown_multiplier DOUBLE PRECISION DEFAULT 1.5;

ALTER TABLE settings
    ADD COLUMN IF NOT EXISTS max_averaging_count INTEGER DEFAULT 3;

-- Добавление новых полей в trading_pair
ALTER TABLE trading_pair
    ADD COLUMN IF NOT EXISTS settings_averaging_drawdown_multiplier DOUBLE PRECISION DEFAULT 0.0;

ALTER TABLE trading_pair
    ADD COLUMN IF NOT EXISTS settings_max_averaging_count INTEGER DEFAULT 0;

ALTER TABLE trading_pair
    ADD COLUMN IF NOT EXISTS last_averaging_profit_percent NUMERIC (19,8) DEFAULT 0.0;