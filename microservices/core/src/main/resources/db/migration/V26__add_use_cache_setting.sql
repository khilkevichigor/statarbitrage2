-- Migration V26: Add use cache setting to stable pairs screener settings
-- Description: Add useCache field to allow choosing between cache and direct loading from OKX

-- ========================================
-- Add use cache field
-- ========================================

-- Step 1: Add use cache flag as nullable first
ALTER TABLE stable_pairs_screener_settings 
ADD COLUMN IF NOT EXISTS use_cache BOOLEAN DEFAULT TRUE;

-- Step 2: Update all existing NULL values to TRUE (maintaining existing behavior)
UPDATE stable_pairs_screener_settings 
SET use_cache = TRUE 
WHERE use_cache IS NULL;

-- Step 3: Now make the column NOT NULL
ALTER TABLE stable_pairs_screener_settings 
ALTER COLUMN use_cache SET NOT NULL;

-- ========================================
-- Add comments
-- ========================================

COMMENT ON COLUMN stable_pairs_screener_settings.use_cache IS 
    'Использовать ли кэш для получения свечей (по умолчанию true). При false - загрузка напрямую с OKX';

-- ========================================
-- Update existing default settings to include new field
-- ========================================

-- Update default settings with new use cache field (set to TRUE to maintain existing behavior)
UPDATE stable_pairs_screener_settings 
SET 
    use_cache = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE is_default = TRUE;

-- ========================================
-- Additional indexes for performance (optional)
-- ========================================

-- Add index on use_cache for performance optimization in queries
CREATE INDEX IF NOT EXISTS idx_stable_pairs_screener_settings_use_cache 
ON stable_pairs_screener_settings(use_cache);