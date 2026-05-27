package com.nuono.next.productselection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

public final class Ali1688GatewaySmokePreparation {

    private static final String SOURCE_COLLECTION_ID_TOKEN = "@ali1688_smoke_source_collection_id";
    private static final String SYSTEM_BROWSER_GATEWAY = "system_browser_gateway";
    private static final String CAPTCHA_RETURN_TYPED_ERROR = "return_typed_error";
    private static final Set<String> ALLOWED_SESSION_STATES;

    static {
        Set<String> states = new LinkedHashSet<>();
        states.add("ready");
        states.add("login_required");
        states.add("captcha_required");
        ALLOWED_SESSION_STATES = Collections.unmodifiableSet(states);
    }

    private Ali1688GatewaySmokePreparation() {
    }

    public static Plan prepare(FixedSample sample, BrowserGatewayBoundary boundary) {
        FixedSample safeSample = sample == null ? new FixedSample() : sample;
        BrowserGatewayBoundary safeBoundary = boundary == null ? new BrowserGatewayBoundary() : boundary;
        List<String> failures = new ArrayList<>();
        validateSample(safeSample, failures);
        validateBoundary(safeBoundary, failures);

        String sourceCollectionIdToken = safeSample.sourceCollectionId == null
                ? SOURCE_COLLECTION_ID_TOKEN
                : String.valueOf(safeSample.sourceCollectionId);
        String sessionState = normalize(safeBoundary.sessionState);
        String expectedOutcome = "ready".equals(sessionState) ? "success_or_partial_success"
                : ALLOWED_SESSION_STATES.contains(sessionState) ? "typed_gateway_error" : "";
        String expectedGatewayErrorCode = "typed_gateway_error".equals(expectedOutcome) ? sessionState : "";

        return new Plan(
                failures.isEmpty(),
                failures,
                sourceCollectionIdToken,
                expectedOutcome,
                expectedGatewayErrorCode,
                environment(safeBoundary),
                boundaryRecord(safeBoundary),
                sampleRecord(safeSample),
                createSampleSql(),
                isolationSql(sourceCollectionIdToken),
                cleanupSql(sourceCollectionIdToken)
        );
    }

    private static void validateSample(FixedSample sample, List<String> failures) {
        if (!StringUtils.hasText(sample.collectionNo)) {
            failures.add("collection no is required");
        }
        if (sample.ownerUserId == null) {
            failures.add("owner user ID is required");
        }
        if (sample.logicalStoreId == null) {
            failures.add("logical store ID is required");
        }
        if (!StringUtils.hasText(sample.sourceTitle)) {
            failures.add("source title is required");
        }
        if (!StringUtils.hasText(sample.sourceImageUrl)) {
            failures.add("source image URL is required");
        }
    }

    private static void validateBoundary(BrowserGatewayBoundary boundary, List<String> failures) {
        String serviceKind = normalize(boundary.gatewayServiceKind);
        String endpointUrl = normalize(boundary.endpointUrl);
        String sessionState = normalize(boundary.sessionState);
        String captchaBoundaryMode = normalize(boundary.captchaBoundaryMode);

        if (!SYSTEM_BROWSER_GATEWAY.equals(serviceKind)) {
            failures.add("gateway service kind must be system_browser_gateway");
        }
        if (!StringUtils.hasText(endpointUrl)) {
            failures.add("browser gateway endpoint is required");
        } else if (looksLikeLocalBridge(endpointUrl)) {
            failures.add("browser gateway endpoint must not point at the local smoke bridge");
        }
        if (!ALLOWED_SESSION_STATES.contains(sessionState)) {
            failures.add("gateway session state must be ready, login_required, or captcha_required");
        }
        if (!CAPTCHA_RETURN_TYPED_ERROR.equals(captchaBoundaryMode)) {
            failures.add("CAPTCHA boundary mode must be return_typed_error");
        }
        if (boundary.captchaAutoSolveEnabled) {
            failures.add("CAPTCHA auto-solve must be disabled");
        }
        if (!StringUtils.hasText(boundary.confirmedBy)) {
            failures.add("gateway boundary confirmer is required");
        }
        if (boundary.usesPluginBridge) {
            failures.add("system smoke must not use Chrome plugin bridge");
        }
        if (boundary.usesLocalSmokeBridge) {
            failures.add("system smoke must not use local smoke bridge");
        }
        if (boundary.usesManualPayload) {
            failures.add("system smoke must not use manual payload sending");
        }
        if (("login_required".equals(sessionState) || "captcha_required".equals(sessionState))
                && !StringUtils.hasText(boundary.providerTraceId)
                && !StringUtils.hasText(boundary.diagnosticNote)) {
            failures.add("blocked gateway session requires provider trace or diagnostic note");
        }
    }

