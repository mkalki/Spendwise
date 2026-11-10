package com.spendwise.payment.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendwise.common.config.TransientHttpRetry;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class PayPalClient implements PaymentGateway {

    private final RestClient restClient;
    private final PayPalAuthService authService;
    private final String returnUrl;
    private final String cancelUrl;
    private final String vaultReturnUrl;
    private final String vaultCancelUrl;

    @org.springframework.beans.factory.annotation.Autowired
    public PayPalClient(
            PayPalAuthService authService,
            ClientHttpRequestFactory requestFactory,
            @Value("${spendwise.paypal.base-url}") String baseUrl,
            @Value("${spendwise.paypal.return-url}") String returnUrl,
            @Value("${spendwise.paypal.cancel-url}") String cancelUrl,
            @Value("${spendwise.paypal.vault-return-url:${spendwise.paypal.return-url}}") String vaultReturnUrl,
            @Value("${spendwise.paypal.vault-cancel-url:${spendwise.paypal.cancel-url}}") String vaultCancelUrl) {
        this(authService, RestClient.builder()
                .baseUrl(requireSandboxBaseUrl(baseUrl))
                .requestFactory(requestFactory)
                .build(), returnUrl, cancelUrl, vaultReturnUrl, vaultCancelUrl);
    }

    PayPalClient(PayPalAuthService authService, RestClient restClient, String returnUrl, String cancelUrl) {
        this(authService, restClient, returnUrl, cancelUrl, returnUrl, cancelUrl);
    }

    private PayPalClient(
            PayPalAuthService authService, RestClient restClient, String returnUrl, String cancelUrl,
            String vaultReturnUrl, String vaultCancelUrl) {
        this.restClient = restClient;
        this.authService = authService;
        this.returnUrl = returnUrl;
        this.cancelUrl = cancelUrl;
        this.vaultReturnUrl = vaultReturnUrl;
        this.vaultCancelUrl = vaultCancelUrl;
    }

    PayPalClient(
            ClientHttpRequestFactory requestFactory,
            String baseUrl,
            String clientId,
            String clientSecret,
            String returnUrl,
            String cancelUrl) {
        this(new PayPalAuthService(requestFactory, baseUrl, clientId, clientSecret),
                RestClient.builder().baseUrl(requireSandboxBaseUrl(baseUrl))
                        .requestFactory(requestFactory).build(),
                returnUrl, cancelUrl, returnUrl, cancelUrl);
    }

    @Override
    public OrderCreation createOrder(Long transactionId, BigDecimal amount, String currencyCode) {
        String accessToken = authService.accessToken();
        Map<String, Object> purchaseUnit = Map.of(
                "reference_id", "spendwise-" + transactionId,
                "description", "Spendwise Sandbox demonstration payment",
                "amount", Map.of(
                        "currency_code", currencyCode,
                        "value", amount.toPlainString()));
        Map<String, Object> applicationContext = Map.of(
                "return_url", returnUrl,
                "cancel_url", cancelUrl,
                "user_action", "PAY_NOW");
        Map<String, Object> request = Map.of(
                "intent", "CAPTURE",
                "purchase_units", List.of(purchaseUnit),
                "application_context", applicationContext);

        JsonNode response = request(() -> restClient.post()
                .uri("/v2/checkout/orders")
                .headers(headers -> {
                    headers.setBearerAuth(accessToken);
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set("PayPal-Request-Id", "spw-order-" + transactionId);
                })
                .body(request)
                .retrieve()
                .body(JsonNode.class));
        String orderId = text(response, "id");
        String approvalUrl = null;
        JsonNode links = response.path("links");
        if (links.isArray()) {
            for (JsonNode link : links) {
                if ("approve".equals(text(link, "rel")) || "payer-action".equals(text(link, "rel"))) {
                    approvalUrl = text(link, "href");
                }
            }
        }
        if (!StringUtils.hasText(orderId) || !isHttpsUrl(approvalUrl)) {
            throw new PaymentProviderException();
        }
        return new OrderCreation(orderId, approvalUrl);
    }

    public VaultSetupCreation createVaultSetup(String merchantCustomerId, UUID requestId) {
        String accessToken = authService.accessToken();
        Map<String, Object> experienceContext = Map.of(
                "return_url", vaultReturnUrl,
                "cancel_url", vaultCancelUrl,
                "shipping_preference", "NO_SHIPPING",
                "payment_method_preference", "IMMEDIATE_PAYMENT_REQUIRED");
        Map<String, Object> paypalSource = Map.of(
                "description", "Spendwise saved PayPal account",
                "usage_pattern", "IMMEDIATE",
                "usage_type", "MERCHANT",
                "customer_type", "CONSUMER",
                "experience_context", experienceContext);
        Map<String, Object> request = Map.of(
                "customer", Map.of("merchant_customer_id", merchantCustomerId),
                "payment_source", Map.of("paypal", paypalSource));
        JsonNode response = request(() -> restClient.post()
                .uri("/v3/vault/setup-tokens")
                .headers(headers -> {
                    headers.setBearerAuth(accessToken);
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set("PayPal-Request-Id", "spw-vault-setup-" + requestId);
                })
                .body(request)
                .retrieve()
                .body(JsonNode.class));
        String setupTokenId = text(response, "id");
        String customerId = text(response.path("customer"), "id");
        String approvalUrl = link(response, "approve");
        if (!StringUtils.hasText(setupTokenId) || !isPayPalApprovalUrl(approvalUrl)) {
            throw new PaymentProviderException();
        }
        return new VaultSetupCreation(setupTokenId, customerId, approvalUrl);
    }

    public VaultTokenCreation createVaultPaymentToken(String setupTokenId, UUID requestId) {
        String accessToken = authService.accessToken();
        JsonNode response = request(() -> restClient.post()
                .uri("/v3/vault/payment-tokens")
                .headers(headers -> {
                    headers.setBearerAuth(accessToken);
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set("PayPal-Request-Id", "spw-vault-token-" + requestId);
                })
                .body(Map.of("payment_source", Map.of("token", Map.of(
                        "id", setupTokenId, "type", "SETUP_TOKEN"))))
                .retrieve()
                .body(JsonNode.class));
        String vaultId = text(response, "id");
        String customerId = text(response.path("customer"), "id");
        String email = text(response.path("payment_source").path("paypal"), "email_address");
        if (!StringUtils.hasText(vaultId)) throw new PaymentProviderException();
        return new VaultTokenCreation(vaultId, customerId, email);
    }

    public void deleteVaultPaymentToken(String vaultId) {
        String accessToken = authService.accessToken();
        request(() -> {
            restClient.delete()
                    .uri("/v3/vault/payment-tokens/{tokenId}", vaultId)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .toBodilessEntity();
            return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        });
    }

    @Override
    public VaultOrderCreation createOrderWithVault(
            Long transactionId, BigDecimal amount, String currencyCode, String vaultId, String clientMetadataId) {
        String accessToken = authService.accessToken();
        Map<String, Object> requestBody = Map.of(
                "intent", "CAPTURE",
                "payment_source", Map.of("paypal", Map.of("vault_id", vaultId)),
                "purchase_units", List.of(Map.of(
                        "reference_id", "spendwise-" + transactionId,
                        "description", "Spendwise Sandbox demonstration payment",
                        "amount", Map.of("currency_code", currencyCode, "value", amount.toPlainString()))));
        JsonNode response = request(() -> restClient.post()
                .uri("/v2/checkout/orders")
                .headers(headers -> {
                    headers.setBearerAuth(accessToken);
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set("PayPal-Request-Id", "spw-vault-order-" + transactionId);
                    if (StringUtils.hasText(clientMetadataId)) {
                        headers.set("PayPal-Client-Metadata-Id", clientMetadataId);
                    }
                })
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class));
        String orderId = text(response, "id");
        String approvalUrl = link(response, "approve");
        CaptureResult capture = firstCapture(response);
        if (!StringUtils.hasText(orderId)) throw new PaymentProviderException();
        return new VaultOrderCreation(orderId, isPayPalApprovalUrl(approvalUrl) ? approvalUrl : null, capture);
    }

    @Override
    public CaptureResult captureOrder(Long transactionId, String paypalOrderId) {
        String accessToken = authService.accessToken();
        JsonNode response = request(() -> restClient.post()
                .uri("/v2/checkout/orders/{orderId}/capture", paypalOrderId)
                .headers(headers -> {
                    headers.setBearerAuth(accessToken);
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set("PayPal-Request-Id", "spw-capture-" + transactionId);
                })
                .body(Map.of())
                .retrieve()
                .body(JsonNode.class));
        JsonNode captures = response.path("purchase_units");
        if (!captures.isArray()) {
            return new CaptureResult(false, null, null, null);
        }
        for (JsonNode unit : captures) {
            JsonNode unitCaptures = unit.path("payments").path("captures");
            if (!unitCaptures.isArray()) {
                continue;
            }
            for (JsonNode capture : unitCaptures) {
                JsonNode captureAmount = capture.path("amount");
                return new CaptureResult(
                        "COMPLETED".equals(text(capture, "status")),
                        text(capture, "id"),
                        decimal(captureAmount.path("value")),
                        text(captureAmount, "currency_code"));
            }
        }
        return new CaptureResult(false, null, null, null);
    }

    @Override
    public boolean canCancelOrder(String paypalOrderId) {
        String accessToken = authService.accessToken();
        JsonNode response = request(() -> restClient.get()
                .uri("/v2/checkout/orders/{orderId}", paypalOrderId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(JsonNode.class));
        if (!"CREATED".equals(text(response, "status"))
                && !"APPROVED".equals(text(response, "status"))) {
            return false;
        }
        JsonNode units = response.path("purchase_units");
        if (!units.isArray()) {
            return true;
        }
        for (JsonNode unit : units) {
            if (!unit.path("payments").path("captures").isMissingNode()
                    && unit.path("payments").path("captures").isArray()
                    && !unit.path("payments").path("captures").isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public OrderStatus getOrderStatus(String paypalOrderId) {
        String accessToken = authService.accessToken();
        JsonNode response = request(() -> restClient.get()
                .uri("/v2/checkout/orders/{orderId}", paypalOrderId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(JsonNode.class));
        JsonNode units = response.path("purchase_units");
        if (!units.isArray() || units.isEmpty()) {
            return new OrderStatus(text(response, "status"), null, null, null, null);
        }
        JsonNode unit = units.get(0);
        JsonNode amount = unit.path("amount");
        JsonNode captures = unit.path("payments").path("captures");
        JsonNode capture = captures.isArray() && !captures.isEmpty() ? captures.get(0) : null;
        JsonNode confirmedAmount = capture == null ? amount : capture.path("amount");
        return new OrderStatus(
                text(response, "status"),
                text(unit, "reference_id"),
                capture == null ? null : text(capture, "id"),
                decimal(confirmedAmount.path("value")),
                text(confirmedAmount, "currency_code"));
    }

    @Override
    public CaptureStatus getCaptureStatus(String paypalCaptureId) {
        String accessToken = authService.accessToken();
        JsonNode response = request(() -> restClient.get()
                .uri("/v2/payments/captures/{captureId}", paypalCaptureId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(JsonNode.class));
        JsonNode amount = response.path("amount");
        return new CaptureStatus(
                text(response, "status"),
                text(response, "id"),
                response.path("supplementary_data").path("related_ids").path("order_id").asText(null),
                decimal(amount.path("value")),
                text(amount, "currency_code"));
    }

    private JsonNode request(java.util.function.Supplier<JsonNode> operation) {
        try {
            JsonNode response = TransientHttpRetry.execute(operation, false);
            if (response == null) {
                throw new PaymentProviderException();
            }
            return response;
        } catch (ResourceAccessException exception) {
            throw new PaymentProviderTimeoutException();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                throw new PaymentProviderRejectedException();
            }
            throw new PaymentProviderException();
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String link(JsonNode response, String relation) {
        JsonNode links = response.path("links");
        if (links.isArray()) {
            for (JsonNode link : links) {
                if (relation.equals(text(link, "rel"))) return text(link, "href");
            }
        }
        return null;
    }

    private CaptureResult firstCapture(JsonNode response) {
        JsonNode units = response.path("purchase_units");
        if (units.isArray()) {
            for (JsonNode unit : units) {
                JsonNode captures = unit.path("payments").path("captures");
                if (captures.isArray() && !captures.isEmpty()) {
                    JsonNode capture = captures.get(0);
                    JsonNode amount = capture.path("amount");
                    return new CaptureResult("COMPLETED".equals(text(capture, "status")),
                            text(capture, "id"), decimal(amount.path("value")), text(amount, "currency_code"));
                }
            }
        }
        return null;
    }

    private boolean isPayPalApprovalUrl(String value) {
        if (!StringUtils.hasText(value)) return false;
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && ("www.sandbox.paypal.com".equalsIgnoreCase(uri.getHost())
                    || "sandbox.paypal.com".equalsIgnoreCase(uri.getHost()))
                    && uri.getUserInfo() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public record VaultSetupCreation(String setupTokenId, String customerId, String approvalUrl) { }
    public record VaultTokenCreation(String vaultId, String customerId, String email) { }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(node.asText());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean isHttpsUrl(String value) {
        if (value == null) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "www.sandbox.paypal.com".equalsIgnoreCase(uri.getHost())
                    && uri.getUserInfo() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String requireSandboxBaseUrl(String value) {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"api-m.sandbox.paypal.com".equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("PayPal integration must use the official HTTPS Sandbox API.");
        }
        return value;
    }
}
