-- Добавление поля для использования стабильных пар из постоянного списка мониторинга

-- Добавление нового поля в settings
ALTER TABLE settings
    ADD COLUMN IF NOT EXISTS use_stable_pairs_for_monitoring BOOLEAN DEFAULT FALSE;