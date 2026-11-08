package com.spendwise.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI spendwiseOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Spendwise API")
                        .version("v1")
                        .description("""
                                APIs for account signup and login, managing spending policies, searching products,
                                generating Gemini responses, creating and verifying PayPal Sandbox payments, and viewing transactions. Browser API calls use
                                an authenticated HTTP session. State-changing requests also require the CSRF token from
                                GET /api/auth/csrf in the X-XSRF-TOKEN header.
                                """))
                .components(new Components()
                        .addSecuritySchemes("SpendwiseSession", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")
                                .description("Authenticated Spendwise session cookie (password or Google sign-in)."))
                        .addSecuritySchemes("CsrfToken", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-XSRF-TOKEN")
                                .description("Token returned by GET /api/auth/csrf; required for state-changing requests.")));
    }

    @Bean
    OpenApiCustomizer spendwiseSessionSecurity() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) -> {
            if (!path.startsWith("/api/") || "/api/health".equals(path)) {
                return;
            }
            pathItem.readOperationsMap().forEach((method, operation) -> {
                boolean publicAuth = path.equals("/api/auth/signup")
                        || path.equals("/api/auth/login")
                        || path.equals("/api/auth/google/login")
                        || path.equals("/api/auth/google/signup");
                if (path.equals("/api/webhooks/paypal")) {
                    return;
                }
                if (publicAuth) {
                    if (HttpMethod.POST.matches(method.name())) {
                        operation.addSecurityItem(new SecurityRequirement().addList("CsrfToken"));
                    }
                    return;
                }
                if (path.equals("/api/auth/csrf")) {
                    return;
                }
                SecurityRequirement requirement = new SecurityRequirement().addList("SpendwiseSession");
                if (HttpMethod.POST.matches(method.name())
                        || HttpMethod.PUT.matches(method.name())
                        || HttpMethod.PATCH.matches(method.name())
                        || HttpMethod.DELETE.matches(method.name())) {
                    requirement.addList("CsrfToken");
                }
                operation.addSecurityItem(requirement);
            });
        });
    }
}
