-- Добавляет поля для сохранения нормализованных цен и количества пересечений в CointPair и TradingPair

-- Добавляем поля в таблицу coint_pair
ALTER TABLE coint_pair
    ADD COLUMN IF NOT EXISTS normalized_long_prices_json TEXT,
    ADD COLUMN IF NOT EXISTS normalized_short_prices_json TEXT,
    ADD COLUMN IF NOT EXISTS intersections_count INTEGER;

-- Добавляем поля в таблицу trading_pair
ALTER TABLE trading_pair
    ADD COLUMN IF NOT EXISTS normalized_long_prices_json TEXT,
    ADD COLUMN IF NOT EXISTS normalized_short_prices_json TEXT,
    ADD COLUMN IF NOT EXISTS intersections_count INTEGER;