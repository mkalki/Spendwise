package com.spendwise.auth.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ResendEmailClientTest {

    @Test
    void sendsVerificationEmailThroughResendWithConfiguredSenderAndBearerKey() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.resend.com")
                .requestFactory(new SimpleClientHttpRequestFactory());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String link = "https://spendwise.example/verify-email?token=abc123";
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer re_test_key"))
                .andExpect(content().json("""
                        {"from":"Spendwise <verified@example.com>","to":["buyer@example.com"],
                         "subject":"Verify your Spendwise email",
                         "html":"<p>Confirm your Spendwise email address:</p><p><a href=\\\"https://spendwise.example/verify-email?token=abc123\\\">Verify email address</a></p><p>This link expires in 24 hours. If you did not create this account, ignore this email.</p>",
                         "text":"Verify your Spendwise email address: https://spendwise.example/verify-email?token=abc123\\n\\nThis link expires in 24 hours. If you did not create this account, ignore this email."}
                        """))
                .andRespond(withNoContent());
        ResendEmailClient client = new ResendEmailClient(builder.build(), "re_test_key",
                "Spendwise <verified@example.com>");

        client.sendVerificationEmail("buyer@example.com", link);

        server.verify();
    }

    @Test
    void refusesToSendWhenApiKeyOrSenderIsMissing() {
        ResendEmailClient client = new ResendEmailClient(RestClient.builder()
                .requestFactory(new SimpleClientHttpRequestFactory()).build(), "", "");

        assertThatThrownBy(() -> client.sendVerificationEmail("buyer@example.com", "https://example.com"))
                .isInstanceOf(EmailDeliveryException.class);
    }

    @Test
    void convertsResendProviderErrorsToSafeDeliveryFailure() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.resend.com")
                .requestFactory(new SimpleClientHttpRequestFactory());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        ResendEmailClient client = new ResendEmailClient(builder.build(), "re_invalid", "verified@example.com");

        assertThatThrownBy(() -> client.sendVerificationEmail("buyer@example.com", "https://example.com/verify"))
                .isInstanceOf(EmailDeliveryException.class);
        server.verify();
    }
}
