package com.spendwise.auth.integration;

import com.spendwise.common.config.TransientHttpRetry;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ResendEmailClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String fromAddress;

    public ResendEmailClient(
            ClientHttpRequestFactory requestFactory,
            @Value("${spendwise.email.resend.api-key:}") String apiKey,
            @Value("${spendwise.email.from:}") String fromAddress) {
        this(RestClient.builder()
                .baseUrl("https://api.resend.com")
                .requestFactory(requestFactory)
                .build(), apiKey, fromAddress);
    }

    ResendEmailClient(RestClient restClient, String apiKey, String fromAddress) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.fromAddress = fromAddress;
    }

    public void sendVerificationEmail(String recipient, String verificationUrl) {
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(fromAddress)) {
            throw new EmailDeliveryException();
        }
        String html = "<p>Confirm your Spendwise email address:</p><p><a href=\""
                + verificationUrl
                + "\">Verify email address</a></p><p>This link expires in 24 hours. If you did not create this account, ignore this email.</p>";
        String text = "Verify your Spendwise email address: " + verificationUrl
                + "\n\nThis link expires in 24 hours. If you did not create this account, ignore this email.";
        try {
            TransientHttpRetry.execute(() -> restClient.post()
                    .uri("/emails")
                    .headers(headers -> {
                        headers.setBearerAuth(apiKey);
                        headers.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(new ResendEmailRequest(fromAddress, List.of(recipient),
                            "Verify your Spendwise email", html, text))
                    .retrieve()
                    .toBodilessEntity(), false);
        } catch (RestClientException exception) {
            throw new EmailDeliveryException();
        }
    }

    private record ResendEmailRequest(String from, List<String> to, String subject, String html, String text) { }
}
