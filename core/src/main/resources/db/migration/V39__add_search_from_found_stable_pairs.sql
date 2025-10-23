-- V39: Добавление поля для поиска из найденных стабильных пар
-- Автор: Система - добавление новой функциональности поиска пар в настройках

-- Добавляем поле для поиска из списка "Найденные стабильные пары" в глобальные настройки
ALTER TABLE settings 
ADD COLUMN IF NOT EXISTS use_found_stable_pairs BOOLEAN DEFAULT FALSE;

-- Обновляем существующие записи с NULL значениями
UPDATE settings 
SET use_found_stable_pairs = FALSE 
WHERE use_found_stable_pairs IS NULL;

-- Добавляем комментарии к новому полю для документации
COMMENT ON COLUMN settings.use_found_stable_pairs IS 
'Включен ли поиск из списка "Найденные стабильные пары" (найденные пары не в мониторинге)';