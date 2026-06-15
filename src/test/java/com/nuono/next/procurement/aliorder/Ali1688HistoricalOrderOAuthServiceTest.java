package com.nuono.next.procurement.aliorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.Ali1688HistoricalOrderMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@ExtendWith(MockitoExtension.class)
class Ali1688HistoricalOrderOAuthServiceTest {

    @Mock
    private Ali1688HistoricalOrderMapper mapper;

    @Test
    void startAuthorizationBuildsSignedUrlWithoutLeakingAppSecret() {
        Ali1688HistoricalOrderOAuthService service = service(new RestTemplate());

        Ali1688HistoricalOrderAuthorizationView.StartView view =
                service.startAuthorization(bossContext(), "PRJ108065", "AE");

        assertThat(view.isConfigured()).isTrue();
        assertThat(view.getProviderCode()).isEqualTo("ALI1688_OPEN_API");
        assertThat(view.getAuthorizationUrl())
                .contains("client_id=5890829")
                .contains("redirect_uri=")
                .contains("state=")
                .contains("_aop_signature=")
                .doesNotContain("secret-should-not-leak");
    }

    @Test
    void completeAuthorizationExchangesCodeAndPersistsEncryptedTokensBoundToStoreSite() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688HistoricalOrderOAuthService service = service(restTemplate);
        Ali1688HistoricalOrderAuthorizationView.StartView start =
                service.startAuthorization(bossContext(), "PRJ108065", "AE");
        String state = UriComponentsBuilder.fromUriString(start.getAuthorizationUrl())
                .build()
                .getQueryParams()
                .getFirst("state");

        server.expect(requestTo("https://gw.open.1688.com/openapi/http/1/system.oauth2/getToken/5890829"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("grant_type=authorization_code")))
                .andExpect(content().string(containsString("code=oauth-code-001")))
                .andRespond(withSuccess(
                        "{"
                                + "\"access_token\":\"real-access-token\","
                                + "\"refresh_token\":\"real-refresh-token\","
                                + "\"expires_in\":3600,"
                                + "\"memberId\":\"buyer-member-001\","
                                + "\"resource_owner\":\"buyer-login-name\""
                                + "}",
                        MediaType.APPLICATION_JSON
                ));
        when(mapper.selectAuthorizationByProviderAccount(307L, "ALI1688_OPEN_API", "buyer-member-001"))
                .thenReturn(null);
        when(mapper.nextAuthorizationId()).thenReturn(91009L);
        when(mapper.nextOrderStoreBindingId()).thenReturn(96009L);
        ArgumentCaptor<Ali1688HistoricalOrderAuthorizationRow> authorizationCaptor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderAuthorizationRow.class);

        Ali1688HistoricalOrderAuthorizationView.CompleteView completed =
                service.completeAuthorization("oauth-code-001", state);

        server.verify();
        verify(mapper).insertAuthorization(authorizationCaptor.capture());
        verify(mapper).insertExplicitStoreBinding(
                eq(96009L),
                eq(307L),
                eq(91009L),
                eq("PRJ108065"),
                eq("AE"),
                eq(307L),
                eq("1688 OpenAPI 授权绑定到当前店铺范围。")
        );
        Ali1688HistoricalOrderAuthorizationRow row = authorizationCaptor.getValue();
        Ali1688TokenCipher cipher = new Ali1688TokenCipher(configuredProperties());
        assertThat(row.getProviderCode()).isEqualTo("ALI1688_OPEN_API");
        assertThat(row.getProviderAccountId()).isEqualTo("buyer-member-001");
        assertThat(row.getAccountLabel()).isEqualTo("buyer-login-name");
        assertThat(row.getAccessTokenCipher()).doesNotContain("real-access-token");
        assertThat(row.getRefreshTokenCipher()).doesNotContain("real-refresh-token");
        assertThat(cipher.decrypt(row.getAccessTokenCipher())).isEqualTo("real-access-token");
        assertThat(cipher.decrypt(row.getRefreshTokenCipher())).isEqualTo("real-refresh-token");
        assertThat(row.getExpiresAt()).isNotNull();
        assertThat(completed.getAuthorizationId()).isEqualTo(91009L);
        assertThat(completed.getProviderAccountId()).isEqualTo("buyer-member-001");
    }

    @Test
    void completeAuthorizationRejectsExpiredStateBeforeTokenExchange() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer.createServer(restTemplate);
        Ali1688HistoricalOrderOAuthService service = service(restTemplate);
        String expiredState = signedState(
                "{\"ownerUserId\":307,\"operatorUserId\":307,\"storeCode\":\"PRJ108065\","
                        + "\"siteCode\":\"AE\",\"nonce\":\"old-nonce\",\"issuedAtEpochSeconds\":1}"
        );

        assertThatThrownBy(() -> service.completeAuthorization("oauth-code-001", expiredState))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("state 已过期");
    }

    private Ali1688HistoricalOrderOAuthService service(RestTemplate restTemplate) {
        Ali1688HistoricalOrderOpenApiProperties properties = configuredProperties();
        return new Ali1688HistoricalOrderOAuthService(
                mapper,
                properties,
                new Ali1688OpenApiSigner(),
                new Ali1688TokenCipher(properties),
                new ObjectMapper(),
                restTemplate
        );
    }

    private Ali1688HistoricalOrderOpenApiProperties configuredProperties() {
        Ali1688HistoricalOrderOpenApiProperties properties = new Ali1688HistoricalOrderOpenApiProperties();
        properties.setEnabled(true);
        properties.setAppKey("5890829");
        properties.setAppSecret("secret-should-not-leak");
        properties.setTokenCipherSecret("token-cipher-secret-for-test");
        properties.setRedirectUri("http://127.0.0.1:18081/api/procurement/ali1688-orders/authorizations/open-api/callback");
        return properties;
    }

    private String signedState(String jsonPayload) {
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(jsonPayload.getBytes(StandardCharsets.UTF_8));
        String signature = new Ali1688OpenApiSigner().hmacSha1Hex(
                Map.of("payload", encoded),
                "secret-should-not-leak"
        );
        return encoded + "." + signature;
    }

    private BusinessAccessContext bossContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.BOSS)
                .roleName("老板")
                .roleLevel(1)
                .menuPaths(Set.of("/purchase/ali1688-orders"))
                .build();
    }
}
