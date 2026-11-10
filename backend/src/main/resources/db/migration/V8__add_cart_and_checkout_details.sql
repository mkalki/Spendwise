CREATE TABLE shopping_cart_items (
    owner_id VARCHAR(120) NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    candidate_id UUID NOT NULL REFERENCES product_candidates(id) ON DELETE CASCADE,
    added_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (owner_id, candidate_id)
);

CREATE TABLE shopping_checkout_details (
    owner_id VARCHAR(120) PRIMARY KEY REFERENCES user_accounts(id) ON DELETE CASCADE,
    details JSONB NOT NULL
);
