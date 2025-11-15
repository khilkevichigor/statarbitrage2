-- V47: Обновление существующих записей settings с дефолтными значениями для полей BTC анализа
-- Автор: AI Assistant  
-- Дата: 2025-11-15

-- Обновляем существующие записи, где поля BTC имеют NULL значения
UPDATE settings 
SET 
    use_btc_volatility_filter = FALSE,
    btc_atr_threshold_multiplier = 1.3,
    btc_daily_range_multiplier = 1.3, 
    max_btc_daily_change_percent = 5.0
WHERE 
    use_btc_volatility_filter IS NULL 
    OR btc_atr_threshold_multiplier IS NULL
    OR btc_daily_range_multiplier IS NULL 
    OR max_btc_daily_change_percent IS NULL;