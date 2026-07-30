package com.nuono.next.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserPasswordServiceTest {

    private UserPasswordService passwordService;

    @BeforeEach
    void setUp() {
        passwordService = new UserPasswordService(4, new SecureRandom());
    }

    @Test
    void shouldEncodeNewPasswordsWithRandomBcryptCredentials() {
        String first = passwordService.encode("Next123!");
        String second = passwordService.encode("Next123!");

        assertThat(first).startsWith("$2").hasSize(60).isNotEqualTo(second);
        assertThat(first).doesNotContain("Next123!");
        assertTrue(passwordService.matches("Next123!", first));
        assertFalse(passwordService.matches("Wrong123!", first));
        assertFalse(passwordService.needsUpgrade(first));
    }

    @Test
    void shouldReadLegacyPlainAndSaltedCredentialsUntilTheyAreUpgraded() {
        String legacyDigest = LegacyPasswordCodec.encryptWithSalt(
                "Legacy123!",
                LegacyPasswordCodec.LEGACY_SALT
        );

        assertTrue(passwordService.matches("Legacy123!", "Legacy123!"));
        assertTrue(passwordService.matches("Legacy123!", legacyDigest));
        assertTrue(passwordService.needsUpgrade("Legacy123!"));
        assertTrue(passwordService.needsUpgrade(legacyDigest));
    }

    @Test
    void shouldUpgradeBcryptCredentialsCreatedWithAWeakerCost() {
        UserPasswordService strongerPasswordService =
                new UserPasswordService(5, new SecureRandom());
        String weakerCredential = passwordService.encode("Next123!");

        assertTrue(strongerPasswordService.matches("Next123!", weakerCredential));
        assertTrue(strongerPasswordService.needsUpgrade(weakerCredential));
    }

    @Test
    void shouldFailClosedForMalformedBcryptAndNeverAcceptTheHashAsTheRawPassword() {
        String encoded = passwordService.encode("Next123!");
        String unsupportedCost = "$2a$99$" + "A".repeat(53);

        assertFalse(passwordService.matches(encoded, encoded));
        assertFalse(passwordService.matches("Next123!", "$2a$12$malformed"));
        assertFalse(passwordService.matches("Next123!", unsupportedCost));
    }

    @Test
    void shouldGenerateHighEntropyTemporaryPasswordWithinTheExistingUiContract() {
        String temporaryPassword = passwordService.generateTemporaryPassword();

        assertThat(temporaryPassword).hasSize(14).matches("[!-~]+");
        assertThat(passwordService.encode(temporaryPassword)).doesNotContain(temporaryPassword);
    }

    @Test
    void shouldRejectMissingPasswordInsteadOfCreatingAnUnusableCredential() {
        assertThrows(IllegalArgumentException.class, () -> passwordService.encode(null));
        assertThrows(IllegalArgumentException.class, () -> passwordService.encode(""));
    }
}
