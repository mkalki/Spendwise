package com.spendwise.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendwise.integration.gemini.GeminiClient;
import com.spendwise.integration.gemini.dto.GeminiRequest;
import com.spendwise.payment.integration.PayPalAuthService;
import com.spendwise.product.integration.Channel3Client;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class ExternalProviderConnectivityIT {

    @Test
    @EnabledIfEnvironmentVariable(named = "SPENDWISE_EXTERNAL_INTEGRATION_TESTS", matches = "true")
    void reachesConfiguredGeminiChannel3AndPayPalSandbox() {
        ClientHttpRequestFactory requestFactory = requestFactory();
        String geminiBaseUrl = env("GEMINI_BASE_URL", "https://generativelanguage.googleapis.com");
        RestClient geminiRestClient = RestClient.builder().baseUrl(geminiBaseUrl)
                .requestFactory(requestFactory).build();
        GeminiClient gemini = new GeminiClient(geminiRestClient, env("GEMINI_API_KEY", ""),
                env("GEMINI_MODEL", "gemini-3.8-flash"));
        assertThat(gemini.generate(new GeminiRequest("Reply with the single word OK." )).text()).isNotBlank();

        Channel3Client channel3 = new Channel3Client(requestFactory,
                env("CHANNEL3_BASE_URL", "https://api.trychannel3.com"), env("CHANNEL3_API_KEY", ""), 1);
        JsonNode products = channel3.search("wireless headphones", null, "US", "USD");
        assertThat(products.path("products").isArray()).isTrue();

        PayPalAuthService paypal = new PayPalAuthService(requestFactory,
                env("PAYPAL_BASE_URL", "https://api-m.sandbox.paypal.com"),
                env("PAYPAL_CLIENT_ID", ""), env("PAYPAL_CLIENT_SECRET", ""));
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
            throw new IllegalStateException(name + " must be set for external provider tests.");
        }
        return value;
    }
}
