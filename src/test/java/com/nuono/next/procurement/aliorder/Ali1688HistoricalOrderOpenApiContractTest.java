package com.nuono.next.procurement.aliorder;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class Ali1688HistoricalOrderOpenApiContractTest {

    @Test
    void acceptsOnlyTheOfficialHttpsAuthorityAndExactApiPaths() {
        Ali1688HistoricalOrderOpenApiProperties properties = readyProperties();

        assertThat(properties.hasProductionDp10Configuration()).isTrue();

        properties.setApiGatewayBaseUrl("https://gw.open.1688.com:443");
        properties.setTokenUrlTemplate(
                "https://gw.open.1688.com:443/openapi/http/1/system.oauth2/getToken/{appKey}"
        );
        assertThat(properties.hasProductionDp10Configuration()).isTrue();
    }

    @Test
    void rejectsUntrustedGatewayAuthoritiesAndUrlComponents() {
        List<String> invalidGatewayUrls = List.of(
                "http://gw.open.1688.com",
                "https://localhost",
                "https://127.0.0.1",
                "https://gw.open.1688.com.evil.example",
                "https://gw.open.1688.com@evil.example",
                "https://user@gw.open.1688.com",
                "https://gw.open.1688.com:8443",
                "https://gw.open.1688.com/openapi",
                "https://gw.open.1688.com/",
                "https://gw.open.1688.com?proxy=evil.example",
                "https://gw.open.1688.com#fragment"
        );

        for (String invalidUrl : invalidGatewayUrls) {
            Ali1688HistoricalOrderOpenApiProperties properties = readyProperties();
            properties.setApiGatewayBaseUrl(invalidUrl);
            assertThat(properties.hasProductionDp10Configuration())
                    .as("gateway URL must be rejected: %s", invalidUrl)
                    .isFalse();
        }
    }

    @Test
    void rejectsUntrustedTokenAuthoritiesAndNonOfficialTokenPaths() {
        List<String> invalidTokenUrls = List.of(
                "http://gw.open.1688.com/openapi/http/1/system.oauth2/getToken/{appKey}",
                "https://localhost/openapi/http/1/system.oauth2/getToken/{appKey}",
                "https://gw.open.1688.com.evil.example/openapi/http/1/system.oauth2/getToken/{appKey}",
                "https://user@gw.open.1688.com/openapi/http/1/system.oauth2/getToken/{appKey}",
                "https://gw.open.1688.com:8443/openapi/http/1/system.oauth2/getToken/{appKey}",
                "https://gw.open.1688.com/openapi/http/1/system.oauth2/getToken-v2/{appKey}",
                "https://gw.open.1688.com/openapi/http/1/system.oauth2/getToken/{appKey}/extra",
                "https://gw.open.1688.com/openapi/http/1/system.oauth2/getToken/{appKey}?proxy=evil.example",
                "https://gw.open.1688.com/openapi/http/1/system.oauth2/getToken/{appKey}#fragment",
                "https://gw.open.1688.com/openapi/http/1/system.oauth2/getToken/static-key",
                "https://gw.open.1688.com/openapi/http/1/system.oauth2/getToken/{appKey}{appKey}"
        );

        for (String invalidUrl : invalidTokenUrls) {
            Ali1688HistoricalOrderOpenApiProperties properties = readyProperties();
            properties.setTokenUrlTemplate(invalidUrl);
            assertThat(properties.hasProductionDp10Configuration())
                    .as("token URL must be rejected: %s", invalidUrl)
                    .isFalse();
        }
    }

    @Test
    void rejectsAppKeyThatCouldChangeTheOfficialApiPath() {
        Ali1688HistoricalOrderOpenApiProperties properties = readyProperties();
        properties.setAppKey("../redirect");

        assertThat(properties.hasProductionDp10Configuration()).isFalse();
    }

    @Test
    void rejectsAnyOrderApiIdentityOutsideTheTwoOfficialMethods() {
        Ali1688HistoricalOrderOpenApiProperties properties = readyProperties();
        properties.setBuyerOrderDetailApiName("attacker.detail");
        assertThat(properties.hasProductionDp10Configuration()).isFalse();

        properties = readyProperties();
        properties.setBuyerOrderDetailNamespace("attacker.namespace");
        assertThat(properties.hasProductionDp10Configuration()).isFalse();

        properties = readyProperties();
        properties.setBuyerOrderListApiName("attacker.list");
        assertThat(properties.hasProductionDp10Configuration()).isFalse();
    }

    @Test
    void rejectsUnmanagedAuthorizationCallbacksAndUnboundedIoSettings() {
        List<String> invalidAuthorizeUrls = List.of(
                "http://auth.1688.com/oauth/authorize",
                "https://auth.1688.com.evil.example/oauth/authorize",
                "https://user@auth.1688.com/oauth/authorize",
                "https://auth.1688.com:8443/oauth/authorize",
                "https://auth.1688.com/oauth/authorize/extra",
                "https://auth.1688.com/oauth/authorize?next=evil.example",
                "https://auth.1688.com/oauth/authorize#fragment"
        );
        for (String invalid : invalidAuthorizeUrls) {
            Ali1688HistoricalOrderOpenApiProperties properties = readyProperties();
            properties.setAuthorizeUrl(invalid);
            assertThat(properties.hasProductionDp10Configuration()).isFalse();
        }

        String callbackPath = "/ai/api/procurement/ali1688-orders/"
                + "authorizations/open-api/callback";
        List<String> invalidRedirectUris = List.of(
                "http://www.nuoon.com" + callbackPath,
                "https://evil.example" + callbackPath,
                "https://www.nuoon.com.evil.example" + callbackPath,
                "https://user@www.nuoon.com" + callbackPath,
                "https://www.nuoon.com:8443" + callbackPath,
                "https://www.nuoon.com" + callbackPath + "/extra",
                "https://www.nuoon.com" + callbackPath + "?code=attacker",
                "https://www.nuoon.com" + callbackPath + "#fragment"
        );
        for (String invalid : invalidRedirectUris) {
            Ali1688HistoricalOrderOpenApiProperties properties = readyProperties();
            properties.setRedirectUri(invalid);
            assertThat(properties.hasProductionDp10Configuration())
                    .as("redirect URI must be rejected: %s", invalid)
                    .isFalse();
        }

        Ali1688HistoricalOrderOpenApiProperties properties = readyProperties();
        properties.setSite("china");
        assertThat(properties.hasProductionDp10Configuration()).isFalse();

        properties = readyProperties();
        properties.setTimeoutSeconds(31);
        assertThat(properties.hasProductionDp10Configuration()).isFalse();

        properties = readyProperties();
        properties.setPageSize(101);
        assertThat(properties.hasProductionDp10Configuration()).isFalse();

        properties = readyProperties();
        properties.setStateTtlSeconds(0);
        assertThat(properties.hasProductionDp10Configuration()).isFalse();

        properties = readyProperties();
        properties.setStateTtlSeconds(601);
        assertThat(properties.hasProductionDp10Configuration()).isFalse();
    }

    private Ali1688HistoricalOrderOpenApiProperties readyProperties() {
        Ali1688HistoricalOrderOpenApiProperties properties =
                new Ali1688HistoricalOrderOpenApiProperties();
        properties.setEnabled(true);
        properties.setAppKey("5890829");
        properties.setAppSecret("test-secret");
        properties.setTokenCipherSecret("test-token-cipher-secret");
        properties.setRedirectUri(
                "https://www.nuoon.com/ai/api/procurement/ali1688-orders/"
                        + "authorizations/open-api/callback"
        );
        return properties;
    }
}
