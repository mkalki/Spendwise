package com.spendwise.integration.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendwise.common.config.TransientHttpRetry;
import com.spendwise.integration.gemini.dto.GeminiRequest;
import com.spendwise.integration.gemini.dto.GeminiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GeminiClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public GeminiClient(
            @Qualifier("geminiRestClient") RestClient restClient,
            @Value("${GEMINI_API_KEY:}") String apiKey,
            @Value("${spendwise.gemini.model:gemini-3.8-flash}") String model) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.model = model;
    }

    public GeminiResponse generate(GeminiRequest request) {
        return generate(request, false);
    }

    public GeminiResponse generateJson(String prompt) {
        return generate(new GeminiRequest(prompt), true);
    }

    private GeminiResponse generate(GeminiRequest request, boolean jsonResponse) {
        if (!StringUtils.hasText(apiKey)) {
            throw new GeminiNotConfiguredException();
        }
        Map<String, Object> body = jsonResponse
                ? Map.of(
                    "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", request.prompt())))),
                    "generationConfig", Map.of("responseMimeType", "application/json"))
                : Map.of("contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", request.prompt())))));
        try {
            JsonNode response = TransientHttpRetry.execute(() -> restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class), false);
            String text = extractText(response);
            if (!StringUtils.hasText(text)) {
                throw new GeminiProviderException();
            }
            return new GeminiResponse(model, text);
        } catch (ResourceAccessException exception) {
            throw new GeminiTimeoutException();
        } catch (RestClientResponseException exception) {
            throw new GeminiProviderException(isInvalidApiKey(exception));
        } catch (RestClientException exception) {
            throw new GeminiProviderException();
        }
    }

    private boolean isInvalidApiKey(RestClientResponseException exception) {
        if (exception.getStatusCode().value() != 400) {
            return false;
        }
        try {
            JsonNode response = exception.getResponseBodyAs(JsonNode.class);
            if (response != null) {
                for (JsonNode detail : response.path("error").path("details")) {
                    if ("API_KEY_INVALID".equals(detail.path("reason").asText())) {
                        return true;
                    }
                }
            }
        } catch (RestClientException | IllegalStateException ignored) {
            // Unrecognized error payloads retain the generic provider error.
        }
        return false;
    }

    private String extractText(JsonNode response) {
        if (response == null || !response.path("candidates").isArray()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode candidate : response.path("candidates")) {
            JsonNode parts = candidate.path("content").path("parts");
            if (!parts.isArray()) {
                continue;
            }
            for (JsonNode part : parts) {
                if (part.path("thought").asBoolean(false)) {
                    continue;
                }
                JsonNode textPart = part.get("text");
                if (textPart != null && textPart.isTextual()) {
                    text.append(textPart.asText());
                }
            }
        }
        return text.toString().strip();
    }
}
