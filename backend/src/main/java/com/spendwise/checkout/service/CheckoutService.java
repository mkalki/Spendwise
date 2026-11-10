package com.spendwise.checkout.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendwise.audit.service.AuditService;
import com.spendwise.checkout.dto.BlockedPurchaseHistoryResponse;
import com.spendwise.checkout.dto.BlockedPurchaseHistoryResponse.Entry;
import com.spendwise.checkout.dto.CheckoutResponse;
import com.spendwise.checkout.dto.TransactionHistoryResponse;
import com.spendwise.checkout.dto.TransactionHistoryResponse.TransactionView;
import com.spendwise.checkout.dto.TransactionSummaryResponse;
import com.spendwise.common.exception.ApiException;
import com.spendwise.common.security.Channel3UserRateLimiter;
import com.spendwise.payment.entity.PaymentTransaction;
import com.spendwise.payment.integration.PaymentGateway;
import com.spendwise.payment.integration.PaymentProviderException;
import com.spendwise.payment.integration.PaymentProviderNotConfiguredException;
import com.spendwise.payment.integration.PaymentProviderRejectedException;
import com.spendwise.payment.integration.PaymentProviderTimeoutException;
import com.spendwise.payment.repository.PaymentTransactionRepository;
import com.spendwise.policy.entity.SpendingPolicy;
import com.spendwise.policy.engine.PolicyEngine;
import com.spendwise.policy.repository.SpendingPolicyRepository;
import com.spendwise.product.entity.ProductCandidate;
import com.spendwise.product.integration.Channel3Client;
import com.spendwise.product.integration.Channel3NotConfiguredException;
import com.spendwise.product.repository.ProductCandidateRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class CheckoutService {

    private static final long RESERVATION_MINUTES = 30;
    private static final String CHECKOUT_BLOCKED = "CHECKOUT_BLOCKED";
    private static final Set<String> BLOCKED_CHECKOUT_CODES = Set.of(
            "PRODUCT_NOT_ELIGIBLE", "POLICY_NOT_FOUND", "POLICY_INACTIVE", "CURRENCY_MISMATCH",
            "POLICY_LIMIT_EXCEEDED", "ORDER_FREQUENCY_LIMIT_EXCEEDED", "OFFER_NO_LONGER_AVAILABLE");

    private final Channel3Client channel3Client;
    private final Channel3UserRateLimiter channel3RateLimiter;
    private final PaymentGateway paymentGateway;
    private final SpendingPolicyRepository policyRepository;
    private final PolicyEngine policyEngine;
    private final ProductCandidateRepository candidateRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final TransactionTemplate transactions;
    private final AuditService auditService;

    public CheckoutService(
            Channel3Client channel3Client,
            Channel3UserRateLimiter channel3RateLimiter,
            PaymentGateway paymentGateway,
            SpendingPolicyRepository policyRepository,
            PolicyEngine policyEngine,
            ProductCandidateRepository candidateRepository,
            PaymentTransactionRepository transactionRepository,
            PlatformTransactionManager transactionManager,
            AuditService auditService) {
        this.channel3Client = channel3Client;
        this.channel3RateLimiter = channel3RateLimiter;
        this.paymentGateway = paymentGateway;
        this.policyRepository = policyRepository;
        this.policyEngine = policyEngine;
        this.candidateRepository = candidateRepository;
        this.transactionRepository = transactionRepository;
        this.transactions = new TransactionTemplate(transactionManager);
        this.auditService = auditService;
    }

    public CheckoutResponse create(
            String ownerId,
            String idempotencyKey,
            java.util.UUID searchSessionId,
            java.util.UUID candidateId) {
        ProductCandidate candidate = candidateRepository
                .findOwnedById(candidateId, ownerId)
                .filter(item -> searchSessionId.equals(item.getSearchSession().getId()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "CANDIDATE_NOT_FOUND", "The selected product candidate was not found."));

        PaymentTransaction existing = transactionRepository
                .findByOwnerIdAndIdempotencyKey(ownerId, idempotencyKey)
                .orElse(null);
        if (existing != null) {
            if (!candidateId.equals(existing.getCandidateId())) {
                throw conflict("IDEMPOTENCY_KEY_REUSED", "The idempotency key was already used for another product.");
            }
            if ("EXPIRED".equals(existing.getStatus())) {
                throw checkoutExpired();
            }
            if ("APPROVED".equals(existing.getStatus()) || "COMPLETED".equals(existing.getStatus())) {
                return CheckoutResponse.from(existing);
            }
            if (existing.getPaypalOrderId() == null
                    && ("CREATED".equals(existing.getStatus())
                            || "REQUIRES_RECONCILIATION".equals(existing.getStatus()))) {
                return recoverOrderCreation(ownerId, existing);
            }
            throw conflict("CHECKOUT_IN_PROGRESS", "A checkout using this idempotency key is already in progress.");
        }

        PaymentTransaction reserved = reserveNewCheckout(ownerId, candidate, idempotencyKey);

        PaymentGateway.OrderCreation order;
        try {
            order = paymentGateway.createOrder(reserved.getId(), reserved.getAmount(), reserved.getCurrencyCode());
        } catch (PaymentProviderNotConfiguredException exception) {
            updateStatus(ownerId, reserved.getId(), PaymentTransaction::markFailed);
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_NOT_CONFIGURED",
                    "Sandbox checkout is unavailable because PayPal is not configured.");
        } catch (PaymentProviderTimeoutException exception) {
            updateStatus(ownerId, reserved.getId(), PaymentTransaction::markRequiresReconciliation);
            throw new ApiException(
                    HttpStatus.GATEWAY_TIMEOUT, "PAYMENT_PROVIDER_TIMEOUT",
                    "PayPal did not confirm order creation. The reservation is held for reconciliation.");
        } catch (PaymentProviderRejectedException exception) {
            updateStatus(ownerId, reserved.getId(), PaymentTransaction::markFailed);
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_ERROR",
                    "PayPal could not create the Sandbox order.");
        } catch (PaymentProviderException exception) {
            updateStatus(ownerId, reserved.getId(), PaymentTransaction::markRequiresReconciliation);
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY, "PAYMENT_ORDER_UNCONFIRMED",
                    "PayPal did not confirm order creation. The reservation remains held.");
        }

        PaymentTransaction updated = transactions.execute(status -> {
            PaymentTransaction current = findForUpdate(ownerId, reserved.getId());
            if (!"CREATED".equals(current.getStatus())) {
                current.markRequiresReconciliation();
                return transactionRepository.save(current);
            }
            current.paypalOrderCreated(order.orderId(), order.approvalUrl());
            PaymentTransaction saved = transactionRepository.save(current);
            auditService.record(ownerId, "PAYPAL_ORDER_CREATED", java.util.Map.of(
                    "checkoutId", saved.getId(), "paypalOrderId", order.orderId()));
            return saved;
        });
        if (updated == null || !"APPROVED".equals(updated.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "CHECKOUT_REQUIRES_RECONCILIATION",
                    "The PayPal order exists, but checkout state needs reconciliation.");
        }
        return CheckoutResponse.from(updated);
    }

    public CheckoutResponse createWithSavedPayPal(
            String ownerId,
            String idempotencyKey,
            java.util.UUID searchSessionId,
            java.util.UUID candidateId,
            String vaultId,
            String clientMetadataId) {
        ProductCandidate candidate = candidateRepository.findOwnedById(candidateId, ownerId)
                .filter(item -> searchSessionId.equals(item.getSearchSession().getId()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "CANDIDATE_NOT_FOUND", "The selected product candidate was not found."));
        PaymentTransaction existing = transactionRepository.findByOwnerIdAndIdempotencyKey(ownerId, idempotencyKey)
                .orElse(null);
        if (existing != null) {
            if (!candidateId.equals(existing.getCandidateId())) {
                throw conflict("IDEMPOTENCY_KEY_REUSED", "The idempotency key was already used for another product.");
            }
            if ("EXPIRED".equals(existing.getStatus())) throw checkoutExpired();
            if ("APPROVED".equals(existing.getStatus()) || "COMPLETED".equals(existing.getStatus())) {
                return CheckoutResponse.from(existing);
            }
            if ("REQUIRES_RECONCILIATION".equals(existing.getStatus())) {
                if (existing.getPaypalOrderId() != null) {
                    return CheckoutResponse.from(getPaymentByPaypalOrderId(ownerId, existing.getPaypalOrderId()));
                }
            } else {
                throw conflict("CHECKOUT_IN_PROGRESS", "A checkout using this idempotency key is already in progress.");
            }
        }
        PaymentTransaction reserved;
        if (existing != null && "REQUIRES_RECONCILIATION".equals(existing.getStatus())
                && existing.getPaypalOrderId() == null) {
            requireCheckoutPolicy(ownerId, candidate);
            reserved = existing;
        } else {
            reserved = reserveNewCheckout(ownerId, candidate, idempotencyKey);
        }

        PaymentGateway.VaultOrderCreation order;
        try {
            order = paymentGateway.createOrderWithVault(reserved.getId(), reserved.getAmount(),
                    reserved.getCurrencyCode(), vaultId, clientMetadataId);
        } catch (PaymentProviderNotConfiguredException exception) {
            updateStatus(ownerId, reserved.getId(), PaymentTransaction::markFailed);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_NOT_CONFIGURED",
                    "Saved PayPal checkout is unavailable because PayPal is not configured.");
        } catch (PaymentProviderTimeoutException exception) {
            updateStatus(ownerId, reserved.getId(), PaymentTransaction::markRequiresReconciliation);
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "PAYMENT_PROVIDER_TIMEOUT",
                    "PayPal did not confirm the saved-payment order. The reservation is held for reconciliation.");
        } catch (PaymentProviderRejectedException exception) {
            updateStatus(ownerId, reserved.getId(), PaymentTransaction::markFailed);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "SAVED_PAYMENT_REJECTED",
                    "PayPal rejected the saved payment. Use the standard approval flow or another payment method.");
        } catch (PaymentProviderException exception) {
            updateStatus(ownerId, reserved.getId(), PaymentTransaction::markRequiresReconciliation);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_ORDER_UNCONFIRMED",
                    "PayPal did not confirm the saved-payment order. The reservation remains held.");
        }

        if (order == null) {
            updateStatus(ownerId, reserved.getId(), PaymentTransaction::markRequiresReconciliation);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_ORDER_UNCONFIRMED",
                    "PayPal did not return a confirmed saved-payment order.");
        }
        PaymentGateway.CaptureResult capture = order.capture();
        if (capture != null && (!capture.completed() || capture.captureId() == null
                || capture.amount() == null || capture.amount().compareTo(reserved.getAmount()) != 0
                || !reserved.getCurrencyCode().equals(capture.currencyCode()))) {
            updateStatus(ownerId, reserved.getId(), PaymentTransaction::markRequiresReconciliation);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_STATUS_MISMATCH",
                    "PayPal's saved-payment result did not match the reserved amount and currency.");
        }
        PaymentTransaction updated = transactions.execute(status -> {
            PaymentTransaction current = findForUpdate(ownerId, reserved.getId());
            if (!"CREATED".equals(current.getStatus())
                    && !"REQUIRES_RECONCILIATION".equals(current.getStatus())) {
                current.markRequiresReconciliation();
                return transactionRepository.save(current);
            }
            current.paypalOrderCreated(order.orderId(), order.approvalUrl());
            if (capture != null) current.markCompleted(capture.captureId(), Instant.now());
            PaymentTransaction saved = transactionRepository.save(current);
            auditService.record(ownerId, "PAYPAL_ORDER_CREATED", java.util.Map.of(
                    "checkoutId", saved.getId(), "paypalOrderId", order.orderId(), "savedPayment", true));
            if (capture != null) {
                auditService.record(ownerId, "PAYMENT_CAPTURED", java.util.Map.of(
                        "checkoutId", saved.getId(), "captureId", capture.captureId(),
                        "amount", saved.getAmount().toPlainString(), "currency", saved.getCurrencyCode()));
            }
            return saved;
        });
        if (updated == null || (!"APPROVED".equals(updated.getStatus()) && !"COMPLETED".equals(updated.getStatus()))) {
            throw new ApiException(HttpStatus.CONFLICT, "CHECKOUT_REQUIRES_RECONCILIATION",
                    "The PayPal payment exists, but checkout state needs reconciliation.");
        }
        if (capture == null && order.approvalUrl() == null) {
            updateStatus(ownerId, reserved.getId(), PaymentTransaction::markRequiresReconciliation);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "SAVED_PAYMENT_ACTION_REQUIRED",
                    "PayPal requires additional approval for this payment. Retry with the standard PayPal approval flow.");
        }
        return CheckoutResponse.from(updated);
    }

    public CheckoutResponse capture(String ownerId, Long checkoutId) {
        CaptureStart start = transactions.execute(status -> {
            PaymentTransaction transaction = findForUpdate(ownerId, checkoutId);
            Instant now = Instant.now();
            if ("EXPIRED".equals(transaction.getStatus())) {
                throw checkoutExpired();
            }
            if ("COMPLETED".equals(transaction.getStatus())) {
                return new CaptureStart(transaction, false, true, false, false);
            }
            if ("CAPTURING".equals(transaction.getStatus())) {
                return new CaptureStart(transaction, false, false, true, false);
            }
            if ("REQUIRES_RECONCILIATION".equals(transaction.getStatus())) {
                return new CaptureStart(transaction, false, false, false, true);
            }
            if (transaction.getReservationExpiresAt() != null
                    && !transaction.getReservationExpiresAt().isAfter(now)
                    && ("APPROVED".equals(transaction.getStatus()) || "CREATED".equals(transaction.getStatus()))) {
                transaction.markExpired();
                transactionRepository.save(transaction);
                auditService.record(ownerId, "CHECKOUT_EXPIRED", java.util.Map.of("checkoutId", checkoutId));
                return new CaptureStart(transaction, true, false, false, false);
            }
            if (!"APPROVED".equals(transaction.getStatus())) {
                throw conflict("CHECKOUT_NOT_CAPTUREABLE", "Checkout is not ready to capture.");
            }
            if (!StringUtils.hasText(transaction.getPaypalOrderId())) {
                throw conflict("PAYMENT_ORDER_UNCONFIRMED", "The PayPal order has not been confirmed yet.");
            }
            transaction.markCapturing();
            return new CaptureStart(transactionRepository.save(transaction), false, false, false, false);
        });

        if (start.expired()) {
            throw checkoutExpired();
        }
        if (start.alreadyCompleted()) {
            return CheckoutResponse.from(start.transaction());
        }
        if (start.inProgress()) {
            throw conflict("CAPTURE_IN_PROGRESS", "A PayPal capture is already being processed.");
        }
        if (start.needsReconciliation()) {
            PaymentTransaction reconciled = getPaymentByPaypalOrderId(
                    ownerId, start.transaction().getPaypalOrderId());
            if ("COMPLETED".equals(reconciled.getStatus())) {
                return CheckoutResponse.from(reconciled);
            }
            if (!"APPROVED".equals(reconciled.getStatus())) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_CAPTURE_UNCONFIRMED",
                        "PayPal did not confirm the order status. The reservation remains held.");
            }
            return capture(ownerId, checkoutId);
        }

        PaymentGateway.CaptureResult capture;
        try {
            capture = paymentGateway.captureOrder(checkoutId, start.transaction().getPaypalOrderId());
        } catch (PaymentProviderNotConfiguredException exception) {
            updateStatus(ownerId, checkoutId, PaymentTransaction::markRequiresReconciliation);
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_NOT_CONFIGURED",
                    "Sandbox capture is unavailable because PayPal is not configured.");
        } catch (PaymentProviderTimeoutException exception) {
            updateStatus(ownerId, checkoutId, PaymentTransaction::markRequiresReconciliation);
            throw new ApiException(
                    HttpStatus.GATEWAY_TIMEOUT, "PAYMENT_CAPTURE_UNCONFIRMED",
                    "PayPal did not confirm the capture. The reservation remains held.");
        } catch (PaymentProviderRejectedException exception) {
            updateStatus(ownerId, checkoutId, PaymentTransaction::markApproved);
            throw conflict("PAYMENT_NOT_APPROVED", "PayPal has not approved this order for capture.");
        } catch (PaymentProviderException exception) {
            updateStatus(ownerId, checkoutId, PaymentTransaction::markRequiresReconciliation);
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY, "PAYMENT_CAPTURE_UNCONFIRMED",
                    "PayPal did not confirm the capture. The reservation remains held.");
        }

        if (!capture.completed()
                || !StringUtils.hasText(capture.captureId())
                || capture.amount() == null
                || capture.amount().compareTo(start.transaction().getAmount()) != 0
                || !start.transaction().getCurrencyCode().equals(capture.currencyCode())) {
            updateStatus(ownerId, checkoutId, PaymentTransaction::markRequiresReconciliation);
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY, "PAYMENT_CAPTURE_MISMATCH",
                    "PayPal capture details did not match the reserved checkout.");
        }

        PaymentTransaction verified = getPaymentByPaypalOrderId(ownerId, start.transaction().getPaypalOrderId());
        if (!"COMPLETED".equals(verified.getStatus())
                || !capture.captureId().equals(verified.getPaypalCaptureId())) {
            updateStatus(ownerId, checkoutId, PaymentTransaction::markRequiresReconciliation);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_CAPTURE_UNCONFIRMED",
                    "PayPal did not confirm the captured order status. The reservation remains held.");
        }
        return CheckoutResponse.from(verified);
    }

    public CheckoutResponse cancel(String ownerId, Long checkoutId) {
        PaymentTransaction transaction = transactionRepository.findByOwnerIdAndId(ownerId, checkoutId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "CHECKOUT_NOT_FOUND", "The checkout session was not found."));
        if ("EXPIRED".equals(transaction.getStatus())) {
            throw checkoutExpired();
        }
        if ("CANCELLED".equals(transaction.getStatus())) {
            return CheckoutResponse.from(transaction);
        }
        if (!"APPROVED".equals(transaction.getStatus())
                && !"REQUIRES_RECONCILIATION".equals(transaction.getStatus())) {
            throw conflict("CHECKOUT_NOT_CANCELLABLE", "Checkout cannot be cancelled in its current state.");
        }
        if (!StringUtils.hasText(transaction.getPaypalOrderId())) {
            throw conflict("PAYMENT_ORDER_UNCONFIRMED", "The PayPal order has not been confirmed yet.");
        }
        if (transaction.getReservationExpiresAt() != null
                && !transaction.getReservationExpiresAt().isAfter(Instant.now())) {
            updateStatus(ownerId, checkoutId, PaymentTransaction::markExpired);
            throw checkoutExpired();
        }

        boolean safeToCancel;
        try {
            safeToCancel = paymentGateway.canCancelOrder(transaction.getPaypalOrderId());
        } catch (PaymentProviderNotConfiguredException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_NOT_CONFIGURED",
                    "Sandbox cancellation is unavailable because PayPal is not configured.");
        } catch (PaymentProviderTimeoutException exception) {
            updateStatus(ownerId, checkoutId, PaymentTransaction::markRequiresReconciliation);
            throw new ApiException(
                    HttpStatus.GATEWAY_TIMEOUT, "PAYMENT_STATUS_UNCONFIRMED",
                    "PayPal did not confirm the order status. The reservation remains held.");
        } catch (PaymentProviderRejectedException | PaymentProviderException exception) {
            updateStatus(ownerId, checkoutId, PaymentTransaction::markRequiresReconciliation);
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY, "PAYMENT_STATUS_UNCONFIRMED",
                    "PayPal did not confirm the order status. The reservation remains held.");
        }
        if (!safeToCancel) {
            throw conflict("CHECKOUT_NOT_CANCELLABLE", "PayPal reports that the order cannot be cancelled.");
        }

        PaymentTransaction cancelled = transactions.execute(status -> {
            PaymentTransaction current = findForUpdate(ownerId, checkoutId);
            if (!"APPROVED".equals(current.getStatus())
                    && !"REQUIRES_RECONCILIATION".equals(current.getStatus())) {
                throw conflict("CHECKOUT_STATE_CHANGED", "Checkout state changed while cancellation was processing.");
            }
            current.markCancelled();
            PaymentTransaction saved = transactionRepository.save(current);
            auditService.record(ownerId, "PAYMENT_CANCELLED", java.util.Map.of(
                    "checkoutId", checkoutId, "paypalOrderId", saved.getPaypalOrderId()));
            return saved;
        });
        return CheckoutResponse.from(cancelled);
    }

    public PaymentTransaction getPaymentByPaypalOrderId(String ownerId, String paypalOrderId) {
        PaymentTransaction transaction = transactionRepository.findByOwnerIdAndPaypalOrderId(ownerId, paypalOrderId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "The payment was not found."));
        if (!StringUtils.hasText(paypalOrderId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "The payment was not found.");
        }
        PaymentGateway.OrderStatus providerStatus;
        try {
            providerStatus = paymentGateway.getOrderStatus(paypalOrderId);
        } catch (PaymentProviderNotConfiguredException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_NOT_CONFIGURED",
                    "PayPal status is unavailable because the provider is not configured.");
        } catch (PaymentProviderTimeoutException exception) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "PAYMENT_STATUS_UNCONFIRMED",
                    "PayPal did not confirm the payment status.");
        } catch (PaymentProviderRejectedException | PaymentProviderException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_STATUS_UNCONFIRMED",
                    "PayPal did not confirm the payment status.");
        }
        if (providerStatus == null
                || !("spendwise-" + transaction.getId()).equals(providerStatus.referenceId())) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_STATUS_MISMATCH",
                    "PayPal payment details did not match this checkout.");
        }
        if (providerStatus.amount() == null
                || providerStatus.amount().compareTo(transaction.getAmount()) != 0
                || !transaction.getCurrencyCode().equals(providerStatus.currencyCode())
                || ("COMPLETED".equals(providerStatus.status()) && providerStatus.captureId() == null)) {
            updateStatus(ownerId, transaction.getId(), PaymentTransaction::markRequiresReconciliation);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_STATUS_MISMATCH",
                    "PayPal payment details did not match this checkout.");
        }
        return updateStatus(ownerId, transaction.getId(), current ->
                current.syncProviderStatus(providerStatus.status(), providerStatus.captureId(), Instant.now()));
    }

    public PaymentTransaction getOwnedPayment(String ownerId, String paypalOrderId) {
        return transactionRepository.findByOwnerIdAndPaypalOrderId(ownerId, paypalOrderId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "The payment was not found."));
    }

    public void reconcilePaypalOrder(String paypalOrderId) {
        transactionRepository.findByPaypalOrderId(paypalOrderId)
                .ifPresent(transaction -> getPaymentByPaypalOrderId(transaction.getOwnerId(), paypalOrderId));
    }

    public void reconcilePaypalCapture(String paypalCaptureId, boolean reversedEvent) {
        if (!StringUtils.hasText(paypalCaptureId)) return;
        PaymentTransaction transaction = transactionRepository.findByPaypalCaptureId(paypalCaptureId).orElse(null);
        if (transaction == null) return;

        PaymentGateway.CaptureStatus providerStatus;
        try {
            providerStatus = paymentGateway.getCaptureStatus(paypalCaptureId);
        } catch (PaymentProviderNotConfiguredException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_NOT_CONFIGURED",
                    "PayPal capture status is unavailable because the provider is not configured.");
        } catch (PaymentProviderTimeoutException exception) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "PAYMENT_STATUS_UNCONFIRMED",
                    "PayPal did not confirm the capture status.");
        } catch (PaymentProviderRejectedException | PaymentProviderException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_STATUS_UNCONFIRMED",
                    "PayPal did not confirm the capture status.");
        }
        if (providerStatus == null
                || !paypalCaptureId.equals(providerStatus.captureId())
                || !transaction.getPaypalOrderId().equals(providerStatus.orderId())
                || providerStatus.amount() == null
                || providerStatus.amount().compareTo(transaction.getAmount()) != 0
                || !transaction.getCurrencyCode().equals(providerStatus.currencyCode())) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_STATUS_MISMATCH",
                    "PayPal capture details did not match this checkout.");
        }

        if ("PARTIALLY_REFUNDED".equals(providerStatus.status())) {
            updateStatus(transaction.getOwnerId(), transaction.getId(), PaymentTransaction::markPartiallyRefunded);
        } else if ("REFUNDED".equals(providerStatus.status())) {
            updateStatus(transaction.getOwnerId(), transaction.getId(), reversedEvent
                    ? PaymentTransaction::markReversed
                    : PaymentTransaction::markRefunded);
        } else if ("REVERSED".equals(providerStatus.status()) && reversedEvent) {
            updateStatus(transaction.getOwnerId(), transaction.getId(), PaymentTransaction::markReversed);
        }
    }

    public PaymentTransaction getOwnedPaymentById(String ownerId, Long id) {
        return transactionRepository.findByOwnerIdAndId(ownerId, id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "The payment was not found."));
    }

    public TransactionHistoryResponse history(String ownerId) {
        return new TransactionHistoryResponse(
                transactionRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                        .map(TransactionView::from)
                        .toList());
    }

    @Transactional
    public TransactionSummaryResponse summary(String ownerId) {
        SpendingPolicy policy = policyRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "POLICY_NOT_FOUND", "No spending policy has been configured."));
        ZoneId zoneId = ZoneId.of(policy.getTimezone());
        LocalDate weekStartDate = LocalDate.now(zoneId).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant start = weekStartDate.atStartOfDay(zoneId).toInstant();
        Instant end = weekStartDate.plusWeeks(1).atStartOfDay(zoneId).toInstant();
        Instant now = Instant.now();
        transactionRepository.expireReservations(ownerId, now);
        BigDecimal spent = transactionRepository.completedSpend(ownerId, start, end);
        BigDecimal reserved = transactionRepository.activeReservationAmount(ownerId, now);
        BigDecimal remaining = policy.getWeeklyBudget().subtract(spent).subtract(reserved).max(BigDecimal.ZERO);
        return new TransactionSummaryResponse(
                start.toString(),
                end.toString(),
                spent.toPlainString(),
                reserved.toPlainString(),
                remaining.toPlainString(),
                transactionRepository.completedOrderCount(ownerId, start, end),
                transactionRepository.activeReservationCount(ownerId, now),
                policy.getMaxOrdersPerWeek(),
                policy.getCurrencyCode());
    }

    @Transactional(readOnly = true)
    public BlockedPurchaseHistoryResponse blockedHistory(String ownerId) {
        var attempts = auditService.recentEvents(ownerId, CHECKOUT_BLOCKED).stream().map(event -> {
            JsonNode details = event.getDetails();
            return new Entry(event.getId().toString(), details.path("productName").asText(),
                    details.path("amount").asText(null), details.path("currency").asText(null),
                    details.path("reason").asText(), event.getCreatedAt());
        }).toList();
        var rejectedOffers = candidateRepository
                .findBySearchSessionOwnerIdAndEligibleFalseOrderByRetrievedAtDescIdDesc(ownerId, PageRequest.ofSize(5))
                .stream().map(candidate -> new Entry(
                        candidate.getId().toString(), candidate.getProductTitle(),
                        candidate.getListedPrice() == null ? null : candidate.getListedPrice().toPlainString(),
                        candidate.getCurrencyCode(), candidate.getRejectionReason(), candidate.getRetrievedAt()))
                .toList();
        return new BlockedPurchaseHistoryResponse(attempts, rejectedOffers);
    }

    private SpendingPolicy requireCheckoutPolicy(String ownerId, ProductCandidate candidate) {
        if (!candidate.isEligible()) {
            throw conflict("PRODUCT_NOT_ELIGIBLE", "The selected product did not pass policy checks.");
        }
        return policyRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "POLICY_NOT_FOUND", "No spending policy has been configured."));
    }

    private PaymentTransaction reserveNewCheckout(String ownerId, ProductCandidate candidate, String idempotencyKey) {
        RefreshedOffer offer = null;
        try {
            SpendingPolicy policy = requireCheckoutPolicy(ownerId, candidate);
            offer = refreshOffer(ownerId, candidate, policy);
            return reserve(ownerId, candidate, offer, idempotencyKey);
        } catch (ApiException exception) {
            if (BLOCKED_CHECKOUT_CODES.contains(exception.getCode())) {
                String reason = "PRODUCT_NOT_ELIGIBLE".equals(exception.getCode()) && candidate.getRejectionReason() != null
                        ? candidate.getRejectionReason() : exception.getMessage();
                BigDecimal amount = offer == null ? candidate.getListedPrice() : offer.amount();
                var details = new HashMap<String, String>();
                details.put("productName", candidate.getProductTitle());
                details.put("reason", reason);
                details.put("amount", amount == null ? null : amount.toPlainString());
                details.put("currency", offer == null ? candidate.getCurrencyCode() : offer.currencyCode());
                // The reservation transaction has rolled back before recording the block.
                auditService.record(ownerId, CHECKOUT_BLOCKED, details);
            }
            throw exception;
        }
    }

    private PaymentTransaction reserve(
            String ownerId,
            ProductCandidate candidate,
            RefreshedOffer offer,
            String idempotencyKey) {
        return transactions.execute(status -> {
            SpendingPolicy policy = policyRepository.findByOwnerIdForUpdate(ownerId)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND, "POLICY_NOT_FOUND", "No spending policy has been configured."));
            Optional<PaymentTransaction> duplicate = transactionRepository
                    .findByOwnerIdAndIdempotencyKey(ownerId, idempotencyKey);
            if (duplicate.isPresent()) {
                throw conflict("IDEMPOTENCY_KEY_REUSED", "The idempotency key has already been used.");
            }
            Instant now = Instant.now();
            transactionRepository.expireReservations(ownerId, now);
            LocalDate weekStart = LocalDate.now(ZoneId.of(policy.getTimezone()))
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            Instant start = weekStart.atStartOfDay(ZoneId.of(policy.getTimezone())).toInstant();
            Instant end = weekStart.plusWeeks(1).atStartOfDay(ZoneId.of(policy.getTimezone())).toInstant();
            policyEngine.authorizeCheckout(
                    policy,
                    offer.amount(),
                    candidate.getSearchSession().getEffectiveMaxPrice(),
                    offer.currencyCode(),
                    transactionRepository.completedSpend(ownerId, start, end),
                    transactionRepository.activeReservationAmount(ownerId, now),
                    transactionRepository.completedOrderCount(ownerId, start, end),
                    transactionRepository.activeReservationCount(ownerId, now));
            PaymentTransaction transaction = new PaymentTransaction(
                    ownerId,
                    candidate.getChannel3ProductId(),
                    candidate.getProductTitle(),
                    offer.merchantName(),
                    offer.offerUrl(),
                    offer.amount(),
                    offer.currencyCode(),
                    idempotencyKey,
                    candidate.getId(),
                    now.plusSeconds(RESERVATION_MINUTES * 60));
            PaymentTransaction saved = transactionRepository.save(transaction);
            auditService.record(ownerId, "CHECKOUT_RESERVED", java.util.Map.of(
                    "checkoutId", saved.getId(), "candidateId", candidate.getId().toString(),
                    "amount", saved.getAmount().toPlainString(), "currency", saved.getCurrencyCode()));
            return saved;
        });
    }

    private RefreshedOffer refreshOffer(String ownerId, ProductCandidate candidate, SpendingPolicy policy) {
        JsonNode product;
        try {
            channel3RateLimiter.acquire(ownerId);
            product = channel3Client.getProduct(
                    candidate.getChannel3ProductId(), policy.getCountryCode(), policy.getCurrencyCode());
        } catch (Channel3NotConfiguredException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PRODUCT_PROVIDER_NOT_CONFIGURED",
                    "Checkout is unavailable because the product provider is not configured.");
        } catch (ResourceAccessException exception) {
            throw new ApiException(
                    HttpStatus.GATEWAY_TIMEOUT, "PRODUCT_PROVIDER_TIMEOUT", "Could not refresh the product offer.");
        } catch (RestClientResponseException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY, "PRODUCT_PROVIDER_ERROR", "Could not refresh the product offer.");
        }
        JsonNode offers = product == null ? null : product.path("offers");
        if (offers == null || !offers.isArray()) {
            throw conflict("OFFER_NO_LONGER_AVAILABLE", "The selected retailer offer is no longer available.");
        }
        for (JsonNode offer : offers) {
            String domain = text(offer, "domain");
            if (candidate.getMerchantName() != null
                    && !candidate.getMerchantName().equalsIgnoreCase(domain)) {
                continue;
            }
            BigDecimal amount = decimal(offer.path("price").path("price"));
            String currencyCode = text(offer.path("price"), "currency");
            String availability = text(offer, "availability");
            String offerUrl = text(offer, "url");
            if (amount != null) {
                try {
                    amount = amount.setScale(2, java.math.RoundingMode.UNNECESSARY);
                } catch (ArithmeticException exception) {
                    amount = null;
                }
            }
            if (amount == null || amount.signum() <= 0
                    || !policy.getCurrencyCode().equals(currencyCode)
                    || !"InStock".equals(availability)
                    || !validHttpUrl(offerUrl)) {
                throw conflict("OFFER_NO_LONGER_AVAILABLE", "The selected retailer offer is no longer valid.");
            }
            return new RefreshedOffer(amount, currencyCode, domain, offerUrl);
        }
        throw conflict("OFFER_NO_LONGER_AVAILABLE", "The selected retailer offer is no longer available.");
    }

    private PaymentTransaction updateStatus(
            String ownerId,
            Long transactionId,
            java.util.function.Consumer<PaymentTransaction> update) {
        return transactions.execute(status -> {
            PaymentTransaction transaction = findForUpdate(ownerId, transactionId);
            String previousStatus = transaction.getStatus();
            update.accept(transaction);
            PaymentTransaction saved = transactionRepository.save(transaction);
            recordStatusTransition(saved, previousStatus);
            return saved;
        });
    }

    private void recordStatusTransition(PaymentTransaction transaction, String previousStatus) {
        String status = transaction.getStatus();
        if (java.util.Objects.equals(previousStatus, status)) return;
        String eventType = switch (status) {
            case "COMPLETED" -> "PAYMENT_CAPTURED";
            case "CANCELLED" -> "PAYMENT_CANCELLED";
            case "FAILED" -> "PAYMENT_FAILED";
            case "REQUIRES_RECONCILIATION" -> "PAYMENT_REQUIRES_RECONCILIATION";
            case "PARTIALLY_REFUNDED" -> "PAYMENT_PARTIALLY_REFUNDED";
            case "REFUNDED" -> "PAYMENT_REFUNDED";
            case "REVERSED" -> "PAYMENT_REVERSED";
            default -> null;
        };
        if (eventType != null) {
            auditService.record(transaction.getOwnerId(), eventType, java.util.Map.of(
                    "checkoutId", transaction.getId(), "previousStatus", previousStatus, "status", status,
                    "amount", transaction.getAmount().toPlainString(),
                    "currency", transaction.getCurrencyCode()));
        }
    }

    private CheckoutResponse recoverOrderCreation(String ownerId, PaymentTransaction reservation) {
        PaymentGateway.OrderCreation order;
        try {
            order = paymentGateway.createOrder(
                    reservation.getId(), reservation.getAmount(), reservation.getCurrencyCode());
        } catch (PaymentProviderNotConfiguredException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_NOT_CONFIGURED",
                    "Sandbox checkout is unavailable because PayPal is not configured.");
        } catch (PaymentProviderTimeoutException exception) {
            throw new ApiException(
                    HttpStatus.GATEWAY_TIMEOUT, "PAYMENT_PROVIDER_TIMEOUT",
                    "PayPal did not confirm order creation. The reservation remains held.");
        } catch (PaymentProviderRejectedException | PaymentProviderException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_ERROR",
                    "PayPal could not create the Sandbox order.");
        }
        PaymentTransaction updated = transactions.execute(status -> {
            PaymentTransaction current = findForUpdate(ownerId, reservation.getId());
            if (!"CREATED".equals(current.getStatus())
                    && !"REQUIRES_RECONCILIATION".equals(current.getStatus())) {
                return current;
            }
            current.paypalOrderCreated(order.orderId(), order.approvalUrl());
            PaymentTransaction saved = transactionRepository.save(current);
            auditService.record(ownerId, "PAYPAL_ORDER_CREATED", java.util.Map.of(
                    "checkoutId", saved.getId(), "paypalOrderId", order.orderId(), "recovered", true));
            return saved;
        });
        return CheckoutResponse.from(updated);
    }

    private PaymentTransaction findForUpdate(String ownerId, Long id) {
        return transactionRepository.findByOwnerIdAndIdForUpdate(ownerId, id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "CHECKOUT_NOT_FOUND", "The checkout session was not found."));
    }

    private ApiException policyLimit(String message, BigDecimal limit, String currency) {
        return new ApiException(
                HttpStatus.CONFLICT,
                "POLICY_LIMIT_EXCEEDED",
                message,
                java.util.Map.of("limit", limit.toPlainString(), "currency", currency));
    }

    private ApiException checkoutExpired() {
        return conflict("CHECKOUT_EXPIRED", "The checkout reservation has expired. Start a new checkout from your cart.");
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(node.asText());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean validHttpUrl(String value) {
        if (value == null) {
            return false;
        }
        try {
            String scheme = java.net.URI.create(value).getScheme();
            return "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private record RefreshedOffer(BigDecimal amount, String currencyCode, String merchantName, String offerUrl) {
    }

    private record CaptureStart(
            PaymentTransaction transaction,
            boolean expired,
            boolean alreadyCompleted,
            boolean inProgress,
            boolean needsReconciliation) {
    }
}
