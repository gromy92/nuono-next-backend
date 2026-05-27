package com.nuono.next.productselection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.springframework.util.StringUtils;

public final class Ali1688GatewaySmokeIntegrationGate {

    private static final String SCHEDULER_STEP =
            "LocalDbAli1688CollectionService.processQueuedTasksOnce or enabled scheduler";
    private static final List<String> FORBIDDEN_PATHS = Collections.unmodifiableList(Arrays.asList(
            "Chrome plugin button",
            "local smoke bridge",
            "manual payload sending",
            "direct candidate SQL insert"
    ));

    private Ali1688GatewaySmokeIntegrationGate() {
    }

    public static RunPlan from(Ali1688GatewaySmokePreparation.Plan preparation) {
        List<String> failures = new ArrayList<>();
        if (preparation == null) {
            failures.add("smoke preparation plan is required");
            return failed(failures);
        }
        failures.addAll(preparation.getFailures());
        String sourceCollectionId = normalize(preparation.getSourceCollectionIdToken());
        if (!isNumeric(sourceCollectionId)) {
            failures.add("fixed source collection ID is required for real gateway smoke run");
        }
        String expectedOutcome = normalize(preparation.getExpectedOutcome());
        if (!StringUtils.hasText(expectedOutcome)) {
            failures.add("expected gateway smoke outcome is required");
        }

        return new RunPlan(
                failures.isEmpty(),
                failures,
                sourceCollectionId,
                expectedOutcome,
                normalize(preparation.getExpectedGatewayErrorCode()),
                apiStep("POST", "/api/product-selection/source-collections/" + sourceCollectionId + "/ali1688/recollect"),
                apiStep("GET", "/api/product-selection/source-collections/" + sourceCollectionId + "/ali1688"),
                apiStep("GET", "/api/product-selection/ali1688-collections"),
                SCHEDULER_STEP,
                postRunDbAssertionSql(),
                FORBIDDEN_PATHS
        );
    }

    private static RunPlan failed(List<String> failures) {
        return new RunPlan(
                false,
                failures,
                "",
                "",
                "",
                apiStep("", ""),
                apiStep("", ""),
                apiStep("", ""),
                SCHEDULER_STEP,
                postRunDbAssertionSql(),
                FORBIDDEN_PATHS
        );
    }

    private static ApiStep apiStep(String method, String path) {
        return new ApiStep(method, path);
    }

    private static boolean isNumeric(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static String postRunDbAssertionSql() {
        return String.join("\n",
                "SELECT 'procurement_candidate' AS table_name, COUNT(*) AS row_count FROM procurement_candidate",
                "UNION ALL SELECT 'procurement_order', COUNT(*) FROM procurement_order",
                "UNION ALL SELECT 'procurement_demand_item', COUNT(*) FROM procurement_demand_item",
                "UNION ALL SELECT 'procurement_auto_inquiry_task', COUNT(*) FROM procurement_auto_inquiry_task",
                "UNION ALL SELECT 'procurement_auto_inquiry_session', COUNT(*) FROM procurement_auto_inquiry_session",
                "UNION ALL SELECT 'procurement_auto_inquiry_event', COUNT(*) FROM procurement_auto_inquiry_event",
                "UNION ALL SELECT 'procurement_candidate_pool', COUNT(*) FROM procurement_candidate_pool",
                "UNION ALL SELECT 'procurement_candidate_pool_item', COUNT(*) FROM procurement_candidate_pool_item",
                "UNION ALL SELECT 'procurement_final_candidate', COUNT(*) FROM procurement_final_candidate;"
        );
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    public static final class ApiStep {
        private final String method;
        private final String path;

        private ApiStep(String method, String path) {
            this.method = method;
            this.path = path;
        }

        public String getMethod() {
            return method;
        }

        public String getPath() {
            return path;
        }
    }

    public static final class RunPlan {
        private final boolean ready;
        private final List<String> failures;
        private final String sourceCollectionId;
        private final String expectedOutcome;
        private final String expectedGatewayErrorCode;
        private final ApiStep triggerStep;
        private final ApiStep detailReadStep;
        private final ApiStep workbenchReadStep;
        private final String schedulerStep;
        private final String postRunDbAssertionSql;
        private final List<String> forbiddenPaths;

        private RunPlan(
                boolean ready,
                List<String> failures,
                String sourceCollectionId,
                String expectedOutcome,
                String expectedGatewayErrorCode,
                ApiStep triggerStep,
                ApiStep detailReadStep,
                ApiStep workbenchReadStep,
                String schedulerStep,
                String postRunDbAssertionSql,
                List<String> forbiddenPaths
        ) {
            this.ready = ready;
            this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
            this.sourceCollectionId = sourceCollectionId;
            this.expectedOutcome = expectedOutcome;
            this.expectedGatewayErrorCode = expectedGatewayErrorCode;
            this.triggerStep = triggerStep;
            this.detailReadStep = detailReadStep;
            this.workbenchReadStep = workbenchReadStep;
            this.schedulerStep = schedulerStep;
            this.postRunDbAssertionSql = postRunDbAssertionSql;
            this.forbiddenPaths = Collections.unmodifiableList(new ArrayList<>(forbiddenPaths));
        }

        public boolean isReady() {
            return ready;
        }

        public List<String> getFailures() {
            return failures;
        }

        public String getSourceCollectionId() {
            return sourceCollectionId;
        }

        public String getExpectedOutcome() {
            return expectedOutcome;
        }

        public String getExpectedGatewayErrorCode() {
            return expectedGatewayErrorCode;
        }

        public ApiStep getTriggerStep() {
            return triggerStep;
        }

        public ApiStep getDetailReadStep() {
            return detailReadStep;
        }

        public ApiStep getWorkbenchReadStep() {
            return workbenchReadStep;
        }

        public String getSchedulerStep() {
            return schedulerStep;
        }

        public String getPostRunDbAssertionSql() {
            return postRunDbAssertionSql;
        }

        public List<String> getForbiddenPaths() {
            return forbiddenPaths;
        }
    }
}
