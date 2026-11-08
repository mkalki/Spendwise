package com.spendwise.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spendwise.checkout.dto.CheckoutResponse;
import com.spendwise.checkout.dto.TransactionHistoryResponse;
import com.spendwise.checkout.dto.TransactionSummaryResponse;
import com.spendwise.checkout.service.CheckoutService;
import com.spendwise.payment.entity.PaymentTransaction;
import com.spendwise.integration.gemini.GeminiService;
import com.spendwise.integration.gemini.dto.GeminiRequest;
import com.spendwise.integration.gemini.dto.GeminiResponse;
import com.spendwise.auth.service.AccountService;
import com.spendwise.auth.service.SessionAuthenticationService;
import com.spendwise.policy.dto.SpendingPolicyRequest;
import com.spendwise.policy.entity.SpendingPolicy;
import com.spendwise.policy.service.SpendingPolicyService;
import com.spendwise.product.dto.ShoppingSearchRequest;
import com.spendwise.product.dto.ShoppingSearchResponse;
import com.spendwise.product.service.ProductSearchService;
import com.spendwise.product.service.ShoppingProfileService;
import com.spendwise.common.security.Channel3UserRateLimiter;
import com.spendwise.common.security.GeminiUserRateLimiter;
import com.spendwise.payment.integration.PayPalWebhookVerifier;
import com.spendwise.payment.service.PayPalWebhookService;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;

@WebMvcTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "spring.security.oauth2.client.registration.google.client-id=test-client-id",
    "spring.security.oauth2.client.registration.google.client-secret=test-client-secret"
})
class SpendwiseApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpendingPolicyService policyService;

    @MockitoBean
    private ProductSearchService productSearchService;

    @MockitoBean
    private ShoppingProfileService shoppingProfileService;

    @MockitoBean
    private CheckoutService checkoutService;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private SessionAuthenticationService sessionAuthenticationService;

    @MockitoBean
    private GeminiService geminiService;

    @MockitoBean
    private Channel3UserRateLimiter channel3UserRateLimiter;

    @MockitoBean
    private GeminiUserRateLimiter geminiUserRateLimiter;

    @MockitoBean
    private PayPalWebhookVerifier payPalWebhookVerifier;

    @MockitoBean
    private PayPalWebhookService payPalWebhookService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void policyPutValidatesAndReturnsThePublicContract() throws Exception {
        SpendingPolicy policy = policy();
        when(policyService.save(eq("google-subject"), any(SpendingPolicyRequest.class))).thenReturn(policy);

        mockMvc.perform(put("/api/v1/policies/active")
                        .with(authenticatedAs("google-subject"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "maxProductPrice":"60.00",
                                  "maxOrderAmount":"100.00",
                                  "weeklyBudget":"150.00",
                                  "maxOrdersPerWeek":1,
                                  "countryCode":"US",
                                  "currencyCode":"USD",
                                  "timezone":"America/Los_Angeles"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxProductPrice").value("60.00"))
                .andExpect(jsonPath("$.currencyCode").value("USD"));

        verify(policyService).save(eq("google-subject"), any(SpendingPolicyRequest.class));
    }

    @Test
    void invalidPolicyRequestDoesNotCallTheService() throws Exception {
        mockMvc.perform(put("/api/v1/policies/active")
                        .with(authenticatedAs("google-subject"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "maxProductPrice":"0.00",
                                  "maxOrderAmount":"100.00",
                                  "weeklyBudget":"150.00",
                                  "maxOrdersPerWeek":1,
                                  "countryCode":"US",
                                  "currencyCode":"USD",
                                  "timezone":"UTC"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void searchUsesAuthenticatedOwnerAndNaturalLanguageMessage() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(productSearchService.search(eq("google-subject"), any(ShoppingSearchRequest.class)))
                .thenReturn(new ShoppingSearchResponse(
                        sessionId,
                        new ShoppingSearchResponse.ShoppingIntent(
                                "wireless headphones under $50",
                                "wireless headphones",
                                "50",
                                "USD",
                                "US"),
                        List.of(),
                        List.of(),
                        null,
                        com.spendwise.product.dto.ShoppingSortBy.LOWEST_PRICE));

        mockMvc.perform(post("/api/v1/shopping/search")
                        .with(authenticatedAs("google-subject"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"wireless headphones under $50\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.searchSessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.intent.requestedMaxPrice").value("50"))
                .andExpect(jsonPath("$.eligibleCandidates").isArray());

        verify(productSearchService).search(eq("google-subject"), any(ShoppingSearchRequest.class));
    }

    @Test
    void searchReturnsEmptyListsWhenThereAreNoMatches() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(productSearchService.search(eq("google-subject"), any(ShoppingSearchRequest.class)))
                .thenReturn(new ShoppingSearchResponse(
                        sessionId,
                        new ShoppingSearchResponse.ShoppingIntent("wireless headphones", "wireless headphones",
                                null, "USD", "US"),
                        List.of(), List.of(), null,
                        com.spendwise.product.dto.ShoppingSortBy.LOWEST_PRICE));

        mockMvc.perform(post("/api/v1/shopping/search")
                        .with(authenticatedAs("google-subject"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"wireless headphones\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligibleCandidates").isEmpty())
                .andExpect(jsonPath("$.rejectedCandidates").isEmpty())
                .andExpect(jsonPath("$.recommendation").doesNotExist());
    }

    @Test
    void searchReturnsStableProviderErrorContract() throws Exception {
        when(productSearchService.search(eq("google-subject"), any(ShoppingSearchRequest.class)))
                .thenThrow(new com.spendwise.common.exception.ApiException(
                        org.springframework.http.HttpStatus.BAD_GATEWAY,
                        "PRODUCT_PROVIDER_ERROR",
                        "The product provider could not complete the search."));

        mockMvc.perform(post("/api/v1/shopping/search")
                        .with(authenticatedAs("google-subject"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"wireless headphones\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PRODUCT_PROVIDER_ERROR"));
    }

    @Test
    void geminiGenerateEndpointUsesAuthenticatedApiContract() throws Exception {
        when(geminiService.generate(new GeminiRequest("Say hello.")))
                .thenReturn(new GeminiResponse("gemini-3.8-flash", "Hello from Gemini."));

        mockMvc.perform(post("/api/v1/ai/generate")
                        .with(authenticatedAs("google-subject"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Say hello.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("gemini-3.8-flash"))
                .andExpect(jsonPath("$.text").value("Hello from Gemini."));

        verify(geminiService).generate(new GeminiRequest("Say hello."));
    }

    @Test
    void geminiGenerateEndpointValidatesPrompt() throws Exception {
        mockMvc.perform(post("/api/v1/ai/generate")
                        .with(authenticatedAs("google-subject"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }


    @Test
    void checkoutAcceptsCandidateIdsAndIdempotencyKeyButNoFrontendPrice() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        when(checkoutService.create("google-subject", "frontend-key-1", sessionId, candidateId))
                .thenReturn(new CheckoutResponse(
                        "101",
                        "APPROVAL_PENDING",
                        "45.00",
                        "USD",
                        "https://www.sandbox.paypal.com/checkoutnow?token=order",
                        new CheckoutResponse.PolicyResult(true, "Allowed")));

        mockMvc.perform(post("/api/v1/checkout-sessions")
                        .with(authenticatedAs("google-subject"))
                        .header("Idempotency-Key", "frontend-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "searchSessionId":"%s",
                                  "candidateId":"%s"
                                }
                                """.formatted(sessionId, candidateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutSessionId").value("101"))
                .andExpect(jsonPath("$.amount").value("45.00"))
                .andExpect(jsonPath("$.status").value("APPROVAL_PENDING"));

        verify(checkoutService).create("google-subject", "frontend-key-1", sessionId, candidateId);
    }

    @Test
    void paymentCreateReturnsPayPalApprovalUrlAndPersistedOrderId() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        when(checkoutService.create("google-subject", "payment-key", sessionId, candidateId))
                .thenReturn(new CheckoutResponse("101", "APPROVAL_PENDING", "45.00", "USD",
                        "https://www.sandbox.paypal.com/checkoutnow?token=order-123",
                        new CheckoutResponse.PolicyResult(true, "Allowed")));
        when(checkoutService.getOwnedPaymentById("google-subject", 101L)).thenReturn(payment());

        mockMvc.perform(post("/api/payments/create")
                        .with(authenticatedAs("google-subject"))
                        .header("Idempotency-Key", "payment-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"searchSessionId":"%s","candidateId":"%s"}
                                """.formatted(sessionId, candidateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order-123"))
                .andExpect(jsonPath("$.approvalUrl").value(
                        "https://www.sandbox.paypal.com/checkoutnow?token=order-123"))
                .andExpect(jsonPath("$.amount").value("45.00"));
    }

    @Test
    void paymentCaptureDelegatesToOwnerScopedCheckoutAndReturnsCaptureId() throws Exception {
        when(checkoutService.getOwnedPayment("google-subject", "order-123"))
                .thenReturn(payment(), completedPayment());

        mockMvc.perform(post("/api/payments/order-123/capture")
                        .with(authenticatedAs("google-subject")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CAPTURED"))
                .andExpect(jsonPath("$.captureId").value("capture-123"));

        verify(checkoutService).capture("google-subject", 101L);
    }

    @Test
    void paymentStatusReturnsPersistedPaymentState() throws Exception {
        when(checkoutService.getPaymentByPaypalOrderId("google-subject", "order-123"))
                .thenReturn(completedPayment());

        mockMvc.perform(get("/api/payments/order-123").with(authenticatedAs("google-subject")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CAPTURED"))
                .andExpect(jsonPath("$.orderId").value("order-123"));
    }

    @Test
    void transactionHistoryAndSummaryExposeFrontendData() throws Exception {
        when(checkoutService.history("google-subject"))
                .thenReturn(new TransactionHistoryResponse(List.of()));
        when(checkoutService.summary("google-subject"))
                .thenReturn(new TransactionSummaryResponse(
                        "2026-11-02T07:00:00Z",
                        "2026-11-09T08:00:00Z",
                        "25.00",
                        "10.00",
                        "115.00",
                        1,
                        1,
                        3,
                        "USD"));

        mockMvc.perform(get("/api/v1/transactions").with(authenticatedAs("google-subject")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions").isArray());
        mockMvc.perform(get("/api/v1/transactions/summary").with(authenticatedAs("google-subject")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingBudget").value("115.00"))
                .andExpect(jsonPath("$.activeReservationCount").value(1));
    }

    private SpendingPolicy policy() {
        return new SpendingPolicy(
                "google-subject",
                new BigDecimal("60.00"),
                new BigDecimal("100.00"),
                new BigDecimal("150.00"),
                1,
                "USD",
                "US",
                "America/Los_Angeles");
    }

    private PaymentTransaction payment() {
        PaymentTransaction payment = new PaymentTransaction(
                "google-subject", "product", "Example Product", "merchant", "https://merchant.test/product",
                new BigDecimal("45.00"), "USD", "payment-key", UUID.randomUUID(),
                java.time.Instant.now().plusSeconds(1800));
        payment.paypalOrderCreated("order-123", "https://www.sandbox.paypal.com/checkoutnow?token=order-123");
        try {
            var id = PaymentTransaction.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(payment, 101L);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
        return payment;
    }

    private PaymentTransaction completedPayment() {
        PaymentTransaction payment = payment();
        payment.markCompleted("capture-123", java.time.Instant.now());
        return payment;
    }

    private RequestPostProcessor authenticatedAs(String subject) {
        OidcUser user = org.mockito.Mockito.mock(OidcUser.class);
        org.mockito.Mockito.when(user.getSubject()).thenReturn(subject);
        org.mockito.Mockito.when(user.getName()).thenReturn(subject);
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(user, null, "ROLE_USER");
        return request -> {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            ((MockHttpServletRequest) request).setUserPrincipal((Principal) authentication);
            return request;
        };
    }
}
