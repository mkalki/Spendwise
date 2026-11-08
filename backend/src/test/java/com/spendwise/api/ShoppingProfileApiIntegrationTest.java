package com.spendwise.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spendwise.common.exception.ApiExceptionHandler;
import com.spendwise.product.controller.ShoppingProfileController;
import com.spendwise.product.dto.ShoppingPreferencesRequest;
import com.spendwise.product.dto.ShoppingPreferencesResponse;
import com.spendwise.product.dto.ShoppingSearchHistoryResponse;
import com.spendwise.product.service.ShoppingProfileService;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ShoppingProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
@TestPropertySource(properties = {
    "spring.security.oauth2.client.registration.google.client-id=test-client-id",
    "spring.security.oauth2.client.registration.google.client-secret=test-client-secret"
})
class ShoppingProfileApiIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private ShoppingProfileService profileService;

    @Test
    void getsPreferencesForAuthenticatedOwner() throws Exception {
        when(profileService.getPreferences("owner-1")).thenReturn(new ShoppingPreferencesResponse(
                List.of("laptops"), List.of("Lenovo"), List.of(), List.of("lightweight"),
                new BigDecimal("1200.00"), "USD", Instant.parse("2026-11-08T00:00:00Z")));

        mockMvc.perform(get("/api/v1/shopping/preferences").with(authenticatedAsOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredCategories[0]").value("laptops"))
                .andExpect(jsonPath("$.preferredBrands[0]").value("Lenovo"))
                .andExpect(jsonPath("$.maxPrice").value(1200.0))
                .andExpect(jsonPath("$.currencyCode").value("USD"));
    }

    @Test
    void savesPreferencesForAuthenticatedOwner() throws Exception {
        when(profileService.savePreferences(eq("owner-1"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ShoppingPreferencesResponse(
                        List.of("laptops"), List.of("Lenovo"), List.of("store.example"),
                        List.of("lightweight"), new BigDecimal("1200.00"), "USD", null));

        mockMvc.perform(put("/api/v1/shopping/preferences").with(authenticatedAsOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preferredCategories":["laptops"],"preferredBrands":["Lenovo"],
                                 "preferredRetailers":["store.example"],"keywords":["lightweight"],
                                 "maxPrice":1200.00,"currencyCode":"USD"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredRetailers[0]").value("store.example"));
        verify(profileService).savePreferences(eq("owner-1"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listsOnlyAuthenticatedOwnersSearchHistoryWithPagination() throws Exception {
        when(profileService.history("owner-1", 1, 5)).thenReturn(new ShoppingSearchHistoryResponse(
                List.of(new ShoppingSearchHistoryResponse.SearchView("session-1", "laptop", "laptop",
                        "1200.00", Instant.parse("2026-11-08T00:00:00Z"))), 1, 5, 7));

        mockMvc.perform(get("/api/v1/shopping/search-history?page=1&size=5").with(authenticatedAsOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.searches[0].query").value("laptop"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(7));
        verify(profileService).history("owner-1", 1, 5);
    }

    @Test
    void rejectsOutOfRangeHistoryPageSize() throws Exception {
        mockMvc.perform(get("/api/v1/shopping/search-history?size=101").with(authenticatedAsOwner()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private RequestPostProcessor authenticatedAsOwner() {
        OidcUser user = mock(OidcUser.class);
        when(user.getName()).thenReturn("owner-1");
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
