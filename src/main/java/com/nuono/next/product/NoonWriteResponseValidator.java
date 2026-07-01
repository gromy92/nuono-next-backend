package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.util.Locale;
import org.springframework.util.StringUtils;

final class NoonWriteResponseValidator {

    private NoonWriteResponseValidator() {
    }

    static void throwIfFailure(JsonNode response, String actionLabel) {
        String failureMessage = failureMessage(response);
        if (!StringUtils.hasText(failureMessage)) {
            return;
        }
        if (StringUtils.hasText(actionLabel)) {
            throw new IllegalStateException(actionLabel + "失败：" + failureMessage);
        }
        throw new IllegalStateException(failureMessage);
    }

    private static String failureMessage(JsonNode response) {
        if (response == null || response.isNull() || response.isMissingNode()) {
            return "";
        }
        int invalid = response.path("invalid").asInt(0);
        boolean explicitFailure = response.path("success").isBoolean() && !response.path("success").asBoolean();
        String status = text(response, "status");
        JsonNode errors = firstNonEmptyErrorNode(
                response.path("error"),
                response.path("errors"),
                response.path("errorMessages"),
                response.path("err")
        );
        if (invalid > 0 || explicitFailure || failureStatus(status) || hasNonEmptyError(errors)) {
            return "Noon write response contains business error: "
                    + (hasNonEmptyError(errors) ? errors.toString() : response.toString());
        }
        return "";
    }

    private static JsonNode firstNonEmptyErrorNode(JsonNode... nodes) {
        if (nodes == null) {
            return MissingNode.getInstance();
        }
        for (JsonNode node : nodes) {
            if (hasNonEmptyError(node)) {
                return node;
            }
        }
        return MissingNode.getInstance();
    }

    private static boolean hasNonEmptyError(JsonNode error) {
        if (error == null || error.isMissingNode() || error.isNull()) {
            return false;
        }
        if (error.isObject() || error.isArray()) {
            return error.size() > 0;
        }
        return StringUtils.hasText(error.asText(""));
    }

    private static boolean failureStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return "error".equals(normalized)
                || "failed".equals(normalized)
                || "failure".equals(normalized)
                || "invalid".equals(normalized);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !StringUtils.hasText(field)) {
            return null;
        }
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return StringUtils.hasText(text) ? text.trim() : null;
    }
}
