-- Добавление колонок timeframe и candle_count во все основные таблицы для лучшего понимания данных

-- Добавление candle_count в таблицу stable_pairs
ALTER TABLE stable_pairs
    ADD COLUMN IF NOT EXISTS candle_count INTEGER;

-- Добавление timeframe и candle_count в таблицу positions
ALTER TABLE positions
    ADD COLUMN IF NOT EXISTS timeframe VARCHAR(10);

ALTER TABLE positions
    ADD COLUMN IF NOT EXISTS candle_count INTEGER;

-- Добавление timeframe и candle_count в таблицу trade_history
ALTER TABLE trade_history
    ADD COLUMN IF NOT EXISTS timeframe VARCHAR(10);

ALTER TABLE trade_history
    ADD COLUMN IF NOT EXISTS candle_count INTEGER;

-- Добавление timeframe и candle_count в таблицу portfolio_history
ALTER TABLE portfolio_history
    ADD COLUMN IF NOT EXISTS timeframe VARCHAR(10);

ALTER TABLE portfolio_history
    ADD COLUMN IF NOT EXISTS candle_count INTEGER;

-- Комментарии для понимания назначения колонок
COMMENT ON COLUMN stable_pairs.candle_count IS 'Количество свечей использованных для анализа стабильности пары';
COMMENT ON COLUMN positions.timeframe IS 'ТФ: таймфрейм (1m, 5m, 15m, 1H, 4H, 1D, 1W, 1M)';
COMMENT ON COLUMN positions.candle_count IS 'Свечей: количество свечей использованных для анализа';
COMMENT ON COLUMN trade_history.timeframe IS 'ТФ: таймфрейм (1m, 5m, 15m, 1H, 4H, 1D, 1W, 1M)';
COMMENT ON COLUMN trade_history.candle_count IS 'Свечей: количество свечей использованных для анализа';
COMMENT ON COLUMN portfolio_history.timeframe IS 'ТФ: таймфрейм используемый для торговли (1m, 5m, 15m, 1H, 4H, 1D, 1W, 1M)';
COMMENT ON COLUMN portfolio_history.candle_count IS 'Свечей: количество свечей использованных для анализа';