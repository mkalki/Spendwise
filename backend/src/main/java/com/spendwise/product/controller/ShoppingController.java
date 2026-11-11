package com.spendwise.product.controller;

import com.spendwise.product.dto.ShoppingSearchRequest;
import com.spendwise.product.dto.ShoppingSearchResponse;
import com.spendwise.product.dto.ShoppingRecommendationsResponse;
import com.spendwise.product.service.ProductSearchService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shopping")
public class ShoppingController {

    private final ProductSearchService searchService;

    public ShoppingController(ProductSearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/search")
    @Operation(
            summary = "Interpret a shopping request and search products",
            description = "Uses Gemini to extract a Channel3-ready query and optional budget, then searches Channel3. "
                    + "Backend policy limits remain authoritative; the request is rejected if its budget currency "
                    + "does not match the active policy.")
    public ShoppingSearchResponse search(
            @AuthenticationPrincipal(expression = "name") String ownerId,
            @Valid @RequestBody ShoppingSearchRequest request) {
        return searchService.search(ownerId, request);
    }

    @PostMapping("/search/{sessionId}/recommendations")
    @Operation(summary = "Generate shopping explanations and ranking",
            description = "Ranks and explains eligible candidates from an owned, completed shopping search.")
    public ShoppingRecommendationsResponse recommendations(
            @AuthenticationPrincipal(expression = "name") String ownerId,
            @PathVariable UUID sessionId) {
        return searchService.recommendations(ownerId, sessionId);
    }

}
