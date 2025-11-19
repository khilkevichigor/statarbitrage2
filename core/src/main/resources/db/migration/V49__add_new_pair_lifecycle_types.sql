-- Миграция для добавления новых типов пар для жизненного цикла торговли
-- Обновляем CHECK констрейнт для поддержки новых типов

-- Обновляем CHECK констрейнт для поля type, добавляя новые значения
ALTER TABLE pairs DROP CONSTRAINT IF EXISTS pairs_type_check;
ALTER TABLE pairs ADD CONSTRAINT pairs_type_check 
    CHECK (type IN ('STABLE', 'COINTEGRATED', 'TRADING', 'FETCHED', 'IN_TRADING', 'COMPLETED'));

-- Логируем изменения
DO $$
BEGIN
    RAISE NOTICE 'Обновлен CHECK констрейнт для поля type, добавлены новые типы: FETCHED, IN_TRADING, COMPLETED';
END $$;

-- Создаем индексы для быстрого поиска по новым типам пар
-- Примечание: CONCURRENTLY нельзя использовать в транзакции, поэтому используем обычные индексы
CREATE INDEX IF NOT EXISTS idx_pairs_type_fetched 
ON pairs(type) 
WHERE type = 'FETCHED';

CREATE INDEX IF NOT EXISTS idx_pairs_type_in_trading 
ON pairs(type) 
WHERE type = 'IN_TRADING';

CREATE INDEX IF NOT EXISTS idx_pairs_type_completed 
ON pairs(type) 
WHERE type = 'COMPLETED';

-- Обновляем комментарий к колонке type для документирования
COMMENT ON COLUMN pairs.type IS 
'Тип пары в жизненном цикле торговли:
STABLE - стабильная пара (результат скрининга)
COINTEGRATED - коинтегрированная пара (прошла анализ)  
FETCHED - найденная для торговли (получила положительный Z-Score, позиции не открыты)
IN_TRADING - активная торговля (открыты позиции)
COMPLETED - завершенная торговля (позиции закрыты)
TRADING - устаревший тип, постепенно заменяется на IN_TRADING';

-- Логируем текущее состояние типов пар для мониторинга
DO $$
DECLARE
    stable_count INTEGER;
    cointegrated_count INTEGER;
    trading_count INTEGER;
    total_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO stable_count FROM pairs WHERE type = 'STABLE';
    SELECT COUNT(*) INTO cointegrated_count FROM pairs WHERE type = 'COINTEGRATED';
    SELECT COUNT(*) INTO trading_count FROM pairs WHERE type = 'TRADING';
    SELECT COUNT(*) INTO total_count FROM pairs;
    
    RAISE NOTICE 'Текущее состояние типов пар:';
    RAISE NOTICE '  STABLE: % пар', stable_count;
    RAISE NOTICE '  COINTEGRATED: % пар', cointegrated_count;
    RAISE NOTICE '  TRADING: % пар', trading_count;
    RAISE NOTICE '  Всего пар: %', total_count;
    RAISE NOTICE 'Новые типы FETCHED, IN_TRADING, COMPLETED добавлены и готовы к использованию';
END $$;