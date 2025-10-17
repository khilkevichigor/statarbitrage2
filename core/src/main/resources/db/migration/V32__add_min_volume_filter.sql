-- Migration V32: Add minimum volume filter to stable pairs screener settings
-- Description: Add min_volume_enabled and min_volume_value fields for volume-based filtering

-- ========================================
-- Add minimum volume filter fields
-- ========================================

-- Add minimum volume filter enabled flag
ALTER TABLE stable_pairs_screener_settings 
ADD COLUMN IF NOT EXISTS min_volume_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Add minimum volume value field (in millions of dollars)
ALTER TABLE stable_pairs_screener_settings 
ADD COLUMN IF NOT EXISTS min_volume_value DOUBLE PRECISION DEFAULT 1.0;

-- ========================================
-- Add comments for new fields
-- ========================================

COMMENT ON COLUMN stable_pairs_screener_settings.min_volume_enabled IS 
    'Включен ли фильтр по минимальному объему торгов';

COMMENT ON COLUMN stable_pairs_screener_settings.min_volume_value IS 
    'Минимальный объем торгов в миллионах долларов (по умолчанию 1.0)';

-- ========================================
-- Update existing default settings
-- ========================================

-- Update default settings to include minimum volume filter with default values
UPDATE stable_pairs_screener_settings 
SET 
    min_volume_enabled = FALSE,
    min_volume_value = 1.0,
    updated_at = CURRENT_TIMESTAMP
WHERE is_default = TRUE;

-- ========================================
-- Log completion
-- ========================================

-- This migration adds minimum volume filtering capability to the stable pairs screener
-- allowing users to filter trading pairs based on minimum trading volume thresholds