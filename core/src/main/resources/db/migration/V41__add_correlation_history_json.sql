-- V41: Добавление колонки correlation_history_json в таблицу pairs
-- Дата: 2025-11-06
-- Описание: Добавляем поле для хранения истории корреляции в JSON формате

ALTER TABLE pairs ADD COLUMN IF NOT EXISTS correlation_history_json TEXT;

-- Добавляем комментарий к колонке
COMMENT ON COLUMN pairs.correlation_history_json IS 'История корреляции в JSON формате - массив объектов {timestamp, correlation}';