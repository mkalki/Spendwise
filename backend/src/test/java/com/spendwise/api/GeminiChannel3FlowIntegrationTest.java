package com.spendwise.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.audit.service.AuditService;
import com.spendwise.common.security.Channel3UserRateLimiter;
import com.spendwise.common.security.GeminiUserRateLimiter;
import com.spendwise.integration.gemini.GeminiService;
import com.spendwise.integration.gemini.dto.GeminiShoppingIntent;
import com.spendwise.integration.gemini.dto.GeminiRecommendationCandidate;
import com.spendwise.integration.gemini.dto.GeminiRecommendationResult;
import com.spendwise.product.dto.ShoppingPreferencesResponse;
import com.spendwise.policy.engine.PolicyEngine;
import com.spendwise.policy.entity.SpendingPolicy;
import com.spendwise.policy.service.SpendingPolicyService;
import com.spendwise.product.integration.Channel3Client;
import com.spendwise.product.repository.ProductCandidateRepository;
import com.spendwise.product.repository.ShoppingSearchSessionRepository;
import com.spendwise.product.service.ProductSearchService;
import com.spendwise.product.service.ShoppingIntentService;
import com.spendwise.product.service.ShoppingProfileService;
import java.math.BigDecimal;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

@WebMvcTest(controllers = com.spendwise.product.controller.ShoppingController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "spring.security.oauth2.client.registration.google.client-id=test-client-id",
    "spring.security.oauth2.client.registration.google.client-secret=test-client-secret"
})
@Import({
    ProductSearchService.class,
    ShoppingIntentService.class,
    PolicyEngine.class,
    Channel3UserRateLimiter.class
})
class GeminiChannel3FlowIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private GeminiService geminiService;
    @MockitoBean private Channel3Client channel3Client;
    @MockitoBean private GeminiUserRateLimiter geminiUserRateLimiter;
    @MockitoBean private SpendingPolicyService policyService;
    @MockitoBean private ShoppingSearchSessionRepository sessionRepository;
    @MockitoBean private ProductCandidateRepository candidateRepository;
    @MockitoBean private PlatformTransactionManager transactionManager;
    @MockitoBean private AuditService auditService;
    @MockitoBean private ShoppingProfileService shoppingProfileService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(policyService.getActive("owner-1")).thenReturn(new SpendingPolicy(
                "owner-1", new BigDecimal("100.00"), new BigDecimal("150.00"),
                new BigDecimal("300.00"), 3, "USD", "US", "UTC"));
    }

    @Test
    void interpretsShoppingRequestWithGeminiThenSendsStructuredQueryToChannel3() throws Exception {
        when(geminiService.extractShoppingIntent("Find a laptop under $50 for coding"))
                .thenReturn(new GeminiShoppingIntent(
                        "laptop for programming", "laptop", new BigDecimal("50.00"), "USD",
                        "programming", java.util.List.of("performance")));
        when(channel3Client.search(anyString(), any(), anyString(), anyString()))
                .thenReturn(objectMapper.readTree("""
                        {"products":[{"id":"laptop-1","title":"Example Laptop",
                          "images":[{"url":"https://images.example/laptop.jpg"}],
                          "offers":[{"domain":"shop.example","url":"https://shop.example/laptop",
                            "availability":"InStock","price":{"price":45.00,"currency":"USD"}}]}]}
                        """));

        mockMvc.perform(post("/api/v1/shopping/search")
                        .with(authenticatedAsOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Find a laptop under $50 for coding\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent.normalizedQuery").value("laptop for programming"))
                .andExpect(jsonPath("$.intent.requestedMaxPrice").value("50"))
                .andExpect(jsonPath("$.eligibleCandidates[0].productTitle").value("Example Laptop"))
                .andExpect(jsonPath("$.eligibleCandidates[0].price").value("45.00"));

        var sequence = inOrder(geminiService, channel3Client);
        sequence.verify(geminiService).extractShoppingIntent("Find a laptop under $50 for coding");
        sequence.verify(channel3Client).search(
                org.mockito.ArgumentMatchers.eq("laptop for programming"),
                argThat(price -> price != null && price.compareTo(new BigDecimal("50.00")) == 0),
                org.mockito.ArgumentMatchers.eq("US"),
                org.mockito.ArgumentMatchers.eq("USD"));
    }

    @Test
    void appliesCustomerSortChoiceToChannel3Results() throws Exception {
        when(geminiService.extractShoppingIntent("Find a laptop for coding"))
                .thenReturn(new GeminiShoppingIntent(
                        "laptop for coding", "laptop", null, null, "coding", java.util.List.of()));
        when(channel3Client.searchWithFilters(anyString(), anyMap(), anyString(), anyString()))
                .thenReturn(objectMapper.readTree("""
                        {"products":[
                          {"id":"match-first","title":"Coding Laptop Pro",
                           "offers":[{"domain":"store.example","url":"https://store.example/pro",
                             "availability":"InStock","price":{"price":80.00,"currency":"USD"}}]},
                          {"id":"cheap-second","title":"Basic Laptop",
                           "offers":[{"domain":"store.example","url":"https://store.example/basic",
                             "availability":"InStock","price":{"price":30.00,"currency":"USD"}}]}
                        ]}
                        """));

        mockMvc.perform(post("/api/v1/shopping/search")
                        .with(authenticatedAsOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Find a laptop for coding",
                                 "sortBy":"BEST_MATCH",
                                 "filters":{"minPrice":50,"maxPrice":100,
                                   "categoryIds":["laptops"],"websiteIds":["shop.example"],
                                   "availability":"IN_STOCK","onlyOnSale":true}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligibleCandidates.length()").value(1))
                .andExpect(jsonPath("$.eligibleCandidates[0].channel3ProductId").value("match-first"))
                .andExpect(jsonPath("$.eligibleCandidates[0].price").value("80.00"));

        org.mockito.ArgumentCaptor<java.util.Map<String, Object>> filters =
                org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        org.mockito.Mockito.verify(channel3Client).searchWithFilters(
                org.mockito.ArgumentMatchers.eq("laptop for coding"),
                filters.capture(),
                org.mockito.ArgumentMatchers.eq("US"),
                org.mockito.ArgumentMatchers.eq("USD"));
        org.assertj.core.api.Assertions.assertThat(filters.getValue())
                .containsEntry("category_ids", java.util.List.of("laptops"))
                .containsEntry("website_ids", java.util.List.of("shop.example"))
                .containsEntry("availability", java.util.List.of("InStock"))
                .containsEntry("sale", "on_sale");
        java.util.Map<?, ?> price = (java.util.Map<?, ?>) filters.getValue().get("price");
        org.assertj.core.api.Assertions.assertThat((BigDecimal) price.get("min_price"))
                .isEqualByComparingTo("50");
        org.assertj.core.api.Assertions.assertThat((BigDecimal) price.get("max_price"))
                .isEqualByComparingTo("100");
    }

    @Test
    void aiRecommendationsOnlyReorderAndReturnFactsFromChannel3() throws Exception {
        String message = "Find a laptop for coding";
        when(geminiService.extractShoppingIntent(message))
                .thenReturn(new GeminiShoppingIntent(
                        "laptop for coding", "laptop", null, null, "coding", java.util.List.of()));
        when(channel3Client.search(anyString(), any(), anyString(), anyString()))
                .thenReturn(objectMapper.readTree("""
                        {"products":[
                          {"id":"channel3-laptop-a","title":"Laptop A",
                           "brands":[{"name":"Brand A"}],"offers":[
                             {"domain":"merchant-a.example","url":"https://merchant-a.example/a",
                              "availability":"InStock","price":{"price":80.00,"currency":"USD"}}]},
                          {"id":"channel3-laptop-b","title":"Laptop B",
                           "brands":[{"name":"Brand B"}],"offers":[
                             {"domain":"merchant-b.example","url":"https://merchant-b.example/b",
                              "availability":"InStock","price":{"price":75.00,"currency":"USD"}}]}
                        ]}
                        """));
        when(geminiService.explainShoppingCandidates(org.mockito.ArgumentMatchers.eq(message), any(), any()))
                .thenAnswer(invocation -> {
                    java.util.List<GeminiRecommendationCandidate> supplied = invocation.getArgument(2);
                    java.util.List<java.util.UUID> ids = supplied.reversed().stream()
                            .map(GeminiRecommendationCandidate::candidateId).toList();
                    return new GeminiRecommendationResult(ids,
                            java.util.Map.of(ids.getFirst(), "Brand B matches your coding use case."),
                            "Laptop B is the closest match to your request.");
                });
        when(shoppingProfileService.getPreferences("owner-1"))
                .thenReturn(ShoppingPreferencesResponse.empty());

        mockMvc.perform(post("/api/v1/shopping/search")
                        .with(authenticatedAsOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Find a laptop for coding","sortBy":"AI_RECOMMENDED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligibleCandidates.length()").value(2))
                .andExpect(jsonPath("$.eligibleCandidates[0].channel3ProductId").value("channel3-laptop-b"))
                .andExpect(jsonPath("$.eligibleCandidates[0].productTitle").value("Laptop B"))
                .andExpect(jsonPath("$.eligibleCandidates[0].brand").value("Brand B"))
                .andExpect(jsonPath("$.eligibleCandidates[0].merchantName").value("merchant-b.example"))
                .andExpect(jsonPath("$.eligibleCandidates[0].price").value("75.00"))
                .andExpect(jsonPath("$.eligibleCandidates[0].offerUrl")
                        .value("https://merchant-b.example/b"))
                .andExpect(jsonPath("$.eligibleCandidates[0].recommendationExplanation")
                        .value("Brand B matches your coding use case."))
                .andExpect(jsonPath("$.recommendation").value("Laptop B is the closest match to your request."))
                .andExpect(jsonPath("$.comparisonSummary").value("Laptop B is the closest match to your request."));
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
