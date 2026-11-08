package com.spendwise.product.integration;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

class Channel3ClientTest {

    @Test
    void refusesToSendTheProviderKeyToAnUntrustedHost() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> client("https://attacker.example"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> client("https://api.trychannel3.com:8443"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> client("https://api.trychannel3.com?redirect=attacker.example"));
    }

    @Test
    void acceptsOnlyTheOfficialChannel3ApiHost() {
        assertThatCode(() -> client("https://api.trychannel3.com"))
                .doesNotThrowAnyException();
    }

    private Channel3Client client(String baseUrl) {
        return new Channel3Client(new SimpleClientHttpRequestFactory(), baseUrl, "test-key");
    }
}
