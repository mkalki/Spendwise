package com.spendwise.payment.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendwise.common.config.TransientHttpRetry;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
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

    @org.springframework.beans.factory.annotation.Autowired
    public PayPalClient(
            PayPalAuthService authService,
            ClientHttpRequestFactory requestFactory,
            @Value("${spendwise.paypal.base-url}") String baseUrl,
            @Value("${spendwise.paypal.return-url}") String returnUrl,
            @Value("${spendwise.paypal.cancel-url}") String cancelUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(requireSandboxBaseUrl(baseUrl))
                .requestFactory(requestFactory)
                .build();
        this.authService = authService;
        this.returnUrl = returnUrl;
        this.cancelUrl = cancelUrl;
    }

    PayPalClient(
            ClientHttpRequestFactory requestFactory,
            String baseUrl,
            String clientId,
            String clientSecret,
            String returnUrl,
            String cancelUrl) {
        this(new PayPalAuthService(requestFactory, baseUrl, clientId, clientSecret),
                requestFactory, baseUrl, returnUrl, cancelUrl);
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

    private String requireSandboxBaseUrl(String value) {
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
