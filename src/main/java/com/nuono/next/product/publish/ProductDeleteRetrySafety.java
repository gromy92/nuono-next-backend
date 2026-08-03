package com.nuono.next.product.publish;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.product.ProductPublishTaskRecord;
import org.springframework.util.StringUtils;

public final class ProductDeleteRetrySafety {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ProductDeleteRetrySafety() {
    }

    public static boolean canResume(ProductPublishTaskRecord task) {
        if (!ProductPublishTaskClassifier.isProductDelete(task)) {
            return false;
        }
        JsonNode result = readResult(task.getResultJson());
        JsonNode writeMarker = result == null ? null : result.get("writeMayHaveOccurred");
        boolean explicitlyNotWritten = writeMarker != null && writeMarker.isBoolean() && !writeMarker.booleanValue();
        String stage = result == null ? null : normalize(result.path("stage").asText());
        boolean preWriteStage = "retry_scheduled".equalsIgnoreCase(stage)
                || "pre_delete_unavailable".equalsIgnoreCase(stage)
                || "pre_delete_captured".equalsIgnoreCase(stage);
        boolean submittedStage = "unmap_submitted".equalsIgnoreCase(stage)
                || "delete_submitted".equalsIgnoreCase(stage)
                || "current_psku_delete_submitted".equalsIgnoreCase(stage);
        boolean unknownOutcome = "product_delete_result_unknown".equalsIgnoreCase(task.getErrorCode());
        return submittedStage || (!unknownOutcome && (explicitlyNotWritten || preWriteStage));
    }

    private static JsonNode readResult(String resultJson) {
        if (!StringUtils.hasText(resultJson)) {
            return null;
        }
        try {
            JsonNode result = OBJECT_MAPPER.readTree(resultJson);
            return result != null && result.isObject() ? result : null;
        } catch (Exception exception) {
            return null;
        }
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
