package com.spendwise.payment.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.spendwise.audit.service.AuditService;
import com.spendwise.common.exception.ApiException;
import com.spendwise.payment.entity.PaymentTransaction;
import com.spendwise.payment.integration.PaymentGateway;
import com.spendwise.payment.integration.PaymentProviderRejectedException;
import com.spendwise.payment.repository.PaymentTransactionRepository;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ProMembershipServiceTest {

    private final PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
    private final PaymentGateway paypal = mock(PaymentGateway.class);
    private final ProMembershipService service = new ProMembershipService(
            transactions, paypal, mock(AuditService.class));

    @Test
    void reportsAnActiveMonthlyMembershipWithoutDateTypeErrors() {
        Instant completedAt = Instant.now().minus(1, ChronoUnit.DAYS);
        PaymentTransaction payment = new PaymentTransaction("owner", "SPENDWISE_PRO_MONTHLY",
                "Spendwise Pro monthly", "Spendwise", null, new BigDecimal("1.00"), "USD",
                "monthly-key", null, null);
        payment.markCompleted("capture-1", completedAt);
        when(transactions.findAllByOwnerIdAndProductIdStartingWithOrderByCreatedAtDesc("owner", "SPENDWISE_PRO_"))
                .thenReturn(List.of(payment));

        assertThat(service.status("owner").pro()).isTrue();
    }

    @Test
    void reportsRejectedPayPalSandboxCredentialsAsGatewayError() {
        when(transactions.findAllByOwnerIdAndProductIdStartingWithOrderByCreatedAtDesc("owner", "SPENDWISE_PRO_"))
                .thenReturn(List.of());
        when(transactions.findByOwnerIdAndIdempotencyKey("owner", "key"))
                .thenReturn(Optional.empty());
        when(transactions.save(any(PaymentTransaction.class))).thenAnswer(call -> call.getArgument(0));
        when(paypal.createOrder(any(), any(), any())).thenThrow(new PaymentProviderRejectedException());

        assertThatThrownBy(() -> service.create("owner", "key", "MONTHLY"))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    org.assertj.core.api.Assertions.assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    org.assertj.core.api.Assertions.assertThat(error.getCode()).isEqualTo("PAYMENT_PROVIDER_ERROR");
                    org.assertj.core.api.Assertions.assertThat(error.getMessage())
                            .contains("Sandbox app credentials");
                });
    }
}
