package com.spendwise.payment.controller;

import com.spendwise.checkout.dto.CheckoutResponse;
import com.spendwise.checkout.service.CheckoutService;
import com.spendwise.payment.dto.CapturePaymentResponse;
import com.spendwise.payment.dto.CreatePaymentRequest;
import com.spendwise.payment.dto.PayPalSdkConfigResponse;
import com.spendwise.payment.dto.PaymentResponse;
import com.spendwise.payment.entity.PaymentTransaction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final CheckoutService checkoutService;
    private final String paypalClientId;

    public PaymentController(
            CheckoutService checkoutService,
            @Value("${spendwise.paypal.client-id:}") String paypalClientId) {
        this.checkoutService = checkoutService;
        this.paypalClientId = paypalClientId;
    }

    @GetMapping("/config")
    public PayPalSdkConfigResponse config() {
        return new PayPalSdkConfigResponse(
                StringUtils.hasText(paypalClientId) ? paypalClientId : null,
                StringUtils.hasText(paypalClientId));
    }

    @PostMapping("/create")
    public PaymentResponse create(
            @AuthenticationPrincipal(expression = "name") String ownerId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        CheckoutResponse created = checkoutService.create(
                ownerId, idempotencyKey, request.searchSessionId(), request.candidateId());
        return PaymentResponse.from(checkoutService.getOwnedPaymentById(
                ownerId, Long.valueOf(created.checkoutSessionId())));
    }

    @PostMapping("/{orderId}/capture")
    public CapturePaymentResponse capture(
            @AuthenticationPrincipal(expression = "name") String ownerId,
            @PathVariable String orderId) {
        PaymentTransaction payment = checkoutService.getOwnedPayment(ownerId, orderId);
        checkoutService.capture(ownerId, payment.getId());
        return CapturePaymentResponse.from(checkoutService.getOwnedPayment(ownerId, orderId));
    }

    @GetMapping("/{orderId}")
    public PaymentResponse status(
            @AuthenticationPrincipal(expression = "name") String ownerId,
            @PathVariable String orderId) {
        return PaymentResponse.from(checkoutService.getPaymentByPaypalOrderId(ownerId, orderId));
    }
}
