-- V40__add_score_filtering_settings.sql
-- Добавляем настройки для фильтрации по скору стабильности

-- Добавляем флаг включения фильтрации по скору (если колонка не существует)
ALTER TABLE settings ADD COLUMN IF NOT EXISTS use_score_filtering BOOLEAN DEFAULT FALSE;

-- Добавляем минимальный скор для фильтрации (если колонка не существует)
ALTER TABLE settings ADD COLUMN IF NOT EXISTS min_stability_score INTEGER DEFAULT 30;

-- Устанавливаем значения по умолчанию для существующих записей
UPDATE settings SET use_score_filtering = FALSE WHERE use_score_filtering IS NULL;
UPDATE settings SET min_stability_score = 30 WHERE min_stability_score IS NULL;