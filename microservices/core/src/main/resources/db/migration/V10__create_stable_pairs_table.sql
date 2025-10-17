-- Создание таблицы для хранения стабильных пар для скриннера
CREATE TABLE stable_pairs (
    id BIGSERIAL PRIMARY KEY,
    ticker_a VARCHAR(20) NOT NULL,
    ticker_b VARCHAR(20) NOT NULL,
    total_score INTEGER,
    stability_rating VARCHAR(20),
    is_tradeable BOOLEAN,
    data_points INTEGER,
    analysis_time_seconds DOUBLE PRECISION,
    timeframe VARCHAR(10),
    period VARCHAR(20),
    search_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    search_settings TEXT,
    analysis_results TEXT,
    is_in_monitoring BOOLEAN DEFAULT FALSE NOT NULL
);

-- Создаем индексы для оптимизации поиска
CREATE INDEX idx_stable_pairs_search_date ON stable_pairs(search_date);
CREATE INDEX idx_stable_pairs_tickers ON stable_pairs(ticker_a, ticker_b);
CREATE INDEX idx_stable_pairs_stability_rating ON stable_pairs(stability_rating);
CREATE INDEX idx_stable_pairs_monitoring ON stable_pairs(is_in_monitoring);
CREATE INDEX idx_stable_pairs_timeframe ON stable_pairs(timeframe);
CREATE INDEX idx_stable_pairs_period ON stable_pairs(period);