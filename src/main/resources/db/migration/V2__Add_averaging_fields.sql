-- Добавляем поля для функции усреднения позиций

-- Новые поля в таблицу settings для конфигурации усреднения
ALTER TABLE settings
    ADD COLUMN auto_averaging_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE settings
    ADD COLUMN averaging_drawdown_threshold REAL DEFAULT 5.0;
ALTER TABLE settings
    ADD COLUMN averaging_volume_multiplier REAL DEFAULT 1.5;

-- Добавляем поля усреднения в таблицу pair_data для отслеживания состояния
ALTER TABLE pair_data
    ADD COLUMN settings_auto_averaging_enabled BOOLEAN;
ALTER TABLE pair_data
    ADD COLUMN settings_averaging_drawdown_threshold REAL;
ALTER TABLE pair_data
    ADD COLUMN settings_averaging_volume_multiplier REAL;
ALTER TABLE pair_data
    ADD COLUMN averaging_count INTEGER DEFAULT 0;
ALTER TABLE pair_data
    ADD COLUMN last_averaging_timestamp BIGINT;