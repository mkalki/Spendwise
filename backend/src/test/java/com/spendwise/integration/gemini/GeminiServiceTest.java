package com.spendwise.integration.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendwise.common.exception.ApiException;
import com.spendwise.integration.gemini.dto.GeminiRequest;
import com.spendwise.integration.gemini.dto.GeminiResponse;
import com.spendwise.integration.gemini.dto.GeminiShoppingIntent;
import com.spendwise.integration.gemini.dto.GeminiRecommendationCandidate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class GeminiServiceTest {

    @Mock private GeminiClient geminiClient;
    private GeminiService geminiService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        geminiService = new GeminiService(geminiClient, new ObjectMapper());
    }

    @Test
    void returnsTheGeneratedModelAndText() {
        GeminiRequest request = new GeminiRequest("Give me a short greeting.");
        when(geminiClient.generate(request)).thenReturn(new GeminiResponse("gemini-3.8-flash", "Hello."));

        assertThat(geminiService.generate(request))
                .isEqualTo(new GeminiResponse("gemini-3.8-flash", "Hello."));
    }

    @Test
    void mapsMissingConfigurationToServiceUnavailable() {
        GeminiRequest request = new GeminiRequest("Hello");
        when(geminiClient.generate(request)).thenThrow(new GeminiNotConfiguredException());

        assertApiError(request, "GEMINI_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void mapsTimeoutToGatewayTimeout() {
        GeminiRequest request = new GeminiRequest("Hello");
        when(geminiClient.generate(request)).thenThrow(new GeminiTimeoutException());

        assertApiError(request, "GEMINI_TIMEOUT", HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void mapsProviderFailureToBadGateway() {
        GeminiRequest request = new GeminiRequest("Hello");
        when(geminiClient.generate(request)).thenThrow(new GeminiProviderException());

        assertApiError(request, "GEMINI_PROVIDER_ERROR", HttpStatus.BAD_GATEWAY);
    }

    @Test
    void mapsInvalidApiKeyToActionableServiceUnavailableError() {
        GeminiRequest request = new GeminiRequest("Hello");
        when(geminiClient.generate(request)).thenThrow(new GeminiInvalidApiKeyException());

        assertApiError(request, "GEMINI_INVALID_API_KEY", HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void extractsTypedShoppingIntentFromGeminiJson() {
        when(geminiClient.generateJson(org.mockito.ArgumentMatchers.contains("Find me a laptop under ₹70,000")))
                .thenReturn(new GeminiResponse("gemini-3.8-flash", """
                        {"normalizedQuery":"laptop for programming","category":"laptop","budget":70000,
                         "budgetCurrency":"INR","useCase":"programming","preferences":["performance","battery life"]}
                        """));

        GeminiShoppingIntent intent = geminiService.extractShoppingIntent(
                "Find me a laptop under ₹70,000");

        assertThat(intent.normalizedQuery()).isEqualTo("laptop for programming");
        assertThat(intent.budget()).isEqualByComparingTo("70000");
        assertThat(intent.budgetCurrency()).isEqualTo("INR");
        assertThat(intent.preferences()).containsExactly("performance", "battery life");
    }

    @Test
    void rankingContainsOnlyUniqueIdsFromTheChannel3CandidateSet() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000020");
        UUID invented = UUID.fromString("00000000-0000-0000-0000-000000000030");
        when(geminiClient.generateJson(org.mockito.ArgumentMatchers.contains("channel3Candidates")))
                .thenReturn(new GeminiResponse("gemini-3.8-flash", """
                        {"rankedCandidateIds":["%s","%s","%s","%s"]}
                        """.formatted(second, invented, first, second)));

        List<UUID> ranking = geminiService.rankShoppingCandidates("coding laptop", List.of(
                new GeminiRecommendationCandidate(first, "Laptop A", "Brand A"),
                new GeminiRecommendationCandidate(second, "Laptop B", "Brand B")));

        assertThat(ranking).containsExactly(second, first);
    }

    @Test
    void rejectsMalformedGeminiProductRanking() {
        when(geminiClient.generateJson(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new GeminiResponse("gemini-3.8-flash", "{}"));
        UUID candidateId = UUID.fromString("00000000-0000-0000-0000-000000000010");

        assertThatThrownBy(() -> geminiService.rankShoppingCandidates("laptop", List.of(
                        new GeminiRecommendationCandidate(candidateId, "Laptop", null))))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("GEMINI_INVALID_RECOMMENDATIONS"));
    }

    @Test
    void explainsOnlySuppliedProductsAndReturnsBoundedCandidateReasons() {
        UUID candidateId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID inventedId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        when(geminiClient.generateJson(org.mockito.ArgumentMatchers.contains("savedPreferences")))
                .thenReturn(new GeminiResponse("gemini-3.8-flash", """
                        {"summary":"Matches your request.","recommendations":[
                          {"candidateId":"%s","explanation":"Matches your preferred brand and price."},
                          {"candidateId":"%s","explanation":"Invented product."}]}
                        """.formatted(candidateId, inventedId)));

        var result = geminiService.explainShoppingCandidates("quiet laptop",
                java.util.Map.of("preferredBrands", List.of("Acme")),
                List.of(new GeminiRecommendationCandidate(candidateId, "Acme Quiet Laptop", "Acme",
                        "shop.example", "45.00", "USD")));

        assertThat(result.rankedCandidateIds()).containsExactly(candidateId);
        assertThat(result.explanations()).containsOnlyKeys(candidateId);
        assertThat(result.explanations().get(candidateId)).contains("preferred brand");
        assertThat(result.summary()).isEqualTo("Matches your request.");
        org.mockito.ArgumentCaptor<String> prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generateJson(prompt.capture());
        assertThat(prompt.getValue()).contains("Acme Quiet Laptop", "shop.example", "45.00", "preferredBrands");
    }

    private void assertApiError(GeminiRequest request, String code, HttpStatus status) {
        assertThatThrownBy(() -> geminiService.generate(request))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException apiError = (ApiException) error;
                    assertThat(apiError.getCode()).isEqualTo(code);
                    assertThat(apiError.getStatus()).isEqualTo(status);
                });
    }
}
