-- Create positions table for trading positions management

CREATE TABLE positions
(
    id                     BIGINT PRIMARY KEY,
    position_id            TEXT UNIQUE,
    pair_data_id           INTEGER,
    symbol                 TEXT,
    type                   TEXT, -- LONG, SHORT (enum PositionType)
    size                   DECIMAL,
    entry_price            DECIMAL,
    closing_price          DECIMAL,
    current_price          DECIMAL,
    leverage               DECIMAL,
    allocated_amount       DECIMAL,
    unrealized_pn_lusdt    DECIMAL,
    unrealized_pn_lpercent DECIMAL,
    realized_pn_lusdt      DECIMAL,
    realized_pn_lpercent   DECIMAL,
    opening_fees           DECIMAL,
    funding_fees           DECIMAL,
    closing_fees           DECIMAL,
    status                 TEXT, -- PENDING, OPEN, CLOSING, CLOSED, FAILED (enum PositionStatus)
    open_time              TIMESTAMP,
    last_updated           TIMESTAMP,
    metadata               TEXT,
    external_order_id      TEXT
);

-- Create indexes for better performance
CREATE INDEX idx_positions_position_id ON positions (position_id);
CREATE INDEX idx_positions_pair_data_id ON positions (pair_data_id);
CREATE INDEX idx_positions_status ON positions (status);
CREATE INDEX idx_positions_symbol ON positions (symbol);