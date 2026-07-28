package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.productlisting.ProductListingWriteAuthRecovery;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.util.StringUtils;

final class ProductRebuildListingState {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final ObjectMapper objectMapper;

    ProductRebuildListingState(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    Map<String, Object> create(
            String status,
            Long listingDraftId,
            Long listingDryRunTaskId,
            Long listingRealRunTaskId,
            String listingStatus,
            String failureCode,
            String failureMessage
    ) {
        return create(
                status,
                listingDraftId,
                listingDryRunTaskId,
                listingRealRunTaskId,
                listingStatus,
                failureCode,
                failureMessage,
                null
        );
    }

    Map<String, Object> create(
            String status,
            Long listingDraftId,
            Long listingDryRunTaskId,
            Long listingRealRunTaskId,
            String listingStatus,
            String failureCode,
            String failureMessage,
            String noonResultJson
    ) {
        AuthRecoveryState auth = authRecoveryState(failureCode, noonResultJson);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("status", auth.pending ? ProductListingWriteAuthRecovery.FAILURE_CODE : status);
        putIfNotNull(state, "listingDraftId", listingDraftId);
        putIfNotNull(state, "listingDryRunTaskId", listingDryRunTaskId);
        putIfNotNull(state, "listingRealRunTaskId", listingRealRunTaskId);
        putIfNotBlank(state, "listingStatus", listingStatus);
        putIfNotBlank(state, "failureCode", failureCode);
        putIfNotBlank(state, "failureMessage", failureMessage);
        if (auth.pending) {
            putIfNotNull(state, "recoveryId", auth.recoveryId);
            state.put("writeMayHaveOccurred", auth.writeMayHaveOccurred);
        }
        state.put("recordedAt", now());
        return state;
    }

    Map<String, Object> claimed(long leaseMinutes, String claimToken) {
        Map<String, Object> state = create("listing_running", null, null, null, null, null, null);
        state.put("claimToken", claimToken);
        state.put("claimExpiresAt", TIME_FORMATTER.format(
                ZonedDateTime.now(BUSINESS_ZONE).plusMinutes(Math.max(1L, leaseMinutes))
        ));
        return state;
    }

    String statusForListing(String listingStatus, String failureCode) {
        String normalizedFailureCode = normalize(failureCode);
        if ("listing_auth_recovery_superseded".equalsIgnoreCase(normalizedFailureCode)
                || "partner_sku_already_exists_superseded".equalsIgnoreCase(
                        normalizedFailureCode)) {
            return "listing_superseded";
        }
        if (ProductListingWriteAuthRecovery.FAILURE_CODE.equalsIgnoreCase(
                normalizedFailureCode)) {
            return ProductListingWriteAuthRecovery.FAILURE_CODE;
        }
        if ("succeeded".equalsIgnoreCase(listingStatus)) {
            return "listing_succeeded";
        }
        if ("failed".equalsIgnoreCase(listingStatus)
                || "rejected".equalsIgnoreCase(listingStatus)
                || "written_verify_failed".equalsIgnoreCase(listingStatus)) {
            return "listing_failed";
        }
        if ("running".equalsIgnoreCase(listingStatus)) {
            return "listing_running";
        }
        return "listing_submitted";
    }

    String statusForExisting(String listingStatus, String failureCode) {
        if ("submitted".equalsIgnoreCase(listingStatus)
                && !ProductListingWriteAuthRecovery.FAILURE_CODE.equalsIgnoreCase(normalize(failureCode))) {
            return "listing_already_submitted";
        }
        return statusForListing(listingStatus, failureCode);
    }

    private AuthRecoveryState authRecoveryState(String failureCode, String noonResultJson) {
        JsonNode root = readJson(noonResultJson);
        String currentFailureCode = normalize(failureCode);
        boolean pending = ProductListingWriteAuthRecovery.FAILURE_CODE.equalsIgnoreCase(currentFailureCode)
                || (!StringUtils.hasText(currentFailureCode)
                && ProductListingWriteAuthRecovery.FAILURE_CODE.equalsIgnoreCase(text(root, "failureCode")));
        Long recoveryId = longValue(root, "recoveryId");
        boolean writeMayHaveOccurred = booleanValue(root, "writeMayHaveOccurred");
        return new AuthRecoveryState(pending, recoveryId, writeMayHaveOccurred);
    }

    private JsonNode readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        return value != null && value.isValueNode() && StringUtils.hasText(value.asText())
                ? value.asText().trim()
                : null;
    }

    private Long longValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        return value != null && value.canConvertToLong() ? value.asLong() : null;
    }

    private boolean booleanValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        return value != null && value.isBoolean() && value.asBoolean();
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    private String now() {
        return TIME_FORMATTER.format(ZonedDateTime.now(BUSINESS_ZONE));
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static final class AuthRecoveryState {
        private final boolean pending;
        private final Long recoveryId;
        private final boolean writeMayHaveOccurred;

        private AuthRecoveryState(boolean pending, Long recoveryId, boolean writeMayHaveOccurred) {
            this.pending = pending;
            this.recoveryId = recoveryId;
            this.writeMayHaveOccurred = writeMayHaveOccurred;
        }
    }
}
