-- Fix pair_data table columns to match PairData entity @Column annotations

-- Fix settings_exit_break_even_percent to match entity annotation
ALTER TABLE pair_data RENAME COLUMN settings_exit_break_even_percent TO settings_exit_breakeven_percent;