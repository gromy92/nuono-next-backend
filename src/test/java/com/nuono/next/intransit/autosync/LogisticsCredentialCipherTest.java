package com.nuono.next.intransit.autosync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LogisticsCredentialCipherTest {

    @Test
    void encryptsAndDecryptsPasswordWithoutPersistingPlaintext() {
        LogisticsCredentialCipher cipher = new LogisticsCredentialCipher(properties("test-logistics-cipher-secret"));

        String encrypted = cipher.encrypt("forwarder-password");

        assertThat(encrypted).startsWith("v1:");
        assertThat(encrypted).doesNotContain("forwarder-password");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("forwarder-password");
    }

    @Test
    void usesRandomIvForEachEncryption() {
        LogisticsCredentialCipher cipher = new LogisticsCredentialCipher(properties("test-logistics-cipher-secret"));

        String first = cipher.encrypt("same-password");
        String second = cipher.encrypt("same-password");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("same-password");
        assertThat(cipher.decrypt(second)).isEqualTo("same-password");
    }

    @Test
    void returnsBlankValuesUnchanged() {
        LogisticsCredentialCipher cipher = new LogisticsCredentialCipher(properties("test-logistics-cipher-secret"));

        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.encrypt("")).isEmpty();
        assertThat(cipher.encrypt("   ")).isEqualTo("   ");
        assertThat(cipher.decrypt(null)).isNull();
        assertThat(cipher.decrypt("")).isEmpty();
        assertThat(cipher.decrypt("   ")).isEqualTo("   ");
    }

    @Test
    void refusesToEncryptRealPasswordWithoutCipherSecret() {
        LogisticsCredentialCipher cipher = new LogisticsCredentialCipher(new LogisticsAutoSyncProperties());

        assertThatThrownBy(() -> cipher.encrypt("forwarder-password"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("logistics credential cipher secret");
    }

    private static LogisticsAutoSyncProperties properties(String secret) {
        LogisticsAutoSyncProperties properties = new LogisticsAutoSyncProperties();
        properties.setCredentialCipherSecret(secret);
        return properties;
    }
}
