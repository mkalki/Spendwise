package com.spendwise.checkout.controller;

import com.spendwise.checkout.dto.CheckoutResponse;
import com.spendwise.checkout.dto.CreateCheckoutRequest;
import com.spendwise.checkout.service.CheckoutService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/checkout-sessions")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping
    public CheckoutResponse create(
            @AuthenticationPrincipal(expression = "name") String ownerId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody CreateCheckoutRequest request) {
        return checkoutService.create(
                ownerId,
                idempotencyKey,
                request.searchSessionId(),
                request.candidateId());
    }

    @PostMapping("/{id}/capture")
    public CheckoutResponse capture(
            @AuthenticationPrincipal(expression = "name") String ownerId,
            @PathVariable Long id) {
        return checkoutService.capture(ownerId, id);
    }

    @PostMapping("/{id}/cancel")
    public CheckoutResponse cancel(
            @AuthenticationPrincipal(expression = "name") String ownerId,
            @PathVariable Long id) {
        return checkoutService.cancel(ownerId, id);
    }
}
