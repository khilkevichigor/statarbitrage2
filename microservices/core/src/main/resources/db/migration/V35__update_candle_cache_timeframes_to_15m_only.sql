-- Migration to update candle cache active timeframes to only 15m

-- Update active timeframes to only 15m in all existing settings records
UPDATE settings 
SET candle_cache_active_timeframes = '15m'
WHERE candle_cache_active_timeframes IS NOT NULL;

-- Add comment for clarity
COMMENT ON COLUMN settings.candle_cache_active_timeframes IS 'Активные таймфреймы для кэша свечей - только 15m для оптимизации';