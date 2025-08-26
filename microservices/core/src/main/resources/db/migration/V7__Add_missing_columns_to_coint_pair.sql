ALTER TABLE coint_pair
    ADD COLUMN settings_auto_averaging_enabled BOOLEAN;
ALTER TABLE coint_pair
    ADD COLUMN settings_averaging_drawdown_threshold REAL;
ALTER TABLE coint_pair
    ADD COLUMN settings_averaging_volume_multiplier REAL;
ALTER TABLE coint_pair
    ADD COLUMN averaging_count INTEGER DEFAULT 0;
ALTER TABLE coint_pair
    ADD COLUMN last_averaging_timestamp BIGINT;