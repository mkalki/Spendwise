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
public class Channel3UserRateLimiter {

    public static final int REQUESTS_PER_MINUTE = 100;
    private static final long WINDOW_NANOS = Duration.ofMinutes(1).toNanos();

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final LongSupplier nanoTime;
    private final AtomicLong calls = new AtomicLong();
    private final ProMembershipService memberships;

    public Channel3UserRateLimiter() { this(System::nanoTime, null); }

    @org.springframework.beans.factory.annotation.Autowired
    public Channel3UserRateLimiter(ProMembershipService memberships) {
        this(System::nanoTime, memberships);
    }

    Channel3UserRateLimiter(LongSupplier nanoTime) {
        this(nanoTime, null);
    }

    private Channel3UserRateLimiter(LongSupplier nanoTime, ProMembershipService memberships) {
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
        buckets.compute(userId, (key, bucket) -> {
            Bucket current = bucket == null ? new Bucket() : bucket;
            synchronized (current) {
                current.prune(now);
                current.lastAccessNanos = now;
                if (current.requestTimes.size() >= REQUESTS_PER_MINUTE) {
                    long retryNanos = current.requestTimes.getFirst() + WINDOW_NANOS - now;
                    decision.retryAfterSeconds = Math.max(1L,
                            (retryNanos + Duration.ofSeconds(1).toNanos() - 1)
                                    / Duration.ofSeconds(1).toNanos());
                } else {
                    current.requestTimes.addLast(now);
                }
            }
            return current;
        });
        cleanupOccasionally(now);
        if (decision.retryAfterSeconds > 0) {
            throw new Channel3RateLimitExceededException(decision.retryAfterSeconds);
        }
    }

    private void cleanupOccasionally(long now) {
        if (calls.incrementAndGet() % 256 != 0) {
            return;
        }
        buckets.forEach((userId, bucket) -> buckets.computeIfPresent(userId, (key, current) -> {
            synchronized (current) {
                current.prune(now);
                return current.requestTimes.isEmpty()
                                && now - current.lastAccessNanos >= WINDOW_NANOS
                        ? null
                        : current;
            }
        }));
    }

    private static final class Bucket {
        private final ArrayDeque<Long> requestTimes = new ArrayDeque<>();
        private long lastAccessNanos;

        private void prune(long now) {
            while (!requestTimes.isEmpty() && now - requestTimes.getFirst() >= WINDOW_NANOS) {
                requestTimes.removeFirst();
            }
        }
    }

    private static final class Decision {
        private long retryAfterSeconds;
    }

    public static class Channel3RateLimitExceededException extends ApiException {
        private final long retryAfterSeconds;

        private Channel3RateLimitExceededException(long retryAfterSeconds) {
            super(HttpStatus.TOO_MANY_REQUESTS,
                    "CHANNEL3_RATE_LIMIT_EXCEEDED",
                    "The Channel3 request limit is 100 calls per minute per user.",
                    java.util.Map.of("retryAfterSeconds", retryAfterSeconds));
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
