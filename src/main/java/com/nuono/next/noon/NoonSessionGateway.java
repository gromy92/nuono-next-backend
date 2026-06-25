package com.nuono.next.noon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noonlog.NoonHttpCallLogService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class NoonSessionGateway {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration SESSION_TTL = Duration.ofMinutes(15);
    private static final String DEFAULT_SIGNIN_LOGIN_URL =
            "https://login.noon.partners/_svc/auth-v1/public/auth/signin";
    private static final String DEFAULT_WHOAMI_URL =
            "https://toolbar.noon.partners/_svc/auth-v1/whoami";
    private static final String DEFAULT_IDENTITY_USER_LOOKUP_URL =
            "https://login-alt.noon.partners/_svc/mp-partner-identity/public/user/lookup";
    private static final String DEFAULT_IDENTITY_PKCE_URL =
            "https://login-alt.noon.partners/_svc/mp-partner-identity/public/client/pkce";
    private static final String DEFAULT_IDENTITY_VALIDATE_URL =
            "https://login-alt.noon.partners/_svc/mp-partner-identity/public/user/validate";
    private static final String DEFAULT_IDENTITY_PROJECT_LIST_URL =
            "https://login-alt.noon.partners/_svc/mp-partner-identity/public/user/project/list";
    private static final String DEFAULT_IDENTITY_SESSION_CREATE_URL =
            "https://login-alt.noon.partners/_svc/mp-partner-identity/public/user/session/create";
    private static final String NOON_WEB_CLIENT_CODE = "web";
    private static final int MAX_RATE_LIMIT_RETRIES = 4;
    private static final long INITIAL_RATE_LIMIT_DELAY_MILLIS = 2000L;
    private static final int MAX_TRANSIENT_READ_RETRIES = 2;
    private static final long INITIAL_TRANSIENT_RETRY_DELAY_MILLIS = 700L;
    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36";
    private static final String DEFAULT_ACCEPT_LANGUAGE = "en-SA,en;q=0.9";

    private final ObjectMapper objectMapper;
    private final StoreSyncMapper storeSyncMapper;
    private final boolean chromeCookieFallbackEnabled;
    private final long accountMinRequestIntervalMillis;
    private final boolean forceHttp11;
    private final String requestUserAgent;
    private final String acceptLanguage;
    private final String localeHeader;
    private final String langHeader;
    private final boolean partnerIdentityLoginEnabled;
    private final boolean signinFallbackEnabled;
    private final String signinLoginUrl;
    private final String whoamiUrl;
    private final String identityUserLookupUrl;
    private final String identityPkceUrl;
    private final String identityValidateUrl;
    private final String identityProjectListUrl;
    private final String identitySessionCreateUrl;
    private final boolean proxyEnabled;
    private final String proxyType;
    private final String proxyHost;
    private final int proxyPort;
    private final String proxyProviderUrl;
    private final ConcurrentMap<String, AuthSessionState> sessionCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> accountLocks = new ConcurrentHashMap<>();
    private final ThreadLocal<LinkedHashMap<String, Integer>> requestCountScope = new ThreadLocal<>();
    private NoonHttpCallLogService noonHttpCallLogService;

    public NoonSessionGateway(
            ObjectMapper objectMapper,
            StoreSyncMapper storeSyncMapper,
            @Value("${nuono.noon.chrome-cookie-fallback-enabled:true}") boolean chromeCookieFallbackEnabled,
            @Value("${nuono.noon.account-min-request-interval-millis:1200}") long accountMinRequestIntervalMillis,
            @Value("${nuono.noon.force-http11:true}") boolean forceHttp11,
            @Value("${nuono.noon.user-agent:}") String requestUserAgent,
            @Value("${nuono.noon.accept-language:}") String acceptLanguage,
            @Value("${nuono.noon.locale-header:en-sa}") String localeHeader,
            @Value("${nuono.noon.lang-header:en}") String langHeader,
            @Value("${nuono.noon.auth.partner-identity-enabled:true}") boolean partnerIdentityLoginEnabled,
            @Value("${nuono.noon.auth.signin-fallback-enabled:true}") boolean signinFallbackEnabled,
            @Value("${nuono.noon.urls.signin:}") String signinLoginUrl,
            @Value("${nuono.noon.urls.whoami:}") String whoamiUrl,
            @Value("${nuono.noon.urls.identity-user-lookup:}") String identityUserLookupUrl,
            @Value("${nuono.noon.urls.identity-pkce:}") String identityPkceUrl,
            @Value("${nuono.noon.urls.identity-validate:}") String identityValidateUrl,
            @Value("${nuono.noon.urls.identity-project-list:}") String identityProjectListUrl,
            @Value("${nuono.noon.urls.identity-session-create:}") String identitySessionCreateUrl,
            @Value("${nuono.noon.proxy.enabled:false}") boolean proxyEnabled,
            @Value("${nuono.noon.proxy.type:HTTP}") String proxyType,
            @Value("${nuono.noon.proxy.host:}") String proxyHost,
            @Value("${nuono.noon.proxy.port:0}") int proxyPort,
            @Value("${nuono.noon.proxy.provider-url:}") String proxyProviderUrl
    ) {
        this.objectMapper = objectMapper;
        this.storeSyncMapper = storeSyncMapper;
        this.chromeCookieFallbackEnabled = chromeCookieFallbackEnabled;
        this.accountMinRequestIntervalMillis = Math.max(0L, accountMinRequestIntervalMillis);
        this.forceHttp11 = forceHttp11;
        this.requestUserAgent = StringUtils.hasText(requestUserAgent)
                ? requestUserAgent.trim()
                : DEFAULT_USER_AGENT;
        this.acceptLanguage = StringUtils.hasText(acceptLanguage)
                ? acceptLanguage.trim()
                : DEFAULT_ACCEPT_LANGUAGE;
        this.localeHeader = StringUtils.hasText(localeHeader) ? localeHeader.trim() : "en-sa";
        this.langHeader = StringUtils.hasText(langHeader) ? langHeader.trim() : "en";
        this.partnerIdentityLoginEnabled = partnerIdentityLoginEnabled;
        this.signinFallbackEnabled = signinFallbackEnabled;
        this.signinLoginUrl = defaultIfBlank(signinLoginUrl, DEFAULT_SIGNIN_LOGIN_URL);
        this.whoamiUrl = defaultIfBlank(whoamiUrl, DEFAULT_WHOAMI_URL);
        this.identityUserLookupUrl = defaultIfBlank(identityUserLookupUrl, DEFAULT_IDENTITY_USER_LOOKUP_URL);
        this.identityPkceUrl = defaultIfBlank(identityPkceUrl, DEFAULT_IDENTITY_PKCE_URL);
        this.identityValidateUrl = defaultIfBlank(identityValidateUrl, DEFAULT_IDENTITY_VALIDATE_URL);
        this.identityProjectListUrl = defaultIfBlank(identityProjectListUrl, DEFAULT_IDENTITY_PROJECT_LIST_URL);
        this.identitySessionCreateUrl = defaultIfBlank(identitySessionCreateUrl, DEFAULT_IDENTITY_SESSION_CREATE_URL);
        this.proxyEnabled = proxyEnabled;
        this.proxyType = StringUtils.hasText(proxyType) ? proxyType.trim().toUpperCase(Locale.ROOT) : "HTTP";
        this.proxyHost = StringUtils.hasText(proxyHost) ? proxyHost.trim() : null;
        this.proxyPort = Math.max(0, proxyPort);
        this.proxyProviderUrl = normalize(proxyProviderUrl);
    }

    @Autowired(required = false)
    public void setNoonHttpCallLogService(NoonHttpCallLogService noonHttpCallLogService) {
        this.noonHttpCallLogService = noonHttpCallLogService;
    }

    public NoonSession login(
            Long ownerUserId,
            String noonUser,
            String noonPassword,
            String persistedCookie,
            String projectCode,
            String storeCode
    ) {
        String normalizedUser = normalizeUser(noonUser);
        if (!StringUtils.hasText(normalizedUser)) {
            throw new IllegalArgumentException("缺少 Noon 登录账号。");
        }
        if (!StringUtils.hasText(noonPassword)) {
            throw new IllegalArgumentException("缺少 Noon 登录密码。");
        }
        AuthSessionState state = getOrCreateState(
                ownerUserId,
                normalizedUser,
                noonPassword,
                normalizeCookie(persistedCookie),
                normalize(projectCode),
                normalize(storeCode),
                false
        );
        return new NoonSession(normalizedUser, noonPassword, state, normalize(projectCode), normalize(storeCode));
    }

    public JsonNode whoamiWithCookie(
            String persistedCookie,
            String projectCode,
            String storeCode
    ) {
        if (!StringUtils.hasText(persistedCookie)) {
            throw new IllegalArgumentException("缺少 Noon Cookie。");
        }
        try {
            CookieManager cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
            HttpClient httpClient = newHttpClient(cookieManager);
            AuthSessionState state = new AuthSessionState(
                    objectMapper,
                    "cookie-check",
                    "",
                    httpClient,
                    cookieManager,
                    accountMinRequestIntervalMillis,
                    requestUserAgent,
                    acceptLanguage,
                    localeHeader,
                    langHeader,
                    this::recordRequest,
                    this::recordHttpCall
            );
            state.importCookieHeader(persistedCookie);
            state.applyContextCookies(normalize(projectCode), normalize(storeCode));
            return state.getJson(normalize(projectCode), normalize(storeCode), whoamiUrl, false, null);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Noon WHOAMI 验证失败：" + exception.getMessage(), exception);
        }
    }

    public RequestCountScope openRequestCountScope() {
        LinkedHashMap<String, Integer> previous = requestCountScope.get();
        LinkedHashMap<String, Integer> current = new LinkedHashMap<>();
        requestCountScope.set(current);
        return new RequestCountScope(previous, current);
    }

    private void recordRequest(String url) {
        LinkedHashMap<String, Integer> counts = requestCountScope.get();
        if (counts == null) {
            return;
        }
        counts.merge(resolveRequestMetricKey(url), 1, Integer::sum);
        counts.merge("__total__", 1, Integer::sum);
    }

    private void recordHttpCall(
            HttpRequest request,
            Integer responseStatusCode,
            String responseBody,
            Long elapsedMs,
            String status,
            String failureType,
            String errorMessage
    ) {
        NoonHttpCallLogService service = noonHttpCallLogService;
        if (service == null) {
            return;
        }
        try {
            service.record(request, responseStatusCode, responseBody, elapsedMs, status, failureType, errorMessage);
        } catch (Exception ignored) {
            // Noon calls must not fail because the audit log table is unavailable.
        }
    }

    private String resolveRequestMetricKey(String url) {
        if (!StringUtils.hasText(url)) {
            return "unknown";
        }
        if (url.contains("/public/auth/signin")) {
            return "auth.signin";
        }
        if (url.contains("/public/user/lookup")) {
            return "auth.identity.lookup";
        }
        if (url.contains("/public/client/pkce")) {
            return "auth.identity.pkce";
        }
        if (url.contains("/public/user/validate")) {
            return "auth.identity.validate";
        }
        if (url.contains("/public/user/project/list")) {
            return "auth.identity.project-list";
        }
        if (url.contains("/public/user/session/create")) {
            return "auth.identity.session-create";
        }
        if (url.contains("/auth-v1/whoami")) {
            return "auth.whoami";
        }
        if (url.contains("/project/list")) {
            return "project.list";
        }
        if (url.contains("/noon/store/list")) {
            return "store.list";
        }
        if (url.contains("/offer/list/noon")) {
            return "offer.list";
        }
        if (url.contains("/zsku/retrieve")) {
            return "zsku.retrieve";
        }
        try {
            return URI.create(url).getPath();
        } catch (Exception ignored) {
            return url;
        }
    }

    private AuthSessionState getOrCreateState(
            Long ownerUserId,
            String noonUser,
            String noonPassword,
            String persistedCookie,
            String projectCode,
            String storeCode,
            boolean forceRefresh
    ) {
        synchronized (accountLocks.computeIfAbsent(noonUser, key -> new Object())) {
            AuthSessionState existing = sessionCache.get(noonUser);
            if (!forceRefresh && existing != null && !existing.isExpired() && existing.matchesPassword(noonPassword)) {
                return existing;
            }

            AuthSessionState created = createAuthenticatedState(
                    ownerUserId,
                    noonUser,
                    noonPassword,
                    persistedCookie,
                    projectCode,
                    storeCode
            );
            sessionCache.put(noonUser, created);
            return created;
        }
    }

    private AuthSessionState createAuthenticatedState(
            Long ownerUserId,
            String noonUser,
            String noonPassword,
            String persistedCookie,
            String projectCode,
            String storeCode
    ) {
        AuthSessionState cookieState = tryCreateStateFromPersistedCookie(
                noonUser,
                noonPassword,
                persistedCookie,
                projectCode,
                storeCode
        );
        if (cookieState != null) {
            return cookieState;
        }

        IllegalStateException partnerIdentityFailure = null;
        if (partnerIdentityLoginEnabled) {
            try {
                AuthSessionState state = createPartnerIdentityState(
                        ownerUserId,
                        noonUser,
                        noonPassword,
                        projectCode,
                        storeCode
                );
                persistCookie(ownerUserId, projectCode, state.exportAuthCookieHeader());
                return state;
            } catch (IllegalStateException exception) {
                partnerIdentityFailure = exception;
            }
        }

        IllegalStateException signinFailure = null;
        if (signinFallbackEnabled) {
            try {
                AuthSessionState state = createSigninState(
                        ownerUserId,
                        noonUser,
                        noonPassword,
                        projectCode,
                        storeCode
                );
                persistCookie(ownerUserId, projectCode, state.exportAuthCookieHeader());
                return state;
            } catch (IllegalStateException exception) {
                AuthSessionState fallbackState = createChromeFallbackState(
                        noonUser,
                        noonPassword,
                        projectCode,
                        storeCode,
                        exception
                );
                if (fallbackState != null) {
                    persistCookie(ownerUserId, projectCode, fallbackState.exportAuthCookieHeader());
                    return fallbackState;
                }
                signinFailure = exception;
            }
        }

        if (partnerIdentityFailure != null && signinFailure != null) {
            throw new IllegalStateException(
                    "Noon login-alt 登录失败：" + partnerIdentityFailure.getMessage()
                            + "；signin 兜底也失败：" + signinFailure.getMessage(),
                    signinFailure
            );
        }
        if (partnerIdentityFailure != null) {
            throw partnerIdentityFailure;
        }
        if (signinFailure != null) {
            throw signinFailure;
        }
        throw new IllegalStateException("Noon 登录失败：未启用可用的登录方式。");
    }

    private AuthSessionState createSigninState(
            Long ownerUserId,
            String noonUser,
            String noonPassword,
            String projectCode,
            String storeCode
    ) {
        try {
            AuthSessionState state = newSessionState(noonUser, noonPassword);

            ObjectNode loginBody = objectMapper.createObjectNode();
            loginBody.put("email", noonUser);
            loginBody.put("password", noonPassword);
            state.postJson(projectCode, storeCode, signinLoginUrl, loginBody, false, null);
            return state;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Noon signin 登录失败：" + exception.getMessage(), exception);
        }
    }

    private AuthSessionState createPartnerIdentityState(
            Long ownerUserId,
            String noonUser,
            String noonPassword,
            String projectCode,
            String storeCode
    ) {
        try {
            AuthSessionState state = newSessionState(noonUser, noonPassword);

            PartnerIdentityUser user = lookupPartnerIdentityUser(state, noonUser);
            PkcePair pkce = createPkcePair(state);
            String accessToken = validatePartnerIdentityPassword(state, user.getUserCode(), noonUser, noonPassword, pkce);
            String sessionProjectCode = resolvePartnerIdentityProjectCode(state, user.getUserCode(), accessToken, projectCode);
            createPartnerIdentitySession(state, user.getUserCode(), accessToken, sessionProjectCode, pkce);
            state.applyContextCookies(sessionProjectCode, storeCode);
            return state;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Noon login-alt 登录失败：" + exception.getMessage(), exception);
        }
    }

    private AuthSessionState newSessionState(String noonUser, String noonPassword) {
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        HttpClient httpClient = newHttpClient(cookieManager);
        return new AuthSessionState(
                objectMapper,
                noonUser,
                noonPassword,
                httpClient,
                cookieManager,
                accountMinRequestIntervalMillis,
                requestUserAgent,
                acceptLanguage,
                localeHeader,
                langHeader,
                this::recordRequest,
                this::recordHttpCall
        );
    }

    private AuthSessionState tryCreateStateFromPersistedCookie(
            String noonUser,
            String noonPassword,
            String persistedCookie,
            String projectCode,
            String storeCode
    ) {
        if (!StringUtils.hasText(persistedCookie)) {
            return null;
        }
        try {
            CookieManager cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
            HttpClient httpClient = newHttpClient(cookieManager);
            AuthSessionState state = new AuthSessionState(
                    objectMapper,
                    noonUser,
                    noonPassword,
                    httpClient,
                    cookieManager,
                    accountMinRequestIntervalMillis,
                    requestUserAgent,
                    acceptLanguage,
                    localeHeader,
                    langHeader,
                    this::recordRequest,
                    this::recordHttpCall
            );
            state.importCookieHeader(persistedCookie);
            state.applyContextCookies(projectCode, storeCode);
            state.getJson(projectCode, storeCode, whoamiUrl, false, null);
            return state;
        } catch (Exception ignored) {
            return null;
        }
    }

    private PartnerIdentityUser lookupPartnerIdentityUser(AuthSessionState state, String noonUser) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("channelIdentifier", noonUser);
        body.put("client_code", NOON_WEB_CLIENT_CODE);
        JsonNode root = state.postJson(null, null, identityUserLookupUrl, body, false, null);
        return extractPartnerIdentityUser(root);
    }

    private PkcePair createPkcePair(AuthSessionState state) {
        String codeVerifier = generateCodeVerifier();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("code_challenge", generateCodeChallenge(codeVerifier));
        body.put("client_code", NOON_WEB_CLIENT_CODE);
        JsonNode root = state.postJson(null, null, identityPkceUrl, body, false, null);
        if (root == null || !root.path("success").asBoolean(false)) {
            throw new IllegalStateException("Noon PKCE 初始化失败：" + text(root, "err"));
        }
        String pkceKey = text(root, "pkce_key");
        if (!StringUtils.hasText(pkceKey)) {
            throw new IllegalStateException("Noon PKCE 初始化失败：缺少 pkce_key。");
        }
        return new PkcePair(codeVerifier, pkceKey);
    }

    private String validatePartnerIdentityPassword(
            AuthSessionState state,
            String userCode,
            String noonUser,
            String noonPassword,
            PkcePair pkce
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("channel_code", "password");
        body.put("client_code", NOON_WEB_CLIENT_CODE);
        body.put("user_code", userCode);
        body.put("channel_identifier", noonUser);
        body.put("channel_credential", noonPassword);
        body.put("code_verifier", pkce.getCodeVerifier());
        body.put("pkce_key", pkce.getPkceKey());
        JsonNode root = state.postJson(null, null, identityValidateUrl, body, false, null);
        if (root == null || !root.path("success").asBoolean(false)) {
            String error = root != null && root.path("err").isArray() && root.path("err").size() > 0
                    ? root.path("err").get(0).asText()
                    : text(root, "err");
            throw new IllegalStateException("Noon password validate 失败：" + error);
        }
        String accessToken = text(root, "access_token");
        if (!StringUtils.hasText(accessToken)) {
            throw new IllegalStateException("Noon password validate 失败：缺少 access_token。");
        }
        return accessToken;
    }

    private String resolvePartnerIdentityProjectCode(
            AuthSessionState state,
            String userCode,
            String accessToken,
            String requestedProjectCode
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("userCode", userCode);
        body.put("accessToken", accessToken);
        JsonNode root = state.postJson(null, null, identityProjectListUrl, body, false, null);
        return selectPartnerIdentityProjectCode(root, requestedProjectCode);
    }

    private void createPartnerIdentitySession(
            AuthSessionState state,
            String userCode,
            String accessToken,
            String projectCode,
            PkcePair pkce
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("userCode", userCode);
        body.put("accessToken", accessToken);
        body.put("pkce_key", pkce.getPkceKey());
        body.put("projectCode", projectCode);
        body.put("clientCode", NOON_WEB_CLIENT_CODE);
        body.put("code_verifier", pkce.getCodeVerifier());
        state.postJson(projectCode, null, identitySessionCreateUrl, body, false, null);
        if (!StringUtils.hasText(state.exportAuthCookieHeader())) {
            throw new IllegalStateException("Noon session/create 未返回有效 Cookie。");
        }
    }

    private AuthSessionState createChromeFallbackState(
            String noonUser,
            String noonPassword,
            String projectCode,
            String storeCode,
            IllegalStateException exception
    ) {
        if (!chromeCookieFallbackEnabled || !isRateLimitedMessage(exception.getMessage())) {
            return null;
        }
        try {
            CookieManager cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
            HttpClient httpClient = newHttpClient(cookieManager);
            AuthSessionState state = new AuthSessionState(
                    objectMapper,
                    noonUser,
                    noonPassword,
                    httpClient,
                    cookieManager,
                    accountMinRequestIntervalMillis,
                    requestUserAgent,
                    acceptLanguage,
                    localeHeader,
                    langHeader,
                    this::recordRequest,
                    this::recordHttpCall
            );
            Map<String, String> cookies = ChromeNoonCookieSupport.loadAuthCookies();
            for (Map.Entry<String, String> entry : cookies.entrySet()) {
                if (StringUtils.hasText(entry.getValue())) {
                    state.addCookie(entry.getKey(), entry.getValue());
                }
            }
            if (StringUtils.hasText(projectCode)) {
                state.addCookie("projectCode", projectCode);
            }
            if (StringUtils.hasText(storeCode)) {
                state.addCookie("noonStore", storeCode);
            }
            return state;
        } catch (Exception ignored) {
            return null;
        }
    }

    static PartnerIdentityUser extractPartnerIdentityUser(JsonNode root) {
        if (root == null || !root.isArray() || root.size() == 0) {
            throw new IllegalStateException("Noon 账号不存在或 lookup 响应为空。");
        }
        JsonNode user = root.get(0);
        String userCode = firstText(user, "userCode", "user_code");
        if (!StringUtils.hasText(userCode)) {
            throw new IllegalStateException("Noon lookup 响应缺少 userCode。");
        }
        JsonNode channels = user.path("channels");
        boolean passwordEnabled = false;
        if (channels.isArray()) {
            for (JsonNode channel : channels) {
                String channelCode = firstText(channel, "channelCode", "channel_code");
                if ("password".equalsIgnoreCase(channelCode)) {
                    passwordEnabled = true;
                    break;
                }
            }
        }
        if (!passwordEnabled) {
            throw new IllegalStateException("请先在 Noon 后台为该账号启用密码登录。");
        }
        return new PartnerIdentityUser(userCode);
    }

    static String selectPartnerIdentityProjectCode(JsonNode root, String requestedProjectCode) {
        JsonNode projects = root == null ? MissingNode.getInstance() : root.path("projects");
        if (!projects.isArray() || projects.size() == 0) {
            throw new IllegalStateException("Noon 账号没有可用 Project。");
        }

        String normalizedRequested = normalize(requestedProjectCode);
        if (StringUtils.hasText(normalizedRequested)) {
            for (JsonNode project : projects) {
                String projectCode = firstText(project, "projectCode", "project_code");
                if (normalizedRequested.equalsIgnoreCase(projectCode)) {
                    return projectCode;
                }
            }
            throw new IllegalStateException("Noon 账号不包含当前项目：" + normalizedRequested);
        }

        String firstProjectCode = firstText(projects.get(0), "projectCode", "project_code");
        if (!StringUtils.hasText(firstProjectCode)) {
            throw new IllegalStateException("Noon project/list 响应缺少 projectCode。");
        }
        return firstProjectCode;
    }

    static String generateCodeVerifier() {
        byte[] randomBytes = new byte[96];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    static String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (Exception exception) {
            throw new IllegalStateException("生成 Noon PKCE challenge 失败：" + exception.getMessage(), exception);
        }
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static String normalizeUser(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        String normalized = normalize(value);
        return StringUtils.hasText(normalized) ? normalized : defaultValue;
    }

    private static String normalizeCookie(String value) {
        String normalized = normalize(value);
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private static String text(JsonNode node, String fieldName) {
        if (node == null || !StringUtils.hasText(fieldName)) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return normalize(text);
    }

    private static String firstText(JsonNode node, String... fieldNames) {
        if (fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            String value = text(node, fieldName);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean isRateLimitedMessage(String message) {
        return StringUtils.hasText(message)
                && (message.contains("HTTP 429")
                || message.contains("HTTP 418")
                || message.toLowerCase(Locale.ROOT).contains("too many requests")
                || message.toLowerCase(Locale.ROOT).contains("ip_channel")
                || message.toLowerCase(Locale.ROOT).contains("teapot"));
    }

    private static String shrinkBody(String body) {
        if (!StringUtils.hasText(body)) {
            return "empty response";
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        return normalized.length() > 220 ? normalized.substring(0, 220) + "..." : normalized;
    }

    void persistCookie(Long ownerUserId, String projectCode, String cookieHeader) {
        if (ownerUserId == null || !StringUtils.hasText(projectCode) || !StringUtils.hasText(cookieHeader)) {
            return;
        }
        storeSyncMapper.updateProjectSessionCookie(ownerUserId, projectCode, cookieHeader, ownerUserId);
    }

    private HttpClient newHttpClient(CookieManager cookieManager) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .cookieHandler(cookieManager);
        if (forceHttp11) {
            builder.version(Version.HTTP_1_1);
        }
        Proxy proxy = resolveProxy();
        if (proxy != null) {
            builder.proxy(new FixedProxySelector(proxy));
        }
        return builder.build();
    }

    private Proxy resolveProxy() {
        if (!proxyEnabled) {
            return null;
        }
        Proxy.Type resolvedProxyType = "SOCKS".equalsIgnoreCase(proxyType) ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
        if (StringUtils.hasText(proxyProviderUrl)) {
            return loadProxyFromProvider(resolvedProxyType);
        }
        if (StringUtils.hasText(proxyHost) && proxyPort > 0) {
            return new Proxy(resolvedProxyType, new InetSocketAddress(proxyHost, proxyPort));
        }
        throw new IllegalStateException("Noon 代理已启用但未配置 provider-url 或 host/port；请检查生产 .env 是否被正确加载。");
    }

    private Proxy loadProxyFromProvider(Proxy.Type proxyType) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .version(Version.HTTP_1_1)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(proxyProviderUrl))
                    .GET()
                    .timeout(REQUEST_TIMEOUT)
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP " + response.statusCode() + " " + shrinkBody(response.body()));
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode firstProxy = root.path("data").isArray() && root.path("data").size() > 0
                    ? root.path("data").get(0)
                    : root;
            String host = firstText(firstProxy, "ip", "host");
            String portText = firstText(firstProxy, "port");
            if (!StringUtils.hasText(host) || !StringUtils.hasText(portText)) {
                throw new IllegalStateException("代理供应商响应缺少 ip/port。");
            }
            int port = Integer.parseInt(portText);
            return new Proxy(proxyType, new InetSocketAddress(host, port));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("加载 Noon 代理被中断：" + exception.getMessage(), exception);
        } catch (Exception exception) {
            throw new IllegalStateException("加载 Noon 代理失败：" + exception.getMessage(), exception);
        }
    }

    public final class NoonSession {
        private final String noonUser;
        private final String noonPassword;
        private volatile AuthSessionState state;
        private final String projectCode;
        private final String storeCode;

        private NoonSession(
                String noonUser,
                String noonPassword,
                AuthSessionState state,
                String projectCode,
                String storeCode
        ) {
            this.noonUser = noonUser;
            this.noonPassword = noonPassword;
            this.state = state;
            this.projectCode = projectCode;
            this.storeCode = storeCode;
        }

        public NoonSession withProjectCode(String nextProjectCode) {
            return new NoonSession(noonUser, noonPassword, state, normalize(nextProjectCode), storeCode);
        }

        public NoonSession withStoreCode(String nextStoreCode) {
            return new NoonSession(noonUser, noonPassword, state, projectCode, normalize(nextStoreCode));
        }

        public String getProjectCode() {
            return projectCode;
        }

        public JsonNode getJson(String url, boolean withProject) {
            return executeWithRefresh(() -> state.getJson(projectCode, storeCode, url, withProject, null));
        }

        public JsonNode getJson(String url, boolean withProject, Map<String, String> extraHeaders) {
            return executeWithRefresh(() -> state.getJson(projectCode, storeCode, url, withProject, extraHeaders));
        }

        public String getText(String url, boolean withProject, Map<String, String> extraHeaders) {
            return executeTextWithRefresh(() -> state.getText(projectCode, storeCode, url, withProject, extraHeaders));
        }

        public JsonNode postJson(String url, JsonNode body, boolean withProject) {
            return postJson(url, body, withProject, null);
        }

        public JsonNode postJson(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
            return executeWithRefresh(() -> state.postJson(projectCode, storeCode, url, body, withProject, extraHeaders));
        }

        public JsonNode postWriteJson(String url, JsonNode body, boolean withProject) {
            return postWriteJson(url, body, withProject, null);
        }

        public JsonNode postWriteJson(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
            return executeWithRefresh(
                    () -> state.postJson(
                            projectCode,
                            storeCode,
                            url,
                            body,
                            withProject,
                            extraHeaders,
                            false
                    )
            );
        }

        public String exportAuthCookieHeader() {
            return state.exportAuthCookieHeader();
        }

        private JsonNode executeWithRefresh(SessionCall sessionCall) {
            boolean authRefreshed = false;
            boolean transportRefreshed = false;
            while (true) {
                try {
                    return sessionCall.execute();
                } catch (SessionExpiredException exception) {
                    if (authRefreshed) {
                        throw exception;
                    }
                    state = getOrCreateState(null, noonUser, noonPassword, null, projectCode, storeCode, true);
                    authRefreshed = true;
                } catch (IllegalStateException exception) {
                    if (!transportRefreshed && shouldRefreshAfterTransientTransportFailure(exception)) {
                        state = getOrCreateState(
                                null,
                                noonUser,
                                noonPassword,
                                state.exportAuthCookieHeader(),
                                projectCode,
                                storeCode,
                                true
                        );
                        transportRefreshed = true;
                        continue;
                    }
                    throw exception;
                }
            }
        }

        private String executeTextWithRefresh(TextSessionCall sessionCall) {
            boolean authRefreshed = false;
            boolean transportRefreshed = false;
            while (true) {
                try {
                    return sessionCall.execute();
                } catch (SessionExpiredException exception) {
                    if (authRefreshed) {
                        throw exception;
                    }
                    state = getOrCreateState(null, noonUser, noonPassword, null, projectCode, storeCode, true);
                    authRefreshed = true;
                } catch (IllegalStateException exception) {
                    if (!transportRefreshed && shouldRefreshAfterTransientTransportFailure(exception)) {
                        state = getOrCreateState(
                                null,
                                noonUser,
                                noonPassword,
                                state.exportAuthCookieHeader(),
                                projectCode,
                                storeCode,
                                true
                        );
                        transportRefreshed = true;
                        continue;
                    }
                    throw exception;
                }
            }
        }
    }

    private boolean shouldRefreshAfterTransientTransportFailure(IllegalStateException exception) {
        if (!proxyEnabled || exception == null) {
            return false;
        }
        String message = throwableMessage(exception).toLowerCase(Locale.ROOT);
        return message.contains("http/1.1 header parser received no bytes")
                || message.contains("connection reset")
                || message.contains("connection refused")
                || message.contains("connect timed out")
                || message.contains("request timed out")
                || message.contains("read timed out")
                || message.contains("unexpected end")
                || message.contains("closed")
                || message.contains("tunnel failed")
                || message.contains("http 407")
                || message.contains("http 408")
                || message.contains("http 502")
                || message.contains("http 503")
                || message.contains("http 504");
    }

    private static String throwableMessage(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())) {
                if (builder.length() > 0) {
                    builder.append(" | ");
                }
                builder.append(current.getMessage());
            }
            current = current.getCause();
        }
        return builder.toString();
    }

    public final class RequestCountScope implements AutoCloseable {
        private final LinkedHashMap<String, Integer> previous;
        private final LinkedHashMap<String, Integer> current;

        private RequestCountScope(LinkedHashMap<String, Integer> previous, LinkedHashMap<String, Integer> current) {
            this.previous = previous;
            this.current = current;
        }

        public Map<String, Integer> snapshot() {
            return current == null ? Map.of() : new LinkedHashMap<>(current);
        }

        @Override
        public void close() {
            if (previous == null) {
                requestCountScope.remove();
            } else {
                requestCountScope.set(previous);
            }
        }
    }

    static final class PartnerIdentityUser {
        private final String userCode;

        private PartnerIdentityUser(String userCode) {
            this.userCode = userCode;
        }

        String getUserCode() {
            return userCode;
        }
    }

    private static final class PkcePair {
        private final String codeVerifier;
        private final String pkceKey;

        private PkcePair(String codeVerifier, String pkceKey) {
            this.codeVerifier = codeVerifier;
            this.pkceKey = pkceKey;
        }

        private String getCodeVerifier() {
            return codeVerifier;
        }

        private String getPkceKey() {
            return pkceKey;
        }
    }

    private interface SessionCall {
        JsonNode execute();
    }

    private interface TextSessionCall {
        String execute();
    }

    private interface HttpCallRecorder {
        void record(
                HttpRequest request,
                Integer responseStatusCode,
                String responseBody,
                Long elapsedMs,
                String status,
                String failureType,
                String errorMessage
        );
    }

    private static final class AuthSessionState {
        private final ObjectMapper objectMapper;
        private final String noonPassword;
        private final HttpClient httpClient;
        private final CookieManager cookieManager;
        private final long minRequestIntervalMillis;
        private final String userAgent;
        private final String acceptLanguage;
        private final String localeHeader;
        private final String langHeader;
        private final Consumer<String> requestRecorder;
        private final HttpCallRecorder httpCallRecorder;
        private final Object requestMutex = new Object();
        private final Instant createdAt = Instant.now();
        private volatile long lastRequestAtMillis = 0L;

        private AuthSessionState(
                ObjectMapper objectMapper,
                String noonUser,
                String noonPassword,
                HttpClient httpClient,
                CookieManager cookieManager,
                long minRequestIntervalMillis,
                String userAgent,
                String acceptLanguage,
                String localeHeader,
                String langHeader,
                Consumer<String> requestRecorder,
                HttpCallRecorder httpCallRecorder
        ) {
            this.objectMapper = objectMapper;
            this.noonPassword = noonPassword;
            this.httpClient = httpClient;
            this.cookieManager = cookieManager;
            this.minRequestIntervalMillis = minRequestIntervalMillis;
            this.userAgent = userAgent;
            this.acceptLanguage = acceptLanguage;
            this.localeHeader = localeHeader;
            this.langHeader = langHeader;
            this.requestRecorder = requestRecorder;
            this.httpCallRecorder = httpCallRecorder;
            addCookie("projectUser", noonUser);
        }

        private boolean matchesPassword(String value) {
            return noonPassword.equals(value);
        }

        private boolean isExpired() {
            return createdAt.plus(SESSION_TTL).isBefore(Instant.now());
        }

        private JsonNode getJson(
                String projectCode,
                String storeCode,
                String url,
                boolean withProject,
                Map<String, String> extraHeaders
        ) {
            synchronized (requestMutex) {
                applyContextCookies(projectCode, storeCode);
                throttleIfNeeded();
                URI uri = buildUri(url, withProject, projectCode);
                HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                        .GET()
                        .timeout(REQUEST_TIMEOUT)
                        .setHeader("Accept", "application/json");
                if (withProject && StringUtils.hasText(projectCode)) {
                    builder.setHeader("X-Project", projectCode);
                }
                applyDefaultHeaders(builder, uri);
                applyHeaders(builder, extraHeaders);
                return send(builder.build(), true);
            }
        }

        private String getText(
                String projectCode,
                String storeCode,
                String url,
                boolean withProject,
                Map<String, String> extraHeaders
        ) {
            synchronized (requestMutex) {
                applyContextCookies(projectCode, storeCode);
                throttleIfNeeded();
                URI uri = buildUri(url, withProject, projectCode);
                HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                        .GET()
                        .timeout(REQUEST_TIMEOUT)
                        .setHeader("Accept", "text/csv,text/plain,*/*");
                if (withProject && StringUtils.hasText(projectCode)) {
                    builder.setHeader("X-Project", projectCode);
                }
                applyDefaultHeaders(builder, uri);
                applyHeaders(builder, extraHeaders);
                return sendText(builder.build(), true);
            }
        }

        private JsonNode postJson(
                String projectCode,
                String storeCode,
                String url,
                JsonNode body,
                boolean withProject,
                Map<String, String> extraHeaders
        ) {
            return postJson(projectCode, storeCode, url, body, withProject, extraHeaders, true);
        }

        private JsonNode postJson(
                String projectCode,
                String storeCode,
                String url,
                JsonNode body,
                boolean withProject,
                Map<String, String> extraHeaders,
                boolean retryTransientReadFailures
        ) {
            synchronized (requestMutex) {
                try {
                    applyContextCookies(projectCode, storeCode);
                    throttleIfNeeded();
                    URI uri = buildUri(url, withProject, projectCode);
                    HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                            .timeout(REQUEST_TIMEOUT)
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Accept", "application/json");
                    if (withProject && StringUtils.hasText(projectCode)) {
                        builder.setHeader("X-Project", projectCode);
                    }
                    applyDefaultHeaders(builder, uri);
                    applyHeaders(builder, extraHeaders);
                    return send(builder.build(), retryTransientReadFailures);
                } catch (IOException exception) {
                    throw new IllegalStateException("序列化 Noon 请求失败：" + exception.getMessage(), exception);
                }
            }
        }

        private void applyHeaders(HttpRequest.Builder builder, Map<String, String> extraHeaders) {
            if (extraHeaders == null) {
                return;
            }
            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                if (StringUtils.hasText(entry.getKey()) && StringUtils.hasText(entry.getValue())) {
                    builder.setHeader(entry.getKey(), entry.getValue());
                }
            }
        }

        private void applyDefaultHeaders(HttpRequest.Builder builder, URI uri) {
            if (StringUtils.hasText(userAgent)) {
                builder.setHeader("User-Agent", userAgent);
            }
            if (StringUtils.hasText(acceptLanguage)) {
                builder.setHeader("Accept-Language", acceptLanguage);
            }
            if (StringUtils.hasText(localeHeader)) {
                builder.setHeader("X-Locale", localeHeader);
            }
            if (StringUtils.hasText(langHeader)) {
                builder.setHeader("X-Lang", langHeader);
            }
            builder.setHeader("X-Platform", "web");
            String origin = buildOrigin(uri);
            if (StringUtils.hasText(origin)) {
                builder.setHeader("Origin", origin);
                builder.setHeader("Referer", origin + "/");
            }
        }

        private void throttleIfNeeded() {
            if (minRequestIntervalMillis <= 0L) {
                return;
            }
            long now = System.currentTimeMillis();
            long waitMillis = minRequestIntervalMillis - (now - lastRequestAtMillis);
            if (waitMillis <= 0L) {
                return;
            }
            try {
                Thread.sleep(waitMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("请求 Noon 前被中断：" + exception.getMessage(), exception);
            }
        }

        private JsonNode send(HttpRequest request, boolean retryTransientReadFailures) {
            int attempt = 0;
            while (true) {
                long startedNanos = System.nanoTime();
                try {
                    if (requestRecorder != null) {
                        requestRecorder.accept(request.uri().toString());
                    }
                    HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                    lastRequestAtMillis = System.currentTimeMillis();
                    String responseBody = decodeResponseBody(response);
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        attempt++;
                        if (shouldRetryRateLimit(response.statusCode(), responseBody, attempt)) {
                            sleepForRateLimit(attempt);
                            continue;
                        }
                        if (isAuthExpiredResponse(response.statusCode(), responseBody)) {
                            recordAttempt(
                                    request,
                                    response.statusCode(),
                                    responseBody,
                                    startedNanos,
                                    "FAILED",
                                    "AUTH_EXPIRED",
                                    "HTTP " + response.statusCode() + " " + shrinkBody(responseBody)
                            );
                            throw new SessionExpiredException(
                                    "HTTP " + response.statusCode() + " " + shrinkBody(responseBody)
                            );
                        }
                        if (shouldRetryTransientResponse(retryTransientReadFailures, response.statusCode(), attempt)) {
                            sleepForTransientFailure(attempt);
                            continue;
                        }
                        recordAttempt(
                                request,
                                response.statusCode(),
                                responseBody,
                                startedNanos,
                                "FAILED",
                                "HTTP_STATUS",
                                "HTTP " + response.statusCode() + " " + shrinkBody(responseBody)
                        );
                        throw new IllegalStateException(
                                "HTTP " + response.statusCode() + " " + shrinkBody(responseBody)
                        );
                    }
                    recordAttempt(request, response.statusCode(), responseBody, startedNanos, "SUCCESS", null, null);
                    if (!StringUtils.hasText(responseBody)) {
                        return MissingNode.getInstance();
                    }
                    return objectMapper.readTree(responseBody);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    recordAttempt(request, null, null, startedNanos, "FAILED", "INTERRUPTED", throwableMessage(exception));
                    throw new IllegalStateException("请求 Noon 失败：" + throwableMessage(exception), exception);
                } catch (IOException exception) {
                    attempt++;
                    if (shouldRetryTransientException(retryTransientReadFailures, attempt)) {
                        try {
                            sleepForTransientFailure(attempt);
                            continue;
                        } catch (InterruptedException interruptedException) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(
                                    "请求 Noon 失败：" + interruptedException.getMessage(),
                                    interruptedException
                            );
                        }
                    }
                    recordAttempt(request, null, null, startedNanos, "FAILED", "IO_EXCEPTION", throwableMessage(exception));
                    throw new IllegalStateException("请求 Noon 失败：" + throwableMessage(exception), exception);
                }
            }
        }

        private String sendText(HttpRequest request, boolean retryTransientReadFailures) {
            int attempt = 0;
            while (true) {
                long startedNanos = System.nanoTime();
                try {
                    if (requestRecorder != null) {
                        requestRecorder.accept(request.uri().toString());
                    }
                    HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                    lastRequestAtMillis = System.currentTimeMillis();
                    String responseBody = decodeResponseBody(response);
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        attempt++;
                        if (shouldRetryRateLimit(response.statusCode(), responseBody, attempt)) {
                            sleepForRateLimit(attempt);
                            continue;
                        }
                        if (isAuthExpiredResponse(response.statusCode(), responseBody)) {
                            recordAttempt(
                                    request,
                                    response.statusCode(),
                                    responseBody,
                                    startedNanos,
                                    "FAILED",
                                    "AUTH_EXPIRED",
                                    "HTTP " + response.statusCode() + " " + shrinkBody(responseBody)
                            );
                            throw new SessionExpiredException(
                                    "HTTP " + response.statusCode() + " " + shrinkBody(responseBody)
                            );
                        }
                        if (shouldRetryTransientResponse(retryTransientReadFailures, response.statusCode(), attempt)) {
                            sleepForTransientFailure(attempt);
                            continue;
                        }
                        recordAttempt(
                                request,
                                response.statusCode(),
                                responseBody,
                                startedNanos,
                                "FAILED",
                                "HTTP_STATUS",
                                "HTTP " + response.statusCode() + " " + shrinkBody(responseBody)
                        );
                        throw new IllegalStateException(
                                "HTTP " + response.statusCode() + " " + shrinkBody(responseBody)
                        );
                    }
                    recordAttempt(request, response.statusCode(), responseBody, startedNanos, "SUCCESS", null, null);
                    return responseBody;
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    recordAttempt(request, null, null, startedNanos, "FAILED", "INTERRUPTED", throwableMessage(exception));
                    throw new IllegalStateException("请求 Noon 失败：" + throwableMessage(exception), exception);
                } catch (IOException exception) {
                    attempt++;
                    if (shouldRetryTransientException(retryTransientReadFailures, attempt)) {
                        try {
                            sleepForTransientFailure(attempt);
                            continue;
                        } catch (InterruptedException interruptedException) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(
                                    "请求 Noon 失败：" + interruptedException.getMessage(),
                                    interruptedException
                            );
                        }
                    }
                    recordAttempt(request, null, null, startedNanos, "FAILED", "IO_EXCEPTION", throwableMessage(exception));
                    throw new IllegalStateException("请求 Noon 失败：" + throwableMessage(exception), exception);
                }
            }
        }

        private void recordAttempt(
                HttpRequest request,
                Integer responseStatusCode,
                String responseBody,
                long startedNanos,
                String status,
                String failureType,
                String errorMessage
        ) {
            if (httpCallRecorder == null) {
                return;
            }
            long elapsedMs = Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
            httpCallRecorder.record(
                    request,
                    responseStatusCode,
                    responseBody,
                    elapsedMs,
                    status,
                    failureType,
                    errorMessage
            );
        }

        private boolean shouldRetryRateLimit(int statusCode, String responseBody, int attempt) {
            return attempt <= MAX_RATE_LIMIT_RETRIES && isRateLimitedResponse(statusCode, responseBody);
        }

        private void sleepForRateLimit(int attempt) throws InterruptedException {
            long delay = Math.min(
                    INITIAL_RATE_LIMIT_DELAY_MILLIS * (1L << Math.max(attempt - 1, 0)),
                    12000L
            );
            delay += ThreadLocalRandom.current().nextLong(200L, 801L);
            Thread.sleep(delay);
        }

        private boolean shouldRetryTransientResponse(boolean retryTransientReadFailures, int statusCode, int attempt) {
            return retryTransientReadFailures
                    && attempt <= MAX_TRANSIENT_READ_RETRIES
                    && isTransientResponseStatus(statusCode);
        }

        private boolean shouldRetryTransientException(boolean retryTransientReadFailures, int attempt) {
            return retryTransientReadFailures && attempt <= MAX_TRANSIENT_READ_RETRIES;
        }

        private boolean isTransientResponseStatus(int statusCode) {
            return statusCode == 408
                    || statusCode == 500
                    || statusCode == 502
                    || statusCode == 503
                    || statusCode == 504;
        }

        private void sleepForTransientFailure(int attempt) throws InterruptedException {
            long delay = Math.min(
                    INITIAL_TRANSIENT_RETRY_DELAY_MILLIS * (1L << Math.max(attempt - 1, 0)),
                    4000L
            );
            delay += ThreadLocalRandom.current().nextLong(100L, 501L);
            Thread.sleep(delay);
        }

        private boolean isAuthExpiredResponse(int statusCode, String responseBody) {
            if (statusCode == 401 || statusCode == 403) {
                return true;
            }
            if (!StringUtils.hasText(responseBody)) {
                return false;
            }
            String normalized = responseBody.toLowerCase(Locale.ROOT);
            return normalized.contains("unauthorized")
                    || normalized.contains("invalid session")
                    || normalized.contains("signin");
        }

        private boolean isRateLimitedResponse(int statusCode, String responseBody) {
            if (statusCode == 429 || statusCode == 418) {
                return true;
            }
            if (!StringUtils.hasText(responseBody)) {
                return false;
            }
            String normalized = responseBody.toLowerCase(Locale.ROOT);
            return normalized.contains("too many requests")
                    || normalized.contains("ip_channel")
                    || normalized.contains("teapot");
        }

        private String decodeResponseBody(HttpResponse<byte[]> response) throws IOException {
            byte[] body = response.body();
            if (body == null || body.length == 0) {
                return "";
            }

            String contentEncoding = response.headers().firstValue("content-encoding").orElse("");
            boolean gzipEncoded = contentEncoding.toLowerCase(Locale.ROOT).contains("gzip")
                    || looksLikeGzip(body);
            if (!gzipEncoded) {
                return new String(body, StandardCharsets.UTF_8);
            }

            try (InputStream inputStream = new GZIPInputStream(new ByteArrayInputStream(body))) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        private boolean looksLikeGzip(byte[] body) {
            return body.length >= 2
                    && (body[0] & 0xFF) == 0x1F
                    && (body[1] & 0xFF) == 0x8B;
        }

        private URI buildUri(String url, boolean withProject, String projectCode) {
            if (!withProject || !StringUtils.hasText(projectCode)) {
                return URI.create(url);
            }
            String separator = url.contains("?") ? "&" : "?";
            return URI.create(url + separator + "project=" + projectCode);
        }

        private void applyContextCookies(String projectCode, String storeCode) {
            if (StringUtils.hasText(projectCode)) {
                addCookie("projectCode", projectCode);
            }
            if (StringUtils.hasText(storeCode)) {
                addCookie("noonStore", storeCode);
            }
        }

        private void addCookie(String name, String value) {
            if (!StringUtils.hasText(name) || !StringUtils.hasText(value)) {
                return;
            }
            HttpCookie cookie = new HttpCookie(name, value);
            cookie.setDomain(".noon.partners");
            cookie.setPath("/");
            cookie.setVersion(0);
            cookieManager.getCookieStore().add(URI.create("https://noon-catalog.noon.partners"), cookie);
            cookieManager.getCookieStore().add(URI.create("https://noon-store.noon.partners"), cookie);
            cookieManager.getCookieStore().add(URI.create("https://toolbar.noon.partners"), cookie);
            cookieManager.getCookieStore().add(URI.create("https://login.noon.partners"), cookie);
            cookieManager.getCookieStore().add(URI.create("https://login-alt.noon.partners"), cookie);
        }

        private void importCookieHeader(String cookieHeader) {
            if (!StringUtils.hasText(cookieHeader)) {
                return;
            }
            String[] segments = cookieHeader.split(";");
            for (String rawSegment : segments) {
                String segment = rawSegment == null ? "" : rawSegment.trim();
                if (!StringUtils.hasText(segment) || !segment.contains("=")) {
                    continue;
                }
                int splitIndex = segment.indexOf('=');
                String name = segment.substring(0, splitIndex).trim();
                String value = segment.substring(splitIndex + 1).trim();
                if (!StringUtils.hasText(name) || !StringUtils.hasText(value)) {
                    continue;
                }
                addCookie(name, value);
            }
        }

        private String exportAuthCookieHeader() {
            StringJoiner joiner = new StringJoiner("; ");
            for (HttpCookie cookie : cookieManager.getCookieStore().getCookies()) {
                String name = cookie.getName();
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                if ("projectCode".equalsIgnoreCase(name)
                        || "noonStore".equalsIgnoreCase(name)
                        || "projectUser".equalsIgnoreCase(name)) {
                    continue;
                }
                if (!StringUtils.hasText(cookie.getValue())) {
                    continue;
                }
                joiner.add(name + "=" + cookie.getValue());
            }
            String exported = joiner.toString();
            return StringUtils.hasText(exported) ? exported : null;
        }
    }

    private static final class SessionExpiredException extends IllegalStateException {
        private SessionExpiredException(String message) {
            super(message);
        }
    }

    private static final class FixedProxySelector extends ProxySelector {
        private final Proxy proxy;

        private FixedProxySelector(Proxy.Type proxyType, String host, int port) {
            this.proxy = new Proxy(proxyType, new InetSocketAddress(host, port));
        }

        private FixedProxySelector(Proxy proxy) {
            this.proxy = proxy;
        }

        @Override
        public java.util.List<Proxy> select(URI uri) {
            return java.util.Collections.singletonList(proxy);
        }

        @Override
        public void connectFailed(URI uri, java.net.SocketAddress sa, IOException ioe) {
            // keep the selector simple; failures are handled at the request layer
        }
    }

    private static String buildOrigin(URI uri) {
        if (uri == null || !StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
            return null;
        }
        return uri.getScheme() + "://" + uri.getHost();
    }

}
