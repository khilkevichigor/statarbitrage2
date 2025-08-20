-- Add missing settings_use_min_p_value_filter column to pair_data table

ALTER TABLE pair_data ADD COLUMN settings_use_min_p_value_filter BOOLEAN;