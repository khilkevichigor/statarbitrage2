-- Миграция для конвертации поля stability_rating из varchar в enum
-- и обновления существующих данных для работы с StabilityRating enum

-- Комментарий: Конвертируем строковые значения stability_rating в правильный формат enum
-- Поддерживаемые значения: EXCELLENT, GOOD, MARGINAL, POOR, REJECTED, FAILED

-- Обновляем любые нестандартные значения в поле stability_rating на FAILED
UPDATE pairs 
SET stability_rating = 'FAILED' 
WHERE stability_rating IS NOT NULL 
  AND stability_rating NOT IN ('EXCELLENT', 'GOOD', 'MARGINAL', 'POOR', 'REJECTED', 'FAILED');

-- Обновляем null значения на FAILED как дефолтное значение
UPDATE pairs 
SET stability_rating = 'FAILED' 
WHERE stability_rating IS NULL 
  AND type = 'STABLE';

-- Добавляем индекс для улучшения производительности запросов по stability_rating
CREATE INDEX IF NOT EXISTS idx_pairs_stability_rating ON pairs(stability_rating) 
WHERE type = 'STABLE';

-- Добавляем комментарий к столбцу для документирования
COMMENT ON COLUMN pairs.stability_rating IS 'Рейтинг стабильности пары: EXCELLENT, GOOD, MARGINAL, POOR, REJECTED, FAILED';