package com.nuono.next.productselection;

import org.springframework.util.StringUtils;

public class Ali1688GatewayException extends RuntimeException {

    private final String errorCode;
    private final String gatewayMessage;
    private final boolean retryable;
    private final String rawSnapshotJson;
    private final String officialSearchUrl;
    private final String providerTraceId;

    public Ali1688GatewayException(
            String errorCode,
            String gatewayMessage,
            boolean retryable,
            String rawSnapshotJson,
            String officialSearchUrl,
            String providerTraceId
    ) {
        super(StringUtils.hasText(gatewayMessage) ? gatewayMessage.trim() : defaultMessage(errorCode));
        this.errorCode = StringUtils.hasText(errorCode) ? errorCode.trim() : "unexpected_response";
        this.gatewayMessage = StringUtils.hasText(gatewayMessage) ? gatewayMessage.trim() : defaultMessage(this.errorCode);
        this.retryable = retryable;
        this.rawSnapshotJson = StringUtils.hasText(rawSnapshotJson) ? rawSnapshotJson.trim() : null;
        this.officialSearchUrl = StringUtils.hasText(officialSearchUrl) ? officialSearchUrl.trim() : null;
        this.providerTraceId = StringUtils.hasText(providerTraceId) ? providerTraceId.trim() : null;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getGatewayMessage() {
        return gatewayMessage;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public String getRawSnapshotJson() {
        return rawSnapshotJson;
    }

    public String getOfficialSearchUrl() {
        return officialSearchUrl;
    }

    public String getProviderTraceId() {
        return providerTraceId;
    }

    private static String defaultMessage(String errorCode) {
        String code = StringUtils.hasText(errorCode) ? errorCode.trim() : "unexpected_response";
        switch (code) {
            case "source_image_missing":
                return "源头商品缺少可用于 1688 图搜的图片。";
            case "gateway_disabled":
                return "1688 图搜网关未启用。";
            case "gateway_timeout":
                return "1688 图搜网关调用超时。";
            case "captcha_required":
                return "1688 图搜出现验证码，需要人工处理。";
            case "login_required":
                return "1688 图搜登录态失效，需要重新维护服务端会话。";
            case "rate_limited":
                return "1688 图搜触发限流，请稍后重试。";
            case "no_candidates":
                return "1688 图搜未返回有效候选。";
            default:
                return "1688 图搜网关返回异常响应。";
        }
    }
}
