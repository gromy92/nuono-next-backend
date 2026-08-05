package com.nuono.next.procurement.aliorder;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

/** Converts upstream error payloads to sanitized typed failures. */
final class Ali1688OpenApiFailureClassifier {
    private final Ali1688OpenApiJson json;

    Ali1688OpenApiFailureClassifier(Ali1688OpenApiJson json) {
        this.json = json;
    }

    boolean hasProviderError(JsonNode payload) {
        return StringUtils.hasText(json.text(
                payload,
                "error_code", "errorCode", "error", "errorMessage"
        ));
    }

    Ali1688HistoricalOrderProvider.Page providerError(JsonNode payload) {
        String providerCode = defaultText(json.text(payload, "error_code", "errorCode", "error"), "");
        String message = defaultText(json.text(payload, "error_message", "errorMessage", "message"), "");
        String normalized = (providerCode + " " + message).toLowerCase(Locale.ROOT);
        Ali1688HistoricalOrderFailureCode code;
        if (contains(normalized, "unauthorized", "invalid_token", "token expired", "access token", "oauth", "未授权", "授权失效", "令牌失效", "无效token")) {
            code = Ali1688HistoricalOrderFailureCode.AUTH_REQUIRED;
        } else if (contains(normalized, "rate", "limit", "too many", "frequency", "频率", "限流", "过于频繁", "调用次数")) {
            code = Ali1688HistoricalOrderFailureCode.RATE_LIMITED;
        } else if (contains(normalized, "risk", "captcha", "forbidden", "anti-spider", "blocked", "风控", "验证码", "禁止访问", "访问受限", "拒绝访问")) {
            code = Ali1688HistoricalOrderFailureCode.BLOCKED_BY_RISK_CONTROL;
        } else if (contains(normalized, "unavailable", "system busy", "timeout", "gateway", "系统繁忙", "服务不可用", "网关", "超时")) {
            code = Ali1688HistoricalOrderFailureCode.PROVIDER_UNAVAILABLE;
        } else {
            code = Ali1688HistoricalOrderFailureCode.UNEXPECTED_RESPONSE;
        }
        Ali1688HistoricalOrderProvider.Page page = failure(code);
        Integer retry = json.integer(payload, "retry_after", "retryAfter", "retry_after_seconds");
        if (retry != null && retry >= 0) page.setRetryAfter(Duration.ofSeconds(retry));
        return page;
    }

    boolean isStructuredOrderAbsence(JsonNode payload) {
        String code = defaultText(json.text(payload, "error_code", "errorCode", "error"), "")
                .trim().toUpperCase(Locale.ROOT);
        return List.of("ORDER_NOT_FOUND", "ORDER_NOT_EXIST", "TRADE_NOT_FOUND", "TRADE_NOT_EXIST")
                .contains(code);
    }

    Ali1688HistoricalOrderProvider.Page failure(Ali1688HistoricalOrderFailureCode code) {
        Ali1688HistoricalOrderProvider.Page page = new Ali1688HistoricalOrderProvider.Page(List.of());
        page.setFailureCode(code.getCode());
        page.setFailureMessage(safeMessage(code));
        page.setRetryableFailure(code.isRetryable());
        page.setProgressPercent(0);
        return page;
    }

    String safeMessage(Ali1688HistoricalOrderFailureCode code) {
        if (code == Ali1688HistoricalOrderFailureCode.AUTH_REQUIRED) return "1688 授权不可用，请重新授权。";
        if (code == Ali1688HistoricalOrderFailureCode.RATE_LIMITED) return "1688 OpenAPI 已限流，任务将退避重试。";
        if (code == Ali1688HistoricalOrderFailureCode.BLOCKED_BY_RISK_CONTROL) return "1688 OpenAPI 触发风控，任务将退避重试。";
        if (code == Ali1688HistoricalOrderFailureCode.PROVIDER_UNAVAILABLE) return "1688 OpenAPI 暂时不可用，任务将退避重试。";
        return "1688 OpenAPI 响应合同无法验证。";
    }

    private boolean contains(String value, String... fragments) {
        for (String fragment : fragments) if (value.contains(fragment)) return true;
        return false;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
