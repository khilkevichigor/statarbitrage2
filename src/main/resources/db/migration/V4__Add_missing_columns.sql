-- Add missing columns to pair_data table to match PairData JPA entity

-- Add missing fields from PairData.java
ALTER TABLE pair_data
    ADD COLUMN z_score_changes DECIMAL;
ALTER TABLE pair_data
    ADD COLUMN long_usdt_changes DECIMAL;
ALTER TABLE pair_data
    ADD COLUMN long_percent_changes DECIMAL;
ALTER TABLE pair_data
    ADD COLUMN short_usdt_changes DECIMAL;
ALTER TABLE pair_data
    ADD COLUMN short_percent_changes DECIMAL;
ALTER TABLE pair_data
    ADD COLUMN portfolio_before_trade_usdt DECIMAL;
ALTER TABLE pair_data
    ADD COLUMN profit_usdt_changes DECIMAL;
ALTER TABLE pair_data
    ADD COLUMN portfolio_after_trade_usdt DECIMAL;
ALTER TABLE pair_data
    ADD COLUMN minutes_to_min_profit_percent INTEGER;
ALTER TABLE pair_data
    ADD COLUMN minutes_to_max_profit_percent INTEGER;
ALTER TABLE pair_data
    ADD COLUMN min_profit_percent_changes DECIMAL;
ALTER TABLE pair_data
    ADD COLUMN max_profit_percent_changes DECIMAL;
ALTER TABLE pair_data
    ADD COLUMN formatted_time_to_min_profit TEXT;
ALTER TABLE pair_data
    ADD COLUMN formatted_time_to_max_profit TEXT;
ALTER TABLE pair_data
    ADD COLUMN formatted_profit_long TEXT;
ALTER TABLE pair_data
    ADD COLUMN formatted_profit_short TEXT;
ALTER TABLE pair_data
    ADD COLUMN formatted_profit_common TEXT;
ALTER TABLE pair_data
    ADD COLUMN max_z DECIMAL;
ALTER TABLE pair_data
    ADD COLUMN min_z DECIMAL;
ALTER TABLE pair_data
    ADD COLUMN max_long DECIMAL;
ALTER TABLE pair_data
    ADD COLUMN min_long DECIMAL;
ALTER TABLE pair_data
    ADD COLUMN max_short DECIMAL;
ALTER TABLE pair_data
    ADD COLUMN min_short DECIMAL;
ALTER TABLE pair_data
    ADD COLUMN max_corr DECIMAL;
ALTER TABLE pair_data
    ADD COLUMN min_corr DECIMAL;
ALTER TABLE pair_data
    ADD COLUMN exit_reason TEXT;
ALTER TABLE pair_data
    ADD COLUMN close_at_breakeven BOOLEAN DEFAULT FALSE;