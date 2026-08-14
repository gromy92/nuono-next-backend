package com.nuono.next.procurement.aliorder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/** Emits only non-reversible OAuth configuration diagnostics during the active investigation. */
final class Ali1688OAuthRuntimeDiagnosticLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(Ali1688HistoricalOrderOAuthService.class);

    private Ali1688OAuthRuntimeDiagnosticLogger() {
    }

    static void log(
            Map<String, String> params,
            String authorizationUrl,
            Ali1688HistoricalOrderOpenApiProperties properties
    ) {
        String runtimeAppKey = text(params == null ? null : params.get("client_id"));
        UriComponents authorizationUri = UriComponentsBuilder.fromUriString(authorizationUrl).build();
        String authorizationClientId = authorizationUri.getQueryParams().getFirst("client_id");
        String authorizationRedirectUri = authorizationUri.getQueryParams().getFirst("redirect_uri");
        LOGGER.info(
                "ALI1688_OAUTH_RUNTIME_DIAGNOSTIC runtimeAppKeyLength={} runtimeAppKeyFingerprint={} "
                        + "authorizationClientIdLength={} authorizationClientIdFingerprint={} "
                        + "clientIdMatchesRuntime={} redirectUriMatchesRuntime={} authorizeHost={}",
                runtimeAppKey.length(),
                fingerprint(runtimeAppKey, properties.getTokenCipherSecret()),
                length(authorizationClientId),
                fingerprint(authorizationClientId, properties.getTokenCipherSecret()),
                runtimeAppKey.equals(authorizationClientId),
                text(properties.getRedirectUri()).equals(authorizationRedirectUri),
                authorizationUri.getHost()
        );
    }

    static String fingerprint(String value, String nonDisclosureKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(text(nonDisclosureKey).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(text(value).getBytes(StandardCharsets.UTF_8)));
            return encoded.substring(0, 16);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to fingerprint 1688 OAuth runtime configuration.", exception);
        }
    }

    private static int length(String value) {
        return value == null ? 0 : value.length();
    }

    private static String text(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
