-- Fix column naming in settings table to match Hibernate's actual naming conversion
-- exitNegativeZMinProfitPercent -> exit_negativezmin_profit_percent (not exit_negative_z_min_profit_percent)
-- and other similar naming issues

-- Rename columns to match Hibernate's actual naming strategy
ALTER TABLE settings RENAME COLUMN exit_negative_z_min_profit_percent TO exit_negativezmin_profit_percent;
ALTER TABLE settings RENAME COLUMN use_exit_negative_z_min_profit_percent TO use_exit_negativezmin_profit_percent;
ALTER TABLE settings RENAME COLUMN max_p_value TO maxpvalue;
ALTER TABLE settings RENAME COLUMN max_adf_value TO max_adf_value; -- This one might be correct already
ALTER TABLE settings RENAME COLUMN min_r_squared TO minrsquared;
ALTER TABLE settings RENAME COLUMN min_z TO minz;
ALTER TABLE settings RENAME COLUMN use_min_z_filter TO use_minzfilter;
ALTER TABLE settings RENAME COLUMN use_min_r_squared_filter TO use_minrsquared_filter;
ALTER TABLE settings RENAME COLUMN use_max_p_value_filter TO use_maxpvalue_filter;
ALTER TABLE settings RENAME COLUMN exit_z_max TO exitzmax;
ALTER TABLE settings RENAME COLUMN exit_z_max_percent TO exitzmax_percent;
ALTER TABLE settings RENAME COLUMN exit_z_min TO exitzmin;
ALTER TABLE settings RENAME COLUMN use_exit_z_max TO use_exitzmax;
ALTER TABLE settings RENAME COLUMN use_exit_z_max_percent TO use_exitzmax_percent;
ALTER TABLE settings RENAME COLUMN use_exit_z_min TO use_exitzmin;
ALTER TABLE settings RENAME COLUMN use_z_score_scoring TO usezscore_scoring;