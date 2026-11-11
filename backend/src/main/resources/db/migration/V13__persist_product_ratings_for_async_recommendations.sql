ALTER TABLE product_candidates
    ADD COLUMN rating_value NUMERIC(3, 2),
    ADD COLUMN rating_count BIGINT,
    ADD COLUMN rating_source_count INTEGER;
