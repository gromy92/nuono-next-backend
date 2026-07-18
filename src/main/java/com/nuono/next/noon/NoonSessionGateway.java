package com.nuono.next.noon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noonlog.NoonHttpCallLogService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
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
    private static final String DEFAULT_IDENTITY_GENERATE_URL =
            "https://login-alt.noon.partners/_svc/mp-partner-identity/public/user/credential/generate";
    private static final String DEFAULT_IDENTITY_VALIDATE_URL =
            "https://login-alt.noon.partners/_svc/mp-partner-identity/public/user/validate";
    private static final String DEFAULT_IDENTITY_PROJECT_LIST_URL =
            "https://login-alt.noon.partners/_svc/mp-partner-identity/public/user/project/list";
    private static final String DEFAULT_IDENTITY_SESSION_CREATE_URL =
            "https://login-alt.noon.partners/_svc/mp-partner-identity/public/user/session/create";
    private static final String DEFAULT_CATALOG_CAPABILITY_PROBE_URL =
            NoonCatalogApiRoutes.OFFER_LIST_NOON;
    private static final String DEFAULT_CATALOG_SESSION_BOOTSTRAP_URL =
            "https://noon-catalog.noon.partners/en/catalog?tab=noon";
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
    private final String identityGenerateUrl;
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
    private NoonEmailOtpReader emailOtpReader;
    @Value("${nuono.noon.auth.email-otp.email:}")
    private String configuredMerchantEmail;
    @Value("${nuono.noon.auth.email-otp.mail-auth-code:}")
    private String configuredMerchantMailAuthCode;
    @Value("${nuono.noon.auth.email-otp.legacy-direct-enabled:false}")
    private boolean legacyDirectEmailOtpEnabled;
    @Value("${nuono.noon.auth.catalog-capability-probe-url:}")
    private String catalogCapabilityProbeUrl = DEFAULT_CATALOG_CAPABILITY_PROBE_URL;
    @Value("${nuono.noon.auth.catalog-session-bootstrap-url:}")
    private String catalogSessionBootstrapUrl = DEFAULT_CATALOG_SESSION_BOOTSTRAP_URL;

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
            @Value("${nuono.noon.urls.identity-generate:}") String identityGenerateUrl,
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
        this.identityGenerateUrl = defaultIfBlank(identityGenerateUrl, DEFAULT_IDENTITY_GENERATE_URL);
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

    @Autowired(required = false)
    public void setEmailOtpReader(NoonEmailOtpReader emailOtpReader) {
        this.emailOtpReader = emailOtpReader;
    }

    void setConfiguredMerchantEmailOtpCredential(String email, String mailAuthCode) {
        this.configuredMerchantEmail = email;
        this.configuredMerchantMailAuthCode = mailAuthCode;
    }

    void setLegacyDirectEmailOtpEnabled(boolean enabled) {
        this.legacyDirectEmailOtpEnabled = enabled;
    }

    void setCatalogCapabilityProbeUrl(String url) {
        this.catalogCapabilityProbeUrl = defaultIfBlank(url, DEFAULT_CATALOG_CAPABILITY_PROBE_URL);
    }

    void setCatalogSessionBootstrapUrl(String url) {
        this.catalogSessionBootstrapUrl = defaultIfBlank(url, DEFAULT_CATALOG_SESSION_BOOTSTRAP_URL);
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
        return new NoonSession(ownerUserId, normalizedUser, noonPassword, state, normalize(projectCode), normalize(storeCode));
    }

    public NoonSession loginWithPersistedCookie(
            Long ownerUserId,
            String noonUser,
            String persistedCookie,
            String projectCode,
            String storeCode
    ) {
        String normalizedProjectCode = normalize(projectCode);
        if (!StringUtils.hasText(normalizedProjectCode)) {
            throw cookieAuthRequired(normalizedProjectCode, normalize(storeCode), "missing_project");
        }
        String normalizedCookie = normalizeCookie(persistedCookie);
        if (!StringUtils.hasText(normalizedCookie)) {
            throw cookieAuthRequired(normalizedProjectCode, normalize(storeCode), "missing_cookie");
        }
        String normalizedUser = normalizeUser(noonUser);
        AuthSessionState state = getOrCreateCookieOnlyState(
                ownerUserId,
                normalizedUser,
                normalizedCookie,
                normalizedProjectCode,
                normalize(storeCode),
                false
        );
        return new NoonSession(
                ownerUserId,
                normalizedUser,
                cookieFingerprint(normalizedCookie),
                state,
                normalizedProjectCode,
                normalize(storeCode),
                SessionRefreshMode.COOKIE_ONLY
        );
    }

    NoonSession loginWithEmailAuthCode(
            Long ownerUserId,
            String noonEmail,
            String mailAuthCode,
            String persistedCookie,
            String projectCode,
            String storeCode
    ) {
        requireLegacyDirectEmailOtpEnabled();
        String normalizedEmail = normalizeUser(noonEmail);
        if (!StringUtils.hasText(normalizedEmail)) {
            throw new IllegalArgumentException("缺少 Noon 商家后台登录邮箱。");
        }
        String normalizedMailAuthCode = normalize(mailAuthCode);
        if (!StringUtils.hasText(normalizedMailAuthCode)) {
            throw new IllegalArgumentException("缺少邮箱授权码。");
        }
        String normalizedProjectCode = normalize(projectCode);
        if (!StringUtils.hasText(normalizedProjectCode)) {
            throw new IllegalArgumentException("缺少 Noon Project，无法使用邮箱登录。");
        }
        AuthSessionState state = getOrCreateEmailOtpState(
                ownerUserId,
                normalizedEmail,
                normalizedMailAuthCode,
                normalizeCookie(persistedCookie),
                normalizedProjectCode,
                normalize(storeCode),
                false
        );
        return new NoonSession(
                ownerUserId,
                normalizedEmail,
                normalizedMailAuthCode,
                state,
                normalizedProjectCode,
                normalize(storeCode),
                true
        );
    }

    NoonSession loginWithConfiguredEmailAuthCode(
            Long ownerUserId,
            String persistedCookie,
            String projectCode,
            String storeCode
    ) {
        return loginWithEmailAuthCode(
                ownerUserId,
                configuredMerchantEmail(),
                configuredMerchantMailAuthCode(),
                persistedCookie,
                projectCode,
                storeCode
        );
    }

    public MerchantAuthorization authorizeMerchantLogin(
            Long ownerUserId,
            String noonUser,
            String noonPassword,
            String requestedProjectCode,
            String storeCode
    ) {
        String normalizedUser = normalizeUser(noonUser);
        if (!StringUtils.hasText(normalizedUser)) {
            throw new IllegalArgumentException("缺少 Noon 登录账号。");
        }
        if (!StringUtils.hasText(noonPassword)) {
            throw new IllegalArgumentException("缺少 Noon 登录密码。");
        }

        String normalizedProjectCode = normalize(requestedProjectCode);
        String normalizedStoreCode = normalize(storeCode);
        if (partnerIdentityLoginEnabled) {
            return authorizePartnerIdentityMerchantLogin(
                    ownerUserId,
                    normalizedUser,
                    noonPassword,
                    normalizedProjectCode,
                    normalizedStoreCode
            );
        }

        if (!StringUtils.hasText(normalizedProjectCode)) {
            throw new IllegalStateException("Noon login-alt 未启用时无法读取 Project 列表，请先选择 Project。");
        }
        NoonSession session = login(
                ownerUserId,
                normalizedUser,
                noonPassword,
                null,
                normalizedProjectCode,
                normalizedStoreCode
        );
        return MerchantAuthorization.authorized(
                new MerchantProject(normalizedProjectCode, normalizedProjectCode, null, null),
                session.exportAuthCookieHeader()
        );
    }

    MerchantAuthorization authorizeMerchantEmailLogin(
            Long ownerUserId,
            String noonEmail,
            String mailAuthCode,
            String requestedProjectCode,
            String storeCode
    ) {
        requireLegacyDirectEmailOtpEnabled();
        String normalizedEmail = normalizeUser(noonEmail);
        if (!StringUtils.hasText(normalizedEmail)) {
            throw new IllegalArgumentException("缺少 Noon 商家后台登录邮箱。");
        }
        String normalizedMailAuthCode = normalize(mailAuthCode);
        if (!StringUtils.hasText(normalizedMailAuthCode)) {
            throw new IllegalArgumentException("缺少邮箱授权码。");
        }
        if (!partnerIdentityLoginEnabled) {
            throw new IllegalStateException("Noon 邮箱登录需要启用 login-alt 身份链路。");
        }
        return authorizePartnerIdentityEmailOtpLogin(
                ownerUserId,
                normalizedEmail,
                normalizedMailAuthCode,
                normalize(requestedProjectCode),
                normalize(storeCode)
        );
    }

    MerchantAuthorization authorizeConfiguredMerchantEmailLogin(
            Long ownerUserId,
            String requestedProjectCode,
            String storeCode
    ) {
        return authorizeMerchantEmailLogin(
                ownerUserId,
                configuredMerchantEmail(),
                configuredMerchantMailAuthCode(),
                requestedProjectCode,
                storeCode
        );
    }

    boolean hasConfiguredMerchantEmailLogin() {
        return StringUtils.hasText(normalizeUser(configuredMerchantEmail))
                && StringUtils.hasText(normalize(configuredMerchantMailAuthCode));
    }

    public String configuredMerchantEmail() {
        String normalizedEmail = normalizeUser(configuredMerchantEmail);
        if (!StringUtils.hasText(normalizedEmail) || !StringUtils.hasText(normalize(configuredMerchantMailAuthCode))) {
            throw new IllegalStateException("未配置统一 Noon 商家后台邮箱和邮箱授权码。");
        }
        return normalizedEmail;
    }

    String configuredMerchantMailAuthCode() {
        String normalizedMailAuthCode = normalize(configuredMerchantMailAuthCode);
        if (!StringUtils.hasText(normalizeUser(configuredMerchantEmail)) || !StringUtils.hasText(normalizedMailAuthCode)) {
            throw new IllegalStateException("未配置统一 Noon 商家后台邮箱和邮箱授权码。");
        }
        return normalizedMailAuthCode;
    }

    private void requireLegacyDirectEmailOtpEnabled() {
        if (!legacyDirectEmailOtpEnabled) {
            throw new IllegalStateException("Noon 邮箱验证码直连已关闭。");
        }
    }

    EmailOtpGeneration prepareEmailOtpGeneration(String noonEmail) {
        String normalizedEmail = normalizeUser(noonEmail);
        if (!StringUtils.hasText(normalizedEmail)) {
            throw new IllegalArgumentException("缺少 Noon 商家后台登录邮箱。");
        }
        if (!partnerIdentityLoginEnabled) {
            throw new IllegalStateException("Noon 邮箱登录需要启用 login-alt 身份链路。");
        }
        AuthSessionState state = newSessionState(normalizedEmail, "");
        PartnerIdentityUser user = lookupPartnerIdentityEmailOtpUser(state, normalizedEmail);
        PkcePair pkce = createPkcePair(state);
        return new EmailOtpGeneration(normalizedEmail, state, user, pkce);
    }

    void sendEmailOtp(EmailOtpGeneration generation) {
        if (generation == null) {
            throw new IllegalArgumentException("缺少 Noon 邮箱验证码代次。");
        }
        generatePartnerIdentityEmailOtp(
                generation.state,
                generation.user.getUserCode(),
                generation.pkce
        );
    }

    EmailIdentityGrant validateEmailOtp(EmailOtpGeneration generation, String otpCode) {
        if (generation == null) {
            throw new IllegalArgumentException("缺少 Noon 邮箱验证码代次。");
        }
        String normalizedOtpCode = normalize(otpCode);
        if (!StringUtils.hasText(normalizedOtpCode)) {
            throw new IllegalArgumentException("缺少 Noon 邮箱验证码。");
        }
        String accessToken = validatePartnerIdentityEmailOtp(
                generation.state,
                generation.user.getUserCode(),
                generation.email,
                normalizedOtpCode,
                generation.pkce
        );
        List<MerchantProject> projects = listPartnerIdentityProjects(
                generation.state,
                generation.user.getUserCode(),
                accessToken
        );
        return new EmailIdentityGrant(generation, accessToken, projects);
    }

    ProjectSessionCookie createEmailOtpProjectSession(
            EmailIdentityGrant grant,
            String requestedProjectCode,
            String storeCode
    ) {
        if (grant == null) {
            throw new IllegalArgumentException("缺少 Noon 邮箱身份授权。");
        }
        MerchantProject selectedProject = selectMerchantProject(grant.projects, requestedProjectCode);
        EmailOtpGeneration generation = grant.generation;
        createPartnerIdentitySession(
                generation.state,
                generation.user.getUserCode(),
                grant.accessToken,
                selectedProject.getProjectCode(),
                generation.pkce
        );
        generation.state.applyContextCookies(selectedProject.getProjectCode(), normalize(storeCode));
        String cookie = generation.state.exportAuthCookieHeader();
        if (!StringUtils.hasText(cookie)) {
            throw new IllegalStateException("Noon session/create 未返回有效 Cookie。");
        }
        return new ProjectSessionCookie(
                selectedProject,
                appendProjectContextCookie(cookie, selectedProject.getProjectCode()),
                generation.state
        );
    }

    private static String appendProjectContextCookie(String cookie, String projectCode) {
        HttpCookie projectContext = new HttpCookie("projectCode", projectCode);
        projectContext.setVersion(0);
        return cookie + "; " + projectContext;
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

    public void validateCatalogSessionWithCookie(
            String persistedCookie,
            String projectCode,
            String storeCode,
            String noonUser
    ) {
        String normalizedProjectCode = normalize(projectCode);
        String normalizedStoreCode = normalize(storeCode);
        String normalizedCookie = normalizeCookie(persistedCookie);
        if (!StringUtils.hasText(normalizedProjectCode)) {
            throw cookieAuthRequired(normalizedProjectCode, normalizedStoreCode, "missing_project");
        }
        if (!StringUtils.hasText(normalizedCookie)) {
            throw cookieAuthRequired(normalizedProjectCode, normalizedStoreCode, "missing_cookie");
        }
        AuthSessionState state = createCookieOnlyState(
                normalizeUser(noonUser),
                cookieFingerprint(normalizedCookie),
                normalizedCookie,
                normalizedProjectCode,
                normalizedStoreCode
        );
        try {
            probeCatalogCapability(state, normalizedProjectCode, normalizedStoreCode);
        } catch (SessionExpiredException exception) {
            throw cookieAuthRequired(
                    normalizedProjectCode,
                    normalizedStoreCode,
                    safeCookieFailureReason(exception),
                    exception
            );
        }
    }

    JsonNode whoamiWithProjectSession(ProjectSessionCookie projectSession, String storeCode) {
        if (projectSession == null || projectSession.state == null || projectSession.project == null) {
            throw new IllegalArgumentException("缺少 Noon Project 会话。");
        }
        return projectSession.state.getJson(
                projectSession.project.getProjectCode(),
                normalize(storeCode),
                whoamiUrl,
                false,
                null
        );
    }

    void validateCatalogProjectSession(ProjectSessionCookie projectSession, String storeCode) {
        if (projectSession == null || projectSession.state == null || projectSession.project == null) {
            throw new IllegalArgumentException("缺少 Noon Project 会话。");
        }
        bootstrapCatalogProjectSession(
                projectSession.state,
                projectSession.project.getProjectCode(),
                normalize(storeCode)
        );
        probeCatalogCapability(
                projectSession.state,
                projectSession.project.getProjectCode(),
                normalize(storeCode)
        );
    }

    private void bootstrapCatalogProjectSession(
            AuthSessionState state,
            String projectCode,
            String storeCode
    ) {
        String bootstrapUrl = defaultIfBlank(
                catalogSessionBootstrapUrl,
                DEFAULT_CATALOG_SESSION_BOOTSTRAP_URL
        );
        state.handoffAuthCookiesTo(bootstrapUrl);
        state.getText(
                projectCode,
                storeCode,
                bootstrapUrl,
                true,
                catalogLocaleHeaders(storeCode)
        );
    }

    private JsonNode probeCatalogCapability(
            AuthSessionState state,
            String projectCode,
            String storeCode
    ) {
        String probeUrl = defaultIfBlank(
                catalogCapabilityProbeUrl,
                DEFAULT_CATALOG_CAPABILITY_PROBE_URL
        );
        state.handoffAuthCookiesTo(probeUrl);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("page", 1);
        body.put("per_page", 1);
        body.put("noon_store_code", storeCode);
        body.put("noonChannelType", "noon");
        return state.postJson(
                projectCode,
                storeCode,
                probeUrl,
                body,
                true,
                catalogLocaleHeaders(storeCode)
        );
    }

    private Map<String, String> catalogLocaleHeaders(String storeCode) {
        String normalizedStore = normalize(storeCode);
        String upperStore = normalizedStore == null ? "" : normalizedStore.toUpperCase(Locale.ROOT);
        String site;
        if (upperStore.endsWith("-NSA") || upperStore.endsWith("-SAU")) {
            site = "sa";
        } else if (upperStore.endsWith("-NEG")) {
            site = "eg";
        } else {
            site = "ae";
        }
        return Map.of("X-Locale", "en-" + site, "X-Lang", "en");
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
            boolean sensitiveAuthCall = isSensitiveAuthRequest(request);
            service.record(
                    request,
                    responseStatusCode,
                    sensitiveAuthCall && StringUtils.hasText(responseBody)
                            ? "{\"redacted\":true,\"category\":\"auth_response\"}"
                            : responseBody,
                    elapsedMs,
                    status,
                    failureType,
                    sensitiveAuthCall && StringUtils.hasText(errorMessage)
                            ? "Noon auth call failed; response redacted"
                            : errorMessage
            );
        } catch (Exception ignored) {
            // Noon calls must not fail because the audit log table is unavailable.
        }
    }

    private boolean isSensitiveAuthRequest(HttpRequest request) {
        if (request == null || request.uri() == null) {
            return false;
        }
        String url = request.uri().toString();
        return sameEndpoint(url, identityUserLookupUrl)
                || sameEndpoint(url, identityPkceUrl)
                || sameEndpoint(url, identityGenerateUrl)
                || sameEndpoint(url, identityValidateUrl)
                || sameEndpoint(url, identityProjectListUrl)
                || sameEndpoint(url, identitySessionCreateUrl)
                || sameEndpoint(url, catalogSessionBootstrapUrl);
    }

    private boolean sameEndpoint(String actualUrl, String configuredUrl) {
        return StringUtils.hasText(actualUrl)
                && StringUtils.hasText(configuredUrl)
                && (actualUrl.equals(configuredUrl)
                    || actualUrl.startsWith(configuredUrl + "?")
                    || actualUrl.startsWith(configuredUrl + "&"));
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
        if (url.contains("/public/user/credential/generate")) {
            return "auth.identity.generate";
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

    private AuthSessionState getOrCreateEmailOtpState(
            Long ownerUserId,
            String noonEmail,
            String mailAuthCode,
            String persistedCookie,
            String projectCode,
            String storeCode,
            boolean forceRefresh
    ) {
        String cacheKey = "emailotp:" + noonEmail + ":" + normalize(projectCode);
        synchronized (accountLocks.computeIfAbsent(cacheKey, key -> new Object())) {
            AuthSessionState existing = sessionCache.get(cacheKey);
            if (!forceRefresh && existing != null && !existing.isExpired() && existing.matchesPassword(mailAuthCode)) {
                return existing;
            }

            AuthSessionState created = createEmailOtpAuthenticatedState(
                    ownerUserId,
                    noonEmail,
                    mailAuthCode,
                    persistedCookie,
                    projectCode,
                    storeCode
            );
            sessionCache.put(cacheKey, created);
            return created;
        }
    }

    private AuthSessionState getOrCreateCookieOnlyState(
            Long ownerUserId,
            String noonUser,
            String persistedCookie,
            String projectCode,
            String storeCode,
            boolean forceRefresh
    ) {
        String cacheKey = "cookie:" + ownerUserId + ":" + normalize(projectCode);
        String cookieFingerprint = cookieFingerprint(persistedCookie);
        synchronized (accountLocks.computeIfAbsent(cacheKey, key -> new Object())) {
            AuthSessionState existing = sessionCache.get(cacheKey);
            if (!forceRefresh
                    && existing != null
                    && !existing.isExpired()
                    && existing.matchesPassword(cookieFingerprint)) {
                return existing;
            }

            AuthSessionState created = createCookieOnlyState(
                    noonUser,
                    cookieFingerprint,
                    persistedCookie,
                    projectCode,
                    storeCode
            );
            sessionCache.put(cacheKey, created);
            return created;
        }
    }

    private AuthSessionState createCookieOnlyState(
            String noonUser,
            String cookieFingerprint,
            String persistedCookie,
            String projectCode,
            String storeCode
    ) {
        if (!StringUtils.hasText(persistedCookie)) {
            throw cookieAuthRequired(projectCode, storeCode, "missing_cookie");
        }
        try {
            CookieManager cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
            HttpClient httpClient = newHttpClient(cookieManager);
            AuthSessionState state = new AuthSessionState(
                    objectMapper,
                    firstNonBlank(noonUser, "cookie-only"),
                    cookieFingerprint,
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
        } catch (SessionExpiredException exception) {
            throw cookieAuthRequired(projectCode, storeCode, safeCookieFailureReason(exception), exception);
        } catch (RuntimeException exception) {
            throw exception;
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

    private AuthSessionState createEmailOtpAuthenticatedState(
            Long ownerUserId,
            String noonEmail,
            String mailAuthCode,
            String persistedCookie,
            String projectCode,
            String storeCode
    ) {
        AuthSessionState cookieState = tryCreateStateFromPersistedCookie(
                noonEmail,
                mailAuthCode,
                persistedCookie,
                projectCode,
                storeCode
        );
        if (cookieState != null) {
            return cookieState;
        }
        if (!partnerIdentityLoginEnabled) {
            throw new IllegalStateException("Noon 邮箱登录需要启用 login-alt 身份链路。");
        }
        try {
            AuthSessionState state = createPartnerIdentityEmailOtpState(
                    noonEmail,
                    mailAuthCode,
                    projectCode,
                    storeCode
            );
            persistCookie(ownerUserId, projectCode, state.exportAuthCookieHeader());
            return state;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Noon 邮箱登录失败：" + exception.getMessage(), exception);
        }
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

    private AuthSessionState createPartnerIdentityEmailOtpState(
            String noonEmail,
            String mailAuthCode,
            String projectCode,
            String storeCode
    ) {
        AuthSessionState state = newSessionState(noonEmail, mailAuthCode);
        PartnerIdentityUser user = lookupPartnerIdentityEmailOtpUser(state, noonEmail);
        PkcePair pkce = createPkcePair(state);
        generatePartnerIdentityEmailOtp(state, user.getUserCode(), pkce);
        String otpCode = readEmailOtp(noonEmail, mailAuthCode);
        String accessToken = validatePartnerIdentityEmailOtp(
                state,
                user.getUserCode(),
                noonEmail,
                otpCode,
                pkce
        );
        List<MerchantProject> projects = listPartnerIdentityProjects(state, user.getUserCode(), accessToken);
        MerchantProject selectedProject = StringUtils.hasText(projectCode)
                ? selectMerchantProject(projects, projectCode)
                : projects.get(0);
        createPartnerIdentitySession(
                state,
                user.getUserCode(),
                accessToken,
                selectedProject.getProjectCode(),
                pkce
        );
        state.applyContextCookies(selectedProject.getProjectCode(), storeCode);
        return state;
    }

    private MerchantAuthorization authorizePartnerIdentityMerchantLogin(
            Long ownerUserId,
            String noonUser,
            String noonPassword,
            String requestedProjectCode,
            String storeCode
    ) {
        try {
            AuthSessionState state = newSessionState(noonUser, noonPassword);
            PartnerIdentityUser user = lookupPartnerIdentityUser(state, noonUser);
            PkcePair pkce = createPkcePair(state);
            String accessToken = validatePartnerIdentityPassword(state, user.getUserCode(), noonUser, noonPassword, pkce);
            List<MerchantProject> projects = listPartnerIdentityProjects(state, user.getUserCode(), accessToken);
            if (!StringUtils.hasText(requestedProjectCode) && projects.size() > 1) {
                return MerchantAuthorization.projectSelectionRequired(projects);
            }

            MerchantProject selectedProject = StringUtils.hasText(requestedProjectCode)
                    ? selectMerchantProject(projects, requestedProjectCode)
                    : projects.get(0);
            createPartnerIdentitySession(
                    state,
                    user.getUserCode(),
                    accessToken,
                    selectedProject.getProjectCode(),
                    pkce
            );
            state.applyContextCookies(selectedProject.getProjectCode(), storeCode);
            String cookie = state.exportAuthCookieHeader();
            persistCookie(ownerUserId, selectedProject.getProjectCode(), cookie);
            return MerchantAuthorization.authorized(selectedProject, cookie);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Noon login-alt 登录失败：" + exception.getMessage(), exception);
        }
    }

    private MerchantAuthorization authorizePartnerIdentityEmailOtpLogin(
            Long ownerUserId,
            String noonEmail,
            String mailAuthCode,
            String requestedProjectCode,
            String storeCode
    ) {
        try {
            AuthSessionState state = newSessionState(noonEmail, "");
            PartnerIdentityUser user = lookupPartnerIdentityEmailOtpUser(state, noonEmail);
            PkcePair pkce = createPkcePair(state);
            generatePartnerIdentityEmailOtp(state, user.getUserCode(), pkce);
            String otpCode = readEmailOtp(noonEmail, mailAuthCode);
            String accessToken = validatePartnerIdentityEmailOtp(
                    state,
                    user.getUserCode(),
                    noonEmail,
                    otpCode,
                    pkce
            );
            List<MerchantProject> projects = listPartnerIdentityProjects(state, user.getUserCode(), accessToken);
            if (!StringUtils.hasText(requestedProjectCode) && projects.size() > 1) {
                return MerchantAuthorization.projectSelectionRequired(projects);
            }

            MerchantProject selectedProject = StringUtils.hasText(requestedProjectCode)
                    ? selectMerchantProject(projects, requestedProjectCode)
                    : projects.get(0);
            createPartnerIdentitySession(
                    state,
                    user.getUserCode(),
                    accessToken,
                    selectedProject.getProjectCode(),
                    pkce
            );
            state.applyContextCookies(selectedProject.getProjectCode(), storeCode);
            String cookie = state.exportAuthCookieHeader();
            persistCookie(ownerUserId, selectedProject.getProjectCode(), cookie);
            return MerchantAuthorization.authorized(selectedProject, cookie);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Noon 邮箱登录失败：" + exception.getMessage(), exception);
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

    private static String cookieFingerprint(String cookie) {
        if (!StringUtils.hasText(cookie)) {
            return "missing";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    digest.digest(cookie.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算 Noon Cookie 指纹。", exception);
        }
    }

    private static String safeCookieFailureReason(Throwable throwable) {
        if (throwable == null || !StringUtils.hasText(throwable.getMessage())) {
            return "cookie_rejected";
        }
        String normalized = throwable.getMessage().replace('\r', ' ').replace('\n', ' ').trim();
        return normalized.length() <= 180 ? normalized : normalized.substring(0, 180);
    }

    private static NoonCookieAuthRequiredException cookieAuthRequired(
            String projectCode,
            String storeCode,
            String reason
    ) {
        return cookieAuthRequired(projectCode, storeCode, reason, null);
    }

    private static NoonCookieAuthRequiredException cookieAuthRequired(
            String projectCode,
            String storeCode,
            String reason,
            Throwable cause
    ) {
        String message = "auth_required: Noon Cookie 无效或已过期，请人工重新授权"
                + "; project=" + firstNonBlank(normalize(projectCode), "unknown")
                + "; store=" + firstNonBlank(normalize(storeCode), "unknown")
                + "; reason=" + firstNonBlank(reason, "cookie_rejected");
        return cause == null
                ? new NoonCookieAuthRequiredException(message)
                : new NoonCookieAuthRequiredException(message, cause);
    }

    private PartnerIdentityUser lookupPartnerIdentityUser(AuthSessionState state, String noonUser) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("channelIdentifier", noonUser);
        body.put("client_code", NOON_WEB_CLIENT_CODE);
        JsonNode root = state.postJson(null, null, identityUserLookupUrl, body, false, null);
        return extractPartnerIdentityUser(root);
    }

    private PartnerIdentityUser lookupPartnerIdentityEmailOtpUser(AuthSessionState state, String noonEmail) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("channelIdentifier", noonEmail);
        body.put("client_code", NOON_WEB_CLIENT_CODE);
        JsonNode root = state.postJson(null, null, identityUserLookupUrl, body, false, null);
        return extractPartnerIdentityEmailOtpUser(root);
    }

    private PkcePair createPkcePair(AuthSessionState state) {
        String codeVerifier = generateCodeVerifier();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("code_challenge", generateCodeChallenge(codeVerifier));
        body.put("client_code", NOON_WEB_CLIENT_CODE);
        JsonNode root = state.postJson(null, null, identityPkceUrl, body, false, null);
        if (root == null || !root.path("success").asBoolean(false)) {
            throw new IllegalStateException("Noon PKCE 初始化失败：" + partnerIdentityError(root));
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
        JsonNode root = state.postJson(null, null, identityValidateUrl, body, false, null, false);
        if (root == null || !root.path("success").asBoolean(false)) {
            throw new IllegalStateException("Noon password validate 失败：" + partnerIdentityError(root));
        }
        String accessToken = text(root, "access_token");
        if (!StringUtils.hasText(accessToken)) {
            throw new IllegalStateException("Noon password validate 失败：缺少 access_token。");
        }
        return accessToken;
    }

    private void generatePartnerIdentityEmailOtp(
            AuthSessionState state,
            String userCode,
            PkcePair pkce
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("channelCode", "emailotp");
        body.put("client_code", NOON_WEB_CLIENT_CODE);
        body.put("userCode", userCode);
        body.put("code_verifier", pkce.getCodeVerifier());
        body.put("pkce_key", pkce.getPkceKey());
        JsonNode root = state.postJson(null, null, identityGenerateUrl, body, false, null, false);
        if (root == null || !"ok".equalsIgnoreCase(root.path("emailotp").asText(null))) {
            throw new IllegalStateException("Noon emailotp 发送失败：" + partnerIdentityError(root));
        }
    }

    private String readEmailOtp(String noonEmail, String mailAuthCode) {
        NoonEmailOtpReader reader = this.emailOtpReader;
        if (reader == null) {
            throw new IllegalStateException("未配置邮箱 OTP 读取器，无法完成 Noon 邮箱登录。");
        }
        String otpCode = normalize(reader.readOtp(noonEmail, mailAuthCode));
        if (!StringUtils.hasText(otpCode)) {
            throw new IllegalStateException("未能从邮箱读取 Noon 验证码。");
        }
        return otpCode;
    }

    private String validatePartnerIdentityEmailOtp(
            AuthSessionState state,
            String userCode,
            String noonEmail,
            String otpCode,
            PkcePair pkce
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("channel_code", "emailotp");
        body.put("client_code", NOON_WEB_CLIENT_CODE);
        body.put("user_code", userCode);
        body.put("channel_identifier", noonEmail);
        body.put("channel_credential", otpCode);
        body.put("code_verifier", pkce.getCodeVerifier());
        body.put("pkce_key", pkce.getPkceKey());
        final JsonNode root;
        try {
            root = state.postJson(null, null, identityValidateUrl, body, false, null, false);
        } catch (SessionExpiredException exception) {
            // A 401/403 from identity validate rejects this OTP exchange; it does not describe an
            // already-created merchant session. Preserve structured HTTP facts for classification
            // while keeping the provider body out of the exception message.
            throw exception.toHttpException();
        }
        if (root == null || !root.path("success").asBoolean(false)) {
            throw new IllegalStateException("Noon emailotp validate 失败：" + partnerIdentityError(root));
        }
        String accessToken = text(root, "access_token");
        if (!StringUtils.hasText(accessToken)) {
            throw new IllegalStateException("Noon emailotp validate 失败：缺少 access_token。");
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

    private List<MerchantProject> listPartnerIdentityProjects(
            AuthSessionState state,
            String userCode,
            String accessToken
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("userCode", userCode);
        body.put("accessToken", accessToken);
        JsonNode root = state.postJson(null, null, identityProjectListUrl, body, false, null);
        return extractPartnerIdentityProjects(root);
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
        state.postJson(projectCode, null, identitySessionCreateUrl, body, false, null, false);
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
        return extractPartnerIdentityUser(root, "password", "请先在 Noon 后台为该账号启用密码登录。");
    }

    static PartnerIdentityUser extractPartnerIdentityEmailOtpUser(JsonNode root) {
        return extractPartnerIdentityUser(root, "emailotp", "该 Noon 商家后台账号未启用邮箱验证码登录。");
    }

    private static PartnerIdentityUser extractPartnerIdentityUser(
            JsonNode root,
            String requiredChannel,
            String missingChannelMessage
    ) {
        if (root == null || !root.isArray() || root.size() == 0) {
            throw new IllegalStateException("Noon 账号不存在或 lookup 响应为空。");
        }
        JsonNode user = root.get(0);
        String userCode = firstText(user, "userCode", "user_code");
        if (!StringUtils.hasText(userCode)) {
            throw new IllegalStateException("Noon lookup 响应缺少 userCode。");
        }
        JsonNode channels = user.path("channels");
        boolean channelEnabled = false;
        if (channels.isArray()) {
            for (JsonNode channel : channels) {
                String channelCode = firstText(channel, "channelCode", "channel_code");
                if (requiredChannel.equalsIgnoreCase(channelCode)) {
                    channelEnabled = true;
                    break;
                }
            }
        }
        if (!channelEnabled) {
            throw new IllegalStateException(missingChannelMessage);
        }
        return new PartnerIdentityUser(userCode);
    }

    static String selectPartnerIdentityProjectCode(JsonNode root, String requestedProjectCode) {
        List<MerchantProject> projects = extractPartnerIdentityProjects(root);
        String normalizedRequested = normalize(requestedProjectCode);
        if (StringUtils.hasText(normalizedRequested)) {
            return selectMerchantProject(projects, normalizedRequested).getProjectCode();
        }
        return projects.get(0).getProjectCode();
    }

    static List<MerchantProject> extractPartnerIdentityProjects(JsonNode root) {
        JsonNode projectsNode = root == null ? MissingNode.getInstance() : root.path("projects");
        if (!projectsNode.isArray() || projectsNode.size() == 0) {
            throw new IllegalStateException("Noon 账号没有可用 Project。");
        }
        List<MerchantProject> projects = new ArrayList<>();
        for (JsonNode project : projectsNode) {
            String projectCode = firstText(project, "projectCode", "project_code");
            if (!StringUtils.hasText(projectCode)) {
                throw new IllegalStateException("Noon project/list 响应缺少 projectCode。");
            }
            projects.add(new MerchantProject(
                    projectCode,
                    firstText(project, "projectName", "project_name"),
                    firstText(project, "orgCode", "org_code"),
                    firstText(project, "orgName", "org_name")
            ));
        }
        return projects;
    }

    private static MerchantProject selectMerchantProject(List<MerchantProject> projects, String requestedProjectCode) {
        String normalizedRequested = normalize(requestedProjectCode);
        for (MerchantProject project : projects) {
            if (StringUtils.hasText(project.getProjectCode())
                    && project.getProjectCode().equalsIgnoreCase(normalizedRequested)) {
                return project;
            }
        }
        throw new IllegalStateException("Noon 账号不包含当前项目：" + normalizedRequested);
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

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalize(value);
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }
        return null;
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

    private static String partnerIdentityError(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return "empty response";
        }
        JsonNode err = root.path("err");
        if (err.isArray() && err.size() > 0) {
            String value = normalize(err.get(0).asText(null));
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        String value = firstText(root, "err", "error", "message", "errorMessage", "error_message");
        return StringUtils.hasText(value) ? value : "provider response indicated failure";
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
        private final Long ownerUserId;
        private final String noonUser;
        private final String noonPassword;
        private volatile AuthSessionState state;
        private final String projectCode;
        private final String storeCode;
        private final SessionRefreshMode refreshMode;

        private NoonSession(
                Long ownerUserId,
                String noonUser,
                String noonPassword,
                AuthSessionState state,
                String projectCode,
                String storeCode
        ) {
            this(ownerUserId, noonUser, noonPassword, state, projectCode, storeCode, SessionRefreshMode.PASSWORD);
        }

        private NoonSession(
                Long ownerUserId,
                String noonUser,
                String noonPassword,
                AuthSessionState state,
                String projectCode,
                String storeCode,
                boolean emailOtpSession
        ) {
            this(
                    ownerUserId,
                    noonUser,
                    noonPassword,
                    state,
                    projectCode,
                    storeCode,
                    emailOtpSession ? SessionRefreshMode.EMAIL_OTP : SessionRefreshMode.PASSWORD
            );
        }

        private NoonSession(
                Long ownerUserId,
                String noonUser,
                String noonPassword,
                AuthSessionState state,
                String projectCode,
                String storeCode,
                SessionRefreshMode refreshMode
        ) {
            this.ownerUserId = ownerUserId;
            this.noonUser = noonUser;
            this.noonPassword = noonPassword;
            this.state = state;
            this.projectCode = projectCode;
            this.storeCode = storeCode;
            this.refreshMode = refreshMode;
        }

        public NoonSession withProjectCode(String nextProjectCode) {
            return new NoonSession(
                    ownerUserId,
                    noonUser,
                    noonPassword,
                    state,
                    normalize(nextProjectCode),
                    storeCode,
                    refreshMode
            );
        }

        public NoonSession withStoreCode(String nextStoreCode) {
            return new NoonSession(
                    ownerUserId,
                    noonUser,
                    noonPassword,
                    state,
                    projectCode,
                    normalize(nextStoreCode),
                    refreshMode
            );
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

        public String postText(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
            return executeTextWithRefresh(() -> state.postText(projectCode, storeCode, url, body, withProject, extraHeaders));
        }

        public byte[] postBytes(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
            return executeBytesWithRefresh(() -> state.postBytes(projectCode, storeCode, url, body, withProject, extraHeaders));
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
            return executeWriteWithAuthRefresh(
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

        public JsonNode postMultipartFile(
                String url,
                String fieldName,
                String fileName,
                String contentType,
                byte[] content,
                boolean withProject,
                Map<String, String> extraHeaders
        ) {
            return executeWriteWithAuthRefresh(
                    () -> state.postMultipartFile(
                            projectCode,
                            storeCode,
                            url,
                            fieldName,
                            fileName,
                            contentType,
                            content,
                            withProject,
                            extraHeaders
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
                    if (refreshMode == SessionRefreshMode.COOKIE_ONLY) {
                        throw cookieAuthRequired(projectCode, storeCode, safeCookieFailureReason(exception), exception);
                    }
                    if (authRefreshed) {
                        throw exception.toHttpException();
                    }
                    state = refreshAuthenticatedState(null, true);
                    authRefreshed = true;
                } catch (IllegalStateException exception) {
                    if (!transportRefreshed && shouldRefreshAfterTransientTransportFailure(exception)) {
                        state = refreshAuthenticatedState(state.exportAuthCookieHeader(), true);
                        transportRefreshed = true;
                        continue;
                    }
                    throw exception;
                }
            }
        }

        private JsonNode executeWriteWithAuthRefresh(SessionCall sessionCall) {
            boolean authRefreshed = false;
            while (true) {
                try {
                    return sessionCall.execute();
                } catch (SessionExpiredException exception) {
                    if (authRefreshed) {
                        throw exception.toHttpException();
                    }
                    state = refreshAuthenticatedState(null, true);
                    authRefreshed = true;
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
                    if (refreshMode == SessionRefreshMode.COOKIE_ONLY) {
                        throw cookieAuthRequired(projectCode, storeCode, safeCookieFailureReason(exception), exception);
                    }
                    if (authRefreshed) {
                        throw exception.toHttpException();
                    }
                    state = refreshAuthenticatedState(null, true);
                    authRefreshed = true;
                } catch (IllegalStateException exception) {
                    if (!transportRefreshed && shouldRefreshAfterTransientTransportFailure(exception)) {
                        state = refreshAuthenticatedState(state.exportAuthCookieHeader(), true);
                        transportRefreshed = true;
                        continue;
                    }
                    throw exception;
                }
            }
        }

        private byte[] executeBytesWithRefresh(BytesSessionCall sessionCall) {
            boolean authRefreshed = false;
            boolean transportRefreshed = false;
            while (true) {
                try {
                    return sessionCall.execute();
                } catch (SessionExpiredException exception) {
                    if (refreshMode == SessionRefreshMode.COOKIE_ONLY) {
                        throw cookieAuthRequired(projectCode, storeCode, safeCookieFailureReason(exception), exception);
                    }
                    if (authRefreshed) {
                        throw exception.toHttpException();
                    }
                    state = refreshAuthenticatedState(null, true);
                    authRefreshed = true;
                } catch (IllegalStateException exception) {
                    if (!transportRefreshed && shouldRefreshAfterTransientTransportFailure(exception)) {
                        state = refreshAuthenticatedState(state.exportAuthCookieHeader(), true);
                        transportRefreshed = true;
                        continue;
                    }
                    throw exception;
                }
            }
        }

        private AuthSessionState refreshAuthenticatedState(String persistedCookie, boolean forceRefresh) {
            if (refreshMode == SessionRefreshMode.COOKIE_ONLY) {
                return getOrCreateCookieOnlyState(
                        ownerUserId,
                        noonUser,
                        persistedCookie,
                        projectCode,
                        storeCode,
                        forceRefresh
                );
            }
            if (refreshMode == SessionRefreshMode.EMAIL_OTP) {
                return getOrCreateEmailOtpState(
                        ownerUserId,
                        noonUser,
                        noonPassword,
                        persistedCookie,
                        projectCode,
                        storeCode,
                        forceRefresh
                );
            }
            return getOrCreateState(
                    ownerUserId,
                    noonUser,
                    noonPassword,
                    persistedCookie,
                    projectCode,
                    storeCode,
                    forceRefresh
            );
        }
    }

    private enum SessionRefreshMode {
        PASSWORD,
        EMAIL_OTP,
        COOKIE_ONLY
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

    public static final class MerchantProject {
        private final String projectCode;
        private final String projectName;
        private final String orgCode;
        private final String orgName;

        public MerchantProject(String projectCode, String projectName, String orgCode, String orgName) {
            this.projectCode = projectCode;
            this.projectName = projectName;
            this.orgCode = orgCode;
            this.orgName = orgName;
        }

        public String getProjectCode() {
            return projectCode;
        }

        public String getProjectName() {
            return projectName;
        }

        public String getOrgCode() {
            return orgCode;
        }

        public String getOrgName() {
            return orgName;
        }
    }

    public static final class MerchantAuthorization {
        private final boolean success;
        private final MerchantProject selectedProject;
        private final String cookie;
        private final List<MerchantProject> projectList;

        private MerchantAuthorization(
                boolean success,
                MerchantProject selectedProject,
                String cookie,
                List<MerchantProject> projectList
        ) {
            this.success = success;
            this.selectedProject = selectedProject;
            this.cookie = cookie;
            this.projectList = projectList == null ? List.of() : List.copyOf(projectList);
        }

        public static MerchantAuthorization authorized(MerchantProject selectedProject, String cookie) {
            return new MerchantAuthorization(true, selectedProject, cookie, List.of());
        }

        public static MerchantAuthorization projectSelectionRequired(List<MerchantProject> projectList) {
            return new MerchantAuthorization(false, null, null, projectList);
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isProjectSelectionRequired() {
            return !success && !projectList.isEmpty();
        }

        public MerchantProject getSelectedProject() {
            return selectedProject;
        }

        public String getCookie() {
            return cookie;
        }

        public List<MerchantProject> getProjectList() {
            return projectList;
        }
    }

    static final class EmailOtpGeneration {
        private final String email;
        private final AuthSessionState state;
        private final PartnerIdentityUser user;
        private final PkcePair pkce;

        private EmailOtpGeneration(
                String email,
                AuthSessionState state,
                PartnerIdentityUser user,
                PkcePair pkce
        ) {
            this.email = email;
            this.state = state;
            this.user = user;
            this.pkce = pkce;
        }
    }

    static final class EmailIdentityGrant {
        private final EmailOtpGeneration generation;
        private final String accessToken;
        private final List<MerchantProject> projects;

        private EmailIdentityGrant(
                EmailOtpGeneration generation,
                String accessToken,
                List<MerchantProject> projects
        ) {
            this.generation = generation;
            this.accessToken = accessToken;
            this.projects = List.copyOf(projects);
        }
    }

    static final class ProjectSessionCookie {
        private final MerchantProject project;
        private final String cookie;
        private final AuthSessionState state;

        private ProjectSessionCookie(MerchantProject project, String cookie, AuthSessionState state) {
            this.project = project;
            this.cookie = cookie;
            this.state = state;
        }

        MerchantProject getProject() {
            return project;
        }

        String getCookie() {
            if (state == null || project == null) {
                return cookie;
            }
            String currentCookie = state.exportAuthCookieHeader();
            if (!StringUtils.hasText(currentCookie)) {
                return cookie;
            }
            return appendProjectContextCookie(currentCookie, project.getProjectCode());
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

    private interface BytesSessionCall {
        byte[] execute();
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

        private String postText(
                String projectCode,
                String storeCode,
                String url,
                JsonNode body,
                boolean withProject,
                Map<String, String> extraHeaders
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
                            .setHeader("Accept", "text/csv,text/plain,*/*");
                    if (withProject && StringUtils.hasText(projectCode)) {
                        builder.setHeader("X-Project", projectCode);
                    }
                    applyDefaultHeaders(builder, uri);
                    applyHeaders(builder, extraHeaders);
                    return sendText(builder.build(), true);
                } catch (IOException exception) {
                    throw new IllegalStateException("序列化 Noon 请求失败：" + exception.getMessage(), exception);
                }
            }
        }

        private byte[] postBytes(
                String projectCode,
                String storeCode,
                String url,
                JsonNode body,
                boolean withProject,
                Map<String, String> extraHeaders
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
                            .setHeader(
                                    "Accept",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/octet-stream,*/*"
                            );
                    if (withProject && StringUtils.hasText(projectCode)) {
                        builder.setHeader("X-Project", projectCode);
                    }
                    applyDefaultHeaders(builder, uri);
                    applyHeaders(builder, extraHeaders);
                    return sendBytes(builder.build(), true);
                } catch (IOException exception) {
                    throw new IllegalStateException("序列化 Noon 请求失败：" + exception.getMessage(), exception);
                }
            }
        }

        private JsonNode postMultipartFile(
                String projectCode,
                String storeCode,
                String url,
                String fieldName,
                String fileName,
                String contentType,
                byte[] content,
                boolean withProject,
                Map<String, String> extraHeaders
        ) {
            synchronized (requestMutex) {
                applyContextCookies(projectCode, storeCode);
                throttleIfNeeded();
                URI uri = buildUri(url, withProject, projectCode);
                String boundary = "nuono-" + Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 16);
                byte[] requestBody = multipartFileBody(boundary, fieldName, fileName, contentType, content);
                HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                        .timeout(REQUEST_TIMEOUT)
                        .setHeader("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .setHeader("Accept", "application/json");
                if (withProject && StringUtils.hasText(projectCode)) {
                    builder.setHeader("X-Project", projectCode);
                }
                applyDefaultHeaders(builder, uri);
                applyHeaders(builder, extraHeaders);
                return send(builder.build(), false);
            }
        }

        private byte[] multipartFileBody(
                String boundary,
                String fieldName,
                String fileName,
                String contentType,
                byte[] content
        ) {
            String safeFieldName = sanitizeMultipartToken(fieldName, "file");
            String safeFileName = sanitizeMultipartToken(fileName, "image");
            String safeContentType = StringUtils.hasText(contentType) ? contentType.trim() : "application/octet-stream";
            byte[] fileContent = content == null ? new byte[0] : content;
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                output.write(("Content-Disposition: form-data; name=\"" + safeFieldName + "\"; filename=\""
                        + safeFileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
                output.write(("Content-Type: " + safeContentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                output.write(fileContent);
                output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                return output.toByteArray();
            } catch (IOException exception) {
                throw new IllegalStateException("构造 Noon 文件上传请求失败：" + exception.getMessage(), exception);
            }
        }

        private String sanitizeMultipartToken(String value, String fallback) {
            String normalized = StringUtils.hasText(value) ? value.trim() : fallback;
            return normalized.replace('"', '_').replace('\r', '_').replace('\n', '_');
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
                        if (retryTransientReadFailures
                                && shouldRetryRateLimit(response.statusCode(), responseBody, attempt)) {
                            sleepForRateLimit(attempt);
                            continue;
                        }
                        if (isAuthExpiredResponse(
                                response.statusCode(),
                                responseBody,
                                request.uri().getPath(),
                                response.headers().firstValue("location").orElse(null)
                        )) {
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
                                    response.statusCode(),
                                    responseBody,
                                    request.uri().getPath()
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
                        throw new NoonHttpException(
                                response.statusCode(),
                                responseBody,
                                request.uri().getPath()
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
                        if (retryTransientReadFailures
                                && shouldRetryRateLimit(response.statusCode(), responseBody, attempt)) {
                            sleepForRateLimit(attempt);
                            continue;
                        }
                        if (isAuthExpiredResponse(
                                response.statusCode(),
                                responseBody,
                                request.uri().getPath(),
                                response.headers().firstValue("location").orElse(null)
                        )) {
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
                                    response.statusCode(),
                                    responseBody,
                                    request.uri().getPath()
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
                        throw new NoonHttpException(
                                response.statusCode(),
                                responseBody,
                                request.uri().getPath()
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

        private byte[] sendBytes(HttpRequest request, boolean retryTransientReadFailures) {
            int attempt = 0;
            while (true) {
                long startedNanos = System.nanoTime();
                try {
                    if (requestRecorder != null) {
                        requestRecorder.accept(request.uri().toString());
                    }
                    HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                    lastRequestAtMillis = System.currentTimeMillis();
                    byte[] responseBody = decodeResponseBytes(response);
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        String responseText = new String(responseBody, StandardCharsets.UTF_8);
                        attempt++;
                        if (retryTransientReadFailures
                                && shouldRetryRateLimit(response.statusCode(), responseText, attempt)) {
                            sleepForRateLimit(attempt);
                            continue;
                        }
                        if (isAuthExpiredResponse(
                                response.statusCode(),
                                responseText,
                                request.uri().getPath(),
                                response.headers().firstValue("location").orElse(null)
                        )) {
                            recordAttempt(
                                    request,
                                    response.statusCode(),
                                    responseText,
                                    startedNanos,
                                    "FAILED",
                                    "AUTH_EXPIRED",
                                    "HTTP " + response.statusCode() + " " + shrinkBody(responseText)
                            );
                            throw new SessionExpiredException(
                                    response.statusCode(),
                                    responseText,
                                    request.uri().getPath()
                            );
                        }
                        if (shouldRetryTransientResponse(retryTransientReadFailures, response.statusCode(), attempt)) {
                            sleepForTransientFailure(attempt);
                            continue;
                        }
                        recordAttempt(
                                request,
                                response.statusCode(),
                                responseText,
                                startedNanos,
                                "FAILED",
                                "HTTP_STATUS",
                                "HTTP " + response.statusCode() + " " + shrinkBody(responseText)
                        );
                        throw new NoonHttpException(
                                response.statusCode(),
                                responseText,
                                request.uri().getPath()
                        );
                    }
                    recordAttempt(
                            request,
                            response.statusCode(),
                            "binary response bytes=" + responseBody.length,
                            startedNanos,
                            "SUCCESS",
                            null,
                            null
                    );
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

        private boolean isAuthExpiredResponse(
                int statusCode,
                String responseBody,
                String requestPath,
                String redirectLocation
        ) {
            if (statusCode == 401 || statusCode == 403) {
                return true;
            }
            if (isRedirectStatus(statusCode) && isWhoamiPath(requestPath)) {
                return true;
            }
            if (isRedirectStatus(statusCode) && isNoonLoginRedirect(redirectLocation)) {
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

        private boolean isNoonLoginRedirect(String redirectLocation) {
            if (!StringUtils.hasText(redirectLocation)) {
                return false;
            }
            try {
                String host = URI.create(redirectLocation.trim()).getHost();
                return "login.noon.partners".equalsIgnoreCase(host)
                        || "login-alt.noon.partners".equalsIgnoreCase(host);
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }

        private boolean isRedirectStatus(int statusCode) {
            return statusCode == 301
                    || statusCode == 302
                    || statusCode == 303
                    || statusCode == 307
                    || statusCode == 308;
        }

        private boolean isWhoamiPath(String requestPath) {
            if (!StringUtils.hasText(requestPath)) {
                return false;
            }
            String normalized = requestPath.trim().toLowerCase(Locale.ROOT);
            return normalized.endsWith("/whoami") || normalized.contains("/auth-v1/whoami");
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
            byte[] body = decodeResponseBytes(response);
            if (body == null || body.length == 0) {
                return "";
            }

            return new String(body, StandardCharsets.UTF_8);
        }

        private byte[] decodeResponseBytes(HttpResponse<byte[]> response) throws IOException {
            byte[] body = response.body();
            if (body == null || body.length == 0) {
                return new byte[0];
            }
            String contentEncoding = response.headers().firstValue("content-encoding").orElse("");
            boolean gzipEncoded = contentEncoding.toLowerCase(Locale.ROOT).contains("gzip")
                    || looksLikeGzip(body);
            if (!gzipEncoded) {
                return body;
            }

            try (InputStream inputStream = new GZIPInputStream(new ByteArrayInputStream(body))) {
                return inputStream.readAllBytes();
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
            for (Map.Entry<String, String> cookie : parseCookieHeader(cookieHeader).entrySet()) {
                addCookie(cookie.getKey(), cookie.getValue());
            }
        }

        private void handoffAuthCookiesTo(String targetUrl) {
            URI targetUri = URI.create(targetUrl);
            String targetHost = targetUri.getHost();
            if (!StringUtils.hasText(targetHost)) {
                throw new IllegalArgumentException("Noon Catalog URL 缺少主机名。");
            }
            for (Map.Entry<String, String> cookie : parseCookieHeader(exportAuthCookieHeader()).entrySet()) {
                HttpCookie targetCookie = new HttpCookie(cookie.getKey(), cookie.getValue());
                targetCookie.setDomain(targetHost);
                targetCookie.setPath("/");
                targetCookie.setVersion(0);
                targetCookie.setSecure("https".equalsIgnoreCase(targetUri.getScheme()));
                cookieManager.getCookieStore().add(targetUri, targetCookie);
            }
        }

        private Map<String, String> parseCookieHeader(String cookieHeader) {
            Map<String, String> cookies = new LinkedHashMap<>();
            if (!StringUtils.hasText(cookieHeader)) {
                return cookies;
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
                cookies.put(name, value);
            }
            return cookies;
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
        private final int statusCode;
        private final String responseBody;
        private final String requestPath;

        private SessionExpiredException(int statusCode, String responseBody, String requestPath) {
            super("Noon session expired with HTTP " + statusCode);
            this.statusCode = statusCode;
            this.responseBody = responseBody;
            this.requestPath = requestPath;
        }

        private NoonHttpException toHttpException() {
            return new NoonHttpException(statusCode, responseBody, requestPath);
        }
    }

    public static final class NoonCookieAuthRequiredException extends IllegalStateException {
        private NoonCookieAuthRequiredException(String message) {
            super(message);
        }

        private NoonCookieAuthRequiredException(String message, Throwable cause) {
            super(message, cause);
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
