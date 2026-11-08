package com.spendwise.product.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ShoppingIntentServiceTest {

    private final ShoppingIntentService service = new ShoppingIntentService();

    @Test
    void extractsRequestedMaximumWithoutWeakeningSavedPolicy() {
        ShoppingIntentService.ParsedIntent intent =
                service.parse("  Find wireless keyboards under $45.50 with good battery life  ");

        assertThat(intent.normalizedQuery())
                .isEqualTo("Find wireless keyboards under $45.50 with good battery life");
        assertThat(intent.keywords()).isEqualTo("Find wireless keyboards with good battery life");
        assertThat(intent.requestedMaxPrice()).isEqualByComparingTo(new BigDecimal("45.50"));
    }

    @Test
    void leavesMaximumUnsetWhenRequestDoesNotSpecifyOne() {
        ShoppingIntentService.ParsedIntent intent = service.parse("comfortable running shoes");

        assertThat(intent.requestedMaxPrice()).isNull();
        assertThat(intent.keywords()).isEqualTo("comfortable running shoes");
    }
}
