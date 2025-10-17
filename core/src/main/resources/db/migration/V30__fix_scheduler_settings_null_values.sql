-- Исправление NULL значений в полях управления шедуллерами
-- Необходимо для существующих записей в таблице settings

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