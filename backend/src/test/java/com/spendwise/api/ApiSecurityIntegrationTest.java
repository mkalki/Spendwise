package com.spendwise.api;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spendwise.auth.service.AccountOidcUserService;
import com.spendwise.common.security.SecurityConfiguration;
import com.spendwise.payment.integration.PayPalWebhookVerifier;
import com.spendwise.payment.service.PayPalWebhookService;
import com.spendwise.payment.controller.PayPalWebhookController;
import com.spendwise.policy.controller.SpendingPolicyController;
import com.spendwise.policy.entity.SpendingPolicy;
import com.spendwise.policy.service.SpendingPolicyService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {SpendingPolicyController.class, PayPalWebhookController.class})
@Import(SecurityConfiguration.class)
@TestPropertySource(properties = {
    "spring.security.oauth2.client.registration.google.client-id=test-client-id",
    "spring.security.oauth2.client.registration.google.client-secret=test-client-secret"
})
class ApiSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpendingPolicyService policyService;

    @MockitoBean
    private AccountOidcUserService accountOidcUserService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private PayPalWebhookVerifier webhookVerifier;

    @MockitoBean
    private PayPalWebhookService webhookService;

    @Test
    void versionedApiRejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/policies/active"))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                        .value("UNAUTHENTICATED"));
    }

    @Test
    void versionedApiUsesAuthenticatedGoogleSubjectAsOwner() throws Exception {
        when(policyService.getActive("google-subject"))
                .thenReturn(new SpendingPolicy(
                        "google-subject",
                        new BigDecimal("60.00"),
                        new BigDecimal("100.00"),
                        new BigDecimal("150.00"),
                        1,
                        "USD",
                        "US",
                        "UTC"));

        mockMvc.perform(get("/api/v1/policies/active")
                        .with(oidcLogin().idToken(token -> token.subject("google-subject"))))
                .andExpect(status().isOk());
    }

    @Test
    void stateChangingRequestsRequireCsrfProtection() throws Exception {
        mockMvc.perform(put("/api/v1/policies/active")
                        .with(oidcLogin().idToken(token -> token.subject("google-subject")))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                        .value("ACCESS_DENIED"));
    }

    @Test
    void publicPaypalWebhookDoesNotRequireSessionOrCsrfToken() throws Exception {
        when(webhookVerifier.verify(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/webhooks/paypal")
                        .header("PayPal-Auth-Algo", "SHA256withRSA")
                        .header("PayPal-Cert-Url", "https://api-m.paypal.com/cert")
                        .header("PayPal-Transmission-Id", "transmission-1")
                        .header("PayPal-Transmission-Sig", "signature")
                        .header("PayPal-Transmission-Time", "2026-11-08T10:00:00Z")
                        .contentType("application/json")
                        .content("{\"id\":\"event-1\",\"event_type\":\"IGNORED\",\"resource\":{}}"))
                .andExpect(status().isNoContent());
    }
}