    private static boolean looksLikeLocalBridge(String endpointUrl) {
        String lower = endpointUrl.toLowerCase();
        return lower.contains("smoke_bridge")
                || lower.contains("smoke-bridge")
                || lower.contains("/ali1688/image-search/latest")
                || lower.contains("plugin");
    }

    private static Map<String, String> environment(BrowserGatewayBoundary boundary) {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("NUONO_PRODUCT_SELECTION_ALI1688_IMAGE_SEARCH_ENABLED", "true");
        environment.put("NUONO_PRODUCT_SELECTION_ALI1688_IMAGE_SEARCH_ENDPOINT_URL", normalize(boundary.endpointUrl));
        environment.put("NUONO_PRODUCT_SELECTION_ALI1688_IMAGE_SEARCH_AUTH_HEADER_NAME", "Authorization");
        environment.put("NUONO_PRODUCT_SELECTION_ALI1688_IMAGE_SEARCH_TIMEOUT_SECONDS", "30");
        environment.put("NUONO_PRODUCT_SELECTION_ALI1688_IMAGE_SEARCH_MAX_CANDIDATES", "10");
        environment.put("NUONO_PRODUCT_SELECTION_ALI1688_SCHEDULER_ENABLED", "true");
        return environment;
    }

    private static Map<String, Object> boundaryRecord(BrowserGatewayBoundary boundary) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("gatewayServiceKind", normalize(boundary.gatewayServiceKind));
        record.put("endpointUrl", normalize(boundary.endpointUrl));
        record.put("sessionState", normalize(boundary.sessionState));
        record.put("captchaBoundaryMode", normalize(boundary.captchaBoundaryMode));
        record.put("captchaAutoSolveEnabled", boundary.captchaAutoSolveEnabled);
        record.put("confirmedBy", normalize(boundary.confirmedBy));
        record.put("providerTraceId", normalize(boundary.providerTraceId));
        record.put("diagnosticNote", normalize(boundary.diagnosticNote));
        record.put("usesPluginBridge", boundary.usesPluginBridge);
        record.put("usesLocalSmokeBridge", boundary.usesLocalSmokeBridge);
        record.put("usesManualPayload", boundary.usesManualPayload);
        return record;
    }

    private static Map<String, Object> sampleRecord(FixedSample sample) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("sourceCollectionId", sample.sourceCollectionId);
        record.put("collectionNo", normalize(sample.collectionNo));
        record.put("ownerUserId", sample.ownerUserId);
        record.put("logicalStoreId", sample.logicalStoreId);
        record.put("sourcePlatform", normalize(sample.sourcePlatform));
        record.put("sourceUrl", normalize(sample.sourceUrl));
        record.put("sourceTitle", normalize(sample.sourceTitle));
        record.put("sourceImageUrl", normalize(sample.sourceImageUrl));
        return record;
    }

    private static String createSampleSql() {
        return String.join("\n",
                "INSERT INTO product_management_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
                "VALUES ('product_selection_source_collection', LAST_INSERT_ID(86000 + 1), NOW(), NOW())",
                "ON DUPLICATE KEY UPDATE",
                "  next_id = LAST_INSERT_ID(next_id + 1),",
                "  gmt_updated = NOW();",
                "",
                "SET " + SOURCE_COLLECTION_ID_TOKEN + " = LAST_INSERT_ID();",
                "",
                "INSERT INTO product_selection_source_collection (",
                "  id, owner_user_id, logical_store_id, collection_no, source_type, source_platform,",
                "  source_url, page_url, source_title, source_image_url, image_urls_json, status,",
                "  created_by, updated_by, collected_at, gmt_create, gmt_updated",
                ") VALUES (",
                "  " + SOURCE_COLLECTION_ID_TOKEN + ", :owner_user_id, :logical_store_id, :collection_no,",
                "  'image-search-source', :source_platform, :source_url, :source_url, :source_title,",
                "  :source_image_url, JSON_ARRAY(:source_image_url), 'success',",
                "  :owner_user_id, :owner_user_id, NOW(), NOW(), NOW()",
                ");"
        );
    }

    private static String isolationSql(String sourceCollectionIdToken) {
        return String.join("\n",
                "UPDATE product_selection_ali1688_candidate_ai_assessment",
                "SET is_deleted = b'1'",
                "WHERE task_id IN (",
                "  SELECT id FROM product_selection_ali1688_collection_task",
                "  WHERE source_collection_id = " + sourceCollectionIdToken,
                ");",
                "",
                "UPDATE product_selection_ali1688_candidate",
                "SET is_deleted = b'1', active_candidate_key = NULL",
                "WHERE source_collection_id = " + sourceCollectionIdToken + ";",
                "",
                "UPDATE product_selection_ali1688_collection_task",
                "SET is_deleted = b'1', current_task_key = NULL",
                "WHERE source_collection_id = " + sourceCollectionIdToken + ";"
        );
    }

    private static String cleanupSql(String sourceCollectionIdToken) {
        return String.join("\n",
                "UPDATE product_selection_ali1688_candidate_ai_assessment",
                "SET is_deleted = b'1'",
                "WHERE task_id IN (",
                "  SELECT id FROM product_selection_ali1688_collection_task",
                "  WHERE source_collection_id = " + sourceCollectionIdToken,
                ");",
                "",
                "UPDATE product_selection_ali1688_candidate",
                "SET is_deleted = b'1', active_candidate_key = NULL",
                "WHERE source_collection_id = " + sourceCollectionIdToken + ";",
                "",
                "UPDATE product_selection_ali1688_collection_task",
                "SET is_deleted = b'1', current_task_key = NULL",
                "WHERE source_collection_id = " + sourceCollectionIdToken + ";"
        );
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    public static final class FixedSample {
        private Long sourceCollectionId;
        private String collectionNo;
        private Long ownerUserId;
        private Long logicalStoreId;
        private String sourcePlatform;
        private String sourceUrl;
        private String sourceTitle;
        private String sourceImageUrl;

        public void setSourceCollectionId(Long sourceCollectionId) {
            this.sourceCollectionId = sourceCollectionId;
        }

        public void setCollectionNo(String collectionNo) {
            this.collectionNo = collectionNo;
        }

        public void setOwnerUserId(Long ownerUserId) {
            this.ownerUserId = ownerUserId;
        }

        public void setLogicalStoreId(Long logicalStoreId) {
            this.logicalStoreId = logicalStoreId;
        }

        public void setSourcePlatform(String sourcePlatform) {
            this.sourcePlatform = sourcePlatform;
        }

        public void setSourceUrl(String sourceUrl) {
            this.sourceUrl = sourceUrl;
        }

        public void setSourceTitle(String sourceTitle) {
            this.sourceTitle = sourceTitle;
        }

        public void setSourceImageUrl(String sourceImageUrl) {
            this.sourceImageUrl = sourceImageUrl;
        }
    }

    public static final class BrowserGatewayBoundary {
        private String gatewayServiceKind;
        private String endpointUrl;
        private String sessionState;
        private String captchaBoundaryMode;
        private boolean captchaAutoSolveEnabled;
        private String confirmedBy;
        private String providerTraceId;
        private String diagnosticNote;
        private boolean usesPluginBridge;
        private boolean usesLocalSmokeBridge;
        private boolean usesManualPayload;

        public void setGatewayServiceKind(String gatewayServiceKind) {
            this.gatewayServiceKind = gatewayServiceKind;
        }

        public void setEndpointUrl(String endpointUrl) {
            this.endpointUrl = endpointUrl;
        }

        public void setSessionState(String sessionState) {
            this.sessionState = sessionState;
        }

        public void setCaptchaBoundaryMode(String captchaBoundaryMode) {
            this.captchaBoundaryMode = captchaBoundaryMode;
        }

        public void setCaptchaAutoSolveEnabled(boolean captchaAutoSolveEnabled) {
            this.captchaAutoSolveEnabled = captchaAutoSolveEnabled;
        }

        public void setConfirmedBy(String confirmedBy) {
            this.confirmedBy = confirmedBy;
        }

        public void setProviderTraceId(String providerTraceId) {
            this.providerTraceId = providerTraceId;
        }

        public void setDiagnosticNote(String diagnosticNote) {
            this.diagnosticNote = diagnosticNote;
        }

        public void setUsesPluginBridge(boolean usesPluginBridge) {
            this.usesPluginBridge = usesPluginBridge;
        }

        public void setUsesLocalSmokeBridge(boolean usesLocalSmokeBridge) {
            this.usesLocalSmokeBridge = usesLocalSmokeBridge;
        }

        public void setUsesManualPayload(boolean usesManualPayload) {
            this.usesManualPayload = usesManualPayload;
        }
    }

    public static final class Plan {
        private final boolean ready;
        private final List<String> failures;
        private final String sourceCollectionIdToken;
        private final String expectedOutcome;
        private final String expectedGatewayErrorCode;
        private final Map<String, String> environment;
        private final Map<String, Object> boundaryRecord;
        private final Map<String, Object> sampleRecord;
        private final String createSampleSql;
        private final String isolationSql;
        private final String cleanupSql;

        private Plan(
                boolean ready,
                List<String> failures,
                String sourceCollectionIdToken,
                String expectedOutcome,
                String expectedGatewayErrorCode,
                Map<String, String> environment,
                Map<String, Object> boundaryRecord,
                Map<String, Object> sampleRecord,
                String createSampleSql,
                String isolationSql,
                String cleanupSql
        ) {
            this.ready = ready;
            this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
            this.sourceCollectionIdToken = sourceCollectionIdToken;
            this.expectedOutcome = expectedOutcome;
            this.expectedGatewayErrorCode = expectedGatewayErrorCode;
            this.environment = Collections.unmodifiableMap(new LinkedHashMap<>(environment));
            this.boundaryRecord = Collections.unmodifiableMap(new LinkedHashMap<>(boundaryRecord));
            this.sampleRecord = Collections.unmodifiableMap(new LinkedHashMap<>(sampleRecord));
            this.createSampleSql = createSampleSql;
            this.isolationSql = isolationSql;
            this.cleanupSql = cleanupSql;
        }

        public boolean isReady() {
            return ready;
        }

        public List<String> getFailures() {
            return failures;
        }

        public String getSourceCollectionIdToken() {
            return sourceCollectionIdToken;
        }

        public String getExpectedOutcome() {
            return expectedOutcome;
        }

        public String getExpectedGatewayErrorCode() {
            return expectedGatewayErrorCode;
        }

        public Map<String, String> getEnvironment() {
            return environment;
        }

        public Map<String, Object> getBoundaryRecord() {
            return boundaryRecord;
        }

        public Map<String, Object> getSampleRecord() {
            return sampleRecord;
        }

        public String getCreateSampleSql() {
            return createSampleSql;
        }

        public String getIsolationSql() {
            return isolationSql;
        }

        public String getCleanupSql() {
            return cleanupSql;
        }
    }
}
