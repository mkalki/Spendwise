DROP TABLE email_verification_tokens;

ALTER TABLE user_accounts
    DROP CONSTRAINT user_accounts_check;

ALTER TABLE user_accounts
    ADD CONSTRAINT user_accounts_identity_check CHECK (
        (provider = 'PASSWORD' AND username IS NOT NULL AND password_hash IS NOT NULL AND google_subject IS NULL)
        OR
        (provider = 'GOOGLE' AND username IS NULL AND password_hash IS NULL AND google_subject IS NOT NULL)
    );
