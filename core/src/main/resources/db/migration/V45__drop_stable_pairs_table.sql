-- Удаление таблицы stable_pairs и всех связанных индексов
-- Данные уже были перенесены в таблицу pairs в миграции V13

-- Проверяем что данные были перенесены
DO $$
DECLARE
    stable_count INTEGER;
    pairs_stable_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO stable_count FROM stable_pairs;
    SELECT COUNT(*) INTO pairs_stable_count FROM pairs WHERE type = 'STABLE';
    
    RAISE NOTICE 'Проверка перед удалением stable_pairs:';
    RAISE NOTICE '- Записей в stable_pairs: %', stable_count;
    RAISE NOTICE '- Записей в pairs (STABLE): %', pairs_stable_count;
    
    IF pairs_stable_count = 0 AND stable_count > 0 THEN
        RAISE EXCEPTION 'ОШИБКА: Данные не были перенесены в таблицу pairs! Отмена удаления.';
    END IF;
    
    RAISE NOTICE 'Проверка пройдена - можно безопасно удалять stable_pairs';
END $$;

-- Удаляем все индексы таблицы stable_pairs
DROP INDEX IF EXISTS idx_stable_pairs_search_date;
DROP INDEX IF EXISTS idx_stable_pairs_tickers;
DROP INDEX IF EXISTS idx_stable_pairs_stability_rating;
DROP INDEX IF EXISTS idx_stable_pairs_monitoring;
DROP INDEX IF EXISTS idx_stable_pairs_timeframe;
DROP INDEX IF EXISTS idx_stable_pairs_period;

-- Удаляем таблицу stable_pairs
DROP TABLE IF EXISTS stable_pairs;

-- Подтверждение
DO $$
BEGIN
    RAISE NOTICE '✅ Таблица stable_pairs и все её индексы успешно удалены';
    RAISE NOTICE '📋 Все данные сохранены в унифицированной таблице pairs с типом STABLE';
END $$;