package com.spendwise.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.integration.gemini.GeminiClient;
import com.spendwise.integration.gemini.dto.GeminiRequest;
import com.spendwise.payment.integration.PayPalAuthService;
import com.spendwise.product.integration.Channel3Client;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class ExternalProviderConnectivityIT {

    @Test
    @EnabledIfEnvironmentVariable(named = "SPENDWISE_EXTERNAL_INTEGRATION_TESTS", matches = "true")
    void reachesConfiguredGemini() {
        String apiKey = requiredEnv("GEMINI_API_KEY");
        ClientHttpRequestFactory requestFactory = requestFactory();
        String geminiBaseUrl = env("GEMINI_BASE_URL", "https://generativelanguage.googleapis.com");
        RestClient geminiRestClient = RestClient.builder().baseUrl(geminiBaseUrl)
                .requestFactory(requestFactory).build();
        GeminiClient gemini = new GeminiClient(geminiRestClient, new ObjectMapper(),
                apiKey,
                env("GEMINI_MODEL", "gemini-3.8-flash"));
        assertThat(gemini.generate(new GeminiRequest("Reply with the single word OK.")).text()).isNotBlank();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SPENDWISE_EXTERNAL_INTEGRATION_TESTS", matches = "true")
    void reachesConfiguredChannel3() {
        String apiKey = requiredEnv("CHANNEL3_API_KEY");
        ClientHttpRequestFactory requestFactory = requestFactory();
        Channel3Client channel3 = new Channel3Client(requestFactory,
                env("CHANNEL3_BASE_URL", "https://api.trychannel3.com"), apiKey, 1);
        JsonNode products = channel3.search("wireless headphones", null, "US", "USD");
        assertThat(products.path("products").isArray()).isTrue();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SPENDWISE_EXTERNAL_INTEGRATION_TESTS", matches = "true")
    void reachesPayPalSandbox() {
        String clientId = requiredEnv("PAYPAL_CLIENT_ID");
        String clientSecret = requiredEnv("PAYPAL_CLIENT_SECRET");
        ClientHttpRequestFactory requestFactory = requestFactory();
        PayPalAuthService paypal = new PayPalAuthService(requestFactory,
                env("PAYPAL_BASE_URL", "https://api-m.sandbox.paypal.com"),
                clientId, clientSecret);
        assertThat(paypal.accessToken()).isNotBlank();
    }

    private ClientHttpRequestFactory requestFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(20));
        return factory;
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        Assumptions.assumeTrue(value != null && !value.isBlank(),
                () -> "Skipping external provider test because " + name + " is not set.");
        return value;
    }
}
