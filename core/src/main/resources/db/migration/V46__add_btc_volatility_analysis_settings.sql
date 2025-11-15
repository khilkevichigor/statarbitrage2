-- V46: Добавление настроек анализа волатильности BTC для фильтрации автотрейдинга
-- Автор: AI Assistant
-- Дата: 2025-11-15

-- Добавляем поля для анализа BTC в таблицу settings
ALTER TABLE settings 
ADD COLUMN IF NOT EXISTS use_btc_volatility_filter BOOLEAN DEFAULT FALSE NOT NULL,
ADD COLUMN IF NOT EXISTS btc_atr_threshold_multiplier DOUBLE PRECISION DEFAULT 1.3 NOT NULL,
ADD COLUMN IF NOT EXISTS btc_daily_range_multiplier DOUBLE PRECISION DEFAULT 1.3 NOT NULL,
ADD COLUMN IF NOT EXISTS max_btc_daily_change_percent DOUBLE PRECISION DEFAULT 5.0 NOT NULL;

-- Добавляем комментарии к новым полям
COMMENT ON COLUMN settings.use_btc_volatility_filter IS 'Включить фильтр волатильности BTC при автотрейдинге';
COMMENT ON COLUMN settings.btc_atr_threshold_multiplier IS 'Множитель порога ATR для BTC (например, 1.3 = 30% превышение)';
COMMENT ON COLUMN settings.btc_daily_range_multiplier IS 'Множитель дневного диапазона для BTC (например, 1.3 = 30% превышение)';
COMMENT ON COLUMN settings.max_btc_daily_change_percent IS 'Максимальное дневное изменение BTC в % (не торгуем если превышено)';