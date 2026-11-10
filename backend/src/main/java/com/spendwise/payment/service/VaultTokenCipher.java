package com.spendwise.payment.service;

import com.spendwise.common.exception.ApiException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class VaultTokenCipher {
    private static final int IV_BYTES = 12;
    private final String encodedKey;
    private final SecureRandom random = new SecureRandom();

    public VaultTokenCipher(@Value("${spendwise.paypal.vault-encryption-key:}") String encodedKey) {
        this.encodedKey = encodedKey;
    }

    public String encrypt(String value) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv).put(encrypted).array());
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw storageUnavailable();
        }
    }

    public void validateConfiguration() {
        key();
    }

    public String decrypt(String value) {
        try {
            if (value == null || !value.startsWith("v1:")) throw storageUnavailable();
            byte[] packed = Base64.getDecoder().decode(value.substring(3));
            ByteBuffer buffer = ByteBuffer.wrap(packed);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw storageUnavailable();
        }
    }

    private SecretKeySpec key() {
        if (!StringUtils.hasText(encodedKey)) throw storageUnavailable();
        byte[] bytes = Base64.getDecoder().decode(encodedKey);
        if (bytes.length != 32) throw storageUnavailable();
        return new SecretKeySpec(bytes, "AES");
    }

    private ApiException storageUnavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_METHOD_STORAGE_NOT_CONFIGURED",
                "Saved PayPal payments are unavailable until PAYPAL_VAULT_ENCRYPTION_KEY is configured.");
    }
}
