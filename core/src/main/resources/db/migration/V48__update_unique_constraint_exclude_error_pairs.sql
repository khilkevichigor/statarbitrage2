-- Migration V48: Update unique constraint to exclude ERROR pairs
-- Description: Create partial unique index that excludes pairs with ERROR status to prevent duplicates

-- ========================================
-- Drop old constraint and create partial index
-- ========================================

-- Сначала удаляем старый констрейнт (если существует)
ALTER TABLE pairs DROP CONSTRAINT IF EXISTS uk_stable_pairs_unique;

-- Создаем новый частичный уникальный индекс, который исключает пары со статусом ERROR
CREATE UNIQUE INDEX IF NOT EXISTS idx_pairs_unique_exclude_error 
ON pairs (ticker_a, ticker_b, timeframe, period, type) 
WHERE status != 'ERROR';

-- ========================================
-- Comments
-- ========================================

COMMENT ON INDEX idx_pairs_unique_exclude_error IS 
    'Уникальный индекс для пар, исключающий пары со статусом ERROR. Предотвращает дублирование активных пар.';