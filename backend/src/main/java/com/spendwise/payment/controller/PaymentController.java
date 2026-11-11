package com.spendwise.payment.controller;

import com.spendwise.checkout.dto.CheckoutResponse;
import com.spendwise.checkout.service.CheckoutService;
import com.spendwise.payment.dto.CapturePaymentResponse;
import com.spendwise.payment.dto.CreatePaymentRequest;
import com.spendwise.payment.dto.PayPalSdkConfigResponse;
import com.spendwise.payment.dto.PayPalVaultSetupRequest;
import com.spendwise.payment.dto.PaymentResponse;
import com.spendwise.payment.dto.SavedPayPalCheckoutRequest;
import com.spendwise.payment.dto.SavedPayPalMethodResponse;
import com.spendwise.payment.entity.PaymentTransaction;
import com.spendwise.payment.service.PayPalVaultService;
import com.spendwise.payment.service.ProMembershipService;
import com.spendwise.payment.dto.ProMembershipResponse;
import com.spendwise.payment.dto.ProCheckoutRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
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
    private final PayPalVaultService paypalVaultService;
    private final String paypalClientId;
    private final ProMembershipService proMembershipService;

    public PaymentController(
            CheckoutService checkoutService,
            PayPalVaultService paypalVaultService,
            ProMembershipService proMembershipService,
            @Value("${spendwise.paypal.client-id:}") String paypalClientId) {
        this.checkoutService = checkoutService;
        this.paypalVaultService = paypalVaultService;
        this.proMembershipService = proMembershipService;
        this.paypalClientId = paypalClientId;
    }

    @GetMapping("/pro")
    public ProMembershipResponse proStatus(@AuthenticationPrincipal(expression = "name") String ownerId) {
        return proMembershipService.status(ownerId);
    }

    @PostMapping("/pro/checkout")
    public PaymentResponse createProCheckout(
            @AuthenticationPrincipal(expression = "name") String ownerId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody ProCheckoutRequest request) {
        return proMembershipService.create(ownerId, idempotencyKey, request.plan());
    }

    @GetMapping("/config")
    public PayPalSdkConfigResponse config() {
        return new PayPalSdkConfigResponse(
                StringUtils.hasText(paypalClientId) ? paypalClientId : null,
                StringUtils.hasText(paypalClientId));
    }

    @PostMapping("/methods/paypal/setup")
    public com.spendwise.payment.dto.PayPalVaultSetupResponse beginSavedPayPalSetup(
            @AuthenticationPrincipal(expression = "name") String ownerId,
            @Valid @RequestBody PayPalVaultSetupRequest request) {
        return paypalVaultService.beginSetup(ownerId);
    }

    @PostMapping("/methods/paypal/setup/{setupId}/complete")
    public SavedPayPalMethodResponse completeSavedPayPalSetup(
            @AuthenticationPrincipal(expression = "name") String ownerId,
            @PathVariable UUID setupId) {
        return paypalVaultService.completeSetup(ownerId, setupId);
    }

    @GetMapping("/methods")
    public List<SavedPayPalMethodResponse> savedPaymentMethods(
            @AuthenticationPrincipal(expression = "name") String ownerId) {
        return paypalVaultService.list(ownerId);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/methods/{methodId}")
    public void deleteSavedPaymentMethod(
            @AuthenticationPrincipal(expression = "name") String ownerId,
            @PathVariable UUID methodId) {
        paypalVaultService.remove(ownerId, methodId);
    }

    @PostMapping("/methods/{methodId}/checkout")
    public PaymentResponse checkoutWithSavedPayPal(
            @AuthenticationPrincipal(expression = "name") String ownerId,
            @PathVariable UUID methodId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody SavedPayPalCheckoutRequest request) {
        String vaultId = paypalVaultService.getOwnedVaultToken(ownerId, methodId);
        var checkout = checkoutService.createWithSavedPayPal(ownerId, idempotencyKey,
                request.searchSessionId(), request.candidateId(), vaultId, request.clientMetadataId());
        PaymentTransaction payment = checkoutService.getOwnedPaymentById(ownerId,
                Long.valueOf(checkout.checkoutSessionId()));
        return PaymentResponse.from(payment);
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
