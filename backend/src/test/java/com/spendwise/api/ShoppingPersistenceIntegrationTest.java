package com.spendwise.api;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
    void searchLoadsSavedPreferencesFromPostgresAndUsesThemForBudgetAndGeminiComparison() throws Exception {
        String owner = "preference-search-owner";
        spendingPolicyService.save(owner, new SpendingPolicyRequest(
                new BigDecimal("100.00"), new BigDecimal("120.00"), new BigDecimal("500.00"),
                5, "USD", "US", "UTC"));
        shoppingProfileService.savePreferences(owner, new com.spendwise.product.dto.ShoppingPreferencesRequest(
                java.util.List.of("laptops"), java.util.List.of("Acme"), java.util.List.of("preferred.example"),
                java.util.List.of("quiet"), new BigDecimal("45.00"), "USD"));
        when(geminiService.extractShoppingIntent("Find a quiet laptop"))
                .thenReturn(new GeminiShoppingIntent("quiet laptop", "laptop", null, null, null, java.util.List.of()));
        when(channel3Client.search(anyString(), any(), anyString(), anyString())).thenReturn(objectMapper.readTree("""
                {"products":[
                  {"id":"within-pref","title":"Acme Quiet Laptop","brands":[{"name":"Acme"}],"offers":[
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
                    var id = candidates.getFirst().candidateId();
                    return new GeminiRecommendationResult(java.util.List.of(id),
                            java.util.Map.of(id, "Matches the saved Acme preference and quiet request."),
                            "This laptop best matches your saved preferences.");
                });

        var result = productSearchService.search(owner,
                new com.spendwise.product.dto.ShoppingSearchRequest(
                        "Find a quiet laptop", com.spendwise.product.dto.ShoppingSortBy.AI_RECOMMENDED));

        assertThat(result.intent().requestedMaxPrice()).isEqualTo("45.00");
        assertThat(result.eligibleCandidates()).hasSize(1);
        assertThat(result.eligibleCandidates().getFirst().price()).isEqualTo("42.00");
        assertThat(result.eligibleCandidates().getFirst().recommendationExplanation())
                .isEqualTo("Matches the saved Acme preference and quiet request.");
        assertThat(result.rejectedCandidates()).hasSize(1);
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
