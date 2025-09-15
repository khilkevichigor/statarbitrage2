-- Исправление check constraint для поля status в таблице pairs
-- Приведение в соответствие с enum TradeStatus в Java коде

-- Удаление старого constraint
ALTER TABLE pairs DROP CONSTRAINT pairs_status_check;

-- Добавление нового constraint с корректными значениями из enum TradeStatus
ALTER TABLE pairs ADD CONSTRAINT pairs_status_check 
    CHECK (status IN ('SELECTED', 'TRADING', 'OBSERVED', 'CLOSED', 'ERROR'));

-- Обновление существующих записей со старыми статусами на новые
UPDATE pairs SET status = 'TRADING' WHERE status = 'IN_PROCESS';
UPDATE pairs SET status = 'CLOSED' WHERE status = 'SUCCESS';  
UPDATE pairs SET status = 'ERROR' WHERE status = 'FAILED';

COMMENT ON CONSTRAINT pairs_status_check ON pairs IS 'Допустимые статусы торговых пар согласно enum TradeStatus';