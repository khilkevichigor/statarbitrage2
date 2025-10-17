-- Migration to add global timeframes and periods settings

-- Add global timeframes and periods configuration columns
ALTER TABLE settings
    ADD COLUMN IF NOT EXISTS global_active_timeframes VARCHAR (200) DEFAULT '15m';
ALTER TABLE settings
    ADD COLUMN IF NOT EXISTS global_active_periods VARCHAR (500) DEFAULT '1 месяц,3 месяца,6 месяцев,1 год';

-- Update existing records with default values
UPDATE settings
SET global_active_timeframes = '15m',
    global_active_periods    = '1 месяц,3 месяца,6 месяцев,1 год'
WHERE global_active_timeframes IS NULL
   OR global_active_periods IS NULL;

-- Add comments for clarity
COMMENT
ON COLUMN settings.global_active_timeframes IS 'Глобально активные таймфреймы для всей системы (кэш, UI, шедуллеры)';
COMMENT
ON COLUMN settings.global_active_periods IS 'Глобально активные периоды для всей системы (поиск пар, анализ)';