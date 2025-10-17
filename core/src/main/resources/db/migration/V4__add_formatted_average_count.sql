ALTER TABLE trading_pair
    ADD COLUMN IF NOT EXISTS formatted_averaging_count TEXT;