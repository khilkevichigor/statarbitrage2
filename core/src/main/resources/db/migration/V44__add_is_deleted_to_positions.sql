-- Добавляем поле is_deleted для soft delete в таблицу positions
ALTER TABLE positions ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false;

-- Создаем индекс для оптимизации запросов с фильтрацией по is_deleted
CREATE INDEX idx_positions_is_deleted ON positions(is_deleted);

-- Комментарий для поля
COMMENT ON COLUMN positions.is_deleted IS 'Флаг мягкого удаления позиции для предотвращения случайной потери данных';