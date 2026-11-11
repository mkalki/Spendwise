package com.spendwise.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spendwise.product.entity.ProductCandidate;
import com.spendwise.product.entity.ShoppingPreferences;
import com.spendwise.product.entity.ShoppingSearchSession;
import com.spendwise.product.dto.ShoppingPreferencesResponse;
import com.spendwise.audit.service.AuditService;
import com.spendwise.audit.repository.AuditEventRepository;
import com.spendwise.product.repository.ShoppingPreferencesRepository;
import com.spendwise.product.repository.ShoppingSearchSessionRepository;
import com.spendwise.product.repository.ProductCandidateRepository;
import com.spendwise.product.service.ProductSearchService;
import com.spendwise.product.service.ShoppingProfileService;
import com.spendwise.product.integration.Channel3Client;
import com.spendwise.integration.gemini.GeminiService;
import com.spendwise.integration.gemini.dto.GeminiRecommendationCandidate;
import com.spendwise.integration.gemini.dto.GeminiRecommendationResult;
import com.spendwise.integration.gemini.dto.GeminiShoppingIntent;
import com.spendwise.policy.dto.SpendingPolicyRequest;
import com.spendwise.policy.service.SpendingPolicyService;
import com.spendwise.payment.repository.PaymentTransactionRepository;
import com.spendwise.auth.entity.UserAccount;
import com.spendwise.auth.repository.UserAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.security.oauth2.client.registration.google.client-id=test-client-id",
    "spring.security.oauth2.client.registration.google.client-secret=test-client-secret"
})
class ShoppingPersistenceIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("spendwise")
            .withUsername("spendwise")
            .withPassword("spendwise-test");

    @Autowired private ShoppingPreferencesRepository preferencesRepository;
    @Autowired private ShoppingSearchSessionRepository searchSessionRepository;
    @Autowired private ProductCandidateRepository candidateRepository;
    @Autowired private ProductSearchService productSearchService;
    @Autowired private SpendingPolicyService spendingPolicyService;
    @Autowired private ShoppingProfileService shoppingProfileService;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private AuditService auditService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository userAccountRepository;
    @MockitoBean private GeminiService geminiService;
    @MockitoBean private Channel3Client channel3Client;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void cleanRows() {
        candidateRepository.deleteAll();
        searchSessionRepository.deleteAll();
        preferencesRepository.deleteAll();
        auditEventRepository.deleteAll();
    }

    @Test
    void cartAndCheckoutDetailsFollowTheSameEmailAcrossSessionsAndStayPrivate() throws Exception {
        var first = googleSession("cart-owner@example.com", "google-cart-owner");
        String owner = objectMapper.readTree(mvc.perform(get("/api/auth/me").session(first))
                .andReturn().getResponse().getContentAsString()).path("id").asText();
        var search = searchSessionRepository.save(new ShoppingSearchSession(
                UUID.randomUUID(), owner, "headphones", "headphones", new BigDecimal("100.00")));
        var candidate = candidateRepository.save(new ProductCandidate(UUID.randomUUID(), search, "catalog-id",
                "Studio headphones", "Acme", null, "store.example", "https://store.example/headphones",
                new BigDecimal("45.00"), "USD", true, null));
        var rejected = candidateRepository.save(new ProductCandidate(UUID.randomUUID(), search, "rejected-id",
                "Over budget", "Acme", null, "store.example", "https://store.example/rejected",
                new BigDecimal("120.00"), "USD", false, "Over budget"));
        String cartItem = "/api/v1/cart/items/" + candidate.getId();

        mvc.perform(put(cartItem).session(first)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/cart/items/" + rejected.getId()).session(first).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(put(cartItem).session(first).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(put(cartItem).session(first).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(put("/api/v1/cart/details").session(first).with(csrf()).contentType("application/json")
                .content("""
                        {"shipping":{"fullName":"Test Shopper","line1":"100 Test Street","city":"Example",
                          "countryCode":"US"},"billing":{"fullName":"Test Shopper","line1":"200 Billing Street",
                          "city":"Example","countryCode":"US"}}
                        """)).andExpect(status().isOk());

        var second = googleSession("cart-owner@example.com", "google-cart-owner");
        assertThat(second.getId()).isNotEqualTo(first.getId());
        mvc.perform(get("/api/v1/cart").session(second)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].candidateId").value(candidate.getId().toString()))
                .andExpect(jsonPath("$[0].searchSessionId").value(search.getId().toString()))
                .andExpect(jsonPath("$[0].price").value(45.0))
                .andExpect(jsonPath("$[0].currencyCode").value("USD"));
        mvc.perform(get("/api/v1/cart/details").session(second)).andExpect(status().isOk())
                .andExpect(jsonPath("$.shipping.line1").value("100 Test Street"))
                .andExpect(jsonPath("$.billing.line1").value("200 Billing Street"));

        var stranger = googleSession("cart-other@example.com", "google-cart-other");
        mvc.perform(get("/api/v1/cart").session(stranger)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/cart/details").session(stranger)).andExpect(jsonPath("$.shipping").doesNotExist());
        mvc.perform(put(cartItem).session(stranger).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete(cartItem).session(stranger).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/cart").session(first)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(delete(cartItem).session(second).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/cart").session(first)).andExpect(jsonPath("$.length()").value(0));
    }

    private MockHttpSession googleSession(String email, String subject) {
        userAccountRepository.findByGoogleSubject(subject).orElseGet(() -> userAccountRepository.saveAndFlush(
                UserAccount.googleAccount(subject, email, email, null)));
        var authentication = new TestingAuthenticationToken(new TestPrincipal(subject), null, "ROLE_USER");
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        return session;
    }

    private record TestPrincipal(String name) {
        public String getName() {
            return name;
        }
    }

    @Test
    void flywaySchemaPersistsPreferencesAndOwnerScopedSearchHistory() {
        preferencesRepository.save(new ShoppingPreferences("owner-a", "[\"laptops\"]",
                "[\"Lenovo\"]", "[]", "[\"lightweight\"]", new BigDecimal("1200.00"), "USD"));
        searchSessionRepository.save(new ShoppingSearchSession(UUID.randomUUID(), "owner-a", "laptop",
                "laptop", new BigDecimal("1200.00")));
        searchSessionRepository.save(new ShoppingSearchSession(UUID.randomUUID(), "owner-b", "camera",
                "camera", new BigDecimal("500.00")));

        var preference = preferencesRepository.findById("owner-a").orElseThrow();
        var history = searchSessionRepository.findByOwnerId("owner-a", PageRequest.of(0, 10,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        assertThat(preference.getPreferredCategories()).isEqualTo("[\"laptops\"]");
        assertThat(preference.getCurrencyCode()).isEqualTo("USD");
        assertThat(history.getContent()).hasSize(1);
        assertThat(history.getContent().getFirst().getOriginalQuery()).isEqualTo("laptop");
    }

    @Test
    void persistsAuditEventsAndStructuredDetailsInPostgresJsonb() {
        auditService.record("owner-a", "PAYMENT_CAPTURED", "capture-request-1",
                java.util.Map.of("checkoutId", 27, "currency", "USD"));

        var event = auditEventRepository.findAll().getFirst();
        assertThat(event.getOwnerId()).isEqualTo("owner-a");
        assertThat(event.getEventType()).isEqualTo("PAYMENT_CAPTURED");
        assertThat(event.getRequestId()).isEqualTo("capture-request-1");
        assertThat(event.getDetails().path("checkoutId").asInt()).isEqualTo(27);
        assertThat(event.getDetails().path("currency").asText()).isEqualTo("USD");
    }

    @Test
    void showsCheckoutBlocksAndEarlierSearchRejectionsOnlyToTheirOwner() throws Exception {
        String owner = "blocked-purchase-owner";
        spendingPolicyService.save(owner, new SpendingPolicyRequest(
                new BigDecimal("40.00"), new BigDecimal("60.00"), new BigDecimal("150.00"),
                3, "USD", "US", "UTC"));
        var search = searchSessionRepository.save(new ShoppingSearchSession(
                UUID.randomUUID(), owner, "headphones", "headphones", new BigDecimal("60.00")));
        var candidate = candidateRepository.save(new ProductCandidate(UUID.randomUUID(), search, "headphones",
                "Studio headphones", null, null, "store.example", "https://store.example/headphones",
                new BigDecimal("35.00"), "USD", true, null));
        candidateRepository.save(new ProductCandidate(UUID.randomUUID(), search, "unavailable",
                "Sold-out headphones", null, null, "store.example", "https://store.example/sold-out",
                new BigDecimal("25.00"), "USD", false, "Offer is not confirmed in stock."));
        when(channel3Client.getProduct("headphones", "US", "USD")).thenReturn(objectMapper.readTree("""
                {"offers":[{"domain":"store.example","url":"https://store.example/headphones",
                  "availability":"InStock","price":{"price":45.00,"currency":"USD"}}]}
                """));

        var ownerLogin = oidcLogin().idToken(token -> token.subject(owner));
        mvc.perform(post("/api/v1/checkout-sessions").with(ownerLogin).with(csrf())
                .header("Idempotency-Key", "blocked-checkout").contentType("application/json")
                .content("""
                        {"searchSessionId":"%s","candidateId":"%s"}
                        """.formatted(search.getId(), candidate.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POLICY_LIMIT_EXCEEDED"));

        String history = "/api/v1/transactions/blocked";
        mvc.perform(get(history).with(ownerLogin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutAttempts.length()").value(1))
                .andExpect(jsonPath("$.checkoutAttempts[0].productName").value("Studio headphones"))
                .andExpect(jsonPath("$.checkoutAttempts[0].amount").value("45.00"))
                .andExpect(jsonPath("$.checkoutAttempts[0].reason").value("The product exceeds the maximum product price."))
                .andExpect(jsonPath("$.rejectedOffers.length()").value(1))
                .andExpect(jsonPath("$.rejectedOffers[0].reason").value("Offer is not confirmed in stock."));
        mvc.perform(get(history).with(oidcLogin().idToken(token -> token.subject("other-shopper"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutAttempts").isEmpty())
                .andExpect(jsonPath("$.rejectedOffers").isEmpty());
        mvc.perform(get(history)).andExpect(status().isUnauthorized());
    }

    @Test
    void searchLoadsSavedPreferencesFromPostgresAndUsesThemForBudgetAndGeminiComparison() throws Exception {
        String owner = "preference-search-owner";
        spendingPolicyService.save(owner, new SpendingPolicyRequest(
                new BigDecimal("100.00"), new BigDecimal("120.00"), new BigDecimal("500.00"),
                5, "USD", "US", "UTC"));
        shoppingProfileService.savePreferences(owner, new com.spendwise.product.dto.ShoppingPreferencesRequest(
                java.util.List.of("laptops"), java.util.List.of("Acme"), java.util.List.of("preferred.example"),
                java.util.List.of("quiet"), new BigDecimal("45.00"), "USD"));
        when(geminiService.extractShoppingIntent("Find a quiet laptop"))
                .thenReturn(new GeminiShoppingIntent("quiet laptop", "laptop", null, null, "work",
                        java.util.List.of("quiet")));
        when(channel3Client.search(anyString(), any(), anyString(), anyString())).thenReturn(objectMapper.readTree("""
                {"products":[
                  {"id":"within-pref","title":"Acme Quiet Laptop","brands":[{"name":"Acme"}],
                   "ratings":{"value":4.6,"count":120,"source_count":3},"offers":[
                    {"domain":"preferred.example","url":"https://preferred.example/item","availability":"InStock",
                     "price":{"price":42.00,"currency":"USD"}}]},
                  {"id":"over-pref","title":"Acme Quiet Laptop Plus","brands":[{"name":"Acme"}],"offers":[
                    {"domain":"preferred.example","url":"https://preferred.example/plus","availability":"InStock",
                     "price":{"price":50.00,"currency":"USD"}}]}
                ]}
                """));
        when(geminiService.explainShoppingCandidates(eq("Find a quiet laptop"), any(), any()))
                .thenAnswer(invocation -> {
                    java.util.List<GeminiRecommendationCandidate> candidates = invocation.getArgument(2);
                    assertThat(candidates.getFirst().rating().count()).isEqualTo(120);
                    var id = candidates.getFirst().candidateId();
                    return new GeminiRecommendationResult(java.util.List.of(id),
                            java.util.Map.of(id, "Matches the saved Acme preference and quiet request."),
                            "This laptop best matches your saved preferences.");
                });

        var result = productSearchService.search(owner,
                new com.spendwise.product.dto.ShoppingSearchRequest(
                        "Find a quiet laptop", com.spendwise.product.dto.ShoppingSortBy.AI_RECOMMENDED));

        assertThat(result.intent().requestedMaxPrice()).isEqualTo("45.00");
        assertThat(result.intent().promptMaxPrice()).isNull();
        assertThat(result.intent().effectiveMaxPrice()).isEqualTo("45.00");
        assertThat(result.intent().category()).isEqualTo("laptop");
        assertThat(result.intent().useCase()).isEqualTo("work");
        assertThat(result.intent().preferences()).containsExactly("quiet");
        assertThat(result.eligibleCandidates()).hasSize(1);
        assertThat(result.eligibleCandidates().getFirst().price()).isEqualTo("42.00");
        assertThat(result.aiRanked()).isFalse();
        assertThat(result.eligibleCandidates().getFirst().rating().value()).isEqualByComparingTo("4.6");
        assertThat(result.eligibleCandidates().getFirst().rating().sourceCount()).isEqualTo(3);
        var recommendations = productSearchService.recommendations(owner, result.searchSessionId());
        assertThat(recommendations.aiRanked()).isTrue();
        assertThat(recommendations.explanations()).containsValue(
                "Matches the saved Acme preference and quiet request.");
        assertThat(result.rejectedCandidates()).hasSize(1);
        assertThat(result.rejectedCandidates().getFirst().rating()).isNull();
        assertThat(result.rejectedCandidates().getFirst().rejectionReason()).contains("maximum product price");
        verify(channel3Client).search("quiet laptop", new BigDecimal("45.00"), "US", "USD");
        verify(geminiService).explainShoppingCandidates(eq("Find a quiet laptop"),
                org.mockito.ArgumentMatchers.<ShoppingPreferencesResponse>argThat(preferences ->
                        preferences.preferredBrands().contains("Acme")
                        && preferences.keywords().contains("quiet")), any());
        assertThat(auditEventRepository.findAll()).extracting(event -> event.getEventType())
                .contains("SHOPPING_SEARCH_STARTED", "SHOPPING_SEARCH_COMPLETED", "PRODUCT_APPROVED",
                        "PRODUCT_REJECTED");
    }
}
