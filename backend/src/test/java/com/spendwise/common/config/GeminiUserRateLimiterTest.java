package com.spendwise.common.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class GeminiUserRateLimiterTest {
    @Test
    void enforcesPerUserWindowAndResetsAfterOneMinute() {
        AtomicLong now = new AtomicLong();
        GeminiUserRateLimiter limiter = new GeminiUserRateLimiter(now::get);
        for (int request = 0; request < GeminiUserRateLimiter.REQUESTS_PER_MINUTE; request++) {
            limiter.acquire("owner-a");
        }
        assertThatThrownBy(() -> limiter.acquire("owner-a"))
                .isInstanceOf(GeminiUserRateLimiter.GeminiRateLimitExceededException.class);
        limiter.acquire("owner-b");
        now.addAndGet(60_000_000_000L);
        limiter.acquire("owner-a");
    }
}
