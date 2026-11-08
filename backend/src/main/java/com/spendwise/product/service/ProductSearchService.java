package com.spendwise.product.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendwise.common.exception.ApiException;
import com.spendwise.common.security.Channel3UserRateLimiter;
import com.spendwise.common.security.GeminiUserRateLimiter;
import com.spendwise.integration.gemini.GeminiService;
import com.spendwise.integration.gemini.dto.GeminiRecommendationCandidate;
import com.spendwise.integration.gemini.dto.GeminiShoppingIntent;
import com.spendwise.policy.entity.SpendingPolicy;
import com.spendwise.policy.engine.PolicyEngine;
import com.spendwise.policy.service.SpendingPolicyService;
import com.spendwise.product.dto.ShoppingSearchRequest;
import com.spendwise.product.dto.ShoppingSearchFilters;
import com.spendwise.product.dto.ShoppingSearchResponse;
import com.spendwise.product.dto.ShoppingSortBy;
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
import java.util.LinkedHashMap;
import java.util.Map;
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
    private final Channel3UserRateLimiter rateLimiter;
    private final GeminiUserRateLimiter geminiRateLimiter;
    private final GeminiService geminiService;
    private final ShoppingIntentService intentService;
    private final PolicyEngine policyEngine;
    private final SpendingPolicyService policyService;
    private final ShoppingSearchSessionRepository sessionRepository;
    private final ProductCandidateRepository candidateRepository;
    private final TransactionTemplate transactions;

    public ProductSearchService(
            Channel3Client channel3Client,
            Channel3UserRateLimiter rateLimiter,
            GeminiUserRateLimiter geminiRateLimiter,
            GeminiService geminiService,
            ShoppingIntentService intentService,
            PolicyEngine policyEngine,
            SpendingPolicyService policyService,
            ShoppingSearchSessionRepository sessionRepository,
            ProductCandidateRepository candidateRepository,
            PlatformTransactionManager transactionManager) {
        this.channel3Client = channel3Client;
        this.rateLimiter = rateLimiter;
        this.geminiRateLimiter = geminiRateLimiter;
        this.geminiService = geminiService;
        this.intentService = intentService;
        this.policyEngine = policyEngine;
        this.policyService = policyService;
        this.sessionRepository = sessionRepository;
        this.candidateRepository = candidateRepository;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public ShoppingSearchResponse search(String ownerId, ShoppingSearchRequest request) {
        geminiRateLimiter.acquire(ownerId);
        SpendingPolicy policy = policyService.getActive(ownerId);
        ShoppingIntentService.ParsedIntent literalIntent = intentService.parse(request.message());
        GeminiShoppingIntent aiIntent = geminiService.extractShoppingIntent(request.message());
        validateBudgetCurrency(literalIntent.requestedCurrency(), policy.getCurrencyCode());
        validateBudgetCurrency(aiIntent.budgetCurrency(), policy.getCurrencyCode());
        ShoppingSearchFilters filters = request.filters();
        BigDecimal requestedMaxPrice = stricterBudget(
                stricterBudget(literalIntent.requestedMaxPrice(), aiIntent.budget()), filters.maxPrice());
        BigDecimal effectiveMax = policyEngine.effectiveMaxProductPrice(policy, requestedMaxPrice);

        rateLimiter.acquire(ownerId);

        JsonNode response;
        try {
            if (hasAdditionalFilters(filters)) {
                response = channel3Client.searchWithFilters(
                        aiIntent.normalizedQuery(),
                        toChannel3Filters(filters, effectiveMax),
                        policy.getCountryCode(),
                        policy.getCurrencyCode());
            } else {
                response = channel3Client.search(
                        aiIntent.normalizedQuery(),
                        effectiveMax,
                        policy.getCountryCode(),
                        policy.getCurrencyCode());
            }
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

        UUID sessionId = UUID.randomUUID();
        ShoppingSearchSession session = new ShoppingSearchSession(
                sessionId, ownerId, request.message().strip(), aiIntent.normalizedQuery(), effectiveMax);
        List<ProductCandidate> candidates = mapCandidates(
                response.path("products"), session, policy, effectiveMax, filters.minPrice());
        List<ProductCandidate> eligibleCandidates = candidates.stream()
                .filter(ProductCandidate::isEligible)
                .toList();
        boolean aiRankingApplied = false;
        if (request.sortBy() == ShoppingSortBy.LOWEST_PRICE) {
            eligibleCandidates = eligibleCandidates.stream()
                    .sorted(Comparator.comparing(ProductCandidate::getListedPrice))
                    .toList();
        } else if (request.sortBy() == ShoppingSortBy.AI_RECOMMENDED && !eligibleCandidates.isEmpty()) {
            CandidateRanking ranking = rankEligibleCandidates(request.message(), eligibleCandidates);
            eligibleCandidates = ranking.candidates();
            aiRankingApplied = ranking.applied();
        }

        List<ProductCandidate> finalEligibleCandidates = eligibleCandidates;
        transactions.executeWithoutResult(status -> {
            sessionRepository.save(session);
            candidateRepository.saveAll(candidates);
        });

        List<ShoppingSearchResponse.ProductCandidateView> eligible = finalEligibleCandidates.stream()
                .map(this::toView)
                .toList();
        List<ShoppingSearchResponse.ProductCandidateView> rejected = candidates.stream()
                .filter(candidate -> !candidate.isEligible())
                .map(this::toView)
                .toList();
        String recommendation = eligible.isEmpty() ? null : switch (request.sortBy()) {
            case BEST_MATCH -> "Channel3's best-matching eligible results for your search.";
            case AI_RECOMMENDED -> aiRankingApplied
                    ? "Gemini ranked eligible products returned by Channel3. Product details are from Channel3."
                    : "Gemini ranking was unavailable; showing Channel3's best-matching eligible results.";
            case LOWEST_PRICE -> "Lowest-priced eligible offer: " + eligible.getFirst().productTitle()
                    + " from " + eligible.getFirst().merchantName() + ".";
        };

        return new ShoppingSearchResponse(
                sessionId,
                new ShoppingSearchResponse.ShoppingIntent(
                        aiIntent.normalizedQuery(),
                        literalIntent.keywords(),
                        requestedMaxPrice == null ? null : requestedMaxPrice.toPlainString(),
                        policy.getCurrencyCode(),
                        policy.getCountryCode()),
                eligible,
                rejected,
                recommendation,
                request.sortBy());
    }

    private CandidateRanking rankEligibleCandidates(String userRequest, List<ProductCandidate> candidates) {
        List<GeminiRecommendationCandidate> rankingInput = candidates.stream()
                .map(candidate -> new GeminiRecommendationCandidate(
                        candidate.getId(), candidate.getProductTitle(), candidate.getBrand()))
                .toList();
        try {
            List<UUID> rankedIds = geminiService.rankShoppingCandidates(userRequest, rankingInput);
            Map<UUID, ProductCandidate> candidatesById = candidates.stream()
                    .collect(java.util.stream.Collectors.toMap(ProductCandidate::getId, candidate -> candidate));
            List<ProductCandidate> rankedCandidates = new ArrayList<>();
            for (UUID candidateId : rankedIds) {
                ProductCandidate candidate = candidatesById.remove(candidateId);
                if (candidate != null) {
                    rankedCandidates.add(candidate);
                }
            }
            if (rankedCandidates.isEmpty()) {
                return new CandidateRanking(candidates, false);
            }
            rankedCandidates.addAll(candidates.stream()
                    .filter(candidate -> candidatesById.containsKey(candidate.getId()))
                    .toList());
            return new CandidateRanking(List.copyOf(rankedCandidates), true);
        } catch (ApiException exception) {
            return new CandidateRanking(candidates, false);
        }
    }

    private record CandidateRanking(List<ProductCandidate> candidates, boolean applied) {
    }

    private boolean hasAdditionalFilters(ShoppingSearchFilters filters) {
        return filters.minPrice() != null
                || filters.maxPrice() != null
                || !filters.categoryIds().isEmpty()
                || !filters.websiteIds().isEmpty()
                || filters.availability() != null
                || Boolean.TRUE.equals(filters.onlyOnSale());
    }

    private Map<String, Object> toChannel3Filters(ShoppingSearchFilters filters, BigDecimal effectiveMax) {
        Map<String, Object> channel3Filters = new LinkedHashMap<>();
        Map<String, Object> price = new LinkedHashMap<>();
        if (filters.minPrice() != null) {
            price.put("min_price", filters.minPrice());
        }
        if (effectiveMax != null) {
            price.put("max_price", effectiveMax);
        }
        if (!price.isEmpty()) {
            channel3Filters.put("price", price);
        }
        if (!filters.categoryIds().isEmpty()) {
            channel3Filters.put("category_ids", filters.categoryIds());
        }
        if (!filters.websiteIds().isEmpty()) {
            channel3Filters.put("website_ids", filters.websiteIds());
        }
        if (filters.availability() != null) {
            channel3Filters.put("availability", List.of(
                    filters.availability() == ShoppingSearchFilters.Availability.IN_STOCK
                            ? "InStock"
                            : "OutOfStock"));
        }
        if (Boolean.TRUE.equals(filters.onlyOnSale())) {
            channel3Filters.put("sale", "on_sale");
        }
        return channel3Filters;
    }

    private BigDecimal stricterBudget(BigDecimal parsedBudget, BigDecimal aiBudget) {
        if (parsedBudget == null) {
            return aiBudget;
        }
        if (aiBudget == null) {
            return parsedBudget;
        }
        return parsedBudget.min(aiBudget);
    }

    private void validateBudgetCurrency(String requestedCurrency, String policyCurrency) {
        if (requestedCurrency != null && !requestedCurrency.equals(policyCurrency)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SHOPPING_CURRENCY_MISMATCH",
                    "The requested budget currency does not match the active spending policy currency.");
        }
    }

    private List<ProductCandidate> mapCandidates(
            JsonNode products,
            ShoppingSearchSession session,
            SpendingPolicy policy,
            BigDecimal effectiveMax,
            BigDecimal minPrice) {
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
                if (price != null && minPrice != null && price.compareTo(minPrice) < 0) {
                    continue;
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
