-- Fix column naming in positions table to match Hibernate naming strategy
-- Hibernate converts camelCase to snake_case (realizedPnLUSDT -> realized_pnlusdt)

-- Drop and recreate positions table with correct column names
DROP TABLE positions;

CREATE TABLE positions
(
    id                    BIGINT PRIMARY KEY,
    position_id           TEXT UNIQUE,
    pair_data_id          INTEGER,
    symbol                TEXT,
    type                  TEXT, -- LONG, SHORT (enum PositionType)
    size                  DECIMAL,
    entry_price           DECIMAL,
    closing_price         DECIMAL,
    current_price         DECIMAL,
    leverage              DECIMAL,
    allocated_amount      DECIMAL,
    unrealized_pnlusdt    DECIMAL,
    unrealized_pnlpercent DECIMAL,
    realized_pnlusdt      DECIMAL,
    realized_pnlpercent   DECIMAL,
    opening_fees          DECIMAL,
    funding_fees          DECIMAL,
    closing_fees          DECIMAL,
    status                TEXT, -- PENDING, OPEN, CLOSING, CLOSED, FAILED (enum PositionStatus)
    open_time             TIMESTAMP,
    last_updated          TIMESTAMP,
    metadata              TEXT,
    external_order_id     TEXT
);

-- Create indexes for better performance
CREATE INDEX idx_positions_position_id ON positions (position_id);
CREATE INDEX idx_positions_pair_data_id ON positions (pair_data_id);
CREATE INDEX idx_positions_status ON positions (status);
CREATE INDEX idx_positions_symbol ON positions (symbol);