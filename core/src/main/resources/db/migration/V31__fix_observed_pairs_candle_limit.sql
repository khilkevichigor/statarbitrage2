-- Исправление поля settings_candle_limit для наблюдаемых пар
-- Устанавливаем значение из настроек для всех пар со статусом OBSERVED, где поле равно NULL

UPDATE pairs 
SET 
    settings_candle_limit = (SELECT candle_limit FROM settings LIMIT 1),
    settings_min_z = (SELECT min_z FROM settings LIMIT 1),
    timeframe = (SELECT timeframe FROM settings LIMIT 1)
WHERE 
    status = 'OBSERVED' 
    AND (settings_candle_limit IS NULL OR settings_min_z IS NULL OR timeframe IS NULL);