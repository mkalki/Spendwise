package com.spendwise.product.controller;

import com.spendwise.product.dto.ShoppingPreferencesRequest;
import com.spendwise.product.dto.ShoppingPreferencesResponse;
import com.spendwise.product.dto.ShoppingSearchHistoryResponse;
import com.spendwise.product.service.ShoppingProfileService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/shopping")
public class ShoppingProfileController {
    private final ShoppingProfileService profileService;

    public ShoppingProfileController(ShoppingProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/preferences")
    @Operation(summary = "Get saved shopping preferences")
    public ShoppingPreferencesResponse getPreferences(
            @AuthenticationPrincipal(expression = "name") String ownerId) {
        return profileService.getPreferences(ownerId);
    }

    @PutMapping("/preferences")
    @Operation(summary = "Save shopping preferences")
    public ShoppingPreferencesResponse savePreferences(
            @AuthenticationPrincipal(expression = "name") String ownerId,
            @Valid @RequestBody ShoppingPreferencesRequest request) {
        return profileService.savePreferences(ownerId, request);
    }

    @GetMapping("/search-history")
    @Operation(summary = "List the signed-in user's past shopping searches")
    public ShoppingSearchHistoryResponse history(
            @AuthenticationPrincipal(expression = "name") String ownerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return profileService.history(ownerId, page, size);
    }
}
