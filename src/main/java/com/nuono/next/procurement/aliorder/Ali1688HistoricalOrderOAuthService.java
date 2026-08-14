package com.nuono.next.procurement.aliorder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.procurement.aliorder.Ali1688OAuthTokenParser.TokenPayload;
import com.nuono.next.infrastructure.mapper.Ali1688HistoricalOrderMapper;
import com.nuono.next.infrastructure.mapper.Ali1688OpenApiAuthorizationMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class Ali1688HistoricalOrderOAuthService {

    public static final String PROVIDER_CODE = "ALI1688_OPEN_API";
    static final String OPEN_API_SCOPE_SUMMARY = "1688 OpenAPI 历史订单只读授权，不会付款、下单或发送供应商消息。";
    private static final Logger LOGGER = LoggerFactory.getLogger(Ali1688HistoricalOrderOAuthService.class);

    private final Ali1688HistoricalOrderMapper mapper;
    private final Ali1688OpenApiAuthorizationMapper authorizationMapper;
    private final Ali1688HistoricalOrderOpenApiProperties properties;
    private final Ali1688OpenApiSigner signer;
    private final Ali1688TokenCipher tokenCipher;
    private final ObjectMapper objectMapper;
    private final Ali1688OAuthTokenParser tokenParser;
    private final RestTemplate restTemplate;

    @Autowired
    public Ali1688HistoricalOrderOAuthService(
            Ali1688HistoricalOrderMapper mapper,
            Ali1688OpenApiAuthorizationMapper authorizationMapper,
            Ali1688HistoricalOrderOpenApiProperties properties,
            Ali1688OpenApiSigner signer,
            Ali1688TokenCipher tokenCipher,
            ObjectMapper objectMapper,
            RestTemplateBuilder restTemplateBuilder
    ) {
        this(
                mapper,
                authorizationMapper,
                properties,
                signer,
                tokenCipher,
                objectMapper,
                restTemplateBuilder
                        .requestFactory(Ali1688NoRedirectRequestFactory::new)
                        .setConnectTimeout(Duration.ofSeconds(Math.min(10, Math.max(1, properties.getTimeoutSeconds()))))
                        .setReadTimeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                        .build()
        );
    }

    Ali1688HistoricalOrderOAuthService(
            Ali1688HistoricalOrderMapper mapper,
            Ali1688OpenApiAuthorizationMapper authorizationMapper,
            Ali1688HistoricalOrderOpenApiProperties properties,
            Ali1688OpenApiSigner signer,
            Ali1688TokenCipher tokenCipher,
            ObjectMapper objectMapper,
            RestTemplate restTemplate
    ) {
        this.mapper = mapper;
        this.authorizationMapper = authorizationMapper;
        this.properties = properties;
        this.signer = signer;
        this.tokenCipher = tokenCipher;
        this.objectMapper = objectMapper;
        this.tokenParser = new Ali1688OAuthTokenParser(objectMapper);
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
        String authorizationUrl = builder.build(true).toUriString();
        view.setConfigured(true);
        view.setAuthorizationUrl(authorizationUrl);
        view.setMessage("请在 1688 页面完成账号授权，系统只读取历史订单。");
        logAuthorizationRuntimeDiagnostic(params, authorizationUrl);
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
            authorizationMapper.updateAuthorizationTokens(row);
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
        Map<String, String> body = new LinkedHashMap<>();
        body.put("grant_type", "authorization_code");
        body.put("need_refresh_token", "true");
        body.put("client_id", trim(properties.getAppKey()));
        body.put("client_secret", trim(properties.getAppSecret()));
        body.put("redirect_uri", trim(properties.getRedirectUri()));
        body.put("code", trim(code));
        ResponseEntity<String> response = Ali1688SensitiveHttpClient.postForm(
                restTemplate,
                tokenUrl(),
                body,
                false
        );
        return tokenParser.parse(
                Ali1688OpenApiHttpResponse.requireSuccessfulBody(response)
        );
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
        return properties.hasProductionDp10Configuration();
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

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private void logAuthorizationRuntimeDiagnostic(Map<String, String> params, String authorizationUrl) {
        String runtimeAppKey = defaultText(params.get("client_id"), "");
        String authorizationClientId = UriComponentsBuilder.fromUriString(authorizationUrl)
                .build()
                .getQueryParams()
                .getFirst("client_id");
        String authorizationRedirectUri = UriComponentsBuilder.fromUriString(authorizationUrl)
                .build()
                .getQueryParams()
                .getFirst("redirect_uri");
        LOGGER.info(
                "ALI1688_OAUTH_RUNTIME_DIAGNOSTIC runtimeAppKeyLength={} runtimeAppKeyFingerprint={} "
                        + "authorizationClientIdLength={} authorizationClientIdFingerprint={} "
                        + "clientIdMatchesRuntime={} redirectUriMatchesRuntime={} authorizeHost={}",
                runtimeAppKey.length(),
                runtimeDiagnosticFingerprint(runtimeAppKey, properties.getTokenCipherSecret()),
                authorizationClientId == null ? 0 : authorizationClientId.length(),
                runtimeDiagnosticFingerprint(authorizationClientId, properties.getTokenCipherSecret()),
                runtimeAppKey.equals(authorizationClientId),
                trim(properties.getRedirectUri()).equals(authorizationRedirectUri),
                UriComponentsBuilder.fromUriString(authorizationUrl).build().getHost()
        );
    }

    static String runtimeDiagnosticFingerprint(String value, String nonDisclosureKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    trimStatic(nonDisclosureKey).getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(trimStatic(value).getBytes(StandardCharsets.UTF_8))
            );
            return encoded.substring(0, 16);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to fingerprint 1688 OAuth runtime configuration.", exception);
        }
    }

    private static String trimStatic(String value) {
        return value == null ? "" : value.trim();
    }

    static class StatePayload {
        public Long ownerUserId;
        public Long operatorUserId;
        public String storeCode;
        public String siteCode;
        public String nonce;
        public Long issuedAtEpochSeconds;
    }

}
