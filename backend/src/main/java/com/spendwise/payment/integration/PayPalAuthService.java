package com.spendwise.payment.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendwise.common.config.TransientHttpRetry;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class PayPalAuthService {

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    public PayPalAuthService(
            ClientHttpRequestFactory requestFactory,
            @Value("${spendwise.paypal.base-url}") String baseUrl,
            @Value("${spendwise.paypal.client-id}") String clientId,
            @Value("${spendwise.paypal.client-secret}") String clientSecret) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String accessToken() {
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            throw new PaymentProviderNotConfiguredException();
        }
        String credentials = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        try {
            JsonNode response = TransientHttpRetry.execute(() -> restClient.post()
                    .uri("/v1/oauth2/token")
                    .header("Authorization", "Basic " + credentials)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class), false);
            String token = response == null ? null : response.path("access_token").asText(null);
            if (!StringUtils.hasText(token)) {
                throw new PaymentProviderException();
            }
            return token;
        } catch (ResourceAccessException exception) {
            throw new PaymentProviderTimeoutException();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                throw new PaymentProviderRejectedException();
            }
            throw new PaymentProviderException();
        }
    }
}
