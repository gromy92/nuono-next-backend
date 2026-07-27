package com.nuono.next.product.publish;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.product.ProductPublishTaskRecord;
import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * Keeps legacy product-delete task recognition consistent across execution,
 * retry safety, retry SQL and operator-facing status messages.
 */
public final class ProductPublishTaskClassifier {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PRODUCT_DELETE = "product-delete";
    private static final String DELETE_DOMAIN = "delete";
    private static final String DELETE_IDEMPOTENCY_PREFIX = "delete:";
    private static final String DELETE_SNAPSHOT_MODE = "product-delete-task";

    private ProductPublishTaskClassifier() {
    }

    public static boolean isProductDelete(ProductPublishTaskRecord task) {
        if (task == null) {
            return false;
        }
        return equalsIgnoreCase(task.getTaskType(), PRODUCT_DELETE)
                || jsonFieldEquals(task.getRequestJson(), "action", PRODUCT_DELETE)
                || startsWithIgnoreCase(task.getIdempotencyKey(), DELETE_IDEMPOTENCY_PREFIX)
                || changedDomainsContainDelete(task.getChangedDomainsJson())
                || jsonFieldEquals(task.getDraftJson(), "mode", DELETE_SNAPSHOT_MODE)
                || jsonFieldEquals(task.getBaselineJson(), "mode", DELETE_SNAPSHOT_MODE);
    }

    private static boolean changedDomainsContainDelete(String json) {
        JsonNode domains = readJson(json);
        if (domains == null) {
            return false;
        }
        if (domains.isArray()) {
            for (JsonNode domain : domains) {
                if (equalsIgnoreCase(domain.asText(), DELETE_DOMAIN)) {
                    return true;
                }
            }
        }
        return equalsIgnoreCase(text(domains, "domain"), DELETE_DOMAIN)
                || equalsIgnoreCase(text(domains, "action"), DELETE_DOMAIN);
    }

    private static boolean jsonFieldEquals(String json, String field, String expected) {
        return equalsIgnoreCase(text(readJson(json), field), expected);
    }

    private static JsonNode readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (Exception exception) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !StringUtils.hasText(field)) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : normalize(value.asText());
    }

    private static boolean equalsIgnoreCase(String value, String expected) {
        return expected != null && expected.equalsIgnoreCase(normalize(value));
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        String normalized = normalize(value);
        return normalized != null
                && normalized.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
