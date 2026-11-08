package com.spendwise.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class Channel3UserRateLimiterTest {

    @Test
    void allowsOneHundredRequestsPerMinutePerUserAndRejectsTheNext() {
        AtomicLong clock = new AtomicLong(1L);
        Channel3UserRateLimiter limiter = new Channel3UserRateLimiter(clock::get);

        for (int request = 0; request < 100; request++) {
            limiter.acquire("alice");
        }
        limiter.acquire("bob");

        assertThatThrownBy(() -> limiter.acquire("alice"))
                .isInstanceOf(Channel3UserRateLimiter.Channel3RateLimitExceededException.class)
                .satisfies(error -> {
                    var rateLimit = (Channel3UserRateLimiter.Channel3RateLimitExceededException) error;
                    assertThat(rateLimit.getRetryAfterSeconds()).isEqualTo(60);
                    assertThat(rateLimit.getStatus().value()).isEqualTo(429);
                    assertThat(rateLimit.getCode()).isEqualTo("CHANNEL3_RATE_LIMIT_EXCEEDED");
                });
    }

    @Test
    void allowsRequestsAgainAfterTheSlidingWindowExpires() {
        AtomicLong clock = new AtomicLong(1L);
        Channel3UserRateLimiter limiter = new Channel3UserRateLimiter(clock::get);
        for (int request = 0; request < 100; request++) {
            limiter.acquire("alice");
        }

        clock.addAndGet(Duration.ofMinutes(1).toNanos());

        limiter.acquire("alice");
    }
}
