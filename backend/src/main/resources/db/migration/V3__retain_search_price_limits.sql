ALTER TABLE shopping_search_sessions
    ADD COLUMN effective_max_price NUMERIC(12, 2);

UPDATE shopping_search_sessions search_session
SET effective_max_price = policy.max_per_product
FROM spending_policies policy
WHERE search_session.owner_id = policy.owner_id;

UPDATE shopping_search_sessions
SET effective_max_price = 0.01
WHERE effective_max_price IS NULL;

ALTER TABLE shopping_search_sessions
    ALTER COLUMN effective_max_price SET NOT NULL,
    ADD CONSTRAINT shopping_search_sessions_effective_max_price_check
        CHECK (effective_max_price > 0);
