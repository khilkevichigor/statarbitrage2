-- Migration to add candle cache settings to the settings table

-- Add new columns for candle cache configuration
ALTER TABLE settings ADD COLUMN IF NOT EXISTS candle_cache_enabled BOOLEAN DEFAULT TRUE;
ALTER TABLE settings ADD COLUMN IF NOT EXISTS candle_cache_default_exchange VARCHAR(20) DEFAULT 'OKX';
ALTER TABLE settings ADD COLUMN IF NOT EXISTS candle_cache_thread_count INTEGER DEFAULT 5;
ALTER TABLE settings ADD COLUMN IF NOT EXISTS candle_cache_active_timeframes VARCHAR(100) DEFAULT '1m,5m,15m,1H,4H,1D';
ALTER TABLE settings ADD COLUMN IF NOT EXISTS candle_cache_preload_schedule VARCHAR(50) DEFAULT '0 0 2 * * SUN';
ALTER TABLE settings ADD COLUMN IF NOT EXISTS candle_cache_daily_update_schedule VARCHAR(50) DEFAULT '0 */30 * * * *';
ALTER TABLE settings ADD COLUMN IF NOT EXISTS candle_cache_force_load_period_days INTEGER DEFAULT 365;

-- Update existing records with default values
UPDATE settings SET 
    candle_cache_enabled = TRUE,
    candle_cache_default_exchange = 'OKX',
    candle_cache_thread_count = 5,
    candle_cache_active_timeframes = '1m,5m,15m,1H,4H,1D',
    candle_cache_preload_schedule = '0 0 2 * * SUN',
    candle_cache_daily_update_schedule = '0 */30 * * * *',
    candle_cache_force_load_period_days = 365
WHERE candle_cache_enabled IS NULL;