package com.spendwise.product.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendwise.common.exception.ApiException;
import com.spendwise.policy.entity.SpendingPolicy;
import com.spendwise.policy.engine.PolicyEngine;
import com.spendwise.policy.service.SpendingPolicyService;
import com.spendwise.product.dto.ShoppingSearchRequest;
import com.spendwise.product.dto.ShoppingSearchResponse;
import com.spendwise.product.entity.ProductCandidate;
import com.spendwise.product.entity.ShoppingSearchSession;
import com.spendwise.product.integration.Channel3Client;
import com.spendwise.product.integration.Channel3NotConfiguredException;
import com.spendwise.product.repository.ProductCandidateRepository;
import com.spendwise.product.repository.ShoppingSearchSessionRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class ProductSearchService {

    private final Channel3Client channel3Client;
    private final ShoppingIntentService intentService;
    private final PolicyEngine policyEngine;
    private final SpendingPolicyService policyService;
    private final ShoppingSearchSessionRepository sessionRepository;
    private final ProductCandidateRepository candidateRepository;
    private final TransactionTemplate transactions;

    public ProductSearchService(
            Channel3Client channel3Client,
            ShoppingIntentService intentService,
            PolicyEngine policyEngine,
            SpendingPolicyService policyService,
            ShoppingSearchSessionRepository sessionRepository,
            ProductCandidateRepository candidateRepository,
            PlatformTransactionManager transactionManager) {
        this.channel3Client = channel3Client;
        this.intentService = intentService;
        this.policyEngine = policyEngine;
        this.policyService = policyService;
        this.sessionRepository = sessionRepository;
        this.candidateRepository = candidateRepository;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public ShoppingSearchResponse search(String ownerId, ShoppingSearchRequest request) {
        SpendingPolicy policy = policyService.getActive(ownerId);
        ShoppingIntentService.ParsedIntent intent = intentService.parse(request.message());
        BigDecimal effectiveMax = policyEngine.effectiveMaxProductPrice(policy, intent.requestedMaxPrice());

        JsonNode response;
        try {
            response = channel3Client.search(
                    intent.normalizedQuery(),
                    effectiveMax,
                    policy.getCountryCode(),
                    policy.getCurrencyCode());
        } catch (Channel3NotConfiguredException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PRODUCT_PROVIDER_NOT_CONFIGURED",
                    "Product search is unavailable because the product provider is not configured.");
        } catch (ResourceAccessException exception) {
            throw new ApiException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "PRODUCT_PROVIDER_TIMEOUT",
                    "Product search timed out. Please try again.");
        } catch (RestClientResponseException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "PRODUCT_PROVIDER_ERROR",
                    "The product provider could not complete the search.");
        }

        if (response == null || !response.path("products").isArray()) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "INVALID_PRODUCT_PROVIDER_RESPONSE",
                    "The product provider returned an invalid response.");
        }

        return transactions.execute(status -> {
            UUID sessionId = UUID.randomUUID();
            ShoppingSearchSession session = sessionRepository.save(new ShoppingSearchSession(
                    sessionId, ownerId, request.message().strip(), intent.normalizedQuery(), effectiveMax));
            List<ProductCandidate> candidates =
                    mapCandidates(response.path("products"), session, policy, effectiveMax);
            candidateRepository.saveAll(candidates);

            List<ShoppingSearchResponse.ProductCandidateView> eligible = candidates.stream()
                    .filter(ProductCandidate::isEligible)
                    .sorted(Comparator.comparing(ProductCandidate::getListedPrice))
                    .map(this::toView)
                    .toList();
            List<ShoppingSearchResponse.ProductCandidateView> rejected = candidates.stream()
                    .filter(candidate -> !candidate.isEligible())
                    .map(this::toView)
                    .toList();
            String recommendation = eligible.isEmpty()
                    ? null
                    : "Lowest-priced eligible offer: " + eligible.getFirst().productTitle()
                            + " from " + eligible.getFirst().merchantName() + ".";

            return new ShoppingSearchResponse(
                    session.getId(),
                    new ShoppingSearchResponse.ShoppingIntent(
                            intent.normalizedQuery(),
                            intent.keywords(),
                            intent.requestedMaxPrice() == null ? null : intent.requestedMaxPrice().toPlainString(),
                            policy.getCurrencyCode(),
                            policy.getCountryCode()),
                    eligible,
                    rejected,
                    recommendation);
        });
    }

    private List<ProductCandidate> mapCandidates(
            JsonNode products,
            ShoppingSearchSession session,
            SpendingPolicy policy,
            BigDecimal effectiveMax) {
        List<ProductCandidate> candidates = new ArrayList<>();
        for (JsonNode product : products) {
            String productId = text(product, "id");
            String title = text(product, "title");
            if (productId == null || title == null) {
                continue;
            }
            if (productId.length() > 200 || title.length() > 500) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "INVALID_PRODUCT_PROVIDER_RESPONSE",
                        "The product provider returned product data outside supported limits.");
            }
            String brand = product.path("brands").isArray() && !product.path("brands").isEmpty()
                    ? text(product.path("brands").get(0), "name")
                    : null;
            if (brand != null && brand.length() > 200) {
                brand = null;
            }
            String imageUrl = firstImage(product.path("images"));
            JsonNode offers = product.path("offers");
            if (!offers.isArray() || offers.isEmpty()) {
                candidates.add(toCandidate(
                        session, productId, title, brand, imageUrl, null, null, null, null,
                        "Product has no retailer offer."));
                continue;
            }
            for (JsonNode offer : offers) {
                BigDecimal price = decimal(offer.path("price").path("price"));
                String currencyCode = validCurrency(text(offer.path("price"), "currency"));
                String merchant = text(offer, "domain");
                if (merchant != null && merchant.length() > 200) {
                    merchant = null;
                }
                String offerUrl = text(offer, "url");
                if (price != null && price.signum() > 0
                        && price.compareTo(new BigDecimal("10000000000")) < 0) {
                    try {
                        price = price.setScale(2, java.math.RoundingMode.UNNECESSARY);
                    } catch (ArithmeticException exception) {
                        price = null;
                    }
                } else {
                    price = null;
                }
                String rejection = policyEngine.productRejectionReason(
                        price, currencyCode, offer.path("availability").asText(null),
                        merchant, offerUrl, policy, effectiveMax);
                candidates.add(toCandidate(
                        session, productId, title, brand, imageUrl, merchant, offerUrl,
                        price, currencyCode, rejection));
            }
        }
        return candidates;
    }

    private ProductCandidate toCandidate(
            ShoppingSearchSession session,
            String productId,
            String title,
            String brand,
            String imageUrl,
            String merchant,
            String offerUrl,
            BigDecimal price,
            String currencyCode,
            String rejection) {
        return new ProductCandidate(
                UUID.randomUUID(),
                session,
                productId,
                title,
                brand,
                imageUrl,
                merchant,
                offerUrl,
                price,
                currencyCode,
                rejection == null,
                rejection);
    }

    private String firstImage(JsonNode images) {
        if (!images.isArray()) {
            return null;
        }
        for (JsonNode image : images) {
            String cleaned = text(image, "cleaned_url");
            if (cleaned != null) {
                return cleaned;
            }
            String url = text(image, "url");
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isValueNode()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private BigDecimal decimal(JsonNode node) {
        return node != null && node.isNumber() ? node.decimalValue() : null;
    }

    private String validCurrency(String currencyCode) {
        return currencyCode != null && currencyCode.matches("[A-Z]{3}") ? currencyCode : null;
    }

    private ShoppingSearchResponse.ProductCandidateView toView(ProductCandidate candidate) {
        return new ShoppingSearchResponse.ProductCandidateView(
                candidate.getId(),
                candidate.getChannel3ProductId(),
                candidate.getProductTitle(),
                candidate.getBrand(),
                candidate.getImageUrl(),
                candidate.getMerchantName(),
                candidate.getOfferUrl(),
                candidate.getListedPrice() == null ? null : candidate.getListedPrice().toPlainString(),
                candidate.getCurrencyCode(),
                candidate.isEligible(),
                candidate.getRejectionReason());
    }
}
