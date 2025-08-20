-- Fix column naming to match Spring Boot's SpringPhysicalNamingStrategy
-- camelCase Java fields should map to snake_case database columns

-- Create new table with proper snake_case column names  
CREATE TABLE chart_settings_new
(
    id                  BIGINT PRIMARY KEY,
    chart_type          TEXT UNIQUE NOT NULL,
    show_z_score        BOOLEAN DEFAULT TRUE,
    show_combined_price BOOLEAN DEFAULT TRUE,
    show_pixel_spread   BOOLEAN DEFAULT FALSE,
    show_ema            BOOLEAN DEFAULT FALSE,
    show_stoch_rsi      BOOLEAN DEFAULT FALSE,
    show_profit         BOOLEAN DEFAULT FALSE,
    show_entry_point    BOOLEAN DEFAULT TRUE
);

-- Copy data from old table (V2 had snake_case with underscores)
INSERT INTO chart_settings_new (id, chart_type, show_z_score, show_combined_price, show_pixel_spread, show_ema, show_stoch_rsi, show_profit,
                                show_entry_point)
SELECT id,
       chart_type,
       show_z_score,
       show_combined_price,
       show_pixel_spread,
       show_ema,
       show_stoch_rsi,
       show_profit,
       show_entry_point
FROM chart_settings;

-- Drop old table
DROP TABLE chart_settings;

-- Rename new table to original name
ALTER TABLE chart_settings_new RENAME TO chart_settings;

-- Recreate index
CREATE INDEX idx_chart_settings_type ON chart_settings (chart_type);