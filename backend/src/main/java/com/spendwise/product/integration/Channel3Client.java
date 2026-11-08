package com.spendwise.product.integration;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class Channel3Client {

    private final RestClient restClient;
    private final String apiKey;

    public Channel3Client(
            ClientHttpRequestFactory requestFactory,
            @Value("${spendwise.channel3.base-url:https://api.trychannel3.com}") String baseUrl,
            @Value("${CHANNEL3_API_KEY:}") String apiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(requireOfficialBaseUrl(baseUrl))
                .requestFactory(requestFactory)
                .build();
        this.apiKey = apiKey;
    }

    public JsonNode search(
            String query,
            BigDecimal maxPrice,
            String countryCode,
            String currencyCode) {
        requireApiKey();
        Map<String, Object> locale = Map.of(
                "country", countryCode,
                "currency", currencyCode);
        Map<String, Object> filters = maxPrice == null
                ? Map.of()
                : Map.of("price", Map.of("max_price", maxPrice));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("query", query);
        request.put("limit", 30);
        request.put("config", locale);
        if (!filters.isEmpty()) {
            request.put("filters", filters);
        }

        return restClient.post()
                .uri("/v1/search")
                .header("x-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode getProduct(String productId, String countryCode, String currencyCode) {
        requireApiKey();
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/products/{productId}")
                        .queryParam("country", countryCode)
                        .queryParam("currency", currencyCode)
                        .build(productId))
                .header("x-api-key", apiKey)
                .retrieve()
                .body(JsonNode.class);
    }

    private void requireApiKey() {
        if (!StringUtils.hasText(apiKey)) {
            throw new Channel3NotConfiguredException();
        }
    }

    private String requireOfficialBaseUrl(String value) {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"api.trychannel3.com".equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("Channel3 integration must use the official HTTPS API.");
        }
        return value;
    }
}
