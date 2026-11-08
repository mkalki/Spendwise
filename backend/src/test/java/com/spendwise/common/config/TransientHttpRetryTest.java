package com.spendwise.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

class TransientHttpRetryTest {
    @Test
    void retriesTransientHttpFailureOnce() {
        AtomicInteger attempts = new AtomicInteger();
        String result = TransientHttpRetry.execute(() -> {
            if (attempts.incrementAndGet() == 1) throw response(HttpStatus.SERVICE_UNAVAILABLE);
            return "ok";
        }, false);

        assertThat(result).isEqualTo("ok");
        assertThat(attempts).hasValue(2);
    }

    @Test
    void doesNotRetryPermanentHttpFailure() {
        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> TransientHttpRetry.execute(() -> {
            attempts.incrementAndGet();
            throw response(HttpStatus.BAD_REQUEST);
        }, true)).isInstanceOf(HttpServerErrorException.class);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void retriesNetworkFailureOnlyForIdempotentCalls() {
        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> TransientHttpRetry.execute(() -> {
            attempts.incrementAndGet();
            throw new ResourceAccessException("timeout");
        }, false)).isInstanceOf(ResourceAccessException.class);
        assertThat(attempts).hasValue(1);
    }

    private HttpServerErrorException response(HttpStatus status) {
        return HttpServerErrorException.create(status, status.getReasonPhrase(), HttpHeaders.EMPTY,
                new byte[0], StandardCharsets.UTF_8);
    }
}
