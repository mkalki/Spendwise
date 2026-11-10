package com.spendwise.api;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spendwise.common.config.OpenApiConfiguration;
import com.spendwise.checkout.controller.CartController;
import com.spendwise.checkout.service.CartService;
import com.spendwise.common.exception.ApiExceptionHandler;
import com.spendwise.integration.gemini.GeminiController;
import com.spendwise.integration.gemini.GeminiService;
import com.spendwise.payment.controller.PayPalWebhookController;
import com.spendwise.payment.integration.PayPalWebhookVerifier;
import com.spendwise.payment.service.PayPalWebhookService;
import com.spendwise.product.controller.ShoppingController;
import com.spendwise.product.controller.ShoppingProfileController;
import com.spendwise.product.service.ProductSearchService;
import com.spendwise.product.service.ShoppingProfileService;
import com.spendwise.common.security.GeminiUserRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = SwaggerUiIntegrationTest.SwaggerTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "spring.security.oauth2.client.registration.google.client-id=test-client-id",
    "spring.security.oauth2.client.registration.google.client-secret=test-client-secret"
})
class SwaggerUiIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void swaggerUiStaticPageIsServed() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("swagger-ui-bundle.js")));
        mockMvc.perform(get("/swagger-ui/swagger-ui-bundle.js"))
                .andExpect(status().isOk());
    }

    @Test
    void swaggerUiConfigurationPointsAtGeneratedOpenApiDocument() throws Exception {
        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"));
    }

    @Test
    void generatedOpenApiDocumentIncludesGeminiAndShoppingEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/ai/generate'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/shopping/search'].post.summary")
                        .value("Interpret a shopping request and search products"))
                .andExpect(jsonPath("$.paths['/api/v1/shopping/preferences'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/shopping/preferences'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/shopping/search-history'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/cart'].get.security[0].SpendwiseSession").exists())
                .andExpect(jsonPath("$.paths['/api/v1/cart/details'].put.security[0].CsrfToken").exists())
                .andExpect(jsonPath("$.paths['/api/webhooks/paypal'].post").exists())
                .andExpect(jsonPath("$.paths['/api/webhooks/paypal'].post.security").doesNotExist());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        FlywayAutoConfiguration.class,
    })
    @Import({
        OpenApiConfiguration.class,
        ApiExceptionHandler.class,
        GeminiController.class,
        ShoppingController.class,
        ShoppingProfileController.class,
        CartController.class,
        PayPalWebhookController.class
    })
    static class SwaggerTestApplication {

        @Bean
        CartService cartService() {
            return mock(CartService.class);
        }

        @Bean
        GeminiService geminiService() {
            return mock(GeminiService.class);
        }

        @Bean
        ProductSearchService productSearchService() {
            return mock(ProductSearchService.class);
        }

        @Bean
        GeminiUserRateLimiter geminiUserRateLimiter() {
            return mock(GeminiUserRateLimiter.class);
        }

        @Bean
        ShoppingProfileService shoppingProfileService() {
            return mock(ShoppingProfileService.class);
        }

        @Bean
        PayPalWebhookVerifier payPalWebhookVerifier() {
            return mock(PayPalWebhookVerifier.class);
        }

        @Bean
        PayPalWebhookService payPalWebhookService() {
            return mock(PayPalWebhookService.class);
        }

    }
}
