package com.nuono.next.procurement.aliorder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.Ali1688HistoricalOrderMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class Ali1688HistoricalOrderOAuthService {

    public static final String PROVIDER_CODE = "ALI1688_OPEN_API";
    static final String OPEN_API_SCOPE_SUMMARY = "1688 OpenAPI 历史订单只读授权，不会付款、下单或发送供应商消息。";

    private final Ali1688HistoricalOrderMapper mapper;
    private final Ali1688HistoricalOrderOpenApiProperties properties;
    private final Ali1688OpenApiSigner signer;
    private final Ali1688TokenCipher tokenCipher;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Autowired
    public Ali1688HistoricalOrderOAuthService(
            Ali1688HistoricalOrderMapper mapper,
            Ali1688HistoricalOrderOpenApiProperties properties,
            Ali1688OpenApiSigner signer,
            Ali1688TokenCipher tokenCipher,
            ObjectMapper objectMapper,
            RestTemplateBuilder restTemplateBuilder
    ) {
        this(
                mapper,
                properties,
                signer,
                tokenCipher,
                objectMapper,
                restTemplateBuilder
                        .setConnectTimeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                        .setReadTimeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                        .build()
        );
    }

    Ali1688HistoricalOrderOAuthService(
            Ali1688HistoricalOrderMapper mapper,
            Ali1688HistoricalOrderOpenApiProperties properties,
            Ali1688OpenApiSigner signer,
            Ali1688TokenCipher tokenCipher,
            ObjectMapper objectMapper,
            RestTemplate restTemplate
    ) {
        this.mapper = mapper;
        this.properties = properties;
        this.signer = signer;
        this.tokenCipher = tokenCipher;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    public Ali1688HistoricalOrderAuthorizationView.StartView startAuthorization(
            BusinessAccessContext context,
            String storeCode,
            String siteCode
    ) {
        Ali1688HistoricalOrderAuthorizationView.StartView view =
                new Ali1688HistoricalOrderAuthorizationView.StartView();
        view.setProviderCode(PROVIDER_CODE);
        if (!isConfigured()) {
            view.setConfigured(false);
            view.setMessage("1688 OpenAPI 尚未配置 AppKey、AppSecret、回调地址或 token 加密密钥。");
            return view;
        }

        String state = encodeState(context, storeCode, siteCode);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", trim(properties.getAppKey()));
        params.put("site", defaultText(properties.getSite(), "1688"));
        params.put("redirect_uri", trim(properties.getRedirectUri()));
        params.put("state", state);
        params.put("_aop_signature", signer.hmacSha1Hex(params, properties.getAppSecret()));

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(trim(properties.getAuthorizeUrl()));
        params.forEach(builder::queryParam);
        view.setConfigured(true);
        view.setAuthorizationUrl(builder.build(true).toUriString());
        view.setMessage("请在 1688 页面完成账号授权，系统只读取历史订单。");
        return view;
    }

    public Ali1688HistoricalOrderAuthorizationView.CompleteView completeAuthorization(
            BusinessAccessContext context,
            String code,
            String state
    ) {
        StatePayload payload = validateCallback(code, state);
        Long ownerUserId = ownerUserId(context);
        if (ownerUserId == null || !ownerUserId.equals(payload.ownerUserId)) {
            throw new IllegalArgumentException("1688 OAuth state 与当前账号不匹配。");
        }
        return completeAuthorization(payload, code);
    }

    public Ali1688HistoricalOrderAuthorizationView.CompleteView completeAuthorization(
            String code,
            String state
    ) {
        StatePayload payload = validateCallback(code, state);
        return completeAuthorization(payload, code);
    }

    private Ali1688HistoricalOrderAuthorizationView.CompleteView completeAuthorization(
            StatePayload payload,
            String code
    ) {
        if (payload.ownerUserId == null || payload.operatorUserId == null) {
            throw new IllegalArgumentException("1688 OAuth state 缺少账号上下文。");
        }
        TokenPayload token = exchangeCode(code);
        if (!StringUtils.hasText(token.providerAccountId)) {
            throw new IllegalStateException("1688 OAuth token 响应缺少授权账号标识。");
        }

        Ali1688HistoricalOrderAuthorizationRow row = mapper.selectAuthorizationByProviderAccount(
                payload.ownerUserId,
                PROVIDER_CODE,
                token.providerAccountId
        );
        boolean insert = row == null;
        if (insert) {
            row = new Ali1688HistoricalOrderAuthorizationRow();
            row.setId(mapper.nextAuthorizationId());
            row.setOwnerUserId(payload.ownerUserId);
            row.setCreatedBy(payload.operatorUserId);
        }
        row.setProviderCode(PROVIDER_CODE);
        row.setProviderAccountId(token.providerAccountId);
        row.setAccountLabel(defaultText(token.accountLabel, token.providerAccountId));
        row.setStatus("authorized");
        row.setScopeSummary(OPEN_API_SCOPE_SUMMARY);
        row.setAccessTokenCipher(tokenCipher.encrypt(token.accessToken));
        row.setRefreshTokenCipher(tokenCipher.encrypt(token.refreshToken));
        row.setExpiresAt(token.expiresAt);
        row.setUpdatedBy(payload.operatorUserId);
        if (insert) {
            mapper.insertAuthorization(row);
        } else {
            mapper.updateAuthorizationTokens(row);
        }

        if (StringUtils.hasText(payload.storeCode)) {
            mapper.insertExplicitStoreBinding(
                    mapper.nextOrderStoreBindingId(),
                    payload.ownerUserId,
                    row.getId(),
                    payload.storeCode,
                    payload.siteCode,
                    payload.operatorUserId,
                    "1688 OpenAPI 授权绑定到当前店铺范围。"
            );
        } else {
            mapper.insertOwnerWideStoreBinding(
                    mapper.nextOrderStoreBindingId(),
                    payload.ownerUserId,
                    row.getId(),
                    payload.operatorUserId
            );
        }

        Ali1688HistoricalOrderAuthorizationView.CompleteView view =
                new Ali1688HistoricalOrderAuthorizationView.CompleteView();
        view.setAuthorizationId(row.getId());
        view.setProviderCode(PROVIDER_CODE);
        view.setProviderAccountId(row.getProviderAccountId());
        view.setAccountLabel(row.getAccountLabel());
        view.setMessage("1688 授权已完成，可以返回系统刷新历史订单。");
        return view;
    }

    private StatePayload validateCallback(String code, String state) {
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("1688 OAuth callback 缺少 code。");
        }
        StatePayload payload = decodeState(state);
        return payload;
    }

    private TokenPayload exchangeCode(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("need_refresh_token", "true");
        body.add("client_id", trim(properties.getAppKey()));
        body.add("client_secret", trim(properties.getAppSecret()));
        body.add("redirect_uri", trim(properties.getRedirectUri()));
        body.add("code", trim(code));

        ResponseEntity<String> response = restTemplate.exchange(
                tokenUrl(),
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );
        return parseTokenPayload(response.getBody());
    }

    private TokenPayload parseTokenPayload(String body) {
        try {
            JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
            JsonNode payload = root.has("result") && root.get("result").isObject() ? root.get("result") : root;
            TokenPayload token = new TokenPayload();
            token.accessToken = firstText(payload, "access_token", "accessToken");
            token.refreshToken = firstText(payload, "refresh_token", "refreshToken");
            token.providerAccountId = firstText(
                    payload,
                    "memberId",
                    "member_id",
                    "resource_owner",
                    "loginId",
                    "login_id",
                    "accountId"
            );
            token.accountLabel = firstText(payload, "resource_owner", "loginName", "login_id", "memberId", "member_id");
            Long expiresIn = firstLong(payload, "expires_in", "expiresIn");
            token.expiresAt = expiresIn == null ? null : LocalDateTime.now().plusSeconds(Math.max(0, expiresIn));
            if (!StringUtils.hasText(token.accessToken)) {
                throw new IllegalStateException("1688 OAuth token 响应缺少 access_token。");
            }
            return token;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("1688 OAuth token 响应解析失败。", exception);
        }
    }

    private String encodeState(BusinessAccessContext context, String storeCode, String siteCode) {
        StatePayload payload = new StatePayload();
        payload.ownerUserId = ownerUserId(context);
        payload.operatorUserId = operatorUserId(context);
        payload.storeCode = trimToNull(storeCode);
        payload.siteCode = trimToNull(siteCode);
        payload.nonce = UUID.randomUUID().toString();
        payload.issuedAtEpochSeconds = Instant.now().getEpochSecond();
        try {
            String encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(payload));
            String signature = signer.hmacSha1Hex(Map.of("payload", encoded), properties.getAppSecret());
            return encoded + "." + signature;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encode 1688 OAuth state.", exception);
        }
    }

    private StatePayload decodeState(String state) {
        if (!StringUtils.hasText(state) || !state.contains(".")) {
            throw new IllegalArgumentException("1688 OAuth callback 缺少有效 state。");
        }
        String[] parts = state.split("\\.", 2);
        String expectedSignature = signer.hmacSha1Hex(Map.of("payload", parts[0]), properties.getAppSecret());
        if (!expectedSignature.equals(parts[1])) {
            throw new IllegalArgumentException("1688 OAuth state 签名无效。");
        }
        StatePayload payload;
        try {
            payload = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[0]),
                    StatePayload.class
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException("1688 OAuth state 解析失败。", exception);
        }
        validateStateFreshness(payload);
        return payload;
    }

    private void validateStateFreshness(StatePayload payload) {
        if (payload == null || payload.issuedAtEpochSeconds == null || payload.issuedAtEpochSeconds <= 0) {
            throw new IllegalArgumentException("1688 OAuth state 已过期，请重新发起授权。");
        }
        int ttlSeconds = properties.getStateTtlSeconds();
        if (ttlSeconds <= 0) {
            return;
        }
        long nowEpochSeconds = Instant.now().getEpochSecond();
        if (payload.issuedAtEpochSeconds > nowEpochSeconds
                || nowEpochSeconds - payload.issuedAtEpochSeconds > ttlSeconds) {
            throw new IllegalArgumentException("1688 OAuth state 已过期，请重新发起授权。");
        }
    }

    private String tokenUrl() {
        return trim(properties.getTokenUrlTemplate()).replace("{appKey}", trim(properties.getAppKey()));
    }

    private boolean isConfigured() {
        return properties.isEnabled()
                && StringUtils.hasText(properties.getAppKey())
                && StringUtils.hasText(properties.getAppSecret())
                && StringUtils.hasText(properties.getRedirectUri())
                && StringUtils.hasText(properties.getTokenCipherSecret());
    }

    private Long ownerUserId(BusinessAccessContext context) {
        if (context == null) {
            return null;
        }
        return context.getBusinessOwnerUserId() == null ? context.getSessionUserId() : context.getBusinessOwnerUserId();
    }

    private Long operatorUserId(BusinessAccessContext context) {
        return context == null ? null : context.getSessionUserId();
    }

    private String firstText(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull() && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private Long firstLong(JsonNode node, String... fieldNames) {
        String value = firstText(node, fieldNames);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    static class StatePayload {
        public Long ownerUserId;
        public Long operatorUserId;
        public String storeCode;
        public String siteCode;
        public String nonce;
        public Long issuedAtEpochSeconds;
    }

    private static class TokenPayload {
        private String accessToken;
        private String refreshToken;
        private String providerAccountId;
        private String accountLabel;
        private LocalDateTime expiresAt;
    }
}
