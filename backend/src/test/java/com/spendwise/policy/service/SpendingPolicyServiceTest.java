package com.spendwise.policy.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.spendwise.common.exception.ApiException;
import com.spendwise.audit.service.AuditService;
import com.spendwise.policy.dto.SpendingPolicyRequest;
import com.spendwise.policy.repository.SpendingPolicyRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SpendingPolicyServiceTest {

    private final SpendingPolicyRepository repository = mock(SpendingPolicyRepository.class);
    private final SpendingPolicyService service = new SpendingPolicyService(repository, mock(AuditService.class));

    @Test
    void rejectsInconsistentFinancialLimitsBeforePersistence() {
        SpendingPolicyRequest request = new SpendingPolicyRequest(
                new BigDecimal("70.00"),
                new BigDecimal("60.00"),
                new BigDecimal("100.00"),
                1,
                "USD",
                "US",
                "UTC");

        assertThatThrownBy(() -> service.save("owner", request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("maxProductPrice");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsCurrenciesNotSupportedByProductDiscovery() {
        SpendingPolicyRequest request = new SpendingPolicyRequest(
                new BigDecimal("50.00"),
                new BigDecimal("75.00"),
                new BigDecimal("100.00"),
                1,
                "JPY",
                "JP",
                "Asia/Tokyo");

        assertThatThrownBy(() -> service.save("owner", request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not supported");
        verifyNoInteractions(repository);
    }
}
