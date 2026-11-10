package com.spendwise.integration.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.common.config.TransientHttpRetry;
import com.spendwise.integration.gemini.dto.GeminiRequest;
import com.spendwise.integration.gemini.dto.GeminiResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    private static final Set<Integer> AUTHENTICATION_ERROR_STATUSES = Set.of(400, 401, 403);
    private static final Set<String> INVALID_API_KEY_CODES = Set.of(
            "API_KEY_INVALID", "INVALID_API_KEY", "API_KEY_NOT_VALID");
    private static final Set<String> ERROR_CODE_FIELDS = Set.of(
            "code", "status", "reason", "errorcode", "error_code", "statuscode");

    public GeminiClient(
            @Qualifier("geminiRestClient") RestClient restClient,
            ObjectMapper objectMapper,
            @Value("${GEMINI_API_KEY:}") String apiKey,
            @Value("${spendwise.gemini.model:gemini-3.8-flash}") String model) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
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
            if (isInvalidApiKeyResponse(exception)) {
                throw new GeminiInvalidApiKeyException();
            }
            throw new GeminiProviderException();
        } catch (RestClientException exception) {
            throw new GeminiProviderException();
        }
    }

    private boolean isInvalidApiKeyResponse(RestClientResponseException exception) {
        if (!AUTHENTICATION_ERROR_STATUSES.contains(exception.getStatusCode().value())) {
            return false;
        }

        String responseBody = exception.getResponseBodyAsString();
        if (containsInvalidApiKeyMessage(responseBody)) {
            return true;
        }

        try {
            JsonNode response = objectMapper.readTree(responseBody);
            return containsInvalidApiKeyCode(response);
        } catch (JsonProcessingException | IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean containsInvalidApiKeyCode(JsonNode node) {
        if (node == null) {
            return false;
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                String fieldName = field.getKey().toLowerCase(Locale.ROOT);
                JsonNode value = field.getValue();
                if (ERROR_CODE_FIELDS.contains(fieldName)
                        && value.isTextual()
                        && INVALID_API_KEY_CODES.contains(normalizeCode(value.asText()))) {
                    return true;
                }
                if ("message".equals(fieldName) && value.isTextual()
                        && containsInvalidApiKeyMessage(value.asText())) {
                    return true;
                }
                if (containsInvalidApiKeyCode(value)) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsInvalidApiKeyCode(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsInvalidApiKeyMessage(String value) {
        if (value == null) {
            return false;
        }
        String message = value.toLowerCase(Locale.ROOT);
        return message.contains("api key not valid")
                || message.contains("api key is not valid")
                || message.contains("invalid api key")
                || message.contains("api key is invalid");
    }

    private String normalizeCode(String value) {
        return value.strip().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
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
