package com.aies.service;

import com.aies.config.AiProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyCipherTest {

    private final ApiKeyCipher cipher = new ApiKeyCipher(new AiProperties());

    @Test
    void encryptAndDecryptRoundTrip() {
        String encrypted = cipher.encrypt("sk-test-123456");

        assertThat(encrypted).startsWith("v1:").doesNotContain("sk-test-123456");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("sk-test-123456");
        assertThat(cipher.hint("sk-test-123456")).isEqualTo("3456");
    }

    @Test
    void wrongSecretCannotDecrypt() {
        String encrypted = cipher.encrypt("sk-test-123456");
        AiProperties other = new AiProperties();
        other.setConfigSecret("another-secret");
        ApiKeyCipher otherCipher = new ApiKeyCipher(other);

        assertThatThrownBy(() -> otherCipher.decrypt(encrypted))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("解密失败");
    }
}
