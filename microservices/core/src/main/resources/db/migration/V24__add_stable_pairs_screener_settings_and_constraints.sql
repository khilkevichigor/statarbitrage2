-- Migration V23: Add stable pairs screener settings and unique constraints
-- Description: Create stable_pairs_screener_settings table and add unique constraints to pairs table

-- ========================================
-- Create stable pairs screener settings table
-- ========================================

CREATE TABLE IF NOT EXISTS stable_pairs_screener_settings (
    id BIGSERIAL PRIMARY KEY,
    
    -- Basic information
    name VARCHAR(100) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Timeframes and periods (comma-separated)
    selected_timeframes TEXT,
    selected_periods TEXT,
    
    -- Correlation filter settings
    min_correlation_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    min_correlation_value DOUBLE PRECISION,
    
    -- Window size filter settings
    min_window_size_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    min_window_size_value DOUBLE PRECISION,
    
    -- ADF filter settings
    max_adf_value_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    max_adf_value DOUBLE PRECISION,
    
    -- R² filter settings
    min_r_squared_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    min_r_squared_value DOUBLE PRECISION,
    
    -- P-Value filter settings
    max_p_value_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    max_p_value DOUBLE PRECISION,
    
    -- Automatic scheduling
    run_on_schedule BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Metadata
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP WITH TIME ZONE
);

-- ========================================
-- Create indexes for screener settings table
-- ========================================

CREATE INDEX IF NOT EXISTS idx_screener_settings_default 
    ON stable_pairs_screener_settings (is_default) 
    WHERE is_default = TRUE;

CREATE INDEX IF NOT EXISTS idx_screener_settings_schedule 
    ON stable_pairs_screener_settings (run_on_schedule) 
    WHERE run_on_schedule = TRUE;

CREATE INDEX IF NOT EXISTS idx_screener_settings_last_used 
    ON stable_pairs_screener_settings (last_used_at DESC NULLS LAST);

CREATE INDEX IF NOT EXISTS idx_screener_settings_updated 
    ON stable_pairs_screener_settings (updated_at DESC);

-- ========================================
-- Add unique constraints to pairs table
-- ========================================

-- Add unique constraint to prevent duplicate stable pairs
-- This ensures no duplicates by ticker_a + ticker_b + timeframe + period + type
DO $$ 
BEGIN 
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'uk_stable_pairs_unique' 
        AND table_name = 'pairs'
    ) THEN
        ALTER TABLE pairs ADD CONSTRAINT uk_stable_pairs_unique 
            UNIQUE (ticker_a, ticker_b, timeframe, period, type);
    END IF;
END $$;

-- Create unique index for monitoring pairs
-- This ensures no duplicate pairs in monitoring
CREATE UNIQUE INDEX IF NOT EXISTS uk_monitoring_pairs_unique 
    ON pairs (ticker_a, ticker_b, timeframe, period, type) 
    WHERE is_in_monitoring = TRUE;

-- ========================================
-- Insert default settings
-- ========================================

-- Insert default screener settings if none exist
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
    run_on_schedule,
    created_at,
    updated_at
)
SELECT 
    'Настройки по умолчанию',
    TRUE,
    '1D',
    'месяц',
    TRUE,
    0.1,
    TRUE,
    100.0,
    TRUE,
    0.1,
    TRUE,
    0.1,
    TRUE,
    0.1,
    FALSE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM stable_pairs_screener_settings WHERE is_default = TRUE
);

-- ========================================
-- Add comments
-- ========================================

COMMENT ON TABLE stable_pairs_screener_settings IS 
    'Настройки скриннера стабильных пар для сохранения пользовательских конфигураций';

COMMENT ON COLUMN stable_pairs_screener_settings.name IS 
    'Пользовательское название настроек';

COMMENT ON COLUMN stable_pairs_screener_settings.is_default IS 
    'Флаг настроек по умолчанию (должна быть только одна)';

COMMENT ON COLUMN stable_pairs_screener_settings.selected_timeframes IS 
    'Выбранные таймфреймы через запятую (например: "1m,5m,1H,1D")';

COMMENT ON COLUMN stable_pairs_screener_settings.selected_periods IS 
    'Выбранные периоды через запятую (например: "день,месяц,6 месяцев,1 год")';

COMMENT ON COLUMN stable_pairs_screener_settings.run_on_schedule IS 
    'Флаг для автоматического запуска по расписанию каждую ночь в 2:00';