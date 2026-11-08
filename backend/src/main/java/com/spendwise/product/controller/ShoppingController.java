package com.spendwise.product.controller;

import com.spendwise.product.dto.ShoppingSearchRequest;
import com.spendwise.product.dto.ShoppingSearchResponse;
import com.spendwise.product.service.ProductSearchService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shopping")
public class ShoppingController {

    private final ProductSearchService searchService;

    public ShoppingController(ProductSearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/search")
    public ShoppingSearchResponse search(
            @AuthenticationPrincipal(expression = "name") String ownerId,
            @Valid @RequestBody ShoppingSearchRequest request) {
        return searchService.search(ownerId, request);
    }
}
