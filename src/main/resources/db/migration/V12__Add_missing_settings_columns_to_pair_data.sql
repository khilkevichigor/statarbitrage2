-- Add missing settings columns to pair_data table that exist in PairData entity

ALTER TABLE pair_data ADD COLUMN settings_min_p_value REAL;