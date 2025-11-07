-- V42: Добавление настроек фильтра "Вход при снижении zScore"
-- Дата: 2025-11-06
-- Описание: Добавляем поля для фильтрации входа при снижении zScore

-- Добавляем флаг включения фильтра снижения zScore
ALTER TABLE settings ADD COLUMN IF NOT EXISTS use_zscore_decline_filter BOOLEAN DEFAULT FALSE;

-- Добавляем количество свечей для проверки снижения
ALTER TABLE settings ADD COLUMN IF NOT EXISTS zscore_decline_candles_count INTEGER DEFAULT 4;

-- Обновляем существующие записи со значениями по умолчанию (для случаев когда колонки уже существовали как NULL)
UPDATE settings SET use_zscore_decline_filter = FALSE WHERE use_zscore_decline_filter IS NULL;
UPDATE settings SET zscore_decline_candles_count = 4 WHERE zscore_decline_candles_count IS NULL;

-- Устанавливаем NOT NULL ограничения после обновления
ALTER TABLE settings ALTER COLUMN use_zscore_decline_filter SET NOT NULL;
ALTER TABLE settings ALTER COLUMN zscore_decline_candles_count SET NOT NULL;

-- Добавляем комментарии к колонкам
COMMENT ON COLUMN settings.use_zscore_decline_filter IS 'Включить фильтр входа при снижении zScore';
COMMENT ON COLUMN settings.zscore_decline_candles_count IS 'Количество свечей для проверки снижения zScore';