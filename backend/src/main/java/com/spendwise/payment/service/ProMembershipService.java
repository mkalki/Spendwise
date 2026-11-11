package com.spendwise.payment.service;

import com.spendwise.audit.service.AuditService;
import com.spendwise.common.exception.ApiException;
import com.spendwise.payment.dto.PaymentResponse;
import com.spendwise.payment.dto.ProMembershipResponse;
import com.spendwise.payment.entity.PaymentTransaction;
import com.spendwise.payment.integration.PaymentGateway;
import com.spendwise.payment.integration.PaymentProviderException;
import com.spendwise.payment.integration.PaymentProviderNotConfiguredException;
import com.spendwise.payment.integration.PaymentProviderRejectedException;
import com.spendwise.payment.integration.PaymentProviderTimeoutException;
import com.spendwise.payment.repository.PaymentTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProMembershipService {
    private static final String PRODUCT_PREFIX = "SPENDWISE_PRO_";
    private final PaymentTransactionRepository transactions;
    private final PaymentGateway paypal;
    private final AuditService audit;

    public ProMembershipService(PaymentTransactionRepository transactions, PaymentGateway paypal, AuditService audit) {
        this.transactions = transactions;
        this.paypal = paypal;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ProMembershipResponse status(String ownerId) {
        List<PaymentTransaction> payments = proPayments(ownerId);
        PaymentTransaction latest = payments.stream().filter(tx -> "COMPLETED".equals(tx.getStatus()))
                .max(Comparator.comparing(PaymentTransaction::getCompletedAt)).orElse(null);
        Instant expires = latest == null ? null : expiry(latest);
        boolean active = expires != null && expires.isAfter(Instant.now());
        return new ProMembershipResponse(active, active ? latest.getProductId().substring(PRODUCT_PREFIX.length()) : null,
                expires, monthlyRenewalPrice(payments).toPlainString());
    }

    public boolean isActive(String ownerId) { return status(ownerId).pro(); }

    @Transactional
    public PaymentResponse create(String ownerId, String key, String plan) {
        ProMembershipResponse current = status(ownerId);
        if (current.pro()) throw new ApiException(HttpStatus.CONFLICT, "PRO_ALREADY_ACTIVE", "Pro is already active.");
        List<PaymentTransaction> previous = proPayments(ownerId);
        boolean existingKey = transactions.findByOwnerIdAndIdempotencyKey(ownerId, key).isPresent();
        if (!existingKey && previous.stream().anyMatch(tx ->
                ("CREATED".equals(tx.getStatus()) || "APPROVED".equals(tx.getStatus()))
                        && tx.getReservationExpiresAt() != null && tx.getReservationExpiresAt().isAfter(Instant.now()))) {
            throw new ApiException(HttpStatus.CONFLICT, "PRO_CHECKOUT_IN_PROGRESS",
                    "A Pro payment is already awaiting completion.");
        }
        String planCode = "MONTHLY".equals(plan) ? "MONTHLY" : "ANNUAL";
        BigDecimal amount = "ANNUAL".equals(plan) ? new BigDecimal("6.00") : monthlyRenewalPrice(previous);
        String productId = PRODUCT_PREFIX + planCode;
        PaymentTransaction tx = transactions.findByOwnerIdAndIdempotencyKey(ownerId, key).orElseGet(() ->
                transactions.save(new PaymentTransaction(ownerId, productId, "Spendwise Pro " + planCode,
                        "Spendwise", null, amount, "USD", key, null, Instant.now().plus(30, ChronoUnit.MINUTES))));
        if (!productId.equals(tx.getProductId())) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "The checkout key was used for another plan.");
        }
        if (tx.getPaypalOrderId() != null) return PaymentResponse.from(tx);
        PaymentGateway.OrderCreation order;
        try {
            order = paypal.createOrder(tx.getId(), tx.getAmount(), "USD");
        } catch (PaymentProviderNotConfiguredException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_NOT_CONFIGURED",
                    "Spendwise Pro checkout is unavailable because PayPal Sandbox is not configured.");
        } catch (PaymentProviderTimeoutException exception) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "PAYMENT_PROVIDER_TIMEOUT",
                    "PayPal did not confirm Pro checkout creation. Please retry shortly.");
        } catch (PaymentProviderRejectedException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_ERROR",
                    "PayPal rejected the Pro checkout request. Check the Sandbox app credentials and try again.");
        } catch (PaymentProviderException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_ORDER_UNCONFIRMED",
                    "PayPal did not confirm Pro checkout creation. Please retry shortly.");
        }
        tx.paypalOrderCreated(order.orderId(), order.approvalUrl());
        tx = transactions.save(tx);
        audit.record(ownerId, "PRO_CHECKOUT_CREATED", java.util.Map.of("checkoutId", tx.getId(), "plan", planCode));
        return PaymentResponse.from(tx);
    }

    private BigDecimal monthlyRenewalPrice(List<PaymentTransaction> payments) {
        return payments.stream().anyMatch(tx -> "COMPLETED".equals(tx.getStatus())
                && (PRODUCT_PREFIX + "MONTHLY").equals(tx.getProductId()))
                ? new BigDecimal("0.80") : new BigDecimal("1.00");
    }

    private List<PaymentTransaction> proPayments(String ownerId) {
        return transactions.findAllByOwnerIdAndProductIdStartingWithOrderByCreatedAtDesc(ownerId, PRODUCT_PREFIX);
    }

    private Instant expiry(PaymentTransaction tx) {
        var completedAtUtc = tx.getCompletedAt().atZone(ZoneOffset.UTC);
        return "SPENDWISE_PRO_ANNUAL".equals(tx.getProductId())
                ? completedAtUtc.plusYears(1).toInstant()
                : completedAtUtc.plusMonths(1).toInstant();
    }
}
