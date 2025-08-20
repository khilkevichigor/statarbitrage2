-- Fix settings table to exactly match Settings entity @Column annotations
-- Revert V8 changes that don't match the actual Entity

-- Revert incorrect V8 changes to match Entity annotations
ALTER TABLE settings RENAME COLUMN maxpvalue TO max_p_value;
ALTER TABLE settings RENAME COLUMN minrsquared TO min_r_squared;  
ALTER TABLE settings RENAME COLUMN minz TO min_z;
ALTER TABLE settings RENAME COLUMN use_minzfilter TO use_min_z_filter;
ALTER TABLE settings RENAME COLUMN use_minrsquared_filter TO use_min_r_squared_filter;
ALTER TABLE settings RENAME COLUMN use_maxpvalue_filter TO use_max_p_value_filter;
ALTER TABLE settings RENAME COLUMN exitzmax TO exit_z_max;
ALTER TABLE settings RENAME COLUMN exitzmax_percent TO exit_z_max_percent;
ALTER TABLE settings RENAME COLUMN exitzmin TO exit_z_min;
ALTER TABLE settings RENAME COLUMN use_exitzmax TO use_exit_z_max;
ALTER TABLE settings RENAME COLUMN use_exitzmax_percent TO use_exit_z_max_percent;
ALTER TABLE settings RENAME COLUMN use_exitzmin TO use_exit_z_min;
ALTER TABLE settings RENAME COLUMN usezscore_scoring TO use_z_score_scoring;
ALTER TABLE settings RENAME COLUMN exit_negativezmin_profit_percent TO exit_negative_z_min_profit_percent;
ALTER TABLE settings RENAME COLUMN use_exit_negativezmin_profit_percent TO use_exit_negative_z_min_profit_percent;