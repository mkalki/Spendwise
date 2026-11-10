package com.spendwise.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spendwise.common.exception.ApiException;
import org.junit.jupiter.api.Test;

class VaultTokenCipherTest {
    private static final String TEST_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Test
    void encryptsVaultTokensAndCanDecryptThemWithTheConfiguredKey() {
        VaultTokenCipher cipher = new VaultTokenCipher(TEST_KEY);

        String encrypted = cipher.encrypt("vault-token-123");

        assertThat(encrypted).startsWith("v1:").doesNotContain("vault-token-123");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("vault-token-123");
    }

    @Test
    void rejectsVaultOperationsWhenTheEncryptionKeyIsNotConfigured() {
        VaultTokenCipher cipher = new VaultTokenCipher("");

        assertThatThrownBy(cipher::validateConfiguration)
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("PAYMENT_METHOD_STORAGE_NOT_CONFIGURED");
    }

    @Test
    void refusesToDecryptDataThatWasChangedOrEncryptedWithAnotherKey() {
        VaultTokenCipher writer = new VaultTokenCipher(TEST_KEY);
        VaultTokenCipher reader = new VaultTokenCipher("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=");

        assertThatThrownBy(() -> reader.decrypt(writer.encrypt("vault-token-123")))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("PAYMENT_METHOD_STORAGE_NOT_CONFIGURED");
    }
}
