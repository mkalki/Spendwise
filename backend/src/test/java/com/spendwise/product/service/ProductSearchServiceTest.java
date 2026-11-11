package com.spendwise.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.audit.service.AuditService;
import com.spendwise.integration.gemini.dto.GeminiRecommendationResult;
import com.spendwise.common.exception.ApiException;
import com.spendwise.common.security.Channel3UserRateLimiter;
import com.spendwise.common.security.GeminiUserRateLimiter;
import com.spendwise.integration.gemini.GeminiService;
import com.spendwise.integration.gemini.dto.GeminiRecommendationCandidate;
import com.spendwise.integration.gemini.dto.GeminiShoppingIntent;
import com.spendwise.policy.engine.PolicyEngine;
import com.spendwise.policy.entity.SpendingPolicy;
import com.spendwise.policy.service.SpendingPolicyService;
import com.spendwise.product.dto.ShoppingSearchRequest;
import com.spendwise.product.dto.ShoppingSortBy;
import com.spendwise.product.dto.ShoppingPreferencesResponse;
import com.spendwise.product.entity.ProductCandidate;
import com.spendwise.product.entity.ShoppingSearchSession;
import com.spendwise.product.integration.Channel3Client;
import com.spendwise.product.repository.ProductCandidateRepository;
import com.spendwise.product.repository.ShoppingSearchSessionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.client.HttpServerErrorException;

@ExtendWith(MockitoExtension.class)
class ProductSearchServiceTest {

    private static final String OWNER = "shopper-1";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private Channel3Client channel3Client;
    @Mock private GeminiService geminiService;
    @Mock private SpendingPolicyService policyService;
    @Mock private ShoppingSearchSessionRepository sessionRepository;
    @Mock private ProductCandidateRepository candidateRepository;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private ShoppingProfileService profileService;
    @Mock private AuditService auditService;

