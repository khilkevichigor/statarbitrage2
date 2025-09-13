-- Миграция данных из stable_pairs в унифицированную таблицу pairs
-- Переносим все данные из stable_pairs как тип STABLE

INSERT INTO pairs (
    -- Основные поля
    uuid,
    type,
    status,
    
    -- Базовая информация о паре
    ticker_a,
    ticker_b,
    pair_name,
    
    -- Поля из StablePair
    total_score,
    stability_rating,
    is_tradeable,
    data_points,
    candle_count,
    analysis_time_seconds,
    timeframe,
    period,
    search_date,
    created_at,
    is_in_monitoring,
    search_settings,
    analysis_results
)
SELECT 
    -- Генерируем UUID для записей, где его нет (старые записи)
    COALESCE(gen_random_uuid(), gen_random_uuid()) as uuid,
    'STABLE' as type, -- Все записи из stable_pairs имеют тип STABLE
    'SELECTED' as status, -- По умолчанию статус SELECTED для стабильных пар
    
    -- Базовая информация о паре
    ticker_a,
    ticker_b,
    COALESCE(ticker_a || '/' || ticker_b) as pair_name, -- Генерируем pair_name
    
    -- Поля из StablePair - прямое копирование
    total_score,
    stability_rating,
    is_tradeable,
    data_points,
    candle_count,
    analysis_time_seconds,
    timeframe,
    period,
    search_date,
    created_at,
    COALESCE(is_in_monitoring, false) as is_in_monitoring, -- По умолчанию false
    search_settings,
    analysis_results

FROM stable_pairs
-- Исключаем записи с некорректными данными
WHERE ticker_a IS NOT NULL 
  AND ticker_b IS NOT NULL
  AND search_date IS NOT NULL
  AND created_at IS NOT NULL;

-- Проверяем результат миграции
DO $$
DECLARE
    stable_count INTEGER;
    pairs_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO stable_count FROM stable_pairs;
    SELECT COUNT(*) INTO pairs_count FROM pairs WHERE type = 'STABLE';
    
    RAISE NOTICE 'Миграция StablePair завершена:';
    RAISE NOTICE '- Записей в stable_pairs: %', stable_count;
    RAISE NOTICE '- Записей в pairs (STABLE): %', pairs_count;
    
    IF pairs_count != stable_count THEN
        RAISE WARNING 'Количество записей не совпадает! Проверьте данные.';
    END IF;
END $$;