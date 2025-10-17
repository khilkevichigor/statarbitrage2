-- Создание таблицы для хранения исторических данных портфолио
CREATE TABLE IF NOT EXISTS portfolio_history
(
    id                     BIGSERIAL PRIMARY KEY,
    snapshot_time          TIMESTAMP NOT NULL,
    total_balance          DECIMAL(20, 8),
    available_balance      DECIMAL(20, 8),
    reserved_balance       DECIMAL(20, 8),
    unrealized_pnl         DECIMAL(20, 8),
    realized_pnl           DECIMAL(20, 8),
    active_positions_count INTEGER,
    provider_type          VARCHAR(50),
    created_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Создание индексов для быстрого поиска по времени
CREATE INDEX IF NOT EXISTS idx_portfolio_history_snapshot_time ON portfolio_history (snapshot_time);
CREATE INDEX IF NOT EXISTS idx_portfolio_history_provider_type ON portfolio_history (provider_type);

-- Комментарии к таблице и полям
COMMENT
ON TABLE portfolio_history IS 'Историческая информация о состоянии портфолио';
COMMENT
ON COLUMN portfolio_history.snapshot_time IS 'Время снимка портфолио';
COMMENT
ON COLUMN portfolio_history.total_balance IS 'Общий баланс портфолио в USDT';
COMMENT
ON COLUMN portfolio_history.available_balance IS 'Доступный баланс в USDT';
COMMENT
ON COLUMN portfolio_history.reserved_balance IS 'Зарезервированный баланс в USDT';
COMMENT
ON COLUMN portfolio_history.unrealized_pnl IS 'Нереализованная прибыль/убыток в USDT';
COMMENT
ON COLUMN portfolio_history.realized_pnl IS 'Реализованная прибыль/убыток в USDT';
COMMENT
ON COLUMN portfolio_history.active_positions_count IS 'Количество активных позиций';
COMMENT
ON COLUMN portfolio_history.provider_type IS 'Тип торгового провайдера (REAL/SIMULATION)';