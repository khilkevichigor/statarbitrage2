-- Добавление полей для фильтра по пересечениям нормализованных цен
ALTER TABLE settings
    ADD COLUMN IF NOT EXISTS min_intersections INTEGER DEFAULT 10;
ALTER TABLE settings
    ADD COLUMN IF NOT EXISTS use_min_intersections BOOLEAN DEFAULT FALSE;

-- Комментарии к полям
COMMENT
ON COLUMN settings.min_intersections IS 'Минимальное количество пересечений нормализованных цен для фильтра';
COMMENT
ON COLUMN settings.use_min_intersections IS 'Включение фильтра по пересечениям нормализованных цен';