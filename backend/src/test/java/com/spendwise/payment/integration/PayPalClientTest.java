package com.spendwise.payment.integration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
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
        PayPalAuthService auth = authWithToken();
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

    @Test
    void createsJavaScriptCheckoutOrderUsingServerAmount() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api-m.sandbox.paypal.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PayPalAuthService auth = authWithToken();
        server.expect(requestTo("https://api-m.sandbox.paypal.com/v2/checkout/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("PayPal-Request-Id", "spw-order-17"))
                .andExpect(content().json("""
                        {"intent":"CAPTURE","purchase_units":[{"reference_id":"spendwise-17",
                          "description":"Spendwise Sandbox demonstration payment",
                          "amount":{"currency_code":"USD","value":"42.00"}}],
                         "application_context":{"return_url":"http://localhost/return",
                          "cancel_url":"http://localhost/cancel","user_action":"PAY_NOW"}}
                        """))
                .andRespond(withSuccess("""
                        {"id":"ORDER-17","links":[
                          {"rel":"approve","href":"https://www.sandbox.paypal.com/checkoutnow?token=ORDER-17"}]}
                        """, MediaType.APPLICATION_JSON));
        PayPalClient client = new PayPalClient(auth, builder.build(), "http://localhost/return", "http://localhost/cancel");

        PaymentGateway.OrderCreation result = client.createOrder(17L, new BigDecimal("42.00"), "USD");

        assertThat(result.orderId()).isEqualTo("ORDER-17");
        assertThat(result.approvalUrl()).isEqualTo("https://www.sandbox.paypal.com/checkoutnow?token=ORDER-17");
        server.verify();
    }

    @Test
    void capturesJavaScriptCheckoutOrderAndReturnsCaptureEvidence() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api-m.sandbox.paypal.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PayPalAuthService auth = authWithToken();
        server.expect(requestTo("https://api-m.sandbox.paypal.com/v2/checkout/orders/ORDER-17/capture"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("PayPal-Request-Id", "spw-capture-17"))
                .andRespond(withSuccess("""
                        {"purchase_units":[{"payments":{"captures":[
                          {"id":"CAPTURE-17","status":"COMPLETED",
                           "amount":{"value":"42.00","currency_code":"USD"}}]}}]}
                        """, MediaType.APPLICATION_JSON));
        PayPalClient client = new PayPalClient(auth, builder.build(), "http://localhost/return", "http://localhost/cancel");

        PaymentGateway.CaptureResult result = client.captureOrder(17L, "ORDER-17");

        assertThat(result.completed()).isTrue();
        assertThat(result.captureId()).isEqualTo("CAPTURE-17");
        assertThat(result.amount()).isEqualByComparingTo("42.00");
        assertThat(result.currencyCode()).isEqualTo("USD");
        server.verify();
    }

    @Test
    void startsPayPalVaultSetupWithImmediateMerchantConsentContext() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api-m.sandbox.paypal.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PayPalAuthService auth = authWithToken();
        server.expect(requestTo("https://api-m.sandbox.paypal.com/v3/vault/setup-tokens"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("PayPal-Request-Id", "spw-vault-setup-00000000-0000-0000-0000-000000000001"))
                .andExpect(content().json("""
                        {"customer":{"merchant_customer_id":"spw-customer"},
                         "payment_source":{"paypal":{"description":"Spendwise saved PayPal account",
                           "usage_pattern":"IMMEDIATE","usage_type":"MERCHANT","customer_type":"CONSUMER",
                           "experience_context":{"return_url":"https://spendwise.example/vault/return",
                             "cancel_url":"https://spendwise.example/vault/cancel",
                             "shipping_preference":"NO_SHIPPING",
                             "payment_method_preference":"IMMEDIATE_PAYMENT_REQUIRED"}}}}
                        """))
                .andRespond(withSuccess("""
                        {"id":"SETUP-1","customer":{"id":"CUSTOMER-1"},"links":[
                          {"rel":"approve","href":"https://sandbox.paypal.com/agreements/approve?token=SETUP-1"}]}
                        """, MediaType.APPLICATION_JSON));
        PayPalClient client = new PayPalClient(auth, builder.build(),
                "https://spendwise.example/vault/return", "https://spendwise.example/vault/cancel");

        var result = client.createVaultSetup("spw-customer",
                UUID.fromString("00000000-0000-0000-0000-000000000001"));

        assertThat(result.setupTokenId()).isEqualTo("SETUP-1");
        assertThat(result.customerId()).isEqualTo("CUSTOMER-1");
        assertThat(result.approvalUrl()).isEqualTo("https://sandbox.paypal.com/agreements/approve?token=SETUP-1");
        server.verify();
    }

    @Test
    void exchangesApprovedSetupTokenForVaultPaymentToken() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api-m.sandbox.paypal.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PayPalAuthService auth = authWithToken();
        server.expect(requestTo("https://api-m.sandbox.paypal.com/v3/vault/payment-tokens"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("PayPal-Request-Id", "spw-vault-token-00000000-0000-0000-0000-000000000001"))
                .andExpect(content().json("""
                        {"payment_source":{"token":{"id":"SETUP-1","type":"SETUP_TOKEN"}}}
                        """))
                .andRespond(withSuccess("""
                        {"id":"VAULT-9","customer":{"id":"CUSTOMER-1"},
                         "payment_source":{"paypal":{"email_address":"buyer@example.com"}}}
                        """, MediaType.APPLICATION_JSON));
        PayPalClient client = new PayPalClient(auth, builder.build(), "http://localhost/return", "http://localhost/cancel");

        var result = client.createVaultPaymentToken("SETUP-1",
                UUID.fromString("00000000-0000-0000-0000-000000000001"));

        assertThat(result.vaultId()).isEqualTo("VAULT-9");
        assertThat(result.customerId()).isEqualTo("CUSTOMER-1");
        assertThat(result.email()).isEqualTo("buyer@example.com");
        server.verify();
    }

    @Test
    void deletesTheVaultPaymentTokenAtPayPal() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api-m.sandbox.paypal.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api-m.sandbox.paypal.com/v3/vault/payment-tokens/VAULT-9"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent());
        PayPalClient client = new PayPalClient(authWithToken(), builder.build(),
                "http://localhost/return", "http://localhost/cancel");

        client.deleteVaultPaymentToken("VAULT-9");

        server.verify();
    }

    @Test
    void chargesFromVaultAndReturnsVerifiedCaptureFields() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api-m.sandbox.paypal.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PayPalAuthService auth = authWithToken();
        server.expect(requestTo("https://api-m.sandbox.paypal.com/v2/checkout/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("PayPal-Request-Id", "spw-vault-order-42"))
                .andExpect(header("PayPal-Client-Metadata-Id", "client-metadata-1"))
                .andExpect(content().json("""
                        {"intent":"CAPTURE","payment_source":{"paypal":{"vault_id":"VAULT-9"}},
                         "purchase_units":[{"reference_id":"spendwise-42",
                           "description":"Spendwise Sandbox demonstration payment",
                           "amount":{"currency_code":"USD","value":"42.00"}}]}
                        """))
                .andRespond(withSuccess("""
                        {"id":"ORDER-VAULT","status":"COMPLETED","purchase_units":[{"payments":{"captures":[
                          {"id":"CAPTURE-9","status":"COMPLETED",
                           "amount":{"value":"42.00","currency_code":"USD"}}]}}]}
                        """, MediaType.APPLICATION_JSON));
        PayPalClient client = new PayPalClient(auth, builder.build(), "http://localhost/return", "http://localhost/cancel");

        var result = client.createOrderWithVault(42L, new BigDecimal("42.00"), "USD", "VAULT-9", "client-metadata-1");

        assertThat(result.orderId()).isEqualTo("ORDER-VAULT");
        assertThat(result.capture().completed()).isTrue();
        assertThat(result.capture().captureId()).isEqualTo("CAPTURE-9");
        assertThat(result.capture().amount()).isEqualByComparingTo("42.00");
        assertThat(result.capture().currencyCode()).isEqualTo("USD");
        server.verify();
    }

    @Test
    void returnsPayerActionUrlWhenVaultChargeNeedsAdditionalApproval() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api-m.sandbox.paypal.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PayPalAuthService auth = authWithToken();
        server.expect(requestTo("https://api-m.sandbox.paypal.com/v2/checkout/orders"))
                .andRespond(withSuccess("""
                        {"id":"ORDER-ACTION","status":"PAYER_ACTION_REQUIRED","links":[
                          {"rel":"payer-action","href":"https://www.sandbox.paypal.com/checkoutnow?token=ORDER-ACTION"}]}
                        """, MediaType.APPLICATION_JSON));
        PayPalClient client = new PayPalClient(auth, builder.build(), "http://localhost/return", "http://localhost/cancel");

        var result = client.createOrderWithVault(9L, new BigDecimal("42.00"), "USD", "VAULT-9", "metadata-9");

        assertThat(result.orderId()).isEqualTo("ORDER-ACTION");
        assertThat(result.approvalUrl()).isEqualTo("https://www.sandbox.paypal.com/checkoutnow?token=ORDER-ACTION");
        assertThat(result.capture()).isNull();
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

    private PayPalAuthService authWithToken() {
        return new PayPalAuthService(new SimpleClientHttpRequestFactory(),
                "https://api-m.sandbox.paypal.com", "test-client", "test-secret") {
            @Override
            public String accessToken() {
                return "access-token";
            }
        };
    }
}
