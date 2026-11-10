package com.spendwise.checkout.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.checkout.dto.CheckoutDetails;
import com.spendwise.common.exception.ApiException;
import com.spendwise.product.repository.ProductCandidateRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {
    private final JdbcClient jdbc;
    private final ProductCandidateRepository candidates;
    private final ObjectMapper json;

    public CartService(JdbcClient jdbc, ProductCandidateRepository candidates, ObjectMapper json) {
        this.jdbc = jdbc;
        this.candidates = candidates;
        this.json = json;
    }

    public List<CartItem> items(String ownerId) {
        return jdbc.sql("""
                SELECT product.id AS candidate_id, product.search_session_id, product.product_title,
                       product.image_url, product.merchant_name, product.listed_price AS price,
                       product.currency AS currency_code
                FROM shopping_cart_items cart
                JOIN product_candidates product ON product.id = cart.candidate_id
                WHERE cart.owner_id = :owner AND NOT EXISTS (
                    SELECT 1 FROM payment_transactions payment
                    WHERE payment.owner_id = cart.owner_id AND payment.candidate_id = cart.candidate_id
                      AND payment.completed_at >= cart.added_at
                )
                ORDER BY cart.added_at DESC, product.id
                """)
                .param("owner", ownerId)
                .query(CartItem.class)
                .list();
    }

    @Transactional
    public void add(String ownerId, UUID candidateId) {
        var candidate = candidates.findOwnedById(candidateId, ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "PRODUCT_NOT_FOUND", "This product was not found in your searches."));
        if (!candidate.isEligible()) {
            throw new ApiException(HttpStatus.CONFLICT, "PRODUCT_INELIGIBLE",
                    "Only offers within your spending policy can be added to the cart.");
        }
        removeCapturedItems(ownerId);
        jdbc.sql("""
                INSERT INTO shopping_cart_items (owner_id, candidate_id) VALUES (:owner, :candidate)
                ON CONFLICT DO NOTHING
                """)
                .param("owner", ownerId)
                .param("candidate", candidateId)
                .update();
    }

    public void remove(String ownerId, UUID candidateId) {
        jdbc.sql("DELETE FROM shopping_cart_items WHERE owner_id = :owner AND candidate_id = :candidate")
                .param("owner", ownerId)
                .param("candidate", candidateId)
                .update();
    }

    public CheckoutDetails details(String ownerId) {
        return jdbc.sql("SELECT CAST(details AS text) FROM shopping_checkout_details WHERE owner_id = :owner")
                .param("owner", ownerId)
                .query(String.class)
                .optional()
                .map(this::readDetails)
                .orElseGet(() -> new CheckoutDetails(null, null));
    }

    public CheckoutDetails saveDetails(String ownerId, CheckoutDetails details) {
        String payload;
        try {
            payload = json.writeValueAsString(details);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize checkout details.", exception);
        }
        jdbc.sql("""
                INSERT INTO shopping_checkout_details (owner_id, details)
                VALUES (:owner, CAST(:details AS jsonb))
                ON CONFLICT (owner_id) DO UPDATE SET details = excluded.details
                """)
                .param("owner", ownerId)
                .param("details", payload)
                .update();
        return details;
    }

    private CheckoutDetails readDetails(String payload) {
        try {
            return json.readValue(payload, CheckoutDetails.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read saved checkout details.", exception);
        }
    }

    private void removeCapturedItems(String ownerId) {
        jdbc.sql("""
                DELETE FROM shopping_cart_items cart
                USING payment_transactions payment
                WHERE cart.owner_id = :owner AND payment.owner_id = cart.owner_id
                  AND payment.candidate_id = cart.candidate_id AND payment.completed_at >= cart.added_at
                """)
                .param("owner", ownerId)
                .update();
    }

    public record CartItem(UUID candidateId, UUID searchSessionId, String productTitle, String imageUrl,
            String merchantName, BigDecimal price, String currencyCode) {
    }
}
