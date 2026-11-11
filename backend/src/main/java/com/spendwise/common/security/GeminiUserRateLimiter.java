package com.spendwise.common.security;

import com.spendwise.common.exception.ApiException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.spendwise.payment.service.ProMembershipService;
import org.springframework.util.StringUtils;

@Component
public class GeminiUserRateLimiter {
    public static final int REQUESTS_PER_MINUTE = 20;
    private static final long WINDOW_NANOS = Duration.ofMinutes(1).toNanos();
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final LongSupplier nanoTime;
    private final AtomicLong calls = new AtomicLong();
    private final ProMembershipService memberships;

    public GeminiUserRateLimiter() { this(System::nanoTime, null); }

    @org.springframework.beans.factory.annotation.Autowired
    public GeminiUserRateLimiter(ProMembershipService memberships) { this(System::nanoTime, memberships); }

    GeminiUserRateLimiter(LongSupplier nanoTime) { this(nanoTime, null); }
    private GeminiUserRateLimiter(LongSupplier nanoTime, ProMembershipService memberships) {
        this.nanoTime = nanoTime;
        this.memberships = memberships;
    }

    public void acquire(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("An authenticated user ID is required for rate limiting.");
        }
        if (memberships != null && memberships.isActive(userId)) return;
        long now = nanoTime.getAsLong();
        Decision decision = new Decision();
        buckets.compute(userId, (key, current) -> {
            Bucket bucket = current == null ? new Bucket() : current;
            synchronized (bucket) {
                bucket.prune(now);
                bucket.lastAccessNanos = now;
                if (bucket.timestamps.size() >= REQUESTS_PER_MINUTE) {
                    long wait = bucket.timestamps.getFirst() + WINDOW_NANOS - now;
                    decision.retryAfter = Math.max(1, (wait + 999_999_999L) / 1_000_000_000L);
                } else {
                    bucket.timestamps.addLast(now);
                }
            }
            return bucket;
        });
        if (calls.incrementAndGet() % 256 == 0) {
            buckets.forEach((key, bucket) -> buckets.computeIfPresent(key, (ignored, current) -> {
                synchronized (current) {
                    current.prune(now);
                    return current.timestamps.isEmpty() && now - current.lastAccessNanos >= WINDOW_NANOS
                            ? null : current;
                }
            }));
        }
        if (decision.retryAfter > 0) {
            throw new GeminiRateLimitExceededException(decision.retryAfter);
        }
    }

    private static final class Bucket {
        private final ArrayDeque<Long> timestamps = new ArrayDeque<>();
        private long lastAccessNanos;
        private void prune(long now) {
            while (!timestamps.isEmpty() && now - timestamps.getFirst() >= WINDOW_NANOS) timestamps.removeFirst();
        }
    }

    private static final class Decision { private long retryAfter; }

    public static final class GeminiRateLimitExceededException extends ApiException {
        private final long retryAfterSeconds;
        private GeminiRateLimitExceededException(long retryAfterSeconds) {
            super(HttpStatus.TOO_MANY_REQUESTS, "GEMINI_RATE_LIMIT_EXCEEDED",
                    "The Gemini request limit is 20 calls per minute per user.",
                    java.util.Map.of("retryAfterSeconds", retryAfterSeconds));
            this.retryAfterSeconds = retryAfterSeconds;
        }
        public long getRetryAfterSeconds() { return retryAfterSeconds; }
    }
}
