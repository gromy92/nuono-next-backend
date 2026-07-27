package com.nuono.next.product.noon;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * Detects explicit authorization failures returned inside successful HTTP response bodies.
 *
 * <p>Only known failure fields are traversed. In particular, ordinary {@code data} payloads are
 * deliberately ignored so catalog text or business codes cannot suspend a store by accident.</p>
 */
public final class ProductNoonAuthEnvelope {
    private static final int MAX_DEPTH = 16;
    private static final Set<String> AUTH_FIELDS = Set.of(
            "error",
            "errors",
            "err",
            "errorcode",
            "code",
            "statuscode",
            "status",
            "message",
            "detail",
            "response",
            "body",
            "errormessages",
            "errormessage"
    );
    private static final Pattern AUTH_HTTP_STATUS =
            Pattern.compile(
                    "\\b(?:http(?:/\\d(?:\\.\\d)?)?|status\\s+code)"
                            + "\\s*[:=]?\\s*(301|302|303|307|308|401|403)\\b"
            );
    private static final Pattern EXACT_AUTH_STATUS =
            Pattern.compile("^(301|302|303|307|308|401|403)$");
    private static final Pattern AUTH_REQUIRED =
            Pattern.compile("\\bauth(?:entication)?[\\s_-]*required\\b");
    private static final Pattern SESSION_OR_COOKIE_EXPIRED =
            Pattern.compile(
                    "(\\b(session|cookie)\\b[^\\r\\n]{0,32}\\b(expired|invalid)\\b)"
                            + "|(\\bexpired\\b[^\\r\\n]{0,32}\\b(session|cookie)\\b)"
            );
    private static final Pattern FORBIDDEN_AUTH_CONTEXT =
            Pattern.compile(
                    "(\\b(access|authorization|authentication|permission)\\b"
                            + "[^\\r\\n]{0,32}\\bforbidden\\b)"
                            + "|(\\bforbidden\\b[^\\r\\n]{0,32}"
                            + "\\b(access|authorization|authentication|permission)\\b)"
            );

    private ProductNoonAuthEnvelope() {
    }

    public static String evidence(JsonNode response) {
        return evidence(response, 0, "");
    }

    private static String evidence(JsonNode node, int depth, String fieldName) {
        if (node == null || node.isNull() || node.isMissingNode() || depth > MAX_DEPTH) {
            return null;
        }
        if (node.isValueNode()) {
            return authMarker(fieldName, node);
        }
        if (node.isArray()) {
            int index = 0;
            for (JsonNode child : node) {
                String nested = evidence(child, depth + 1, fieldName);
                if (StringUtils.hasText(nested)) {
                    return "[" + index + "]=" + nested;
                }
                index++;
            }
            return null;
        }
        if (!node.isObject()) {
            return null;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!AUTH_FIELDS.contains(normalizeFieldName(field.getKey()))) {
                continue;
            }
            JsonNode value = field.getValue();
            String direct = authMarker(normalizeFieldName(field.getKey()), value);
            if (StringUtils.hasText(direct)) {
                return field.getKey() + "=" + direct;
            }
            String nested = evidence(value, depth + 1, normalizeFieldName(field.getKey()));
            if (StringUtils.hasText(nested)) {
                return field.getKey() + "." + nested;
            }
        }
        return null;
    }

    private static String authMarker(String fieldName, JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        if (value.isIntegralNumber()) {
            int status = value.asInt();
            return isAuthStatusField(fieldName) && isAuthStatus(status)
                    ? "HTTP_" + status
                    : null;
        }
        if (!value.isTextual()) {
            return null;
        }
        String normalized = value.asText("").trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String normalizedWords = normalized.replace('_', ' ').replace('-', ' ');
        if (AUTH_REQUIRED.matcher(normalized).find()
                || SESSION_OR_COOKIE_EXPIRED.matcher(normalizedWords).find()
                || normalizedWords.contains("authentication expired")
                || normalizedWords.contains("unauthorized")
                || normalizedWords.contains("authorization rejected")
                || FORBIDDEN_AUTH_CONTEXT.matcher(normalizedWords).find()
                || ("forbidden".equals(normalizedWords)
                        && isStructuredStatusOrCodeField(fieldName))) {
            return "AUTH_REQUIRED";
        }
        Matcher contextualStatus = AUTH_HTTP_STATUS.matcher(normalized);
        if (contextualStatus.find()) {
            return "HTTP_" + contextualStatus.group(1);
        }
        Matcher exactStatus = EXACT_AUTH_STATUS.matcher(normalized);
        return exactStatus.matches() && isAuthStatusField(fieldName)
                ? "HTTP_" + exactStatus.group(1)
                : null;
    }

    private static boolean isAuthStatusField(String fieldName) {
        return !StringUtils.hasText(fieldName)
                || "error".equals(fieldName)
                || "err".equals(fieldName)
                || "errorcode".equals(fieldName)
                || "code".equals(fieldName)
                || "statuscode".equals(fieldName)
                || "status".equals(fieldName);
    }

    private static boolean isStructuredStatusOrCodeField(String fieldName) {
        return "errorcode".equals(fieldName)
                || "code".equals(fieldName)
                || "statuscode".equals(fieldName)
                || "status".equals(fieldName);
    }

    private static boolean isAuthStatus(int status) {
        return status == 301
                || status == 302
                || status == 303
                || status == 307
                || status == 308
                || status == 401
                || status == 403;
    }

    private static String normalizeFieldName(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
