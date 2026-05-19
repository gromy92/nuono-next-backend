package com.nuono.next.mobile;

import java.time.Instant;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalMobileSessionStore {

    private static final SecureRandom SMS_RANDOM = new SecureRandom();

    private final Map<String, SessionToken> accessTokenStore = new ConcurrentHashMap<>();
    private final Map<String, SessionToken> refreshTokenStore = new ConcurrentHashMap<>();
    private final Map<String, ExpiringStringValue> bindTokenStore = new ConcurrentHashMap<>();
    private final Map<String, ExpiringStringValue> smsCodeStore = new ConcurrentHashMap<>();
    private final Map<String, Long> smsCooldownStore = new ConcurrentHashMap<>();
    private final Map<String, Long> wechatBindingStore = new ConcurrentHashMap<>();

    @Value("${mobile.auth.access-token-expire:7200}")
    private long accessTokenExpireSeconds;

    @Value("${mobile.auth.refresh-token-expire:2592000}")
    private long refreshTokenExpireSeconds;

    @Value("${mobile.auth.bind-token-expire:600}")
    private long bindTokenExpireSeconds;

    @Value("${mobile.auth.sms-code-expire:300}")
    private long smsCodeExpireSeconds;

    @Value("${mobile.auth.fixed-sms-code:}")
    private String fixedSmsCode;

    public AuthTokens issueAuthTokens(Long userId) {
        purgeExpiredEntries();
        String accessToken = randomToken();
        String refreshToken = randomToken();
        accessTokenStore.put(accessToken, new SessionToken(userId, expireAt(accessTokenExpireSeconds)));
        refreshTokenStore.put(refreshToken, new SessionToken(userId, expireAt(refreshTokenExpireSeconds)));
        return new AuthTokens(accessToken, refreshToken, (int) accessTokenExpireSeconds);
    }

    public Long findUserIdByAccessToken(String accessToken) {
        purgeExpiredEntries();
        if (!StringUtils.hasText(accessToken)) {
            return null;
        }
        SessionToken token = accessTokenStore.get(accessToken.trim());
        if (token == null || token.isExpired()) {
            accessTokenStore.remove(accessToken);
            return null;
        }
        return token.getUserId();
    }

    public Long consumeRefreshToken(String refreshToken) {
        purgeExpiredEntries();
        if (!StringUtils.hasText(refreshToken)) {
            return null;
        }
        SessionToken token = refreshTokenStore.remove(refreshToken.trim());
        if (token == null || token.isExpired()) {
            return null;
        }
        return token.getUserId();
    }

    public String createBindToken(String wechatIdentity) {
        purgeExpiredEntries();
        String bindToken = randomToken();
        bindTokenStore.put(bindToken, new ExpiringStringValue(wechatIdentity, expireAt(bindTokenExpireSeconds)));
        return bindToken;
    }

    public String consumeWechatIdentity(String bindToken) {
        purgeExpiredEntries();
        if (!StringUtils.hasText(bindToken)) {
            return null;
        }
        ExpiringStringValue value = bindTokenStore.remove(bindToken.trim());
        if (value == null || value.isExpired()) {
            return null;
        }
        return value.getValue();
    }

    public Long findBoundUserId(String wechatIdentity) {
        if (!StringUtils.hasText(wechatIdentity)) {
            return null;
        }
        return wechatBindingStore.get(wechatIdentity.trim());
    }

    public void bindWechatIdentity(String wechatIdentity, Long userId) {
        if (StringUtils.hasText(wechatIdentity) && userId != null) {
            wechatBindingStore.put(wechatIdentity.trim(), userId);
        }
    }

    public String issueSmsCode(String phone) {
        return issueSmsCode(phone, 0L);
    }

    public String issueSmsCode(String phone, long cooldownSeconds) {
        purgeExpiredEntries();
        String normalizedPhone = normalize(phone);
        String smsCode = StringUtils.hasText(fixedSmsCode)
                ? fixedSmsCode.trim()
                : String.format("%06d", SMS_RANDOM.nextInt(1_000_000));
        smsCodeStore.put(normalizedPhone, new ExpiringStringValue(smsCode, expireAt(smsCodeExpireSeconds)));
        if (cooldownSeconds > 0) {
            smsCooldownStore.put(normalizedPhone, expireAt(cooldownSeconds));
        }
        return smsCode;
    }

    public long secondsUntilSmsAllowed(String phone) {
        purgeExpiredEntries();
        Long cooldownUntil = smsCooldownStore.get(normalize(phone));
        if (cooldownUntil == null) {
            return 0L;
        }
        long remainingMillis = cooldownUntil - Instant.now().toEpochMilli();
        if (remainingMillis <= 0) {
            smsCooldownStore.remove(normalize(phone));
            return 0L;
        }
        return Math.max(1L, (remainingMillis + 999L) / 1000L);
    }

    public boolean verifySmsCode(String phone, String captcha) {
        purgeExpiredEntries();
        ExpiringStringValue value = smsCodeStore.get(normalize(phone));
        if (value == null || value.isExpired()) {
            smsCodeStore.remove(normalize(phone));
            return false;
        }
        boolean matched = value.getValue().equals(normalize(captcha));
        if (matched) {
            smsCodeStore.remove(normalize(phone));
        }
        return matched;
    }

    private void purgeExpiredEntries() {
        accessTokenStore.entrySet().removeIf(entry -> entry.getValue().isExpired());
        refreshTokenStore.entrySet().removeIf(entry -> entry.getValue().isExpired());
        bindTokenStore.entrySet().removeIf(entry -> entry.getValue().isExpired());
        smsCodeStore.entrySet().removeIf(entry -> entry.getValue().isExpired());
        long now = Instant.now().toEpochMilli();
        smsCooldownStore.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= now);
    }

    private long expireAt(long expireSeconds) {
        return Instant.now().plusSeconds(Math.max(expireSeconds, 1)).toEpochMilli();
    }

    private String randomToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String normalize(String value) {
        return StringUtils.trimWhitespace(value);
    }

    public static class AuthTokens {
        private final String accessToken;
        private final String refreshToken;
        private final int expiresIn;

        public AuthTokens(String accessToken, String refreshToken, int expiresIn) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public int getExpiresIn() {
            return expiresIn;
        }
    }

    private static class SessionToken {
        private final Long userId;
        private final long expireAt;

        private SessionToken(Long userId, long expireAt) {
            this.userId = userId;
            this.expireAt = expireAt;
        }

        private Long getUserId() {
            return userId;
        }

        private boolean isExpired() {
            return Instant.now().toEpochMilli() >= expireAt;
        }
    }

    private static class ExpiringStringValue {
        private final String value;
        private final long expireAt;

        private ExpiringStringValue(String value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }

        private String getValue() {
            return value;
        }

        private boolean isExpired() {
            return Instant.now().toEpochMilli() >= expireAt;
        }
    }
}
