-- Migration to update default stable pairs screener settings
-- This ensures consistent default values after database recreation

-- Update existing default settings to use correct values
UPDATE stable_pairs_screener_settings
SET selected_timeframes = '15m',
    selected_periods = '1 месяц',
    min_window_size_value = 450,
    min_volume_enabled = true,
    min_volume_value = 50,
    updated_at = CURRENT_TIMESTAMP
WHERE is_default = true;

-- Insert default settings if none exist
INSERT INTO stable_pairs_screener_settings (
    name,
    is_default,
    selected_timeframes,
    selected_periods,
    min_correlation_enabled,
    min_correlation_value,
    min_window_size_enabled,
    min_window_size_value,
    max_adf_value_enabled,
    max_adf_value,
    min_r_squared_enabled,
    min_r_squared_value,
    max_p_value_enabled,
    max_p_value,
    min_volume_enabled,
    min_volume_value,
    search_tickers_enabled,
    search_tickers,
    run_on_schedule,
    use_cache,
    created_at,
    updated_at
)
SELECT 
    'Настройки по умолчанию',
    true,
    '15m',
    '1 месяц',
    true,
    0.1,
    true,
    450.0,
    true,
    0.1,
    true,
    0.1,
    true,
    0.1,
    true,
    50.0,
    false,
    '',
    false,
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM stable_pairs_screener_settings WHERE is_default = true
);

-- Ensure only one default setting exists (cleanup duplicates)
WITH ranked_defaults AS (
    SELECT id, 
           ROW_NUMBER() OVER (ORDER BY created_at ASC) as rn
    FROM stable_pairs_screener_settings 
    WHERE is_default = true
)
UPDATE stable_pairs_screener_settings 
SET is_default = false,
    updated_at = CURRENT_TIMESTAMP
WHERE id IN (
    SELECT id FROM ranked_defaults WHERE rn > 1
);

-- Add comment for documentation
COMMENT ON TABLE stable_pairs_screener_settings IS 'Настройки скриннера стабильных пар с актуальными значениями по умолчанию';