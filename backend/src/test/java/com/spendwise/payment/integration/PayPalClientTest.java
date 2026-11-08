package com.spendwise.payment.integration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

class PayPalClientTest {

    @Test
    void refusesProductionPayPalEndpoint() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> client("https://api-m.paypal.com"));
    }

    @Test
    void allowsOnlyTheOfficialSandboxApiEndpoint() {
        assertThatCode(() -> client("https://api-m.sandbox.paypal.com"))
                .doesNotThrowAnyException();
    }

    private PayPalClient client(String baseUrl) {
        return new PayPalClient(
                new SimpleClientHttpRequestFactory(),
                baseUrl,
                "test-client",
                "test-secret",
                "http://localhost:3000/checkout/return",
                "http://localhost:3000/checkout/cancel");
    }
}
