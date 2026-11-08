CREATE TABLE shopping_preferences (
    owner_id VARCHAR(120) PRIMARY KEY,
    preferred_categories TEXT NOT NULL DEFAULT '[]',
    preferred_brands TEXT NOT NULL DEFAULT '[]',
    preferred_retailers TEXT NOT NULL DEFAULT '[]',
    keywords TEXT NOT NULL DEFAULT '[]',
    max_price NUMERIC(12, 2),
    currency_code VARCHAR(3),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

