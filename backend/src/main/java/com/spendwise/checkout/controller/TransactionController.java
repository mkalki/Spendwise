package com.spendwise.checkout.controller;

import com.spendwise.checkout.dto.BlockedPurchaseHistoryResponse;
import com.spendwise.checkout.dto.TransactionHistoryResponse;
import com.spendwise.checkout.dto.TransactionSummaryResponse;
import com.spendwise.checkout.service.CheckoutService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final CheckoutService checkoutService;

    public TransactionController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @GetMapping
    public TransactionHistoryResponse history(@AuthenticationPrincipal(expression = "name") String ownerId) {
        return checkoutService.history(ownerId);
    }

    @GetMapping("/blocked")
    public BlockedPurchaseHistoryResponse blockedHistory(@AuthenticationPrincipal(expression = "name") String ownerId) {
        return checkoutService.blockedHistory(ownerId);
    }

    @GetMapping("/summary")
    public TransactionSummaryResponse summary(@AuthenticationPrincipal(expression = "name") String ownerId) {
        return checkoutService.summary(ownerId);
    }
}
