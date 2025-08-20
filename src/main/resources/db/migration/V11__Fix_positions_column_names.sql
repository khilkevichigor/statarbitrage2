-- Fix positions table columns to match Position entity @Column annotations

-- Fix PnL column names to match entity annotations
ALTER TABLE positions RENAME COLUMN unrealized_pnlusdt TO unrealized_pnl_usdt;
ALTER TABLE positions RENAME COLUMN unrealized_pnlpercent TO unrealized_pnl_percent;
ALTER TABLE positions RENAME COLUMN realized_pnlusdt TO realized_pnl_usdt;
ALTER TABLE positions RENAME COLUMN realized_pnlpercent TO realized_pnl_percent;