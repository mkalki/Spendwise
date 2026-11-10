package com.spendwise.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.payment.integration.PayPalClient;
import com.spendwise.payment.integration.PaymentGateway;
import com.spendwise.payment.repository.PayPalPaymentMethodRepository;
import com.spendwise.payment.service.VaultTokenCipher;
import com.spendwise.policy.dto.SpendingPolicyRequest;
import com.spendwise.policy.service.SpendingPolicyService;
import com.spendwise.product.entity.ProductCandidate;
import com.spendwise.product.entity.ShoppingSearchSession;
import com.spendwise.product.integration.Channel3Client;
import com.spendwise.product.repository.ProductCandidateRepository;
import com.spendwise.product.repository.ShoppingSearchSessionRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.security.oauth2.client.registration.google.client-id=test-client-id",
    "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
    "spendwise.paypal.vault-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class PayPalCheckoutFlowsIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("spendwise")
            .withUsername("spendwise")
            .withPassword("spendwise-test");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PayPalPaymentMethodRepository paymentMethods;
    @Autowired private VaultTokenCipher vaultTokenCipher;
    @Autowired private ShoppingSearchSessionRepository searchSessions;
    @Autowired private ProductCandidateRepository candidates;
    @Autowired private SpendingPolicyService policies;
    @MockitoBean private PayPalClient paypalClient;
    @MockitoBean private Channel3Client channel3Client;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void javascriptSdkCheckoutCreatesOrderAndCapturesAfterApproval() throws Exception {
        TestUser user = createUser("paypal-js");
        ShoppingFixture fixture = createShoppingFixture(user.id());
        when(paypalClient.createOrder(anyLong(), any(BigDecimal.class), eq("USD")))
                .thenReturn(new PaymentGateway.OrderCreation(
                        "ORDER-JS-1", "https://www.sandbox.paypal.com/checkoutnow?token=ORDER-JS-1"));

        String createdBody = mvc.perform(post("/api/payments/create")
                        .session(user.session()).with(csrf())
                        .header("Idempotency-Key", "js-checkout-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutRequest(fixture)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORDER-JS-1"))
                .andExpect(jsonPath("$.status").value("APPROVAL_PENDING"))
                .andExpect(jsonPath("$.approvalUrl").value(
                        "https://www.sandbox.paypal.com/checkoutnow?token=ORDER-JS-1"))
                .andReturn().getResponse().getContentAsString();
        String checkoutSessionId = objectMapper.readTree(createdBody).path("checkoutSessionId").asText();
        when(paypalClient.captureOrder(anyLong(), eq("ORDER-JS-1")))
                .thenReturn(new PaymentGateway.CaptureResult(true, "CAPTURE-JS-1", new BigDecimal("42.00"), "USD"));

        mvc.perform(post("/api/payments/ORDER-JS-1/capture").session(user.session()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutSessionId").value(checkoutSessionId))
                .andExpect(jsonPath("$.orderId").value("ORDER-JS-1"))
                .andExpect(jsonPath("$.status").value("CAPTURED"))
                .andExpect(jsonPath("$.captureId").value("CAPTURE-JS-1"))
                .andExpect(jsonPath("$.amount").value("42.00"));
    }

    @Test
    void vaultSetupPersistsEncryptedTokenAndChargesForTheSameOwner() throws Exception {
        TestUser user = createUser("paypal-vault");
        when(paypalClient.createVaultSetup(anyString(), any(UUID.class)))
                .thenReturn(new PayPalClient.VaultSetupCreation("SETUP-1", "CUSTOMER-1",
                        "https://sandbox.paypal.com/agreements/approve?token=SETUP-1"));
        String setupBody = mvc.perform(post("/api/payments/methods/paypal/setup")
                        .session(user.session()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consent\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAYER_ACTION_REQUIRED"))
                .andExpect(jsonPath("$.approvalUrl").value(
                        "https://sandbox.paypal.com/agreements/approve?token=SETUP-1"))
                .andReturn().getResponse().getContentAsString();
        String setupId = objectMapper.readTree(setupBody).path("setupSessionId").asText();
        when(paypalClient.createVaultPaymentToken(eq("SETUP-1"), any(UUID.class)))
                .thenReturn(new PayPalClient.VaultTokenCreation(
                        "VAULT-TOKEN-SECRET", "CUSTOMER-1", "buyer@example.com"));

        String savedBody = mvc.perform(post("/api/payments/methods/paypal/setup/{setupId}/complete", setupId)
                        .session(user.session()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("PAYPAL"))
                .andExpect(jsonPath("$.paypalEmail").value("buyer@example.com"))
                .andExpect(jsonPath("$.status").value("SAVED"))
                .andReturn().getResponse().getContentAsString();
        String methodId = objectMapper.readTree(savedBody).path("id").asText();

        var stored = paymentMethods.findByOwnerIdAndIdAndStatus(user.id(), UUID.fromString(methodId), "SAVED")
                .orElseThrow();
        assertThat(stored.getVaultTokenEncrypted()).doesNotContain("VAULT-TOKEN-SECRET");
        assertThat(vaultTokenCipher.decrypt(stored.getVaultTokenEncrypted())).isEqualTo("VAULT-TOKEN-SECRET");
        mvc.perform(post("/api/payments/methods/paypal/setup/{setupId}/complete", setupId)
                        .session(user.session()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(methodId));
        mvc.perform(get("/api/payments/methods").session(user.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(methodId))
                .andExpect(jsonPath("$[0].vaultToken").doesNotExist());

        ShoppingFixture fixture = createShoppingFixture(user.id());
        when(paypalClient.createOrderWithVault(anyLong(), any(BigDecimal.class), eq("USD"),
                eq("VAULT-TOKEN-SECRET"), eq("sdk-session-123")))
                .thenReturn(new PaymentGateway.VaultOrderCreation("ORDER-VAULT-1", null,
                        new PaymentGateway.CaptureResult(true, "CAPTURE-VAULT-1",
                                new BigDecimal("42.00"), "USD")));

        mvc.perform(post("/api/payments/methods/{methodId}/checkout", methodId)
                        .session(user.session()).with(csrf())
                        .header("Idempotency-Key", "vault-checkout-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"searchSessionId":"%s","candidateId":"%s","clientMetadataId":"sdk-session-123"}
                                """.formatted(fixture.searchSessionId(), fixture.candidateId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORDER-VAULT-1"))
                .andExpect(jsonPath("$.status").value("CAPTURED"))
                .andExpect(jsonPath("$.captureId").value("CAPTURE-VAULT-1"))
                .andExpect(jsonPath("$.amount").value("42.00"));

        mvc.perform(delete("/api/payments/methods/{methodId}", methodId)
                        .session(user.session()).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/payments/methods").session(user.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void savedPaymentMethodCannotBeReadOrRemovedByAnotherAccount() throws Exception {
        TestUser owner = createUser("paypal-vault-owner");
        when(paypalClient.createVaultSetup(anyString(), any(UUID.class)))
                .thenReturn(new PayPalClient.VaultSetupCreation("SETUP-OWNER", "CUSTOMER-OWNER",
                        "https://sandbox.paypal.com/agreements/approve?token=SETUP-OWNER"));
        String setupBody = mvc.perform(post("/api/payments/methods/paypal/setup")
                        .session(owner.session()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consent\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String setupId = objectMapper.readTree(setupBody).path("setupSessionId").asText();
        when(paypalClient.createVaultPaymentToken(eq("SETUP-OWNER"), any(UUID.class)))
                .thenReturn(new PayPalClient.VaultTokenCreation("OWNER-VAULT", "CUSTOMER-OWNER", null));
        String savedBody = mvc.perform(post("/api/payments/methods/paypal/setup/{setupId}/complete", setupId)
                        .session(owner.session()).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String methodId = objectMapper.readTree(savedBody).path("id").asText();

        TestUser stranger = createUser("paypal-vault-stranger");
        mvc.perform(get("/api/payments/methods").session(stranger.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete("/api/payments/methods/{methodId}", methodId)
                        .session(stranger.session()).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/payments/methods").session(owner.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(methodId));
    }

    @Test
    void savedPayPalSetupRequiresExplicitConsent() throws Exception {
        TestUser user = createUser("paypal-no-consent");

        mvc.perform(post("/api/payments/methods/paypal/setup")
                        .session(user.session()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consent\":false}"))
                .andExpect(status().isBadRequest());
    }

    private TestUser createUser(String prefix) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var signup = mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s-%s@example.com","username":"%s-%s","password":"test-password-123"}
                                """.formatted(prefix, suffix, prefix, suffix)))
                .andExpect(status().isCreated())
                .andReturn();
        return new TestUser(objectMapper.readTree(signup.getResponse().getContentAsString()).path("id").asText(),
                (MockHttpSession) signup.getRequest().getSession(false));
    }

    private ShoppingFixture createShoppingFixture(String ownerId) throws Exception {
        policies.save(ownerId, new SpendingPolicyRequest(new BigDecimal("100.00"),
                new BigDecimal("100.00"), new BigDecimal("500.00"), 5, "USD", "US", "UTC"));
        ShoppingSearchSession search = searchSessions.save(new ShoppingSearchSession(
                UUID.randomUUID(), ownerId, "compact laptop", "compact laptop", new BigDecimal("100.00")));
        ProductCandidate candidate = candidates.save(new ProductCandidate(
                UUID.randomUUID(), search, "catalog-laptop", "Compact laptop", "Acme", null,
                "store.example", "https://store.example/laptop", new BigDecimal("42.00"), "USD", true, null));
        JsonNode product = objectMapper.readTree("""
                {"offers":[{"domain":"store.example","url":"https://store.example/laptop",
                  "availability":"InStock","price":{"price":"42.00","currency":"USD"}}]}
                """);
        when(channel3Client.getProduct(eq("catalog-laptop"), eq("US"), eq("USD"))).thenReturn(product);
        return new ShoppingFixture(search.getId(), candidate.getId());
    }

    private String checkoutRequest(ShoppingFixture fixture) {
        return """
                {"searchSessionId":"%s","candidateId":"%s"}
                """.formatted(fixture.searchSessionId(), fixture.candidateId());
    }

    private record TestUser(String id, MockHttpSession session) { }
    private record ShoppingFixture(UUID searchSessionId, UUID candidateId) { }
}
