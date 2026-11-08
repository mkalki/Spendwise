package com.spendwise.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendwise.product.entity.ShoppingPreferences;
import com.spendwise.product.entity.ShoppingSearchSession;
import com.spendwise.product.repository.ShoppingPreferencesRepository;
import com.spendwise.product.repository.ShoppingSearchSessionRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@TestPropertySource(properties = {
    "spring.security.oauth2.client.registration.google.client-id=test-client-id",
    "spring.security.oauth2.client.registration.google.client-secret=test-client-secret"
})
class ShoppingPersistenceIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("spendwise")
            .withUsername("spendwise")
            .withPassword("spendwise-test");

    @Autowired private ShoppingPreferencesRepository preferencesRepository;
    @Autowired private ShoppingSearchSessionRepository searchSessionRepository;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void cleanRows() {
        searchSessionRepository.deleteAll();
        preferencesRepository.deleteAll();
    }

    @Test
    void flywaySchemaPersistsPreferencesAndOwnerScopedSearchHistory() {
        preferencesRepository.save(new ShoppingPreferences("owner-a", "[\"laptops\"]",
                "[\"Lenovo\"]", "[]", "[\"lightweight\"]", new BigDecimal("1200.00"), "USD"));
        searchSessionRepository.save(new ShoppingSearchSession(UUID.randomUUID(), "owner-a", "laptop",
                "laptop", new BigDecimal("1200.00")));
        searchSessionRepository.save(new ShoppingSearchSession(UUID.randomUUID(), "owner-b", "camera",
                "camera", new BigDecimal("500.00")));

        var preference = preferencesRepository.findById("owner-a").orElseThrow();
        var history = searchSessionRepository.findByOwnerId("owner-a", PageRequest.of(0, 10,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        assertThat(preference.getPreferredCategories()).isEqualTo("[\"laptops\"]");
        assertThat(preference.getCurrencyCode()).isEqualTo("USD");
        assertThat(history.getContent()).hasSize(1);
        assertThat(history.getContent().getFirst().getOriginalQuery()).isEqualTo("laptop");
    }
}
