package com.spendwise.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PayPalWebhookVerifierTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void postsEventAndTransmissionHeadersToPayPalVerificationEndpoint() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api-m.sandbox.paypal.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PayPalAuthService auth = mock(PayPalAuthService.class);
        when(auth.accessToken()).thenReturn("access-token");
        JsonNode event = objectMapper.readTree("""
                {"id":"event-1","event_type":"PAYMENT.CAPTURE.COMPLETED","resource":{"id":"capture-1"}}
                """);
        server.expect(requestTo("https://api-m.sandbox.paypal.com/v1/notifications/verify-webhook-signature"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"auth_algo":"SHA256withRSA","cert_url":"https://api-m.paypal.com/cert",
                         "transmission_id":"transmission-1","transmission_sig":"signature",
                         "transmission_time":"2026-11-08T10:00:00Z","webhook_id":"webhook-1",
                         "webhook_event":{"id":"event-1","event_type":"PAYMENT.CAPTURE.COMPLETED",
                           "resource":{"id":"capture-1"}}}
                        """))
                .andRespond(withSuccess("{\"verification_status\":\"SUCCESS\"}", MediaType.APPLICATION_JSON));
        PayPalWebhookVerifier verifier = new PayPalWebhookVerifier(builder.build(), auth, "webhook-1");

        assertThat(verifier.verify("SHA256withRSA", "https://api-m.paypal.com/cert", "transmission-1",
                "signature", "2026-11-08T10:00:00Z", event)).isTrue();
        server.verify();
    }

    @Test
    void returnsFalseWhenPayPalRejectsSignature() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api-m.sandbox.paypal.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PayPalAuthService auth = mock(PayPalAuthService.class);
        when(auth.accessToken()).thenReturn("access-token");
        server.expect(requestTo("https://api-m.sandbox.paypal.com/v1/notifications/verify-webhook-signature"))
                .andRespond(withSuccess("{\"verification_status\":\"FAILURE\"}", MediaType.APPLICATION_JSON));
        PayPalWebhookVerifier verifier = new PayPalWebhookVerifier(builder.build(), auth, "webhook-1");
        JsonNode event = objectMapper.readTree("{\"id\":\"event-1\"}");

        assertThat(verifier.verify("SHA256withRSA", "https://api-m.paypal.com/cert", "transmission-1",
                "signature", "2026-11-08T10:00:00Z", event)).isFalse();
        server.verify();
    }
}
