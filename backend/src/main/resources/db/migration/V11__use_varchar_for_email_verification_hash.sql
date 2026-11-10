ALTER TABLE email_verification_tokens
    ALTER COLUMN token_hash TYPE VARCHAR(64);
