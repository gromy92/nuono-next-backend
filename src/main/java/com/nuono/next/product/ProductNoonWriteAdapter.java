package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import java.net.http.HttpTimeoutException;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

public class ProductNoonWriteAdapter {

    public JsonNode postWriteJson(
            NoonSession session,
            String action,
            String url,
            JsonNode body,
            boolean withProject
    ) {
        return execute(action, () -> session.postWriteJson(url, body, withProject));
    }

    public JsonNode postWriteJson(
            NoonSession session,
            String action,
            String url,
            JsonNode body,
            boolean withProject,
            Map<String, String> extraHeaders
    ) {
        return execute(action, () -> session.postWriteJson(url, body, withProject, extraHeaders));
    }

    JsonNode execute(String action, ProductNoonWriteOperation operation) {
        try {
            JsonNode response = operation.execute();
            ProductNoonWriteResult result = classifyResponse(action, response);
            result.throwIfFailure();
            return result.getResponse();
        } catch (ProductNoonWriteException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw classifyException(action, exception);
        }
    }

    ProductNoonWriteResult classifyResponse(String action, JsonNode response) {
        String message = firstNonBlank(
                businessErrorMessage(response == null ? null : response.path("error")),
                businessErrorMessage(response == null ? null : response.path("detail"))
        );
        if (StringUtils.hasText(message)) {
            return ProductNoonWriteResult.failure(
                    action,
                    response,
                    ProductNoonWriteErrorCategory.BUSINESS_FAILURE,
                    "noon_business_failure",
                    normalizeAction(action) + " 失败：" + message
            );
        }
        return ProductNoonWriteResult.success(action, response);
    }

    ProductNoonWriteException classifyException(String action, RuntimeException exception) {
        String normalizedAction = normalizeAction(action);
        String message = shrink(exception == null ? null : exception.getMessage());
        if (isTimeoutException(exception)) {
            return new ProductNoonWriteException(
                    ProductNoonWriteErrorCategory.UNKNOWN_WRITE_RESULT,
                    "noon_write_timeout",
                    normalizedAction,
                    normalizedAction + " 请求结果未知：" + message,
                    exception
            );
        }
        if (isUnknownWriteResult(exception)) {
            return new ProductNoonWriteException(
                    ProductNoonWriteErrorCategory.UNKNOWN_WRITE_RESULT,
                    "noon_write_unknown",
                    normalizedAction,
                    normalizedAction + " 请求结果未知：" + message,
                    exception
            );
        }
        if (isRateLimitException(exception)) {
            return new ProductNoonWriteException(
                    ProductNoonWriteErrorCategory.RATE_LIMITED,
                    "noon_rate_limited",
                    normalizedAction,
                    normalizedAction + " 请求被 Noon 限流：" + message,
                    exception
            );
        }
        if (isAuthException(exception)) {
            return new ProductNoonWriteException(
                    ProductNoonWriteErrorCategory.AUTH_REQUIRED,
                    "noon_auth_required",
                    normalizedAction,
                    normalizedAction + " 登录或权限失效：" + message,
                    exception
            );
        }
        return new ProductNoonWriteException(
                ProductNoonWriteErrorCategory.WRITE_FAILED,
                "noon_write_failed",
                normalizedAction,
                normalizedAction + " 失败：" + message,
                exception
        );
    }

    private String businessErrorMessage(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value;
        if (node.isObject() || node.isArray()) {
            value = node.size() == 0 ? null : node.toString();
        } else {
            value = node.asText(null);
        }
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if ("false".equalsIgnoreCase(normalized)
                || "0".equals(normalized)
                || "{}".equals(normalized)
                || "[]".equals(normalized)) {
            return null;
        }
        return shrink(normalized);
    }

