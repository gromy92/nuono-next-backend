package com.nuono.next.procurement.aliorder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.Ali1688OpenApiAuthorizationMapper;
import java.time.LocalDateTime;
import org.springframework.web.client.RestTemplate;

abstract class HttpAli1688HistoricalOrderProviderTestSupport {

    Ali1688HistoricalOrderOpenApiProperties properties() {
        Ali1688HistoricalOrderOpenApiProperties properties =
                new Ali1688HistoricalOrderOpenApiProperties();
        properties.setEnabled(true);
        properties.setAppKey("5890829");
        properties.setAppSecret("app-secret");
        properties.setTokenCipherSecret("token-cipher-secret-for-test");
        properties.setApiGatewayBaseUrl("http://openapi.test");
        properties.setTokenUrlTemplate(
                "http://openapi.test/openapi/http/1/system.oauth2/getToken/{appKey}"
        );
        return properties;
    }

    Ali1688HistoricalOrderOpenApiProperties incrementalProperties() {
        Ali1688HistoricalOrderOpenApiProperties properties = properties();
        properties.setApiGatewayBaseUrl("https://gw.open.1688.com");
        properties.setTokenUrlTemplate(
                "https://gw.open.1688.com/openapi/http/1/system.oauth2/getToken/{appKey}"
        );
        properties.setRedirectUri(
                "https://www.nuoon.com/ai/api/procurement/ali1688-orders/"
                        + "authorizations/open-api/callback"
        );
        properties.setModifiedFromParameterName("modifyStartTime");
        properties.setModifiedToParameterName("modifyEndTime");
        properties.setHistoryParameterName("isHis");
        properties.setModifiedFromFormat("yyyyMMddHHmmssSSSZ");
        properties.setModifiedAtResponseFieldNames("modifyTime");
        properties.setProviderZoneId("Asia/Shanghai");
        return properties;
    }

    HttpAli1688HistoricalOrderProvider provider(
            Ali1688HistoricalOrderOpenApiProperties properties,
            RestTemplate restTemplate
    ) {
        return provider(properties, restTemplate, null);
    }

    HttpAli1688HistoricalOrderProvider provider(
            Ali1688HistoricalOrderOpenApiProperties properties,
            RestTemplate restTemplate,
            Ali1688OpenApiAuthorizationMapper authorizationMapper
    ) {
        return new HttpAli1688HistoricalOrderProvider(
                properties,
                new Ali1688OpenApiSigner(),
                new Ali1688TokenCipher(properties),
                new ObjectMapper(),
                restTemplate,
                authorizationMapper
        );
    }

    Ali1688HistoricalOrderAuthorizationRow authorization(
            Ali1688HistoricalOrderOpenApiProperties properties
    ) {
        Ali1688TokenCipher cipher = new Ali1688TokenCipher(properties);
        Ali1688HistoricalOrderAuthorizationRow row = new Ali1688HistoricalOrderAuthorizationRow();
        row.setId(91_009L);
        row.setOwnerUserId(307L);
        row.setProviderCode("ALI1688_OPEN_API");
        row.setProviderAccountId("member-307");
        row.setStatus("authorized");
        row.setAccessTokenCipher(cipher.encrypt("access-token-001"));
        return row;
    }

    Ali1688HistoricalOrderAuthorizationRow expiredAuthorization(
            Ali1688HistoricalOrderOpenApiProperties properties
    ) {
        Ali1688TokenCipher cipher = new Ali1688TokenCipher(properties);
        Ali1688HistoricalOrderAuthorizationRow row = authorization(properties);
        row.setRefreshTokenCipher(cipher.encrypt("refresh-token-001"));
        row.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        return row;
    }

    String incrementalOrderJson(String orderNo, String modifiedAt) {
        return "{\"baseInfo\":{"
                + "\"idOfStr\":\"" + orderNo + "\","
                + "\"modifyTime\":\"" + modifiedAt + "\"},"
                + "\"productItems\":[{\"offerId\":\"OFFER-1\","
                + "\"skuId\":\"SKU-1\",\"quantity\":1}]}";
    }
}
