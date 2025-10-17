-- Migration V33: Add monitoring pairs update scheduler settings
-- Description: Add scheduler settings for automatic monitoring pairs update

-- ========================================
-- Add monitoring pairs update scheduler fields
-- ========================================

-- Add monitoring pairs update scheduler enabled flag
ALTER TABLE settings 
ADD COLUMN IF NOT EXISTS scheduler_monitoring_pairs_update_enabled BOOLEAN DEFAULT TRUE;

-- Add monitoring pairs update scheduler cron expression
ALTER TABLE settings 
ADD COLUMN IF NOT EXISTS scheduler_monitoring_pairs_update_cron VARCHAR(50) DEFAULT '0 0 1 * * *';

-- ========================================
-- Add comments for new fields
-- ========================================

COMMENT ON COLUMN settings.scheduler_monitoring_pairs_update_enabled IS 
    'Включен ли шедуллер автоматического обновления пар в мониторинге (по умолчанию включен)';

COMMENT ON COLUMN settings.scheduler_monitoring_pairs_update_cron IS 
    'CRON выражение для шедуллера обновления пар в мониторинге (по умолчанию каждый день в 01:00)';

-- ========================================
-- Update existing settings with default values
-- ========================================

-- Update existing settings to include new scheduler settings with default values
UPDATE settings 
SET 
    scheduler_monitoring_pairs_update_enabled = TRUE,
    scheduler_monitoring_pairs_update_cron = '0 0 1 * * *'
WHERE scheduler_monitoring_pairs_update_enabled IS NULL 
   OR scheduler_monitoring_pairs_update_cron IS NULL;

-- ========================================
-- Log completion
-- ========================================

-- This migration adds scheduler control for automatic monitoring pairs update
-- allowing users to enable/disable and configure the update schedule for monitoring pairs