package com.spendwise.payment.service;

import com.spendwise.common.exception.ApiException;
import com.spendwise.payment.dto.PayPalVaultSetupResponse;
import com.spendwise.payment.dto.SavedPayPalMethodResponse;
import com.spendwise.payment.entity.PayPalPaymentMethod;
import com.spendwise.payment.integration.PayPalClient;
import com.spendwise.payment.integration.PaymentProviderNotConfiguredException;
import com.spendwise.payment.integration.PaymentProviderRejectedException;
import com.spendwise.payment.integration.PaymentProviderTimeoutException;
import com.spendwise.payment.integration.PaymentProviderException;
import com.spendwise.payment.repository.PayPalPaymentMethodRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PayPalVaultService {
    private final PayPalClient paypalClient;
    private final PayPalPaymentMethodRepository methods;
    private final VaultTokenCipher cipher;

    public PayPalVaultService(PayPalClient paypalClient, PayPalPaymentMethodRepository methods, VaultTokenCipher cipher) {
        this.paypalClient = paypalClient;
        this.methods = methods;
        this.cipher = cipher;
    }

    @Transactional
    public PayPalVaultSetupResponse beginSetup(String ownerId) {
        cipher.validateConfiguration();
        UUID setupId = UUID.randomUUID();
        try {
            PayPalClient.VaultSetupCreation setup = paypalClient.createVaultSetup(merchantCustomerId(ownerId), setupId);
            methods.save(new PayPalPaymentMethod(setupId, ownerId, setup.setupTokenId(), setup.customerId()));
            return new PayPalVaultSetupResponse(setupId, setup.approvalUrl(), "PAYER_ACTION_REQUIRED");
        } catch (PaymentProviderNotConfiguredException exception) {
            throw providerUnavailable();
        } catch (PaymentProviderTimeoutException exception) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "PAYPAL_VAULT_SETUP_UNCONFIRMED",
                    "PayPal did not confirm the saved-payment setup. Try again shortly.");
        } catch (PaymentProviderRejectedException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYPAL_VAULT_SETUP_REJECTED",
                    "PayPal rejected saved-payment setup. Check vault eligibility in the PayPal account.");
        } catch (PaymentProviderException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYPAL_VAULT_SETUP_UNCONFIRMED",
                    "PayPal could not confirm saved-payment setup.");
        }
    }

    @Transactional
    public SavedPayPalMethodResponse completeSetup(String ownerId, UUID setupId) {
        PayPalPaymentMethod method = methods.findOwnedForUpdate(ownerId, setupId)
                .orElseThrow(() -> notFound());
        if ("SAVED".equals(method.getStatus())) return response(method);
        if (!"SETUP_PENDING".equals(method.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYPAL_VAULT_SETUP_NOT_PENDING",
                    "This saved-payment setup is no longer pending.");
        }
        try {
            PayPalClient.VaultTokenCreation token = paypalClient.createVaultPaymentToken(
                    method.getSetupTokenId(), method.getId());
            method.markSaved(cipher.encrypt(token.vaultId()), token.customerId(), token.email());
            return response(methods.save(method));
        } catch (PaymentProviderNotConfiguredException exception) {
            throw providerUnavailable();
        } catch (PaymentProviderTimeoutException exception) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "PAYPAL_VAULT_TOKEN_UNCONFIRMED",
                    "PayPal did not confirm that the payment method was saved. Retry to reconcile it.");
        } catch (PaymentProviderRejectedException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYPAL_VAULT_TOKEN_REJECTED",
                    "PayPal could not save this payment method. Confirm the PayPal approval first.");
        } catch (PaymentProviderException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYPAL_VAULT_TOKEN_UNCONFIRMED",
                    "PayPal could not confirm that the payment method was saved.");
        }
    }

    @Transactional(readOnly = true)
    public List<SavedPayPalMethodResponse> list(String ownerId) {
        return methods.findAllByOwnerIdAndStatusOrderByCreatedAtDesc(ownerId, "SAVED")
                .stream().map(PayPalVaultService::response).toList();
    }

    @Transactional
    public void remove(String ownerId, UUID methodId) {
        PayPalPaymentMethod method = methods.findOwnedForUpdate(ownerId, methodId)
                .orElseThrow(() -> notFound());
        if ("REMOVED".equals(method.getStatus())) return;
        if (!"SAVED".equals(method.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYPAL_VAULT_METHOD_NOT_SAVED",
                    "Only a saved PayPal payment method can be removed.");
        }
        try {
            paypalClient.deleteVaultPaymentToken(cipher.decrypt(method.getVaultTokenEncrypted()));
            method.markRemoved();
            methods.save(method);
        } catch (PaymentProviderNotConfiguredException exception) {
            throw providerUnavailable();
        } catch (PaymentProviderTimeoutException exception) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "PAYPAL_VAULT_DELETE_UNCONFIRMED",
                    "PayPal did not confirm removal of the saved payment method.");
        } catch (PaymentProviderRejectedException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYPAL_VAULT_DELETE_REJECTED",
                    "PayPal could not remove the saved payment method.");
        } catch (PaymentProviderException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYPAL_VAULT_DELETE_UNCONFIRMED",
                    "PayPal could not confirm removal of the saved payment method.");
        }
    }

    @Transactional(readOnly = true)
    public String getOwnedVaultToken(String ownerId, UUID methodId) {
        PayPalPaymentMethod method = methods.findByOwnerIdAndIdAndStatus(ownerId, methodId, "SAVED")
                .orElseThrow(() -> notFound());
        return cipher.decrypt(method.getVaultTokenEncrypted());
    }

    private static SavedPayPalMethodResponse response(PayPalPaymentMethod method) {
        return new SavedPayPalMethodResponse(method.getId(), "PAYPAL", method.getPaypalEmail(),
                method.getStatus(), method.getCreatedAt());
    }

    private String merchantCustomerId(String ownerId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(ownerId.getBytes(StandardCharsets.UTF_8));
            return "spw" + HexFormat.of().formatHex(digest, 0, 9);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create PayPal customer reference.", exception);
        }
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PAYPAL_PAYMENT_METHOD_NOT_FOUND",
                "The PayPal payment method was not found.");
    }

    private ApiException providerUnavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_NOT_CONFIGURED",
                "Saved PayPal payments are unavailable because PayPal is not configured.");
    }
}
