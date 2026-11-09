package com.spendwise.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendwise.checkout.service.CheckoutService;
import com.spendwise.common.exception.ApiException;
import com.spendwise.common.security.Channel3UserRateLimiter;
import com.spendwise.payment.entity.PaymentTransaction;
import com.spendwise.payment.integration.PaymentGateway;
import com.spendwise.payment.integration.PaymentProviderTimeoutException;
import com.spendwise.payment.repository.PaymentTransactionRepository;
import com.spendwise.policy.engine.PolicyEngine;
import com.spendwise.policy.repository.SpendingPolicyRepository;
import com.spendwise.product.integration.Channel3Client;
import com.spendwise.product.repository.ProductCandidateRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class CheckoutCaptureSafetyTest {
    private static final String OWNER = "owner-1";
    private static final Long ID = 42L;
    private static final String ORDER = "order-1";

    @Mock private Channel3Client channel3Client;
    @Mock private PaymentGateway paymentGateway;
    @Mock private SpendingPolicyRepository policyRepository;
    @Mock private ProductCandidateRepository candidateRepository;
    @Mock private PaymentTransactionRepository transactionRepository;
    @Mock private PlatformTransactionManager transactionManager;
    private CheckoutService service;
    private PaymentTransaction transaction;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        transaction = new PaymentTransaction(OWNER, "product-1", "Product", "shop.example",
                "https://shop.example/product", new BigDecimal("50.00"), "USD", "idempotency-1",
                UUID.randomUUID(), Instant.now().plusSeconds(300));
        Field id = PaymentTransaction.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(transaction, ID);
        transaction.paypalOrderCreated(ORDER, "https://www.sandbox.paypal.com/checkoutnow?token=order-1");
        lenient().when(transactionRepository.findByOwnerIdAndIdForUpdate(OWNER, ID))
                .thenReturn(Optional.of(transaction));
        lenient().when(transactionRepository.findByOwnerIdAndPaypalOrderId(OWNER, ORDER))
                .thenReturn(Optional.of(transaction));
        lenient().when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new CheckoutService(channel3Client, new Channel3UserRateLimiter(), paymentGateway,
                policyRepository, new PolicyEngine(), candidateRepository, transactionRepository, transactionManager);
    }

    @Test
    void marksPaidOnlyAfterPayPalOrderStatusConfirmsCapture() {
        when(paymentGateway.captureOrder(ID, ORDER)).thenReturn(
                new PaymentGateway.CaptureResult(true, "capture-1", new BigDecimal("50.00"), "USD"));
        when(paymentGateway.getOrderStatus(ORDER)).thenReturn(new PaymentGateway.OrderStatus(
                "COMPLETED", "spendwise-42", "capture-1", new BigDecimal("50.00"), "USD"));

        service.capture(OWNER, ID);

        assertThat(transaction.getStatus()).isEqualTo("COMPLETED");
        assertThat(transaction.getPaypalCaptureId()).isEqualTo("capture-1");
    }

    @Test
    void keepsReservationUnconfirmedWhenPayPalOrderIsNotCompleted() {
        when(paymentGateway.captureOrder(ID, ORDER)).thenReturn(
                new PaymentGateway.CaptureResult(true, "capture-1", new BigDecimal("50.00"), "USD"));
        when(paymentGateway.getOrderStatus(ORDER)).thenReturn(new PaymentGateway.OrderStatus(
                "APPROVED", "spendwise-42", null, new BigDecimal("50.00"), "USD"));

        assertThatThrownBy(() -> service.capture(OWNER, ID))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getStatus())
                .isEqualTo(HttpStatus.BAD_GATEWAY);

        assertThat(transaction.getStatus()).isEqualTo("REQUIRES_RECONCILIATION");
        assertThat(transaction.getPaypalCaptureId()).isNull();
    }

    @Test
    void doesNotSendSecondCaptureWhileFirstCaptureIsInProgress() {
        transaction.markCapturing();

        assertThatThrownBy(() -> service.capture(OWNER, ID))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo("CAPTURE_IN_PROGRESS");

        verify(paymentGateway, never()).captureOrder(any(), any());
    }

    @Test
    void doesNotMarkPaidWhenPayPalStatusLookupTimesOut() {
        when(paymentGateway.captureOrder(ID, ORDER)).thenReturn(
                new PaymentGateway.CaptureResult(true, "capture-1", new BigDecimal("50.00"), "USD"));
        when(paymentGateway.getOrderStatus(ORDER)).thenThrow(new PaymentProviderTimeoutException());

        assertThatThrownBy(() -> service.capture(OWNER, ID)).isInstanceOf(ApiException.class);
        assertThat(transaction.getStatus()).isEqualTo("CAPTURING");
        assertThat(transaction.getPaypalCaptureId()).isNull();
    }

    @Test
    void recordsRefundOnlyAfterPayPalConfirmsTheMatchingCapture() {
        transaction.syncProviderStatus("COMPLETED", "capture-1", Instant.parse("2026-11-08T10:00:00Z"));
        when(transactionRepository.findByPaypalCaptureId("capture-1")).thenReturn(Optional.of(transaction));
        when(paymentGateway.getCaptureStatus("capture-1")).thenReturn(new PaymentGateway.CaptureStatus(
                "REFUNDED", "capture-1", ORDER, new BigDecimal("50.00"), "USD"));

        service.reconcilePaypalCapture("capture-1", false);

        assertThat(transaction.getStatus()).isEqualTo("REFUNDED");
    }

    @Test
    void doesNotApplyRefundWhenPayPalCaptureDoesNotMatchTheStoredPayment() {
        transaction.syncProviderStatus("COMPLETED", "capture-1", Instant.parse("2026-11-08T10:00:00Z"));
        when(transactionRepository.findByPaypalCaptureId("capture-1")).thenReturn(Optional.of(transaction));
        when(paymentGateway.getCaptureStatus("capture-1")).thenReturn(new PaymentGateway.CaptureStatus(
                "REFUNDED", "different-capture", ORDER, new BigDecimal("50.00"), "USD"));

        assertThatThrownBy(() -> service.reconcilePaypalCapture("capture-1", false))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo("PAYMENT_STATUS_MISMATCH");
        assertThat(transaction.getStatus()).isEqualTo("COMPLETED");
    }
}