    private boolean isTimeoutException(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof HttpTimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("timeout") || lower.contains("timed out") || lower.contains("超时")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isUnknownWriteResult(Throwable exception) {
        String message = exceptionMessage(exception);
        return message.contains("connection reset")
                || message.contains("broken pipe")
                || message.contains("eof")
                || message.contains("closed")
                || message.contains("goaway")
                || containsHttpStatus(message, 408)
                || containsHttpStatus(message, 502)
                || containsHttpStatus(message, 503)
                || containsHttpStatus(message, 504)
                || message.contains("bad gateway")
                || message.contains("service unavailable")
                || message.contains("gateway timeout");
    }

    private boolean isRateLimitException(Throwable exception) {
        String message = exceptionMessage(exception);
        return containsHttpStatus(message, 418)
                || containsHttpStatus(message, 429)
                || message.contains("too many requests")
                || message.contains("rate limit");
    }

    private boolean isAuthException(Throwable exception) {
        String message = exceptionMessage(exception);
        return containsHttpStatus(message, 401)
                || containsHttpStatus(message, 403)
                || message.contains("unauthorized")
                || message.contains("forbidden");
    }

    private boolean containsHttpStatus(String message, int status) {
        return message.contains("http " + status)
                || message.contains("http/" + status)
                || message.contains("status " + status)
                || message.contains("\"status\":" + status)
                || message.contains("\"status\": " + status);
    }

    private String exceptionMessage(Throwable exception) {
        StringBuilder builder = new StringBuilder();
        Throwable current = exception;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(current.getMessage());
            }
            current = current.getCause();
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String normalizeAction(String action) {
        return StringUtils.hasText(action) ? action.trim() : "Noon 写回";
    }

    private String shrink(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = value.trim();
        return text.length() <= 220 ? text : text.substring(0, 220) + "...";
    }
}

@FunctionalInterface
interface ProductNoonWriteOperation {
    JsonNode execute();
}

enum ProductNoonWriteErrorCategory {
    SUCCESS(false),
    BUSINESS_FAILURE(false),
    UNKNOWN_WRITE_RESULT(true),
    RATE_LIMITED(false),
    AUTH_REQUIRED(false),
    WRITE_FAILED(false);

    private final boolean readbackOnly;

    ProductNoonWriteErrorCategory(boolean readbackOnly) {
        this.readbackOnly = readbackOnly;
    }

    boolean isReadbackOnly() {
        return readbackOnly;
    }
}

final class ProductNoonWriteResult {

    private final ProductNoonWriteErrorCategory category;
    private final String action;
    private final JsonNode response;
    private final String errorCode;
    private final String message;

    private ProductNoonWriteResult(
            ProductNoonWriteErrorCategory category,
            String action,
            JsonNode response,
            String errorCode,
            String message
    ) {
        this.category = category;
        this.action = action;
        this.response = response;
        this.errorCode = errorCode;
        this.message = message;
    }

    static ProductNoonWriteResult success(String action, JsonNode response) {
        return new ProductNoonWriteResult(
                ProductNoonWriteErrorCategory.SUCCESS,
                action,
                response,
                null,
                null
        );
    }

    static ProductNoonWriteResult failure(
            String action,
            JsonNode response,
            ProductNoonWriteErrorCategory category,
            String errorCode,
            String message
    ) {
        return new ProductNoonWriteResult(category, action, response, errorCode, message);
    }

    ProductNoonWriteErrorCategory getCategory() {
        return category;
    }

    String getAction() {
        return action;
    }

    JsonNode getResponse() {
        return response;
    }

    String getErrorCode() {
        return errorCode;
    }

    boolean isSuccess() {
        return ProductNoonWriteErrorCategory.SUCCESS.equals(category);
    }

    boolean isReadbackOnly() {
        return category != null && category.isReadbackOnly();
    }

    void throwIfFailure() {
        if (!isSuccess()) {
            throw new ProductNoonWriteException(category, errorCode, action, message, null);
        }
    }
}

final class ProductNoonWriteException extends IllegalStateException {

    private final ProductNoonWriteErrorCategory category;
    private final String errorCode;
    private final String action;

    ProductNoonWriteException(
            ProductNoonWriteErrorCategory category,
            String errorCode,
            String action,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.category = category;
        this.errorCode = errorCode;
        this.action = action;
    }

    ProductNoonWriteErrorCategory getCategory() {
        return category;
    }

    String getErrorCode() {
        return errorCode;
    }

    String getAction() {
        return action;
    }

    boolean isReadbackOnly() {
        return category != null && category.isReadbackOnly();
    }
}
