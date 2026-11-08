package com.spendwise.product.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendwise.common.config.TransientHttpRetry;
import java.math.BigDecimal;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class Channel3Client {

    private final RestClient restClient;
    private final String apiKey;
    private final int searchLimit;

    @Autowired
    public Channel3Client(
            ClientHttpRequestFactory requestFactory,
            @Value("${spendwise.channel3.base-url:https://api.trychannel3.com}") String baseUrl,
            @Value("${CHANNEL3_API_KEY:}") String apiKey,
            @Value("${spendwise.channel3.search-limit:10}") int searchLimit) {
        this(buildClient(requestFactory, requireOfficialBaseUrl(baseUrl)), apiKey, searchLimit);
    }

    Channel3Client(ClientHttpRequestFactory requestFactory, String baseUrl, String apiKey) {
        this(requestFactory, baseUrl, apiKey, 10);
    }

    Channel3Client(RestClient restClient, String apiKey, int searchLimit) {
        if (searchLimit < 1 || searchLimit > 100) {
            throw new IllegalArgumentException("Channel3 search limit must be between 1 and 100.");
        }
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.searchLimit = searchLimit;
    }

    public JsonNode search(
            String query,
            BigDecimal maxPrice,
            String countryCode,
            String currencyCode) {
        Map<String, Object> filters = maxPrice == null
                ? Map.of()
                : Map.of("price", Map.of("max_price", maxPrice));
        return searchWithFilters(query, filters, countryCode, currencyCode);
    }

    public JsonNode searchWithFilters(
            String query,
            Map<String, Object> filters,
            String countryCode,
            String currencyCode) {
        requireApiKey();
        Map<String, Object> locale = Map.of(
                "country", countryCode,
                "currency", currencyCode);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("query", query);
        request.put("limit", searchLimit);
        request.put("config", locale);
        if (filters != null && !filters.isEmpty()) {
            request.put("filters", filters);
        }

        return TransientHttpRetry.execute(() -> restClient.post()
                .uri("/v1/search")
                .header("x-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class), true);
    }

    public JsonNode getProduct(String productId, String countryCode, String currencyCode) {
        requireApiKey();
        return TransientHttpRetry.execute(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/products/{productId}")
                        .queryParam("country", countryCode)
                        .queryParam("currency", currencyCode)
                        .build(productId))
                .header("x-api-key", apiKey)
                .retrieve()
                .body(JsonNode.class), true);
    }

    private void requireApiKey() {
        if (!StringUtils.hasText(apiKey)) {
            throw new Channel3NotConfiguredException();
        }
    }

    private static RestClient buildClient(ClientHttpRequestFactory requestFactory, String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    private static String requireOfficialBaseUrl(String value) {
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
