-- Migration V25: Add search tickers filter to stable pairs screener settings
-- Description: Add searchTickers field to allow filtering by specific tickers

-- ========================================
-- Add search tickers filter fields
-- ========================================

-- Add search tickers enabled flag
ALTER TABLE stable_pairs_screener_settings 
ADD COLUMN IF NOT EXISTS search_tickers_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Add search tickers field (comma-separated list of tickers)
ALTER TABLE stable_pairs_screener_settings 
ADD COLUMN IF NOT EXISTS search_tickers TEXT;

-- ========================================
-- Add comments
-- ========================================

COMMENT ON COLUMN stable_pairs_screener_settings.search_tickers_enabled IS 
    'Включен ли фильтр по определенным тикерам';

COMMENT ON COLUMN stable_pairs_screener_settings.search_tickers IS 
    'Список тикеров для поиска через запятую (например: "BTC,ETH,XRP,ADA"). Если пустое - поиск по всем тикерам';

-- ========================================
-- Update existing default settings to include new fields
-- ========================================

-- Update default settings with new search tickers fields
UPDATE stable_pairs_screener_settings 
SET 
    search_tickers_enabled = FALSE,
    search_tickers = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE is_default = TRUE;