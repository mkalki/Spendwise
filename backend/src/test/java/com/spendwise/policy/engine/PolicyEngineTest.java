package com.spendwise.policy.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spendwise.common.exception.ApiException;
import com.spendwise.policy.entity.SpendingPolicy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PolicyEngineTest {

    private final PolicyEngine engine = new PolicyEngine();
    private final SpendingPolicy policy = new SpendingPolicy(
            "owner",
            new BigDecimal("60.00"),
            new BigDecimal("100.00"),
            new BigDecimal("150.00"),
            2,
            "USD",
            "US",
            "America/New_York");

    @Test
    void requestedLimitCanOnlyTightenSavedProductLimit() {
        assertThat(engine.effectiveMaxProductPrice(policy, new BigDecimal("45.00")))
                .isEqualByComparingTo("45.00");
        assertThat(engine.effectiveMaxProductPrice(policy, new BigDecimal("120.00")))
                .isEqualByComparingTo("60.00");
        assertThat(engine.effectiveMaxProductPrice(policy, null))
                .isEqualByComparingTo("60.00");
    }

    @Test
    void productMustHaveAnInStockOfferWithinTheEffectiveCapAndPolicyCurrency() {
        assertThat(engine.productRejectionReason(
                        new BigDecimal("45.00"), "USD", "InStock", "shop.example", "https://shop.example/item", policy,
                        new BigDecimal("45.00")))
                .isNull();
        assertThat(engine.productRejectionReason(
                        new BigDecimal("45.01"), "USD", "InStock", "shop.example", "https://shop.example/item", policy,
                        new BigDecimal("45.00")))
                .contains("effective maximum");
        assertThat(engine.productRejectionReason(
                        new BigDecimal("45.00"), "EUR", "InStock", "shop.example", "https://shop.example/item", policy,
                        new BigDecimal("60.00")))
                .contains("currency");
    }

    @Test
    void weeklyBudgetAndOrderLimitAreEnforcedIncludingReservations() {
        assertThatThrownBy(() -> engine.authorizeCheckout(
                        policy,
                        new BigDecimal("20.00"),
                        new BigDecimal("60.00"),
                        "USD",
                        new BigDecimal("110.00"),
                        new BigDecimal("25.00"),
                        1,
                        0))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("remaining weekly budget");
        assertThatThrownBy(() -> engine.authorizeCheckout(
                        policy,
                        new BigDecimal("20.00"),
                        new BigDecimal("60.00"),
                        "USD",
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        1,
                        1))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("weekly order limit");
    }

    @Test
    void refreshedOfferCannotExceedTheOriginalRequestLimit() {
        assertThatThrownBy(() -> engine.authorizeCheckout(
                        policy,
                        new BigDecimal("50.00"),
                        new BigDecimal("45.00"),
                        "USD",
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        0,
                        0))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("original shopping request");
    }
}
