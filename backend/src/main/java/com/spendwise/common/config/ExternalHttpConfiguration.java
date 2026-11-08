package com.spendwise.common.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

@Configuration
public class ExternalHttpConfiguration {

    @Bean
    ClientHttpRequestFactory externalHttpRequestFactory(
            @Value("${spendwise.http.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${spendwise.http.read-timeout-seconds:20}") int readTimeoutSeconds) {
        if (connectTimeoutSeconds < 1 || readTimeoutSeconds < 1) {
            throw new IllegalArgumentException("External HTTP timeouts must be positive.");
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return requestFactory;
    }
}