    private ProductSearchService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        lenient().when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(policyService.getActive(OWNER)).thenReturn(new SpendingPolicy(
                OWNER, new BigDecimal("150.00"), new BigDecimal("200.00"),
                new BigDecimal("500.00"), 5, "USD", "US", "UTC"));
        service = new ProductSearchService(
                channel3Client,
                new Channel3UserRateLimiter(),
                new GeminiUserRateLimiter(),
                geminiService,
                new ShoppingIntentService(),
                new PolicyEngine(),
                policyService,
                sessionRepository,
                candidateRepository,
                transactionManager,
                auditService,
                profileService);
    }

    @Test
    void savedPreferenceBudgetRestrictsSearchAndGeminiExplainsTheMatch() throws Exception {
        var preferences = new ShoppingPreferencesResponse(
                List.of("laptops"), List.of("Acme"), List.of("preferred.example"), List.of("quiet"),
                new BigDecimal("45.00"), "USD", null);
        when(profileService.getPreferences(OWNER)).thenReturn(preferences);
        when(geminiService.extractShoppingIntent("Find a quiet laptop"))
                .thenReturn(new GeminiShoppingIntent("quiet laptop", "laptop", null, null, null, List.of()));
        when(channel3Client.search(anyString(), any(), anyString(), anyString())).thenReturn(objectMapper.readTree("""
                {"products":[{"id":"p-pref","title":"Acme Quiet Laptop","brands":[{"name":"Acme"}],
                  "offers":[{"domain":"preferred.example","url":"https://preferred.example/item",
                    "availability":"InStock","price":{"price":42.00,"currency":"USD"}}]}]}
                """));
        when(geminiService.explainShoppingCandidates(anyString(), any(), any()))
                .thenAnswer(invocation -> {
                    List<GeminiRecommendationCandidate> supplied = invocation.getArgument(2);
                    UUID candidateId = supplied.getFirst().candidateId();
                    return new GeminiRecommendationResult(List.of(candidateId),
                            java.util.Map.of(candidateId, "Matches your Acme and quiet preferences."),
                            "This offer matches your saved brand and keyword preferences.");
                });

        var response = service.search(OWNER,
                new ShoppingSearchRequest("Find a quiet laptop", ShoppingSortBy.AI_RECOMMENDED));

        assertThat(response.intent().requestedMaxPrice()).isEqualTo("45.00");
        assertThat(response.aiRanked()).isFalse();
        assertThat(response.eligibleCandidates().getFirst().recommendationExplanation()).isNull();
        ArgumentCaptor<ShoppingSearchSession> sessionCaptor = ArgumentCaptor.forClass(ShoppingSearchSession.class);
        ArgumentCaptor<List<ProductCandidate>> candidatesCaptor = ArgumentCaptor.forClass(List.class);
        verify(sessionRepository).save(sessionCaptor.capture());
        verify(candidateRepository).saveAll(candidatesCaptor.capture());
        when(sessionRepository.findByIdAndOwnerId(response.searchSessionId(), OWNER))
                .thenReturn(Optional.of(sessionCaptor.getValue()));
        when(candidateRepository.findEligibleBySearchSession(response.searchSessionId(), OWNER))
                .thenReturn(candidatesCaptor.getValue());

        var recommendations = service.recommendations(OWNER, response.searchSessionId());

        assertThat(recommendations.aiRanked()).isTrue();
        assertThat(recommendations.explanations()).containsValue("Matches your Acme and quiet preferences.");
        assertThat(recommendations.summary())
                .isEqualTo("This offer matches your saved brand and keyword preferences.");
        verify(channel3Client).search("quiet laptop", new BigDecimal("45.00"), "US", "USD");
        verify(geminiService).explainShoppingCandidates(
                org.mockito.ArgumentMatchers.eq("Find a quiet laptop"),
                org.mockito.ArgumentMatchers.eq(preferences), any());
    }

    @Test
    void naturalLanguageSearchReturnsImagesAndComparesOffersAcrossMerchants() throws Exception {
        when(geminiService.extractShoppingIntent("Find wireless headphones with noise cancellation"))
                .thenReturn(new GeminiShoppingIntent(
                        "wireless headphones with noise cancellation", "headphones", null, null,
                        "noise cancellation", List.of("wireless")));
        when(channel3Client.search(anyString(), any(), anyString(), anyString())).thenReturn(objectMapper.readTree("""
                {"products":[{"id":"p-1","title":"Wireless Headphones","brands":[{"name":"Acme"}],
                  "images":[{"cleaned_url":"https://images.example/headphones.jpg"}],
                  "offers":[
                    {"domain":"store-b.example","url":"https://store-b.example/headphones","availability":"InStock","price":{"price":59.99,"currency":"USD"}},
                    {"domain":"store-a.example","url":"https://store-a.example/headphones","availability":"InStock","price":{"price":49.99,"currency":"USD"}}
                  ]}]}
                """));

        var response = service.search(OWNER,
                new ShoppingSearchRequest("Find wireless headphones with noise cancellation"));

        assertThat(response.eligibleCandidates()).hasSize(2);
        assertThat(response.eligibleCandidates()).extracting(candidate -> candidate.merchantName())
                .containsExactly("store-a.example", "store-b.example");
        assertThat(response.eligibleCandidates()).extracting(candidate -> candidate.price())
                .containsExactly("49.99", "59.99");
        assertThat(response.eligibleCandidates().getFirst().imageUrl())
                .isEqualTo("https://images.example/headphones.jpg");
        assertThat(response.recommendation()).contains("store-a.example");
        verify(channel3Client).search("wireless headphones with noise cancellation",
                new BigDecimal("150.00"), "US", "USD");
        var calls = inOrder(geminiService, channel3Client);
        calls.verify(geminiService).extractShoppingIntent("Find wireless headphones with noise cancellation");
        calls.verify(channel3Client).search("wireless headphones with noise cancellation",
                new BigDecimal("150.00"), "US", "USD");
    }

    @Test
    void naturalLanguagePriceLimitIsAppliedToProviderSearchAndCandidateEligibility() throws Exception {
        when(geminiService.extractShoppingIntent("Find wireless headphones under $45"))
                .thenReturn(new GeminiShoppingIntent(
                        "wireless headphones", "headphones", new BigDecimal("90.00"), "USD", null, List.of()));
        when(channel3Client.search(anyString(), any(), anyString(), anyString())).thenReturn(objectMapper.readTree("""
                {"products":[{"id":"p-2","title":"Wireless Headphones",
                  "images":[{"url":"https://images.example/headphones.jpg"}],
                  "offers":[
                    {"domain":"cheap.example","url":"https://cheap.example/item","availability":"InStock","price":{"price":39.99,"currency":"USD"}},
                    {"domain":"costly.example","url":"https://costly.example/item","availability":"InStock","price":{"price":59.99,"currency":"USD"}}
                  ]}]}
                """));

        var response = service.search(OWNER,
                new ShoppingSearchRequest("Find wireless headphones under $45"));

        assertThat(response.eligibleCandidates()).hasSize(1);
        assertThat(response.eligibleCandidates().getFirst().merchantName()).isEqualTo("cheap.example");
        assertThat(response.rejectedCandidates()).hasSize(1);
        assertThat(response.rejectedCandidates().getFirst().rejectionReason()).contains("maximum product price");
        ArgumentCaptor<BigDecimal> maxPrice = ArgumentCaptor.forClass(BigDecimal.class);
        verify(channel3Client).search(anyString(), maxPrice.capture(), anyString(), anyString());
        assertThat(maxPrice.getValue()).isEqualByComparingTo("45.00");
    }

    @Test
    void returnsEmptyCandidatesWhenProviderHasNoResults() throws Exception {
        when(geminiService.extractShoppingIntent("wireless headphones"))
                .thenReturn(new GeminiShoppingIntent(
                        "wireless headphones", "headphones", null, null, null, List.of()));
        when(channel3Client.search(anyString(), any(), anyString(), anyString()))
                .thenReturn(objectMapper.readTree("{\"products\":[]}"));

        var response = service.search(OWNER, new ShoppingSearchRequest("wireless headphones"));

        assertThat(response.eligibleCandidates()).isEmpty();
        assertThat(response.rejectedCandidates()).isEmpty();
        assertThat(response.recommendation()).isNull();
    }

    @Test
    void mapsChannel3HttpErrorsToStableApiError() {
        when(geminiService.extractShoppingIntent("wireless headphones"))
                .thenReturn(new GeminiShoppingIntent(
                        "wireless headphones", "headphones", null, null, null, List.of()));
        when(channel3Client.search(anyString(), any(), anyString(), anyString()))
                .thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> service.search(OWNER, new ShoppingSearchRequest("wireless headphones")))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException apiError = (ApiException) error;
                    assertThat(apiError.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(apiError.getCode()).isEqualTo("PRODUCT_PROVIDER_ERROR");
                });
    }

    @Test
    void rejectsBudgetCurrencyThatDoesNotMatchSavedPolicyBeforeCallingChannel3() {
        when(geminiService.extractShoppingIntent("Find a laptop under ₹70,000"))
                .thenReturn(new GeminiShoppingIntent(
                        "laptop for programming", "laptop", new BigDecimal("70000"), "INR",
                        "programming", List.of("performance")));

        assertThatThrownBy(() -> service.search(OWNER,
                        new ShoppingSearchRequest("Find a laptop under ₹70,000")))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException apiError = (ApiException) error;
                    assertThat(apiError.getCode()).isEqualTo("SHOPPING_CURRENCY_MISMATCH");
                    assertThat(apiError.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
        org.mockito.Mockito.verifyNoInteractions(channel3Client);
    }
}
