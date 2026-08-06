package com.nuono.next.procurement.aliorder;

import java.net.URI;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Validates the production DP10 OpenAPI configuration as one explicit contract. */
final class Ali1688HistoricalOrderOpenApiContract {
    private static final String OFFICIAL_GATEWAY_HOST = "gw.open.1688.com";
    private static final String OFFICIAL_AUTHORIZE_HOST = "auth.1688.com";
    private static final String OFFICIAL_AUTHORIZE_PATH = "/oauth/authorize";
    private static final String OFFICIAL_REDIRECT_HOST = "www.nuoon.com";
    private static final String OFFICIAL_REDIRECT_PATH =
            "/ai/api/procurement/ali1688-orders/authorizations/open-api/callback";
    private static final String OFFICIAL_TOKEN_PATH_PREFIX =
            "/openapi/http/1/system.oauth2/getToken/";

    private final Ali1688HistoricalOrderOpenApiProperties properties;

    Ali1688HistoricalOrderOpenApiContract(
            Ali1688HistoricalOrderOpenApiProperties properties
    ) {
        this.properties = properties;
    }

    boolean isProductionReady() {
        if (!properties.isEnabled()
                || !hasText(properties.getAppKey())
                || !hasText(properties.getAppSecret())
                || !hasText(properties.getTokenCipherSecret())
                || !validPathSegment(properties.getAppKey())
                || !validOfficialGatewayBaseUrl(properties.getApiGatewayBaseUrl())
                || !validOfficialTokenUrlTemplate(properties.getTokenUrlTemplate())
                || !validHttpsEndpoint(
                        properties.getAuthorizeUrl(),
                        OFFICIAL_AUTHORIZE_HOST,
                        OFFICIAL_AUTHORIZE_PATH
                )
                || !validHttpsEndpoint(
                        properties.getRedirectUri(),
                        OFFICIAL_REDIRECT_HOST,
                        OFFICIAL_REDIRECT_PATH
                )
                || !"1688".equals(properties.getSite())
                || !"1".equals(properties.getApiVersion())
                || !"com.alibaba.trade".equals(properties.getBuyerOrderListNamespace())
                || !"alibaba.trade.getBuyerOrderList".equals(
                        properties.getBuyerOrderListApiName())
                || !"com.alibaba.trade".equals(
                        properties.getBuyerOrderDetailNamespace())
                || !"alibaba.trade.get.buyerView".equals(
                        properties.getBuyerOrderDetailApiName())
                || properties.getTimeoutSeconds() <= 0
                || properties.getTimeoutSeconds() > 30
                || properties.getPageSize() <= 0
                || properties.getPageSize() > 100
                || properties.getStateTtlSeconds() != 600
                || !"page".equals(properties.getPageNumberParameterName())
                || !"pageSize".equals(properties.getPageSizeParameterName())
                || !"modifyStartTime".equals(properties.getModifiedFromParameterName())
                || !"modifyEndTime".equals(properties.getModifiedToParameterName())
                || !"isHis".equals(properties.getHistoryParameterName())
                || !"yyyyMMddHHmmssSSSZ".equals(properties.getModifiedFromFormat())
                || !"modifyTime".equals(properties.getModifiedAtResponseFieldNames())
                || !"Asia/Shanghai".equals(properties.getProviderZoneId())
                || hasText(properties.getCursorParameterName())
                || hasText(properties.getNextCursorResponseFieldNames())) {
            return false;
        }
        try {
            ZoneId.of(properties.getProviderZoneId().trim());
            if (!"ISO_OFFSET_DATE_TIME".equalsIgnoreCase(
                    properties.getModifiedFromFormat().trim()
            )) {
                DateTimeFormatter.ofPattern(properties.getModifiedFromFormat().trim());
            }
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean validPathSegment(String value) {
        return hasText(value) && value.trim().matches("[A-Za-z0-9][A-Za-z0-9_.-]*");
    }

    private boolean validOfficialGatewayBaseUrl(String value) {
        return validHttpsEndpoint(value, OFFICIAL_GATEWAY_HOST, "");
    }

    private boolean validOfficialTokenUrlTemplate(String value) {
        if (!hasText(value) || countOccurrences(value, "{appKey}") != 1) {
            return false;
        }
        String appKey = properties.getAppKey().trim();
        return validHttpsEndpoint(
                value.replace("{appKey}", appKey),
                OFFICIAL_GATEWAY_HOST,
                OFFICIAL_TOKEN_PATH_PREFIX + appKey
        );
    }

    private boolean validHttpsEndpoint(
            String value,
            String expectedHost,
            String expectedRawPath
    ) {
        if (!hasText(value)) {
            return false;
        }
        try {
            URI uri = URI.create(value.trim());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && expectedHost.equalsIgnoreCase(uri.getHost())
                    && uri.getUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && expectedRawPath.equals(uri.getRawPath())
                    && uri.getRawQuery() == null
                    && uri.getRawFragment() == null;
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private int countOccurrences(String value, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }
}
