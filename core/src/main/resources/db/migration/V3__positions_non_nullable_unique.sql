ALTER TABLE positions
    ALTER COLUMN position_id SET NOT NULL;

ALTER TABLE positions
    ADD CONSTRAINT uk_positions_position_id UNIQUE (position_id);