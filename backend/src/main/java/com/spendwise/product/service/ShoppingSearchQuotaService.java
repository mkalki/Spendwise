package com.spendwise.product.service;

import com.spendwise.common.exception.ApiException;
import com.spendwise.payment.service.ProMembershipService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShoppingSearchQuotaService {
    public static final int FREE_SEARCHES_PER_WINDOW = 20;
    private static final Duration WINDOW = Duration.ofHours(24);
    private final JdbcTemplate jdbc;
    private final ProMembershipService memberships;

    public ShoppingSearchQuotaService(JdbcTemplate jdbc, ProMembershipService memberships) {
        this.jdbc = jdbc;
        this.memberships = memberships;
    }

    @Transactional
    public void acquire(String ownerId) {
        if (memberships.isActive(ownerId)) return;
        jdbc.update("INSERT INTO shopping_search_quota_locks(owner_id) VALUES (?) ON CONFLICT DO NOTHING", ownerId);
        jdbc.queryForObject("SELECT owner_id FROM shopping_search_quota_locks WHERE owner_id = ? FOR UPDATE",
                String.class, ownerId);
        Instant now = Instant.now();
        Instant cutoff = now.minus(WINDOW);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM shopping_search_request_events "
                + "WHERE owner_id = ? AND requested_at > ?", Integer.class, ownerId, Timestamp.from(cutoff));
        if (count != null && count >= FREE_SEARCHES_PER_WINDOW) {
            Timestamp oldest = jdbc.queryForObject("SELECT MIN(requested_at) FROM shopping_search_request_events "
                    + "WHERE owner_id = ? AND requested_at > ?", Timestamp.class, ownerId, Timestamp.from(cutoff));
            long retryAfter = oldest == null ? 86400 : Math.max(1, Duration.between(now, oldest.toInstant().plus(WINDOW)).toSeconds());
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "DAILY_SEARCH_LIMIT_EXCEEDED",
                    "Free accounts can make 20 shopping searches in a rolling 24-hour period.",
                    Map.of("limit", FREE_SEARCHES_PER_WINDOW, "retryAfterSeconds", retryAfter));
        }
        jdbc.update("INSERT INTO shopping_search_request_events(owner_id) VALUES (?)", ownerId);
        jdbc.update("DELETE FROM shopping_search_request_events WHERE owner_id = ? AND requested_at <= ?",
                ownerId, Timestamp.from(cutoff));
    }
}
