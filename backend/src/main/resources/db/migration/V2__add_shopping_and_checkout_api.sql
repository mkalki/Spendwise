ALTER TABLE spending_policies
    ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'UTC';

CREATE TABLE shopping_search_sessions (
    id UUID PRIMARY KEY,
    owner_id VARCHAR(120) NOT NULL,
    original_query VARCHAR(1000) NOT NULL,
    normalized_query VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX shopping_search_sessions_owner_created_idx
    ON shopping_search_sessions (owner_id, created_at DESC);

CREATE TABLE product_candidates (
    id UUID PRIMARY KEY,
    search_session_id UUID NOT NULL REFERENCES shopping_search_sessions(id) ON DELETE CASCADE,
    channel3_product_id VARCHAR(200) NOT NULL,
    product_title VARCHAR(500) NOT NULL,
    brand VARCHAR(200),
    image_url TEXT,
    merchant_name VARCHAR(200),
    offer_url TEXT,
    listed_price NUMERIC(12, 2),
    currency CHAR(3),
    eligible BOOLEAN NOT NULL,
    rejection_reason VARCHAR(500),
    retrieved_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX product_candidates_search_session_idx
    ON product_candidates (search_session_id);

ALTER TABLE payment_transactions
    DROP CONSTRAINT payment_transactions_status_check;

ALTER TABLE payment_transactions
    ALTER COLUMN status TYPE VARCHAR(30),
    ADD COLUMN candidate_id UUID REFERENCES product_candidates(id),
    ADD COLUMN paypal_approval_url TEXT,
    ADD COLUMN reservation_expires_at TIMESTAMPTZ;

ALTER TABLE payment_transactions
    ADD CONSTRAINT payment_transactions_status_check
        CHECK (status IN (
            'CREATED',
            'APPROVED',
            'CAPTURING',
            'COMPLETED',
            'CANCELLED',
            'EXPIRED',
            'FAILED',
            'REQUIRES_RECONCILIATION'
        ));

CREATE INDEX payment_transactions_owner_status_idx
    ON payment_transactions (owner_id, status, created_at DESC);

CREATE INDEX payment_transactions_reservation_idx
    ON payment_transactions (owner_id, reservation_expires_at)
    WHERE status IN ('CREATED', 'APPROVED', 'CAPTURING');
