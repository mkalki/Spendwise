package com.spendwise.payment.integration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

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

    @Test
    void fetchesCaptureStatusFromPayPalForRefundReconciliation() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api-m.sandbox.paypal.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PayPalAuthService auth = mock(PayPalAuthService.class);
        when(auth.accessToken()).thenReturn("access-token");
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(
                        "https://api-m.sandbox.paypal.com/v2/payments/captures/capture-1"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(HttpMethod.GET))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("""
                        {"id":"capture-1","status":"REFUNDED","amount":{"value":"50.00","currency_code":"USD"},
                         "supplementary_data":{"related_ids":{"order_id":"order-1"}}}
                        """, MediaType.APPLICATION_JSON));
        PayPalClient client = new PayPalClient(auth, builder.build(),
                "http://localhost/return", "http://localhost/cancel");

        PaymentGateway.CaptureStatus status = client.getCaptureStatus("capture-1");

        assertThat(status.status()).isEqualTo("REFUNDED");
        assertThat(status.captureId()).isEqualTo("capture-1");
        assertThat(status.orderId()).isEqualTo("order-1");
        assertThat(status.amount()).isEqualByComparingTo("50.00");
        assertThat(status.currencyCode()).isEqualTo("USD");
        server.verify();
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
