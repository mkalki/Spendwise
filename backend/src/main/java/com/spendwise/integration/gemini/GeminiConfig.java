package com.spendwise.integration.gemini;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class GeminiConfig {

    @Bean("geminiRestClient")
    RestClient geminiRestClient(
            ClientHttpRequestFactory requestFactory,
            @Value("${spendwise.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(requireOfficialBaseUrl(baseUrl))
                .requestFactory(requestFactory)
                .build();
    }

    private String requireOfficialBaseUrl(String value) {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"generativelanguage.googleapis.com".equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("Gemini integration must use the official HTTPS API.");
        }
        return value;
    }
}
