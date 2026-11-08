CREATE TABLE user_accounts (
    id VARCHAR(120) PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    username VARCHAR(32) UNIQUE,
    password_hash VARCHAR(200),
    display_name VARCHAR(120),
    picture_url TEXT,
    google_subject VARCHAR(120) UNIQUE,
    provider VARCHAR(16) NOT NULL CHECK (provider IN ('PASSWORD', 'GOOGLE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (
        (provider = 'PASSWORD' AND username IS NOT NULL AND password_hash IS NOT NULL AND google_subject IS NULL)
        OR
        (provider = 'GOOGLE' AND username IS NULL AND password_hash IS NULL AND google_subject = id)
    )
);
