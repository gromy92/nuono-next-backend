package com.nuono.next.procurement.aliorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Ali1688OpenApiSecurityTest {

    @Test
    void tokenCipherEncryptsAndDecryptsWithoutPersistingPlainText() {
        Ali1688HistoricalOrderOpenApiProperties properties = new Ali1688HistoricalOrderOpenApiProperties();
        properties.setTokenCipherSecret("test-token-cipher-secret");
        Ali1688TokenCipher cipher = new Ali1688TokenCipher(properties);

        String encrypted = cipher.encrypt("access-token-secret");

        assertThat(encrypted).startsWith("v1:");
        assertThat(encrypted).doesNotContain("access-token-secret");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("access-token-secret");
    }

    @Test
    void tokenCipherRefusesToPersistRealTokensWithoutCipherSecret() {
        Ali1688TokenCipher cipher = new Ali1688TokenCipher(new Ali1688HistoricalOrderOpenApiProperties());

        assertThatThrownBy(() -> cipher.encrypt("access-token-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("token cipher secret");
    }

    @Test
    void signerBuildsStableAuthorizationAndApiSignaturesWithoutLeakingAppSecret() {
        Ali1688OpenApiSigner signer = new Ali1688OpenApiSigner();
        Map<String, String> authorizationParams = new LinkedHashMap<>();
        authorizationParams.put("client_id", "10000");
        authorizationParams.put("site", "china");
        authorizationParams.put("redirect_uri", "http://gw.open.1688.com/auth/auth.htm?key=value&secret=abcd");
        authorizationParams.put("state", "test");

        String oauthSignature = signer.hmacSha1Hex(authorizationParams, "abcd");
        String apiSignature = signer.apiSignature(
                "/openapi/param2/1/com.alibaba.trade/alibaba.trade.getBuyerOrderList/5890829",
                Map.of("access_token", "token-123", "page", "1", "pageSize", "20"),
                "app-secret"
        );
        String normalizedApiSignature = signer.apiSignature(
                "param2/1/com.alibaba.trade/alibaba.trade.getBuyerOrderList/5890829",
                Map.of("access_token", "token-123", "page", "1", "pageSize", "20"),
                "app-secret"
        );
        String officialSampleSignature = signer.apiSignature(
                "param2/1/system/currentTime/1000000",
                Map.of("a", "1", "b", "2"),
                "test123"
        );

        assertThat(oauthSignature).isEqualTo("300E0E24C543CAAEACBDB4883520D6CF2165B3AB");
        assertThat(apiSignature).isEqualTo("7B91F432F48774204045D86636AFD333B71E95FB");
        assertThat(normalizedApiSignature).isEqualTo(apiSignature);
        assertThat(officialSampleSignature).isEqualTo("33E54F4F7B989E3E0E912D3FBD2F1A03CA7CCE88");
        assertThat(oauthSignature).doesNotContain("abcd");
        assertThat(apiSignature).doesNotContain("app-secret");
    }
}
