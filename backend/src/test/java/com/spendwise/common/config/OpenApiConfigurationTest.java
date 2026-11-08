package com.spendwise.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.Operation;
import org.junit.jupiter.api.Test;

class OpenApiConfigurationTest {

    private final OpenApiConfiguration configuration = new OpenApiConfiguration();

    @Test
    void definesSpendwiseApiMetadataAndBrowserSecuritySchemes() {
        OpenAPI openApi = configuration.spendwiseOpenApi();

        assertThat(openApi.getInfo().getTitle()).isEqualTo("Spendwise API");
        assertThat(openApi.getInfo().getVersion()).isEqualTo("v1");
        assertThat(openApi.getComponents().getSecuritySchemes())
                .containsKeys("SpendwiseSession", "CsrfToken");
        assertThat(openApi.getComponents().getSecuritySchemes().get("SpendwiseSession").getName())
                .isEqualTo("JSESSIONID");
        assertThat(openApi.getComponents().getSecuritySchemes().get("CsrfToken").getName())
                .isEqualTo("X-XSRF-TOKEN");
    }

    @Test
    void appliesSessionAndCsrfRequirementsToProtectedStateChangingApiOperations() {
        Operation updatePolicy = new Operation();
        Operation readPolicy = new Operation();
        Paths paths = new Paths()
                .addPathItem("/api/v1/policies/active", new PathItem()
                        .put(updatePolicy)
                        .get(readPolicy))
                .addPathItem("/api/health", new PathItem().get(new Operation()));
        OpenAPI openApi = new OpenAPI().paths(paths);

        configuration.spendwiseSessionSecurity().customise(openApi);

        assertThat(updatePolicy.getSecurity()).hasSize(1);
        assertThat(updatePolicy.getSecurity().get(0).keySet())
                .containsExactlyInAnyOrder("SpendwiseSession", "CsrfToken");
        assertThat(readPolicy.getSecurity()).singleElement()
                .satisfies(requirement -> assertThat(requirement).containsKey("SpendwiseSession"));
        assertThat(paths.get("/api/health").getGet().getSecurity()).isNull();
    }

    @Test
    void protectsPayPalPaymentRoutesWithSessionAndCsrf() {
        Operation createPayment = new Operation();
        Operation capturePayment = new Operation();
        Operation paymentStatus = new Operation();
        Paths paths = new Paths()
                .addPathItem("/api/payments/create", new PathItem().post(createPayment))
                .addPathItem("/api/payments/{orderId}/capture", new PathItem().post(capturePayment))
                .addPathItem("/api/payments/{orderId}", new PathItem().get(paymentStatus));
        OpenAPI openApi = new OpenAPI().paths(paths);

        configuration.spendwiseSessionSecurity().customise(openApi);

        assertThat(createPayment.getSecurity().get(0).keySet())
                .containsExactlyInAnyOrder("SpendwiseSession", "CsrfToken");
        assertThat(capturePayment.getSecurity().get(0).keySet())
                .containsExactlyInAnyOrder("SpendwiseSession", "CsrfToken");
        assertThat(paymentStatus.getSecurity()).singleElement()
                .satisfies(requirement -> assertThat(requirement).containsKey("SpendwiseSession"));
    }
}
