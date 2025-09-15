-- Добавление поля для максимального количества усреднений

ALTER TABLE pairs ADD COLUMN IF NOT EXISTS settings_max_averaging_count INTEGER;

COMMENT ON COLUMN pairs.settings_max_averaging_count IS 'Максимальное количество усреднений позиции';