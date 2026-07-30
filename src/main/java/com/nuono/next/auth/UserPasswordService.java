package com.nuono.next.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Owns the complete storage boundary for Nuono user login passwords.
 *
 * <p>New values are always encoded with BCrypt. Legacy plaintext and fixed-salt
 * MD5 values remain readable only so a successful user-password login can
 * migrate them in place.</p>
 */
@Component
public class UserPasswordService {

    static final int BCRYPT_STRENGTH = 12;
    static final int TEMPORARY_PASSWORD_LENGTH = 14;

    private static final int BCRYPT_MAX_RAW_BYTES = 72;
    private static final int MAX_SUPPORTED_BCRYPT_STRENGTH = 16;
    private static final Pattern BCRYPT_CREDENTIAL = Pattern.compile(
            "^\\$2[aby]\\$(\\d{2})\\$[./A-Za-z0-9]{53}$"
    );
    private static final String BCRYPT_PREFIX = "$2";
    private static final char[] TEMPORARY_PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%*+-_".toCharArray();

    private final BCryptPasswordEncoder bcrypt;
    private final SecureRandom secureRandom;

    public UserPasswordService() {
        this(BCRYPT_STRENGTH, new SecureRandom());
    }

    UserPasswordService(int strength, SecureRandom secureRandom) {
        if (strength < 4 || strength > MAX_SUPPORTED_BCRYPT_STRENGTH) {
            throw new IllegalArgumentException("BCrypt strength must be between 4 and 16.");
        }
        this.bcrypt = new BCryptPasswordEncoder(strength);
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
    }

    public String encode(String rawPassword) {
        requireEncodable(rawPassword);
        return bcrypt.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String storedCredential) {
        if (rawPassword == null || storedCredential == null) {
            return false;
        }
        if (isBcryptCredential(storedCredential)) {
            try {
                return bcrypt.matches(rawPassword, storedCredential);
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
        if (storedCredential.startsWith(BCRYPT_PREFIX)) {
            return false;
        }
        return LegacyPasswordCodec.matchesStoredPassword(rawPassword, storedCredential);
    }

    public boolean needsUpgrade(String storedCredential) {
        if (!isBcryptCredential(storedCredential)) {
            return true;
        }
        return bcrypt.upgradeEncoding(storedCredential);
    }

    public String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder(TEMPORARY_PASSWORD_LENGTH);
        for (int index = 0; index < TEMPORARY_PASSWORD_LENGTH; index += 1) {
            password.append(TEMPORARY_PASSWORD_ALPHABET[
                    secureRandom.nextInt(TEMPORARY_PASSWORD_ALPHABET.length)
            ]);
        }
        return password.toString();
    }

    private boolean isBcryptCredential(String value) {
        if (value == null) {
            return false;
        }
        Matcher matcher = BCRYPT_CREDENTIAL.matcher(value);
        if (!matcher.matches()) {
            return false;
        }
        int strength = Integer.parseInt(matcher.group(1));
        return strength >= 4 && strength <= MAX_SUPPORTED_BCRYPT_STRENGTH;
    }

    private void requireEncodable(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空。");
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_RAW_BYTES) {
            throw new IllegalArgumentException("密码过长，暂时不能保存。");
        }
    }
}
