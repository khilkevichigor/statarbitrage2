-- V43: Исправление NULL значений в полях фильтра снижения zScore
-- Дата: 2025-11-06
-- Описание: Обновляем существующие NULL значения и устанавливаем NOT NULL ограничения

-- Обновляем существующие записи со значениями по умолчанию (для случаев когда колонки уже существовали как NULL)
UPDATE settings SET use_zscore_decline_filter = FALSE WHERE use_zscore_decline_filter IS NULL;
UPDATE settings SET zscore_decline_candles_count = 4 WHERE zscore_decline_candles_count IS NULL;

-- Устанавливаем NOT NULL ограничения после обновления
ALTER TABLE settings ALTER COLUMN use_zscore_decline_filter SET NOT NULL;
ALTER TABLE settings ALTER COLUMN zscore_decline_candles_count SET NOT NULL;