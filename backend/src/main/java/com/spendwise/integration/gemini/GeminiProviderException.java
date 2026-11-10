package com.spendwise.integration.gemini;

public class GeminiProviderException extends RuntimeException {
    private final boolean invalidApiKey;

    public GeminiProviderException() {
        this(false);
    }

    public GeminiProviderException(boolean invalidApiKey) {
        this.invalidApiKey = invalidApiKey;
    }

    public boolean isInvalidApiKey() {
        return invalidApiKey;
    }
}
