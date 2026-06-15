package com.nuono.next.procurement.aliorder;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nuono.procurement.ali1688.historical-order.open-api")
public class Ali1688HistoricalOrderOpenApiProperties {

    private boolean enabled;
    private String appKey;
    private String appSecret;
    private String tokenCipherSecret;
    private String authorizeUrl = "https://auth.1688.com/oauth/authorize";
    private String redirectUri;
    private String site = "1688";
    private String tokenUrlTemplate = "https://gw.open.1688.com/openapi/http/1/system.oauth2/getToken/{appKey}";
    private String apiGatewayBaseUrl = "https://gw.open.1688.com";
    private String apiVersion = "1";
    private String buyerOrderListNamespace = "com.alibaba.trade";
    private String buyerOrderListApiName = "alibaba.trade.getBuyerOrderList";
    private String buyerOrderDetailNamespace = "com.alibaba.trade";
    private String buyerOrderDetailApiName = "alibaba.trade.get.buyerView";
    private int timeoutSeconds = 30;
    private int pageSize = 20;
    private int stateTtlSeconds = 600;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getTokenCipherSecret() {
        return tokenCipherSecret;
    }

    public void setTokenCipherSecret(String tokenCipherSecret) {
        this.tokenCipherSecret = tokenCipherSecret;
    }

    public String getAuthorizeUrl() {
        return authorizeUrl;
    }

    public void setAuthorizeUrl(String authorizeUrl) {
        this.authorizeUrl = authorizeUrl;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public String getTokenUrlTemplate() {
        return tokenUrlTemplate;
    }

    public void setTokenUrlTemplate(String tokenUrlTemplate) {
        this.tokenUrlTemplate = tokenUrlTemplate;
    }

    public String getApiGatewayBaseUrl() {
        return apiGatewayBaseUrl;
    }

    public void setApiGatewayBaseUrl(String apiGatewayBaseUrl) {
        this.apiGatewayBaseUrl = apiGatewayBaseUrl;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getBuyerOrderListNamespace() {
        return buyerOrderListNamespace;
    }

    public void setBuyerOrderListNamespace(String buyerOrderListNamespace) {
        this.buyerOrderListNamespace = buyerOrderListNamespace;
    }

    public String getBuyerOrderListApiName() {
        return buyerOrderListApiName;
    }

    public void setBuyerOrderListApiName(String buyerOrderListApiName) {
        this.buyerOrderListApiName = buyerOrderListApiName;
    }

    public String getBuyerOrderDetailNamespace() {
        return buyerOrderDetailNamespace;
    }

    public void setBuyerOrderDetailNamespace(String buyerOrderDetailNamespace) {
        this.buyerOrderDetailNamespace = buyerOrderDetailNamespace;
    }

    public String getBuyerOrderDetailApiName() {
        return buyerOrderDetailApiName;
    }

    public void setBuyerOrderDetailApiName(String buyerOrderDetailApiName) {
        this.buyerOrderDetailApiName = buyerOrderDetailApiName;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getStateTtlSeconds() {
        return stateTtlSeconds;
    }

    public void setStateTtlSeconds(int stateTtlSeconds) {
        this.stateTtlSeconds = stateTtlSeconds;
    }
}
