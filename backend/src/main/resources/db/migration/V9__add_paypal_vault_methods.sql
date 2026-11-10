CREATE TABLE paypal_payment_methods (
    id UUID PRIMARY KEY,
    owner_id VARCHAR(120) NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    setup_token_id VARCHAR(100) UNIQUE,
    vault_token_encrypted TEXT,
    paypal_customer_id VARCHAR(100),
    paypal_email VARCHAR(320),
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    consented_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    consent_version VARCHAR(32) NOT NULL DEFAULT 'saved-paypal-v1',
    CONSTRAINT paypal_payment_method_status_check
        CHECK (status IN ('SETUP_PENDING', 'SAVED', 'REMOVED', 'FAILED')),
    CONSTRAINT paypal_payment_method_saved_token_check
        CHECK ((status = 'SAVED' AND vault_token_encrypted IS NOT NULL)
            OR status <> 'SAVED')
);

CREATE INDEX paypal_payment_methods_owner_status_idx
    ON paypal_payment_methods(owner_id, status, created_at DESC);
