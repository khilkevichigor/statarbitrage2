-- Добавление полей для управления шедуллерами

-- === ШЕДУЛЛЕРЫ CORE МИКРОСЕРВИСА ===

-- UpdateTradesScheduler (обновление торговых пар каждую минуту)
ALTER TABLE settings
    ADD COLUMN IF NOT EXISTS scheduler_update_trades_enabled BOOLEAN DEFAULT TRUE;

-- StablePairsScheduler (поиск стабильных пар ночью в 02:10)
ALTER TABLE settings
    ADD COLUMN IF NOT EXISTS scheduler_stable_pairs_enabled BOOLEAN DEFAULT TRUE;

ALTER TABLE settings
    ADD COLUMN IF NOT EXISTS scheduler_stable_pairs_cron VARCHAR(50) DEFAULT '0 10 1 * * *';

-- PortfolioHistoryService снапшот (каждые 15 минут)
ALTER TABLE settings
    ADD COLUMN IF NOT EXISTS scheduler_portfolio_snapshot_enabled BOOLEAN DEFAULT TRUE;

-- PortfolioHistoryService очистка (каждый день в 2:00)
ALTER TABLE settings
    ADD COLUMN IF NOT EXISTS scheduler_portfolio_cleanup_enabled BOOLEAN DEFAULT TRUE;

ALTER TABLE settings
    ADD COLUMN IF NOT EXISTS scheduler_portfolio_cleanup_cron VARCHAR(50) DEFAULT '0 0 2 * * ?';

-- === ШЕДУЛЛЕРЫ CANDLES МИКРОСЕРВИСА (для справки) ===
-- Эти поля пока добавляем для будущего использования
-- Управление через REST API или отдельный сервис

-- CandleCacheScheduler ночная синхронизация (3:00)
ALTER TABLE settings
    ADD COLUMN IF NOT EXISTS scheduler_candle_cache_sync_enabled BOOLEAN DEFAULT TRUE;

ALTER TABLE settings
    ADD COLUMN IF NOT EXISTS scheduler_candle_cache_sync_cron VARCHAR(50) DEFAULT '0 0 3 * * ?';

-- CandleCacheScheduler обновление свечей (каждые 4 часа)
ALTER TABLE settings
    ADD COLUMN IF NOT EXISTS scheduler_candle_cache_update_enabled BOOLEAN DEFAULT TRUE;

-- CandleCacheScheduler статистика (каждый час)
ALTER TABLE settings
    ADD COLUMN IF NOT EXISTS scheduler_candle_cache_stats_enabled BOOLEAN DEFAULT TRUE;

ALTER TABLE settings
    ADD COLUMN IF NOT EXISTS scheduler_candle_cache_stats_cron VARCHAR(50) DEFAULT '0 0 * * * ?';

-- Обновляем существующие записи, устанавливая значения по умолчанию
UPDATE settings 
SET 
    scheduler_update_trades_enabled = COALESCE(scheduler_update_trades_enabled, TRUE),
    scheduler_stable_pairs_enabled = COALESCE(scheduler_stable_pairs_enabled, TRUE),
    scheduler_stable_pairs_cron = COALESCE(scheduler_stable_pairs_cron, '0 10 1 * * *'),
    scheduler_portfolio_snapshot_enabled = COALESCE(scheduler_portfolio_snapshot_enabled, TRUE),
    scheduler_portfolio_cleanup_enabled = COALESCE(scheduler_portfolio_cleanup_enabled, TRUE),
    scheduler_portfolio_cleanup_cron = COALESCE(scheduler_portfolio_cleanup_cron, '0 0 2 * * ?'),
    scheduler_candle_cache_sync_enabled = COALESCE(scheduler_candle_cache_sync_enabled, TRUE),
    scheduler_candle_cache_sync_cron = COALESCE(scheduler_candle_cache_sync_cron, '0 0 3 * * ?'),
    scheduler_candle_cache_update_enabled = COALESCE(scheduler_candle_cache_update_enabled, TRUE),
    scheduler_candle_cache_stats_enabled = COALESCE(scheduler_candle_cache_stats_enabled, TRUE),
    scheduler_candle_cache_stats_cron = COALESCE(scheduler_candle_cache_stats_cron, '0 0 * * * ?')
WHERE id IS NOT NULL;