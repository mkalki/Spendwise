package com.spendwise.common.config;

import java.util.function.Supplier;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

public final class TransientHttpRetry {
    private static final int MAX_ATTEMPTS = 2;
    private static final long BACKOFF_MILLIS = 150;

    private TransientHttpRetry() {}

    public static <T> T execute(Supplier<T> operation, boolean retryNetworkFailure) {
        for (int attempt = 1; ; attempt++) {
            try {
                return operation.get();
            } catch (ResourceAccessException exception) {
                if (attempt >= MAX_ATTEMPTS || !retryNetworkFailure) throw exception;
                pause(exception);
            } catch (RestClientResponseException exception) {
                int status = exception.getStatusCode().value();
                boolean transientStatus = status == 429 || status == 500 || status == 502
                        || status == 503 || status == 504;
                if (attempt >= MAX_ATTEMPTS || !transientStatus) throw exception;
                pause(exception);
            }
        }
    }

    private static void pause(RuntimeException original) {
        try {
            Thread.sleep(BACKOFF_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            original.addSuppressed(exception);
            throw original;
        }
    }
}
