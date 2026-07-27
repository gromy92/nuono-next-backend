package com.nuono.next.auth;

import com.nuono.next.infrastructure.mapper.AuthMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.function.LongFunction;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthSessionTokenService {

    public static final String COOKIE_NAME = "NUONO_NEXT_SESSION";
    public static final String SESSION_REQUEST_ATTRIBUTE = AuthSessionTokenService.class.getName() + ".SESSION";
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String LOCAL_DEV_USER_HEADER = "X-Nuono-Dev-Session-User-Id";
    private static final String LOCAL_DEV_ROLE_HEADER = "X-Nuono-Dev-Session-Role-Id";
    private static final String LOCAL_DEV_LEVEL_HEADER = "X-Nuono-Dev-Session-Level";
    private static final String TOKEN_FORMAT_V2 = "v2";
    private static final long LEGACY_CREDENTIAL_VERSION = 0L;
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final byte[] secret;
    private final long ttlSeconds;
    private final LongFunction<AuthSessionState> sessionStateReader;

    @Autowired
    public AuthSessionTokenService(
            @Value("${nuono.auth.session-secret:}") String configuredSecret,
            @Value("${nuono.auth.session-ttl-seconds:28800}") long ttlSeconds,
            ObjectProvider<AuthMapper> authMapperProvider
    ) {
        this(
                configuredSecret,
                ttlSeconds,
                userId -> {
                    AuthMapper authMapper = authMapperProvider.getIfAvailable();
                    return authMapper == null ? null : authMapper.selectSessionState(userId);
                }
        );
    }

    AuthSessionTokenService(
            String configuredSecret,
            long ttlSeconds,
            LongFunction<AuthSessionState> sessionStateReader
    ) {
        this.secret = resolveSecret(configuredSecret);
        this.ttlSeconds = Math.max(300L, ttlSeconds);
        this.sessionStateReader = sessionStateReader;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public String issue(AuthLoginResult session) {
        if (session == null || session.getUserId() == null) {
            throw new IllegalArgumentException("缺少登录账号信息，不能创建会话。");
        }
        Long credentialVersion = session.getCredentialVersion();
        if (credentialVersion == null || credentialVersion < 0L) {
            throw new IllegalStateException("缺少账号凭据版本，不能创建会话。");
        }
        long expiresAt = Instant.now().getEpochSecond() + ttlSeconds;
        String payload = TOKEN_FORMAT_V2
                + ":" + session.getUserId()
                + ":" + credentialVersion
                + ":" + expiresAt
                + ":" + UUID.randomUUID();
        return encode(payload.getBytes(StandardCharsets.UTF_8)) + "." + encode(sign(payload));
    }

    public AuthenticatedSession requireSession(HttpServletRequest request) {
        Object existingSession = request == null ? null : request.getAttribute(SESSION_REQUEST_ATTRIBUTE);
        if (existingSession instanceof AuthenticatedSession) {
            return (AuthenticatedSession) existingSession;
        }

        String localDevUserId = request != null ? request.getHeader(LOCAL_DEV_USER_HEADER) : null;
        if (StringUtils.hasText(localDevUserId) && isLocalDevRequest(request)) {
            try {
                AuthenticatedSession session = new AuthenticatedSession(
                        Long.valueOf(localDevUserId.trim()),
                        parseNullableLong(request.getHeader(LOCAL_DEV_ROLE_HEADER)),
                        parseNullableInteger(request.getHeader(LOCAL_DEV_LEVEL_HEADER))
                );
                request.setAttribute(SESSION_REQUEST_ATTRIBUTE, session);
                return session;
            } catch (NumberFormatException error) {
                throw unauthorized();
            }
        }

        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            throw unauthorized();
        }
        AuthenticatedSession session = verify(token);
        if (session == null) {
            throw unauthorized();
        }
        request.setAttribute(SESSION_REQUEST_ATTRIBUTE, session);
        return session;
    }

    private AuthenticatedSession verify(String token) {
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2 || !StringUtils.hasText(parts[0]) || !StringUtils.hasText(parts[1])) {
            return null;
        }
        byte[] payloadBytes;
        byte[] signature;
        try {
            payloadBytes = URL_DECODER.decode(parts[0]);
            signature = URL_DECODER.decode(parts[1]);
        } catch (IllegalArgumentException error) {
            return null;
        }
        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(sign(payload), signature)) {
            return null;
        }
        try {
            String[] fields = payload.split(":", -1);
            boolean versionTwoToken = fields.length == 5 && TOKEN_FORMAT_V2.equals(fields[0]);
            boolean legacyToken = fields.length == 5 && !versionTwoToken;
            if (!legacyToken && !versionTwoToken) {
                return null;
            }
            Long userId = Long.valueOf(fields[versionTwoToken ? 1 : 0]);
            long credentialVersion = legacyToken
                    ? LEGACY_CREDENTIAL_VERSION
                    : Long.parseLong(fields[2]);
            long expiresAt = Long.parseLong(fields[3]);
            if (credentialVersion < 0L) {
                return null;
            }
            if (expiresAt < Instant.now().getEpochSecond()) {
                return null;
            }
            AuthSessionState sessionState;
            try {
                sessionState = sessionStateReader.apply(userId);
            } catch (RuntimeException error) {
                throw sessionStateUnavailable(error);
            }
            if (sessionState == null
                    || sessionState.getCredentialVersion() == null
                    || sessionState.getCredentialVersion() != credentialVersion) {
                return null;
            }
            return new AuthenticatedSession(
                    userId,
                    sessionState.getRoleId(),
                    sessionState.getLevel(),
                    credentialVersion
            );
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private String extractToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String authorization = request.getHeader(AUTH_HEADER);
        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new IllegalStateException("无法创建登录会话签名。", error);
        }
    }

    private static byte[] resolveSecret(String configuredSecret) {
        if (StringUtils.hasText(configuredSecret)) {
            return configuredSecret.getBytes(StandardCharsets.UTF_8);
        }
        byte[] generated = new byte[32];
        new SecureRandom().nextBytes(generated);
        return generated;
    }

    private static String encode(byte[] value) {
        return URL_ENCODER.encodeToString(value);
    }

    private static Long parseNullableLong(String value) {
        return StringUtils.hasText(value) ? Long.valueOf(value) : null;
    }

    private static Integer parseNullableInteger(String value) {
        return StringUtils.hasText(value) ? Integer.valueOf(value) : null;
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录后再继续操作。");
    }

    private static ResponseStatusException sessionStateUnavailable(RuntimeException error) {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "暂时无法校验登录状态，请稍后重试。",
                error
        );
    }

    private static boolean isLocalDevRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String host = request.getHeader("Host");
        if (!StringUtils.hasText(host)) {
            return false;
        }
        String normalizedHost = host.toLowerCase();
        boolean localHost = normalizedHost.startsWith("localhost")
                || normalizedHost.startsWith("127.0.0.1")
                || normalizedHost.startsWith("[::1]");
        if (!localHost) {
            return false;
        }
        String remoteAddr = request.getRemoteAddr();
        return "127.0.0.1".equals(remoteAddr) || "0:0:0:0:0:0:0:1".equals(remoteAddr) || "::1".equals(remoteAddr);
    }
}
