package com.spendwise.integration.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.integration.gemini.dto.GeminiRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiClientTest {

    @Test
    void identifiesGoogleInvalidApiKeyWithoutExposingItsResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("""
                        {"error":{"code":400,"details":[{"reason":"API_KEY_INVALID"}]}}
                        """));
        ObjectMapper mapper = new ObjectMapper();
        GeminiClient client = new GeminiClient(builder.build(), mapper, "test-gemini-key", "gemini-3.8-flash");
        GeminiService service = new GeminiService(client, mapper);

        assertThatThrownBy(() -> service.extractShoppingIntent("boAt headphones"))
                .isInstanceOfSatisfying(com.spendwise.common.exception.ApiException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("GEMINI_INVALID_API_KEY");
                    assertThat(error.getMessage()).doesNotContain("test-gemini-key", "API_KEY_INVALID");
                });
        server.verify();
    }

    @Test
    void sendsPromptToGeminiAndExtractsGeneratedText() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-gemini-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"contents":[{"role":"user","parts":[{"text":"Say hello."}]}],
                         "generationConfig":{"thinkingConfig":{"thinkingLevel":"low"}}}
                        """))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"Hello"},{"text":" from Gemini."}]}}]}
                        """, MediaType.APPLICATION_JSON));
        GeminiClient client = new GeminiClient(builder.build(), new ObjectMapper(), "test-gemini-key", "gemini-3.8-flash");

        var response = client.generate(new GeminiRequest("Say hello."));

        assertThat(response.model()).isEqualTo("gemini-3.8-flash");
        assertThat(response.text()).isEqualTo("Hello from Gemini.");
        server.verify();
    }

    @Test
    void convertsGeminiHttpErrorsIntoProviderException() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(HttpStatus.BAD_GATEWAY));
        GeminiClient client = new GeminiClient(builder.build(), new ObjectMapper(), "test-gemini-key", "gemini-3.8-flash");

        assertThatThrownBy(() -> client.generate(new GeminiRequest("Say hello.")))
                .isInstanceOf(GeminiProviderException.class);
        server.verify();
    }

    @Test
    void rejectsResponsesWithoutGeneratedText() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
                .andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));
        GeminiClient client = new GeminiClient(builder.build(), new ObjectMapper(), "test-gemini-key", "gemini-3.8-flash");

        assertThatThrownBy(() -> client.generate(new GeminiRequest("Say hello.")))
                .isInstanceOf(GeminiProviderException.class);
        server.verify();
    }

    @Test
    void requestsJsonOutputForStructuredIntentExtraction() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
                .andExpect(content().json("""
                        {
                          "contents":[{"role":"user","parts":[{"text":"Extract shopping intent."}]}],
                          "generationConfig":{"responseMimeType":"application/json","thinkingConfig":{"thinkingLevel":"low"}}
                        }
                        """))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"{\\"normalizedQuery\\":\\"laptop\\"}"}]}}]}
                        """, MediaType.APPLICATION_JSON));
        GeminiClient client = new GeminiClient(builder.build(), new ObjectMapper(), "test-gemini-key", "gemini-3.8-flash");

        var response = client.generateJson("Extract shopping intent.");

        assertThat(response.text()).isEqualTo("{\"normalizedQuery\":\"laptop\"}");
        server.verify();
    }

    @Test
    void reportsMissingApiKeyWithoutMakingARequest() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiClient client = new GeminiClient(
                builder.build(),
                new ObjectMapper(),
                " ",
                "gemini-3.8-flash");

        assertThatThrownBy(() -> client.generate(new GeminiRequest("Say hello.")))
                .isInstanceOf(GeminiNotConfiguredException.class);
        server.verify();
    }

    @Test
    void identifiesDocumentedInvalidApiKeyPayload() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error":{"code":400,"status":"INVALID_ARGUMENT","details":[
                                  {"reason":"API_KEY_INVALID","metadata":{"service":"generativelanguage.googleapis.com"}}
                                ]}}
                                """));
        GeminiClient client = new GeminiClient(builder.build(), new ObjectMapper(), "test-gemini-key", "gemini-3.8-flash");

        assertThatThrownBy(() -> client.generate(new GeminiRequest("Say hello.")))
                .isInstanceOf(GeminiInvalidApiKeyException.class);
        server.verify();
    }

    @Test
    void recognizesInvalidKeyInAlternateErrorShapeAndStatus() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error":{"errorCode":"INVALID_API_KEY"}}
                                """));
        GeminiClient client = new GeminiClient(builder.build(), new ObjectMapper(), "test-gemini-key", "gemini-3.8-flash");

        assertThatThrownBy(() -> client.generate(new GeminiRequest("Say hello.")))
                .isInstanceOf(GeminiInvalidApiKeyException.class);
        server.verify();
    }

    @Test
    void identifiesPlainTextInvalidKeyMessageWithoutAssumingJson() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("API key not valid. Please pass a valid API key."));
        GeminiClient client = new GeminiClient(builder.build(), new ObjectMapper(), "test-gemini-key", "gemini-3.8-flash");

        assertThatThrownBy(() -> client.generate(new GeminiRequest("Say hello.")))
                .isInstanceOf(GeminiInvalidApiKeyException.class);
        server.verify();
    }

    @Test
    void leavesPermissionErrorsAndMalformedBodiesAsGenericProviderErrors() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent";
        server.expect(requestTo(url))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error":{"status":"PERMISSION_DENIED","message":"The key lacks permission."}}
                                """));
        server.expect(requestTo(url))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{not-json"));
        GeminiClient client = new GeminiClient(builder.build(), new ObjectMapper(), "test-gemini-key", "gemini-3.8-flash");

        assertThatThrownBy(() -> client.generate(new GeminiRequest("Say hello.")))
                .isInstanceOf(GeminiProviderException.class)
                .isNotInstanceOf(GeminiInvalidApiKeyException.class);
        assertThatThrownBy(() -> client.generate(new GeminiRequest("Say hello.")))
                .isInstanceOf(GeminiProviderException.class)
                .isNotInstanceOf(GeminiInvalidApiKeyException.class);
        server.verify();
    }
}
