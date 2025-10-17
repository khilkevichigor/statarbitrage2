-- Добавление поля для множителя усреднения при просадке

ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_averaging_drawdown_multiplier DECIMAL(10,4);

COMMENT ON COLUMN pairs.settings_averaging_drawdown_multiplier IS 'Множитель для расчёта объёма при усреднении позиции';