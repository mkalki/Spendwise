package com.spendwise.product.integration;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.util.Map;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
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

    @Test
    void postsNaturalLanguageSearchWithApiKeyAndTenResultLimit() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.trychannel3.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.trychannel3.com/v1/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "test-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "query":"Find wireless headphones under $45",
                          "limit":10,
                          "config":{"country":"US","currency":"USD"},
                          "filters":{"price":{"max_price":45.00}}
                        }
                        """))
                .andRespond(withSuccess("""
                        {"products":[{"id":"headphones-1","title":"Wireless Headphones",
                          "images":[{"cleaned_url":"https://images.example/headphones.jpg"}],
                          "offers":[{"domain":"shop.example","url":"https://shop.example/item",
                            "availability":"InStock","price":{"price":39.99,"currency":"USD"}}]}]}
                        """, MediaType.APPLICATION_JSON));
        Channel3Client client = new Channel3Client(builder.build(), "test-key", 10);

        var response = client.search("Find wireless headphones under $45", new BigDecimal("45.00"), "US", "USD");

        assertThat(response.path("products").get(0).path("images").get(0).path("cleaned_url").asText())
                .isEqualTo("https://images.example/headphones.jpg");
        assertThat(response.path("products").get(0).path("offers").get(0).path("price").path("price").decimalValue())
                .isEqualByComparingTo("39.99");
        server.verify();
    }

    @Test
    void forwardsCustomerFiltersUsingChannel3SearchFilterFields() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.trychannel3.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.trychannel3.com/v1/search"))
                .andExpect(content().json("""
                        {
                          "query":"laptop",
                          "limit":10,
                          "config":{"country":"US","currency":"USD"},
                          "filters":{
                            "price":{"min_price":500,"max_price":1500},
                            "category_ids":["laptops"],
                            "website_ids":["shop.example"],
                            "availability":["InStock"],
                            "sale":"on_sale"
                          }
                        }
                        """))
                .andRespond(withSuccess("{\"products\":[]}", MediaType.APPLICATION_JSON));
        Channel3Client client = new Channel3Client(builder.build(), "test-key", 10);

        client.searchWithFilters("laptop", Map.of(
                "price", Map.of("min_price", 500, "max_price", 1500),
                "category_ids", List.of("laptops"),
                "website_ids", List.of("shop.example"),
                "availability", List.of("InStock"),
                "sale", "on_sale"), "US", "USD");

        server.verify();
    }

    @Test
    void rejectsAnInvalidSearchResultLimit() {
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> new Channel3Client(RestClient.builder().build(), "test-key", 101));
    }

    private Channel3Client client(String baseUrl) {
        return new Channel3Client(new SimpleClientHttpRequestFactory(), baseUrl, "test-key");
    }
}
