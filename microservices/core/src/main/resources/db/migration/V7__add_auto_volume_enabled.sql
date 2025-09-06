-- Добавление поля auto_volume_enabled для функции автообъема
ALTER TABLE settings ADD COLUMN IF NOT EXISTS auto_volume_enabled BOOLEAN DEFAULT FALSE;

-- Комментарий к полю
COMMENT ON COLUMN settings.auto_volume_enabled IS 'Включение автоматического расчета объемов позиций на основе свободных USDT';