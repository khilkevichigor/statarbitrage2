-- Добавление недостающих полей для усреднения позиций

ALTER TABLE pairs ADD COLUMN IF NOT EXISTS last_averaging_profit_percent DECIMAL(10,4);
ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_averaging_drawdown_multiplier DECIMAL(10,4);

COMMENT ON COLUMN pairs.last_averaging_profit_percent IS 'Процент прибыли на момент последнего усреднения позиции';
COMMENT ON COLUMN pairs.settings_averaging_drawdown_multiplier IS 'Множитель для расчёта объёма при усреднении позиции';