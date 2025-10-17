-- Исправление NULL значений в boolean полях

-- Обновляем NULL значения в is_tradeable на false
UPDATE pairs SET is_tradeable = false WHERE is_tradeable IS NULL;

-- Обновляем NULL значения в is_in_monitoring на false
UPDATE pairs SET is_in_monitoring = false WHERE is_in_monitoring IS NULL;

-- Обновляем NULL значения в close_at_breakeven на false  
UPDATE pairs SET close_at_breakeven = false WHERE close_at_breakeven IS NULL;

-- Делаем поля NOT NULL для предотвращения проблем в будущем
ALTER TABLE pairs ALTER COLUMN is_tradeable SET NOT NULL;
ALTER TABLE pairs ALTER COLUMN is_in_monitoring SET NOT NULL;
ALTER TABLE pairs ALTER COLUMN close_at_breakeven SET NOT NULL;

-- Устанавливаем значения по умолчанию
ALTER TABLE pairs ALTER COLUMN is_tradeable SET DEFAULT false;
ALTER TABLE pairs ALTER COLUMN is_in_monitoring SET DEFAULT false;
ALTER TABLE pairs ALTER COLUMN close_at_breakeven SET DEFAULT false;