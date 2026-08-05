package com.nuono.next.procurement.aliorder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.Ali1688OpenApiAuthorizationMapper;
import com.nuono.next.procurement.aliorder.Ali1688OAuthTokenParser.TokenPayload;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/** Owns the single-call token refresh boundary and stable account-identity check. */
final class Ali1688OpenApiTokenRefresher {
    private final Ali1688HistoricalOrderOpenApiProperties properties;
    private final Ali1688TokenCipher tokenCipher;
    private final RestTemplate restTemplate;
    private final Ali1688OpenApiAuthorizationMapper authorizationMapper;
    private final Ali1688OAuthTokenParser tokenParser;

    Ali1688OpenApiTokenRefresher(
            Ali1688HistoricalOrderOpenApiProperties properties,
            Ali1688TokenCipher tokenCipher,
            RestTemplate restTemplate,
            Ali1688OpenApiAuthorizationMapper authorizationMapper,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.tokenCipher = tokenCipher;
        this.restTemplate = restTemplate;
        this.authorizationMapper = authorizationMapper;
        this.tokenParser = new Ali1688OAuthTokenParser(objectMapper);
    }

    boolean requiresRefresh(Ali1688HistoricalOrderAuthorizationRow authorization) {
        LocalDateTime expiresAt = authorization == null ? null : authorization.getExpiresAt();
        return expiresAt != null && !expiresAt.isAfter(LocalDateTime.now().plusMinutes(2));
    }

    Ali1688HistoricalOrderAuthorizationRefreshResult refresh(
            Ali1688HistoricalOrderAuthorizationRow authorization
    ) {
        if (authorization == null) {
            return failure(Ali1688HistoricalOrderFailureCode.AUTH_REQUIRED, null);
        }
        if (!requiresRefresh(authorization)) {
            return Ali1688HistoricalOrderAuthorizationRefreshResult.success();
        }
        try {
            return refreshOnce(authorization);
        } catch (HttpStatusCodeException exception) {
            return failure(httpCode(exception.getRawStatusCode()), retryAfter(exception.getResponseHeaders()));
        } catch (RestClientException exception) {
            return failure(Ali1688HistoricalOrderFailureCode.AUTH_REFRESH_OUTCOME_UNKNOWN, null);
        } catch (RuntimeException exception) {
            return failure(Ali1688HistoricalOrderFailureCode.AUTH_REQUIRED, null);
        }
    }

    private Ali1688HistoricalOrderAuthorizationRefreshResult refreshOnce(
            Ali1688HistoricalOrderAuthorizationRow authorization
    ) {
        String refreshToken = tokenCipher.decrypt(authorization.getRefreshTokenCipher());
        if (!StringUtils.hasText(refreshToken)) {
            return failure(Ali1688HistoricalOrderFailureCode.AUTH_REQUIRED, null);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("need_refresh_token", "true");
        body.add("client_id", trim(properties.getAppKey()));
        body.add("client_secret", trim(properties.getAppSecret()));
        body.add("refresh_token", refreshToken.trim());
        ResponseEntity<String> response = restTemplate.exchange(
                tokenUrl(),
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );
        TokenPayload token = tokenParser.parse(
                Ali1688OpenApiHttpResponse.requireSuccessfulBody(response)
        );
        requireStableIdentity(authorization, token);
        authorization.setStatus("authorized");
        authorization.setAccessTokenCipher(tokenCipher.encrypt(token.accessToken));
        authorization.setRefreshTokenCipher(tokenCipher.encrypt(
                StringUtils.hasText(token.refreshToken) ? token.refreshToken.trim() : refreshToken
        ));
        authorization.setExpiresAt(token.expiresAt);
        if (StringUtils.hasText(token.accountLabel)) {
            authorization.setAccountLabel(token.accountLabel.trim());
        }
        if (authorizationMapper.updateAuthorizationTokens(authorization) != 1) {
            throw new IllegalStateException("1688 OAuth refresh authorization changed");
        }
        return Ali1688HistoricalOrderAuthorizationRefreshResult.success();
    }

    private void requireStableIdentity(
            Ali1688HistoricalOrderAuthorizationRow authorization,
            TokenPayload token
    ) {
        if (StringUtils.hasText(token.providerAccountId)
                && !trim(authorization.getProviderAccountId()).equals(token.providerAccountId.trim())) {
            throw new IllegalStateException("1688 OAuth refresh account identity changed");
        }
    }

    private Ali1688HistoricalOrderFailureCode httpCode(int status) {
        if (status == 401) return Ali1688HistoricalOrderFailureCode.AUTH_REQUIRED;
        if (status == 403) return Ali1688HistoricalOrderFailureCode.BLOCKED_BY_RISK_CONTROL;
        if (status == 429) return Ali1688HistoricalOrderFailureCode.RATE_LIMITED;
        return status >= 500
                ? Ali1688HistoricalOrderFailureCode.PROVIDER_UNAVAILABLE
                : Ali1688HistoricalOrderFailureCode.UNEXPECTED_RESPONSE;
    }

    private Duration retryAfter(HttpHeaders headers) {
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (!StringUtils.hasText(value)) return null;
        try {
            return Duration.ofSeconds(Math.max(0L, Long.parseLong(value.trim())));
        } catch (NumberFormatException ignored) {
            try {
                Instant retryAt = ZonedDateTime.parse(
                        value.trim(),
                        DateTimeFormatter.RFC_1123_DATE_TIME
                ).toInstant();
                Duration delay = Duration.between(Instant.now(), retryAt);
                return delay.isNegative() ? Duration.ZERO : delay;
            } catch (RuntimeException ignoredDate) {
                return null;
            }
        }
    }

    private Ali1688HistoricalOrderAuthorizationRefreshResult failure(
            Ali1688HistoricalOrderFailureCode code,
            Duration retryAfter
    ) {
        return Ali1688HistoricalOrderAuthorizationRefreshResult.failure(code, retryAfter);
    }

    private String tokenUrl() {
        return trim(properties.getTokenUrlTemplate())
                .replace("{appKey}", trim(properties.getAppKey()));
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
