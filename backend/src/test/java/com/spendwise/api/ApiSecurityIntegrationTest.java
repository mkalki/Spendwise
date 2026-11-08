package com.spendwise.api;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spendwise.auth.service.AccountOidcUserService;
import com.spendwise.common.security.SecurityConfiguration;
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
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SpendingPolicyController.class)
@Import(SecurityConfiguration.class)
class ApiSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpendingPolicyService policyService;

    @MockitoBean
    private AccountOidcUserService accountOidcUserService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void versionedApiRejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/policies/active"))
                .andExpect(status().isUnauthorized());
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
                .andExpect(status().isForbidden());
    }
}
