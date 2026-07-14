package com.nuono.next.noon;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Deterministic, operation-aware recognition for Noon provider responses. */
@Component
public class NoonResponseClassifier {

    public static final String RULESET_VERSION = "2026-07-14.1";

    private static final Pattern HEX_PSKU_PATTERN = Pattern.compile("(?i)\\b[a-f0-9]{24,64}\\b");
    private static final Pattern PSKU_LIST_PATTERN = Pattern.compile(
            "(?i)psku(?:_codes?)?\\s*[:=]?\\s*\\[([^]]+)]"
    );
    private static final Pattern QUOTED_TOKEN_PATTERN = Pattern.compile("['\"]([A-Za-z0-9_-]{8,128})['\"]");

    public NoonResponseClassification classify(String operation, NoonHttpException exception) {
        String normalizedOperation = normalizeOperation(operation);
        int status = exception == null ? 0 : exception.getStatusCode();
        String body = exception == null ? null : exception.getResponseBody();
        String normalizedBody = StringUtils.hasText(body) ? body.toLowerCase(Locale.ROOT) : "";

        if (isRateLimited(status, normalizedBody)) {
            return result(
                    "NOON_RATE_LIMITED",
                    NoonFailureCategory.RATE_LIMIT,
                    normalizedOperation,
                    "Noon 请求过于频繁，已停止本次操作，请稍后再试。",
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    status,
                    true,
                    List.of()
            );
        }
        if (status == 401 || status == 403 || containsAny(normalizedBody, "unauthorized", "invalid session", "signin")) {
            return result(
                    "NOON_AUTH_REQUIRED",
                    NoonFailureCategory.AUTHENTICATION,
                    normalizedOperation,
                    "Noon 登录态已失效或无权执行该操作，请重新授权后再试。",
                    HttpStatus.BAD_GATEWAY.value(),
                    status,
                    false,
                    List.of()
            );
        }
        if (isNoCapacity(normalizedBody)) {
            return result(
                    "NOON_NO_CAPACITY",
                    NoonFailureCategory.BUSINESS_WAITING,
                    normalizedOperation,
                    "Noon 当前没有匹配的可约仓日期或时段，可调整条件或稍后再试。",
                    HttpStatus.CONFLICT.value(),
                    status,
                    true,
                    List.of()
            );
        }
        if (isPbarcodeMappingFailure(normalizedOperation, status, normalizedBody)) {
            return result(
                    "NOON_PBARCODE_UNMAPPED",
                    NoonFailureCategory.BUSINESS_VALIDATION,
                    normalizedOperation,
                    "Noon 中相关 PSKU 没有有效的 pbarcode 映射，请先在 Noon 后台补全商品映射。",
                    HttpStatus.UNPROCESSABLE_ENTITY.value(),
                    status,
                    false,
                    extractPskuCodes(body)
            );
        }
        if (status == 409 || containsAny(normalizedBody, "already exists", "already scheduled", "duplicate")) {
            return result(
                    "NOON_REQUEST_CONFLICT",
                    NoonFailureCategory.CONFLICT,
                    normalizedOperation,
                    "Noon 提示该业务对象已存在或当前状态不允许重复操作，请先刷新状态。",
                    HttpStatus.CONFLICT.value(),
                    status,
                    false,
                    List.of()
            );
        }
        if (status >= 500) {
            return result(
                    "NOON_UPSTREAM_UNAVAILABLE",
                    NoonFailureCategory.UPSTREAM,
                    normalizedOperation,
                    "Noon 服务暂时不可用，请稍后确认业务状态后再处理。",
                    HttpStatus.BAD_GATEWAY.value(),
                    status,
                    true,
                    List.of()
            );
        }
        return result(
                "NOON_UNCLASSIFIED_RESPONSE",
                NoonFailureCategory.UNKNOWN,
                normalizedOperation,
                "Noon 返回了尚未识别的响应，系统已保留调用记录，请勿盲目重复提交。",
                HttpStatus.BAD_GATEWAY.value(),
                status,
                false,
                List.of()
        );
    }

    private static boolean isPbarcodeMappingFailure(String operation, int status, String body) {
        return "CREATE_ASN_LINES".equals(operation)
                && status == HttpStatus.BAD_REQUEST.value()
                && body.contains("psku")
                && body.contains("pbarcode")
                && containsAny(body, "not mapped", "invalid", "not valid");
    }

    private static boolean isRateLimited(int status, String body) {
        return status == 429
                || status == 418
                || containsAny(body, "too many requests", "rate_limited", "ip_channel", "teapot");
    }

    private static boolean isNoCapacity(String body) {
        return containsAny(body, "no_capacity", "no capacity", "no available capacity");
    }

    private static boolean containsAny(String value, String... markers) {
        if (value == null || markers == null) {
            return false;
        }
        for (String marker : markers) {
            if (marker != null && value.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> extractPskuCodes(String body) {
        if (!StringUtils.hasText(body)) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        Matcher hexMatcher = HEX_PSKU_PATTERN.matcher(body);
        while (hexMatcher.find()) {
            values.add(hexMatcher.group());
        }
        Matcher listMatcher = PSKU_LIST_PATTERN.matcher(body);
        while (listMatcher.find()) {
            Matcher tokenMatcher = QUOTED_TOKEN_PATTERN.matcher(listMatcher.group(1));
            while (tokenMatcher.find()) {
                values.add(tokenMatcher.group(1));
            }
        }
        return new ArrayList<>(values);
    }

    private static String normalizeOperation(String operation) {
        return StringUtils.hasText(operation) ? operation.trim().toUpperCase(Locale.ROOT) : "UNKNOWN";
    }

    private static NoonResponseClassification result(
            String code,
            NoonFailureCategory category,
            String operation,
            String message,
            int apiStatus,
            int providerStatus,
            boolean retryable,
            List<String> affectedPskuCodes
    ) {
        return new NoonResponseClassification(
                code,
                category,
                operation,
                message,
                apiStatus,
                providerStatus,
                retryable,
                affectedPskuCodes
        );
    }
}
