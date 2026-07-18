package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noonlog.NoonHttpCallLogService;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectTarget;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

class NoonSessionGatewayTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void legacyDirectEmailOtpEntrypointsAreNotPublicApi() throws Exception {
        List<Method> legacyEntrypoints = List.of(
                NoonSessionGateway.class.getDeclaredMethod(
                        "loginWithEmailAuthCode",
                        Long.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class
                ),
                NoonSessionGateway.class.getDeclaredMethod(
                        "loginWithConfiguredEmailAuthCode",
                        Long.class,
                        String.class,
                        String.class,
                        String.class
                ),
                NoonSessionGateway.class.getDeclaredMethod(
                        "authorizeMerchantEmailLogin",
                        Long.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class
                ),
                NoonSessionGateway.class.getDeclaredMethod(
                        "authorizeConfiguredMerchantEmailLogin",
                        Long.class,
                        String.class,
                        String.class
                )
        );

        assertTrue(legacyEntrypoints.stream().noneMatch(method -> Modifier.isPublic(method.getModifiers())));
    }

    @Test
    void shouldParsePartnerIdentityLookupPasswordChannel() throws Exception {
        JsonNode root = objectMapper.readTree(
                "[{\"user_code\":\"prd-user@idp.noon.partners\",\"channels\":[{\"channel_code\":\"password\"}]}]"
        );

        NoonSessionGateway.PartnerIdentityUser user = NoonSessionGateway.extractPartnerIdentityUser(root);

        assertEquals("prd-user@idp.noon.partners", user.getUserCode());
    }

    @Test
    void shouldRejectPartnerIdentityLookupWithoutPasswordChannel() throws Exception {
        JsonNode root = objectMapper.readTree(
                "[{\"userCode\":\"prd-user@idp.noon.partners\",\"channels\":[{\"channelCode\":\"emailotp\"}]}]"
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> NoonSessionGateway.extractPartnerIdentityUser(root)
        );
        assertTrue(exception.getMessage().contains("密码登录"));
    }

    @Test
    void shouldParsePartnerIdentityLookupEmailOtpChannel() throws Exception {
        JsonNode root = objectMapper.readTree(
                "[{\"userCode\":\"prd-user@idp.noon.partners\",\"channels\":[{\"channelCode\":\"emailotp\"}]}]"
        );

        NoonSessionGateway.PartnerIdentityUser user = NoonSessionGateway.extractPartnerIdentityEmailOtpUser(root);

        assertEquals("prd-user@idp.noon.partners", user.getUserCode());
    }

    @Test
    void shouldSelectRequestedProjectFromPartnerIdentityProjectList() throws Exception {
        JsonNode root = objectMapper.readTree(
                "{\"projects\":[{\"project_code\":\"PRJ108065\"},{\"project_code\":\"PRJ245027\"}]}"
        );

        assertEquals("PRJ245027", NoonSessionGateway.selectPartnerIdentityProjectCode(root, "prj245027"));
    }

    @Test
    void shouldRejectMissingRequestedProjectFromPartnerIdentityProjectList() throws Exception {
        JsonNode root = objectMapper.readTree(
                "{\"projects\":[{\"projectCode\":\"PRJ108065\"}]}"
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> NoonSessionGateway.selectPartnerIdentityProjectCode(root, "PRJ245027")
        );
        assertTrue(exception.getMessage().contains("PRJ245027"));
    }

    @Test
    void shouldGenerateSpecLengthPkceVerifierAndKnownChallenge() {
        String verifier = NoonSessionGateway.generateCodeVerifier();

        assertEquals(128, verifier.length());
        assertEquals(
                "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                NoonSessionGateway.generateCodeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        );
    }

    @Test
    void shouldRefreshProxySessionWhenCachedProxyDropsConnection() throws Exception {
        try (OneGoodThenDropProxy staleProxy = new OneGoodThenDropProxy();
                StaticHttpProxy refreshedProxy = new StaticHttpProxy();
                ProxyProviderServer provider = new ProxyProviderServer(staleProxy.port(), refreshedProxy.port())) {
            NoonSessionGateway gateway = gateway(provider.url());
            NoonSessionGateway.NoonSession session = gateway.login(
                    10001L,
                    "merchant@example.com",
                    "password",
                    "sid=existing",
                    "PRJ1",
                    "STORE1"
            );

            String body = session.getText("http://noon.test/report.csv", false, null);

            assertEquals("download-ok", body);
            assertTrue(staleProxy.requestCount() >= 2);
            assertTrue(refreshedProxy.awaitRequests(2));
        }
    }

    @Test
    void shouldRefreshProxySessionForTunnelFailureStatus() throws Exception {
        NoonSessionGateway gateway = gateway("http://127.0.0.1:1/proxy");
        Method method = NoonSessionGateway.class.getDeclaredMethod(
                "shouldRefreshAfterTransientTransportFailure",
                IllegalStateException.class
        );
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(gateway, new IllegalStateException("请求 Noon 失败：Tunnel failed, got: 435")));
        assertTrue((Boolean) method.invoke(gateway, new IllegalStateException("请求 Noon 失败：Tunnel failed, got: 436")));
    }

    @Test
    void shouldRejectNoonRequestWhenProxyEnabledWithoutEndpoint() {
        NoonSessionGateway gateway = gatewayWithSignin("");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> gateway.login(
                        10001L,
                        "merchant@example.com",
                        "password",
                        "sid=existing",
                        "PRJ1",
                        "STORE1"
                )
        );

        assertTrue(exception.getMessage().contains("Noon 代理已启用但未配置"));
    }

    @Test
    void shouldPersistSessionCookieForRequestedProjectOnly() {
        StoreSyncMapper mapper = mock(StoreSyncMapper.class);
        NoonSessionGateway gateway = gateway(mapper, "");

        gateway.persistCookie(308L, "PRJ100085", "sid=project-session");

        verify(mapper).updateProjectSessionCookie(308L, "PRJ100085", "sid=project-session", 308L);
    }

    @Test
    void shouldUseValidPersistedCookieWithoutEmailOtp() throws Exception {
        StoreSyncMapper mapper = mock(StoreSyncMapper.class);
        try (AuthRefreshServer server = new AuthRefreshServer()) {
            NoonSessionGateway gateway = identityGateway(mapper, server);
            gateway.setEmailOtpReader((email, mailAuthCode) -> {
                throw new AssertionError("cookie-only session must not read email OTP");
            });

            NoonSessionGateway.NoonSession session = gateway.loginWithPersistedCookie(
                    308L,
                    "merchant@example.com",
                    "sid=valid",
                    "PRJ313934",
                    "STR313934-NAE"
            );

            assertEquals("PRJ313934", session.getProjectCode());
            assertEquals(0, server.generateCount());
            verifyNoInteractions(mapper);
        }
    }

    @Test
    void shouldRejectInvalidPersistedCookieWithoutEmailOtp() throws Exception {
        StoreSyncMapper mapper = mock(StoreSyncMapper.class);
        try (AuthRefreshServer server = new AuthRefreshServer(true)) {
            NoonSessionGateway gateway = identityGateway(mapper, server);
            gateway.setEmailOtpReader((email, mailAuthCode) -> {
                throw new AssertionError("cookie-only session must not read email OTP");
            });

            NoonSessionGateway.NoonCookieAuthRequiredException exception = assertThrows(
                    NoonSessionGateway.NoonCookieAuthRequiredException.class,
                    () -> gateway.loginWithPersistedCookie(
                            308L,
                            "merchant@example.com",
                            "sid=expired",
                            "PRJ313934",
                            "STR313934-NAE"
                    )
            );

            assertTrue(exception.getMessage().contains("auth_required"));
            assertTrue(exception.getMessage().contains("PRJ313934"));
            assertEquals(0, server.generateCount());
            verifyNoInteractions(mapper);
        }
    }

    @Test
    void shouldClassifyWhoamiRedirectAsAuthRequired() throws Exception {
        StoreSyncMapper mapper = mock(StoreSyncMapper.class);
        try (AuthRefreshServer server = new AuthRefreshServer(307)) {
            NoonSessionGateway gateway = identityGateway(mapper, server);
            gateway.setEmailOtpReader((email, mailAuthCode) -> {
                throw new AssertionError("cookie-only session must not read email OTP");
            });

            NoonSessionGateway.NoonCookieAuthRequiredException exception = assertThrows(
                    NoonSessionGateway.NoonCookieAuthRequiredException.class,
                    () -> gateway.loginWithPersistedCookie(
                            308L,
                            "merchant@example.com",
                            "sid=expired",
                            "PRJ313934",
                            "STR313934-NAE"
                    )
            );

            assertTrue(exception.getMessage().contains("auth_required"));
            assertTrue(exception.getMessage().contains("307"));
            assertEquals(0, server.generateCount());
            verifyNoInteractions(mapper);
        }
    }

    @Test
    void shouldClassifyCatalogLoginRedirectAsAuthRequired() throws Exception {
        StoreSyncMapper mapper = mock(StoreSyncMapper.class);
        try (AuthRefreshServer server = new AuthRefreshServer()) {
            NoonSessionGateway gateway = identityGateway(mapper, server);
            NoonSessionGateway.NoonSession session = gateway.loginWithPersistedCookie(
                    308L,
                    "merchant@example.com",
                    "sid=valid",
                    "PRJ313934",
                    "STR313934-NAE"
            );

            NoonSessionGateway.NoonCookieAuthRequiredException exception = assertThrows(
                    NoonSessionGateway.NoonCookieAuthRequiredException.class,
                    () -> session.postJson(
                            server.url("/catalog-redirect"),
                            objectMapper.createObjectNode(),
                            true
                    )
            );

            assertTrue(exception.getMessage().contains("auth_required"));
            assertTrue(exception.getMessage().contains("307"));
            verifyNoInteractions(mapper);
        }
    }

    @Test
    void shouldPreserveRateLimitClassificationWhenCookieValidationIsThrottled() throws Exception {
        StoreSyncMapper mapper = mock(StoreSyncMapper.class);
        try (AuthRefreshServer server = new AuthRefreshServer(429)) {
            NoonSessionGateway gateway = identityGateway(mapper, server);
            gateway.setEmailOtpReader((email, mailAuthCode) -> {
                throw new AssertionError("cookie-only session must not read email OTP");
            });

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> gateway.loginWithPersistedCookie(
                            308L,
                            "merchant@example.com",
                            "sid=expired",
                            "PRJ313934",
                            "STR313934-NAE"
                    )
            );

            assertTrue(exception.getMessage().contains("429"));
            assertFalse(exception.getMessage().contains("auth_required"));
            assertEquals(0, server.generateCount());
            verifyNoInteractions(mapper);
        }
    }

    @Test
    void shouldNotRefreshEmailOtpWhenCookieOnlySessionExpiresDuringRequest() throws Exception {
        StoreSyncMapper mapper = mock(StoreSyncMapper.class);
        try (AuthRefreshServer server = new AuthRefreshServer()) {
            NoonSessionGateway gateway = identityGateway(mapper, server);
            gateway.setEmailOtpReader((email, mailAuthCode) -> {
                throw new AssertionError("cookie-only session must not read email OTP");
            });
            NoonSessionGateway.NoonSession session = gateway.loginWithPersistedCookie(
                    308L,
                    "merchant@example.com",
                    "sid=old",
                    "PRJ313934",
                    "STR313934-NAE"
            );

            NoonSessionGateway.NoonCookieAuthRequiredException exception = assertThrows(
                    NoonSessionGateway.NoonCookieAuthRequiredException.class,
                    () -> session.getJson(server.url("/protected"), false)
            );

            assertTrue(exception.getMessage().contains("auth_required"));
            assertEquals(0, server.generateCount());
            verifyNoInteractions(mapper);
        }
    }

    @Test
    void shouldPersistCookieWhenRefreshingExpiredRuntimeSession() throws Exception {
        StoreSyncMapper mapper = mock(StoreSyncMapper.class);
        try (AuthRefreshServer server = new AuthRefreshServer()) {
            NoonSessionGateway gateway = identityGateway(mapper, server);
            NoonSessionGateway.NoonSession session = gateway.login(
                    10001L,
                    "merchant@example.com",
                    "password",
                    "sid=old",
                    "PRJ1",
                    "STORE1"
            );

            JsonNode response = session.getJson(server.url("/protected"), false);

            assertTrue(response.path("ok").asBoolean(false));
            verify(mapper).updateProjectSessionCookie(
                    eq(10001L),
                    eq("PRJ1"),
                    argThat(cookie -> cookie != null && cookie.contains("sid=new")),
                    eq(10001L)
            );
        }
    }

    @Test
    void shouldReturnProjectChoicesWithoutCreatingSessionWhenMerchantLoginHasMultipleProjects() throws Exception {
        StoreSyncMapper mapper = mock(StoreSyncMapper.class);
        try (AuthRefreshServer server = new AuthRefreshServer(
                "{\"projects\":["
                        + "{\"projectCode\":\"PRJ7001\",\"projectName\":\"新店铺\",\"orgCode\":\"ORG7001\",\"orgName\":\"新组织\"},"
                        + "{\"projectCode\":\"PRJ8001\",\"projectName\":\"另一个店铺\",\"orgCode\":\"ORG8001\",\"orgName\":\"另一个组织\"}"
                        + "]}",
                "sid=new; Path=/"
        )) {
            NoonSessionGateway gateway = identityGateway(mapper, server);

            NoonSessionGateway.MerchantAuthorization result = gateway.authorizeMerchantLogin(
                    10001L,
                    "merchant@example.com",
                    "password",
                    null,
                    null
            );

            assertFalse(result.isSuccess());
            assertTrue(result.isProjectSelectionRequired());
            assertEquals(2, result.getProjectList().size());
            assertEquals("PRJ7001", result.getProjectList().get(0).getProjectCode());
            assertEquals("ORG7001", result.getProjectList().get(0).getOrgCode());
            assertEquals(0, server.sessionCreateCount());
            verifyNoInteractions(mapper);
        }
    }

    @Test
    void shouldCreateMerchantSessionForSelectedProjectAndPersistCookie() throws Exception {
        StoreSyncMapper mapper = mock(StoreSyncMapper.class);
        try (AuthRefreshServer server = new AuthRefreshServer(
                "{\"projects\":["
                        + "{\"projectCode\":\"PRJ7001\",\"projectName\":\"新店铺\",\"orgCode\":\"ORG7001\",\"orgName\":\"新组织\"},"
                        + "{\"projectCode\":\"PRJ8001\",\"projectName\":\"另一个店铺\",\"orgCode\":\"ORG8001\",\"orgName\":\"另一个组织\"}"
                        + "]}",
                "sid=selected; Path=/"
        )) {
            NoonSessionGateway gateway = identityGateway(mapper, server);

            NoonSessionGateway.MerchantAuthorization result = gateway.authorizeMerchantLogin(
                    10001L,
                    "merchant@example.com",
                    "password",
                    "PRJ8001",
                    "STR8001-NAE"
            );

            assertTrue(result.isSuccess());
            assertEquals("PRJ8001", result.getSelectedProject().getProjectCode());
            assertTrue(result.getCookie().contains("sid=selected"));
            assertEquals(1, server.sessionCreateCount());
            verify(mapper).updateProjectSessionCookie(
                    eq(10001L),
                    eq("PRJ8001"),
                    argThat(cookie -> cookie != null && cookie.contains("sid=selected")),
                    eq(10001L)
            );
        }
    }

    @Test
    void shouldCreateMerchantEmailOtpSessionForSelectedProjectAndPersistCookie() throws Exception {
        StoreSyncMapper mapper = mock(StoreSyncMapper.class);
        try (AuthRefreshServer server = new AuthRefreshServer(
                "{\"projects\":[{\"projectCode\":\"PRJ7001\",\"projectName\":\"新店铺\",\"orgCode\":\"ORG7001\",\"orgName\":\"新组织\"}]}",
                "sid=email-otp; Path=/"
        )) {
            NoonSessionGateway gateway = identityGateway(mapper, server);
            gateway.setLegacyDirectEmailOtpEnabled(true);
            gateway.setEmailOtpReader((email, mailAuthCode) -> {
                assertEquals("merchant@example.com", email);
                assertEquals("imap-secret", mailAuthCode);
                return "654321";
            });

            NoonSessionGateway.MerchantAuthorization result = gateway.authorizeMerchantEmailLogin(
                    10001L,
                    "merchant@example.com",
                    "imap-secret",
                    "PRJ7001",
                    "STR7001-NAE"
            );

            assertTrue(result.isSuccess());
            assertEquals("PRJ7001", result.getSelectedProject().getProjectCode());
            assertTrue(result.getCookie().contains("sid=email-otp"));
            assertEquals(1, server.generateCount());
            assertEquals("emailotp", server.lastValidateBody().path("channel_code").asText());
            assertEquals("merchant@example.com", server.lastValidateBody().path("channel_identifier").asText());
            assertEquals("654321", server.lastValidateBody().path("channel_credential").asText());
            verify(mapper).updateProjectSessionCookie(
                    eq(10001L),
                    eq("PRJ7001"),
                    argThat(cookie -> cookie != null && cookie.contains("sid=email-otp")),
                    eq(10001L)
            );
        }
    }

    @Test
    void shouldNotRetryEmailOtpGeneratePostWhenProviderReturnsServerError() throws Exception {
        StoreSyncMapper mapper = mock(StoreSyncMapper.class);
        try (AuthRefreshServer server = AuthRefreshServer.withGenerateStatus(500)) {
            NoonSessionGateway gateway = identityGateway(mapper, server);
            gateway.setLegacyDirectEmailOtpEnabled(true);

            assertThrows(
                    IllegalStateException.class,
                    () -> gateway.authorizeMerchantEmailLogin(
                            10001L,
                            "merchant@example.com",
                            "imap-secret",
                            "PRJ7001",
                            "STR7001-NAE"
                    )
            );

            assertEquals(1, server.generateCount());
            verifyNoInteractions(mapper);
        }
    }

    @Test
    void shouldCreateProjectCookieThroughCentralEmailOtpPrimitivesWithoutPersistingIt() throws Exception {
        StoreSyncMapper mapper = mock(StoreSyncMapper.class);
        try (AuthRefreshServer server = new AuthRefreshServer(
                "{\"projects\":[{\"projectCode\":\"PRJ7001\",\"projectName\":\"新店铺\"}]}",
                "sid=central-worker; Path=/"
        )) {
            NoonSessionGateway gateway = identityGateway(mapper, server);

            NoonSessionGateway.EmailOtpGeneration generation =
                    gateway.prepareEmailOtpGeneration("merchant@example.com");
            gateway.sendEmailOtp(generation);
            NoonSessionGateway.EmailIdentityGrant grant = gateway.validateEmailOtp(generation, "654321");
            NoonSessionGateway.ProjectSessionCookie projectSession = gateway.createEmailOtpProjectSession(
                    grant,
                    "PRJ7001",
                    "STR7001-NAE"
            );

            assertEquals("PRJ7001", projectSession.getProject().getProjectCode());
            assertTrue(projectSession.getCookie().contains("sid=central-worker"));
            assertTrue(projectSession.getCookie().contains("projectCode=PRJ7001"));
            assertEquals(1, server.generateCount());
            assertEquals(1, server.sessionCreateCount());
            verifyNoInteractions(mapper);
        }
    }

    @Test
    void centralRecoveryAdapterAuthenticatesOnceAndRecoversQueuedProjectsSerially() throws Exception {
        StoreSyncMapper mapper = mock(StoreSyncMapper.class);
        AtomicInteger acknowledgeCount = new AtomicInteger();
        NoonEmailOtpReader reader = new NoonEmailOtpReader() {
            @Override
            public String readOtp(String email, String mailAuthCode) {
                throw new AssertionError("central recovery must use generation-aware mailbox reads");
            }

            @Override
            public MailboxCursor snapshot(String email, String mailAuthCode) {
                return new MailboxCursor(7L, 100L, Instant.parse("2026-07-16T00:00:00Z"));
            }

            @Override
            public Optional<OtpCandidate> pollAfter(
                    String email,
                    String mailAuthCode,
                    MailboxCursor cursor,
                    Instant notBefore,
                    Set<String> excludedMessageKeyHashes
            ) {
                return Optional.of(new OtpCandidate(
                        "654321",
                        "message-key-hash",
                        Instant.parse("2026-07-16T00:00:01Z"),
                        7L,
                        101L
                ));
            }

            @Override
            public void acknowledge(String email, String mailAuthCode, OtpCandidate candidate) {
                acknowledgeCount.incrementAndGet();
            }
        };
        try (AuthRefreshServer server = new AuthRefreshServer(
                "{\"projects\":[{\"projectCode\":\"PRJ7001\"},{\"projectCode\":\"PRJ8001\"}]}",
                "sid=central-worker; Path=/"
        )) {
            NoonSessionGateway gateway = identityGateway(mapper, server);
            gateway.setConfiguredMerchantEmailOtpCredential("merchant@example.com", "imap-secret");
            NoonSessionGatewayAuthRecoveryGateway recoveryGateway = new NoonSessionGatewayAuthRecoveryGateway(
                    gateway,
                    reader,
                    Duration.ofMillis(1),
                    Duration.ofSeconds(1),
                    Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC),
                    millis -> {
                        throw new AssertionError("an immediately available OTP must not sleep");
                    }
            );

            NoonAuthRecoveryAttemptResult result = recoveryGateway.attempt(new NoonAuthRecoveryAttemptCommand(
                    9001L,
                    1,
                    Instant.parse("2026-07-16T00:00:00Z"),
                    Set.of(),
                    List.of(
                            new NoonAuthRecoveryProjectTarget(307L, "PRJ7001", "STR7001-NAE", 0L),
                            new NoonAuthRecoveryProjectTarget(308L, "PRJ8001", "STR8001-NAE", 0L)
                    ),
                    () -> true,
                    () -> true
            ));

            assertTrue(result.isIdentityAuthenticated());
            assertEquals("message-key-hash", result.getMessageKeyHash());
            assertEquals(2, result.getProjectResults().size());
            assertTrue(result.getProjectResults().stream().allMatch(item -> item.isRecovered()));
            assertEquals(1, server.generateCount());
            assertEquals(2, server.sessionCreateCount());
            assertEquals(1, acknowledgeCount.get());
            verifyNoInteractions(mapper);
        }
    }

    @Test
    void shouldRedactIdentityTokensFromAuthHttpResponseLogs() throws Exception {
        StoreSyncMapper mapper = mock(StoreSyncMapper.class);
        NoonHttpCallLogService logService = mock(NoonHttpCallLogService.class);
        try (AuthRefreshServer server = new AuthRefreshServer()) {
            NoonSessionGateway gateway = identityGateway(mapper, server);
            gateway.setNoonHttpCallLogService(logService);

            NoonSessionGateway.EmailOtpGeneration generation =
                    gateway.prepareEmailOtpGeneration("merchant@example.com");
            gateway.sendEmailOtp(generation);
            gateway.validateEmailOtp(generation, "654321");

            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
            verify(logService, atLeastOnce()).record(
                    any(HttpRequest.class),
                    nullable(Integer.class),
                    bodyCaptor.capture(),
                    nullable(Long.class),
                    nullable(String.class),
                    nullable(String.class),
                    nullable(String.class)
            );
            assertTrue(bodyCaptor.getAllValues().stream().noneMatch(body ->
                    body != null && (body.contains("token-1") || body.contains("access_token"))
            ));
        }
    }

    @Test
    void shouldRejectLegacyDirectEmailOtpEntryByDefault() throws Exception {
        StoreSyncMapper mapper = mock(StoreSyncMapper.class);
        try (AuthRefreshServer server = new AuthRefreshServer()) {
            NoonSessionGateway gateway = identityGateway(mapper, server);

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> gateway.authorizeMerchantEmailLogin(
                            10001L,
                            "merchant@example.com",
                            "imap-secret",
                            "PRJ7001",
                            "STR7001-NAE"
                    )
            );

            assertTrue(exception.getMessage().contains("直连已关闭"));
            assertEquals(0, server.generateCount());
            verifyNoInteractions(mapper);
        }
    }

    @Test
    void shouldExposeEmailOtpGenerateErrorForRateLimitClassification() throws Exception {
        StoreSyncMapper mapper = mock(StoreSyncMapper.class);
        try (AuthRefreshServer server = new AuthRefreshServer(
                "{\"projects\":[{\"projectCode\":\"PRJ7001\",\"projectName\":\"新店铺\",\"orgCode\":\"ORG7001\",\"orgName\":\"新组织\"}]}",
                "sid=email-otp; Path=/",
                "{\"success\":false,\"error\":\"Too many requests, please try again later\"}"
        )) {
            NoonSessionGateway gateway = identityGateway(mapper, server);
            gateway.setLegacyDirectEmailOtpEnabled(true);

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> gateway.authorizeMerchantEmailLogin(
                            10001L,
                            "merchant@example.com",
                            "imap-secret",
                            "PRJ7001",
                            "STR7001-NAE"
                    )
            );

            assertTrue(exception.getMessage().contains("Too many requests"));
            assertFalse(exception.getMessage().contains("null"));
            verifyNoInteractions(mapper);
        }
    }

    @Test
    void shouldSkipPersistingCookieWhenProjectCodeIsMissing() {
        StoreSyncMapper mapper = mock(StoreSyncMapper.class);
        NoonSessionGateway gateway = gateway(mapper, "");

        gateway.persistCookie(308L, null, "sid=project-session");

        verifyNoInteractions(mapper);
    }

    @Test
    void shouldFailFastWithoutRetryingRateLimitedWriteRequest() throws Exception {
        AtomicInteger writeCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            if ("/whoami".equals(exchange.getRequestURI().getPath())) {
                AuthRefreshServer.sendJson(exchange, 200, "{\"ok\":true}", null);
                return;
            }
            writeCount.incrementAndGet();
            AuthRefreshServer.sendJson(exchange, 429, "{\"error\":\"Too many requests\"}", null);
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            NoonSessionGateway gateway = directGateway(baseUrl + "/whoami");
            NoonSessionGateway.NoonSession session = gateway.login(
                    10001L,
                    "merchant@example.com",
                    "password",
                    "sid=existing",
                    "PRJ1",
                    "STORE1"
            );

            NoonHttpException exception = assertThrows(
                    NoonHttpException.class,
                    () -> session.postWriteJson(
                            baseUrl + "/write",
                            objectMapper.createObjectNode(),
                            false
                    )
            );

            assertEquals(429, exception.getStatusCode());
            assertEquals(1, writeCount.get());
        } finally {
            server.stop(0);
        }
    }

    private NoonSessionGateway gateway(String proxyProviderUrl) {
        return gateway(mock(StoreSyncMapper.class), proxyProviderUrl);
    }

    private NoonSessionGateway gateway(StoreSyncMapper storeSyncMapper, String proxyProviderUrl) {
        return new NoonSessionGateway(
                objectMapper,
                storeSyncMapper,
                false,
                0L,
                true,
                "",
                "",
                "",
                "",
                false,
                false,
                "",
                "http://noon.test/whoami",
                "",
                "",
                "",
                "",
                "",
                "",
                true,
                "HTTP",
                "",
                0,
                proxyProviderUrl
        );
    }

    private NoonSessionGateway gatewayWithSignin(String proxyProviderUrl) {
        return new NoonSessionGateway(
                objectMapper,
                mock(StoreSyncMapper.class),
                false,
                0L,
                true,
                "",
                "",
                "",
                "",
                false,
                true,
                "http://noon.test/signin",
                "http://noon.test/whoami",
                "",
                "",
                "",
                "",
                "",
                "",
                true,
                "HTTP",
                "",
                0,
                proxyProviderUrl
        );
    }

    private NoonSessionGateway directGateway(String whoamiUrl) {
        return new NoonSessionGateway(
                objectMapper,
                mock(StoreSyncMapper.class),
                false,
                0L,
                true,
                "",
                "",
                "",
                "",
                false,
                false,
                "",
                whoamiUrl,
                "",
                "",
                "",
                "",
                "",
                "",
                false,
                "HTTP",
                "",
                0,
                ""
        );
    }

    private NoonSessionGateway identityGateway(StoreSyncMapper mapper, AuthRefreshServer server) {
        NoonSessionGateway gateway = new NoonSessionGateway(
                objectMapper,
                mapper,
                false,
                0L,
                true,
                "",
                "",
                "",
                "",
                true,
                false,
                "",
                server.url("/whoami"),
                server.url("/lookup"),
                server.url("/pkce"),
                server.url("/generate"),
                server.url("/validate"),
                server.url("/projects"),
                server.url("/session-create"),
                false,
                "HTTP",
                "",
                0,
                ""
        );
        gateway.setCatalogCapabilityProbeUrl(server.url("/catalog"));
        gateway.setCatalogSessionBootstrapUrl(server.url("/catalog"));
        return gateway;
    }

    private static final class AuthRefreshServer implements AutoCloseable {
        private final HttpServer server;
        private final String projectsBody;
        private final String sessionCookie;
        private final String generateBody;
        private final int generateStatus;
        private final AtomicInteger sessionCreateCount = new AtomicInteger();
        private final AtomicInteger generateCount = new AtomicInteger();
        private final int whoamiStatus;
        private volatile JsonNode lastValidateBody;
        private volatile String lastSessionProjectCode;

        private AuthRefreshServer() throws IOException {
            this("{\"projects\":[{\"projectCode\":\"PRJ1\"}]}", "sid=new; Path=/", "{\"emailotp\":\"ok\"}", 200, 200);
        }

        private AuthRefreshServer(boolean rejectWhoami) throws IOException {
            this("{\"projects\":[{\"projectCode\":\"PRJ1\"}]}", "sid=new; Path=/", "{\"emailotp\":\"ok\"}", rejectWhoami ? 403 : 200, 200);
        }

        private AuthRefreshServer(int whoamiStatus) throws IOException {
            this("{\"projects\":[{\"projectCode\":\"PRJ1\"}]}", "sid=new; Path=/", "{\"emailotp\":\"ok\"}", whoamiStatus, 200);
        }

        private AuthRefreshServer(String projectsBody, String sessionCookie) throws IOException {
            this(projectsBody, sessionCookie, "{\"emailotp\":\"ok\"}", 200, 200);
        }

        private AuthRefreshServer(String projectsBody, String sessionCookie, String generateBody) throws IOException {
            this(projectsBody, sessionCookie, generateBody, 200, 200);
        }

        private AuthRefreshServer(
                String projectsBody,
                String sessionCookie,
                String generateBody,
                int whoamiStatus,
                int generateStatus
        ) throws IOException {
            this.projectsBody = projectsBody;
            this.sessionCookie = sessionCookie;
            this.generateBody = generateBody;
            this.whoamiStatus = whoamiStatus;
            this.generateStatus = generateStatus;
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", (exchange) -> {
                String path = exchange.getRequestURI().getPath();
                if ("/whoami".equals(path)) {
                    if (this.whoamiStatus != 200) {
                        sendJson(exchange, this.whoamiStatus, "{\"message\":\"whoami rejected\"}", null);
                    } else if (this.lastSessionProjectCode != null) {
                        sendJson(exchange, 200,
                                "{\"projectCode\":\"" + this.lastSessionProjectCode + "\"}", null);
                    } else {
                        sendJson(exchange, 200, "{\"ok\":true}", null);
                    }
                    return;
                }
                if ("/protected".equals(path)) {
                    String cookie = exchange.getRequestHeaders().getFirst("Cookie");
                    if (cookie != null && cookie.contains("sid=new")) {
                        sendJson(exchange, 200, "{\"ok\":true}", null);
                    } else {
                        sendJson(exchange, 403, "{\"message\":\"invalid session\"}", null);
                    }
                    return;
                }
                if ("/catalog-redirect".equals(path)) {
                    exchange.getResponseHeaders().set(
                            "Location",
                            "https://login.noon.partners/en/?domain=noon-catalog.noon.partners"
                    );
                    sendJson(exchange, 307, "temporary redirect", null);
                    return;
                }
                if ("/catalog".equals(path)) {
                    sendJson(exchange, 200, "{\"data\":{\"hits\":[],\"total\":0}}", null);
                    return;
                }
                if ("/lookup".equals(path)) {
                    sendJson(
                            exchange,
                            200,
                            "[{\"userCode\":\"merchant@example.com\",\"channels\":[{\"channelCode\":\"password\"},{\"channelCode\":\"emailotp\"}]}]",
                            null
                    );
                    return;
                }
                if ("/pkce".equals(path)) {
                    sendJson(exchange, 200, "{\"success\":true,\"pkce_key\":\"pkce-1\"}", null);
                    return;
                }
                if ("/validate".equals(path)) {
                    lastValidateBody = new ObjectMapper().readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    sendJson(exchange, 200, "{\"success\":true,\"access_token\":\"token-1\"}", null);
                    return;
                }
                if ("/generate".equals(path)) {
                    generateCount.incrementAndGet();
                    sendJson(exchange, this.generateStatus, this.generateBody, null);
                    return;
                }
                if ("/projects".equals(path)) {
                    sendJson(exchange, 200, this.projectsBody, null);
                    return;
                }
                if ("/session-create".equals(path)) {
                    sessionCreateCount.incrementAndGet();
                    JsonNode sessionBody = new ObjectMapper().readTree(
                            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
                    );
                    lastSessionProjectCode = sessionBody.path("projectCode").asText(null);
                    sendJson(exchange, 200, "{\"success\":true}", this.sessionCookie);
                    return;
                }
                sendJson(exchange, 404, "{\"message\":\"not found\"}", null);
            });
            server.start();
        }

        private static AuthRefreshServer withGenerateStatus(int generateStatus) throws IOException {
            return new AuthRefreshServer(
                    "{\"projects\":[{\"projectCode\":\"PRJ7001\"}]}",
                    "sid=new; Path=/",
                    "{\"message\":\"temporary failure\"}",
                    200,
                    generateStatus
            );
        }

        private String url(String path) {
            return "http://127.0.0.1:" + server.getAddress().getPort() + path;
        }

        private int sessionCreateCount() {
            return sessionCreateCount.get();
        }

        private int generateCount() {
            return generateCount.get();
        }

        private JsonNode lastValidateBody() {
            return lastValidateBody == null ? new ObjectMapper().createObjectNode() : lastValidateBody;
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private static void sendJson(
                com.sun.net.httpserver.HttpExchange exchange,
                int status,
                String body,
                String setCookie
        ) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (setCookie != null) {
                exchange.getResponseHeaders().add("Set-Cookie", setCookie);
            }
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream response = exchange.getResponseBody()) {
                response.write(bytes);
            }
        }
    }

    private static final class ProxyProviderServer implements AutoCloseable {
        private final HttpServer server;
        private final ConcurrentLinkedQueue<Integer> ports = new ConcurrentLinkedQueue<>();

        private ProxyProviderServer(int firstPort, int secondPort) throws IOException {
            ports.add(firstPort);
            ports.add(secondPort);
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/proxy", (exchange) -> {
                Integer port = ports.poll();
                if (port == null) {
                    port = secondPort;
                }
                byte[] body = ("{\"data\":[{\"ip\":\"127.0.0.1\",\"port\":\"" + port + "\"}]}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream response = exchange.getResponseBody()) {
                    response.write(body);
                }
            });
            server.start();
        }

        private String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/proxy";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static class StaticHttpProxy implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final AtomicInteger requestCount = new AtomicInteger();
        private final CountDownLatch requestLatch = new CountDownLatch(2);
        private volatile boolean running = true;
        private final Thread acceptThread;

        private StaticHttpProxy() throws IOException {
            serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            acceptThread = new Thread(this::acceptLoop, getClass().getSimpleName() + "-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();
        }

        protected int port() {
            return serverSocket.getLocalPort();
        }

        protected int requestCount() {
            return requestCount.get();
        }

        private boolean awaitRequests(int count) throws InterruptedException {
            if (requestCount.get() >= count) {
                return true;
            }
            return requestLatch.await(2, TimeUnit.SECONDS);
        }

        private void acceptLoop() {
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    handle(socket);
                } catch (IOException exception) {
                    if (running) {
                        throw new IllegalStateException(exception);
                    }
                }
            }
        }

        protected void handle(Socket socket) throws IOException {
            try (Socket accepted = socket) {
                String request = readHeaders(accepted);
                incrementRequestCount();
                requestLatch.countDown();
                byte[] body = responseBody(request).getBytes(StandardCharsets.UTF_8);
                accepted.getOutputStream().write((
                        "HTTP/1.1 200 OK\r\n"
                                + "Content-Type: text/plain\r\n"
                                + "Content-Length: " + body.length + "\r\n"
                                + "Connection: close\r\n"
                                + "\r\n"
                ).getBytes(StandardCharsets.US_ASCII));
                accepted.getOutputStream().write(body);
            }
        }

        protected String responseBody(String request) {
            return request.contains("whoami") ? "{}" : "download-ok";
        }

        protected void incrementRequestCount() {
            requestCount.incrementAndGet();
        }

        private String readHeaders(Socket socket) throws IOException {
            socket.setSoTimeout(2000);
            StringBuilder builder = new StringBuilder();
            int previous = -1;
            int current;
            int matched = 0;
            while ((current = socket.getInputStream().read()) != -1) {
                builder.append((char) current);
                if ((previous == '\r' && current == '\n') || current == '\n') {
                    matched++;
                } else if (current != '\r') {
                    matched = 0;
                }
                if (matched >= 2 || builder.length() > 8192) {
                    break;
                }
                previous = current;
            }
            return builder.toString();
        }

        @Override
        public void close() throws IOException {
            running = false;
            serverSocket.close();
        }
    }

    private static final class OneGoodThenDropProxy extends StaticHttpProxy {
        private OneGoodThenDropProxy() throws IOException {
            super();
        }

        @Override
        protected void handle(Socket socket) throws IOException {
            if (requestCount() == 0) {
                super.handle(socket);
                return;
            }
            try (Socket accepted = socket) {
                readAndDrop(accepted);
            }
        }

        private void readAndDrop(Socket socket) throws IOException {
            socket.setSoTimeout(2000);
            while (socket.getInputStream().read() != -1) {
                if (socket.getInputStream().available() == 0) {
                    break;
                }
            }
            incrementRequestCount();
        }
    }
}
