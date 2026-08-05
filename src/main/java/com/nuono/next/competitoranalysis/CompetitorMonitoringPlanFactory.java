package com.nuono.next.competitoranalysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;

final class CompetitorMonitoringPlanFactory {
    private static final String STORE_NATURAL_KEY_PREFIX = "store:";
    private static final ObjectMapper JSON = new ObjectMapper();

    CompetitorMonitoringCheckpoint storeCheckpoint(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            Long requestedBy,
            CompetitorRefreshExecutionMode mode,
            CompetitorMonitoringBoundaryRow boundary
    ) {
        CompetitorMonitoringCheckpoint checkpoint = baseCheckpoint(
                "STORE",
                UUID.randomUUID().toString(),
                mode
        );
        checkpoint.setRequestedBy(requestedBy);
        checkpoint.setCurrentOwnerUserId(ownerUserId);
        checkpoint.setCurrentStoreCode(storeCode);
        checkpoint.setCurrentSiteCode(siteCode);
        checkpoint.setEligibleProductTotal(eligibleTotal(boundary));
        checkpoint.setUpperWatchProductId(boundary.getUpperWatchProductId());
        return checkpoint;
    }

    String storeNaturalKey(Long owner, String store, String site, CompetitorRefreshExecutionMode mode) {
        String base = STORE_NATURAL_KEY_PREFIX + owner + ":" + store + ":" + site;
        return mode == CompetitorRefreshExecutionMode.FULL_MANUAL_MONITOR
                ? base
                : base + ":" + mode.taskKey();
    }

    CompetitorRefreshExecutionMode legacyStoreMode(String payloadJson) {
        try {
            JsonNode payload = JSON.readTree(payloadJson);
            if (payload == null
                    || payload.hasNonNull("batchKind")
                    || !payload.has("watchProductTotal")) {
                return null;
            }
            String triggerMode = payload.path("triggerMode").asText("");
            String executionMode = payload.path("executionMode").asText("");
            for (CompetitorRefreshExecutionMode mode : CompetitorRefreshExecutionMode.values()) {
                if (mode != CompetitorRefreshExecutionMode.FULL_MANUAL
                        && mode.triggerMode().equals(triggerMode)
                        && mode.taskKey().equals(executionMode)) {
                    return mode;
                }
            }
            return null;
        } catch (RuntimeException | java.io.IOException exception) {
            return null;
        }
    }

    long eligibleTotal(CompetitorMonitoringBoundaryRow boundary) {
        return boundary.getEligibleTotal() == null ? 0L : boundary.getEligibleTotal();
    }

    private CompetitorMonitoringCheckpoint baseCheckpoint(
            String kind,
            String batchKey,
            CompetitorRefreshExecutionMode mode
    ) {
        CompetitorMonitoringCheckpoint checkpoint = new CompetitorMonitoringCheckpoint();
        checkpoint.setBatchKind(kind);
        checkpoint.setBatchKey(batchKey);
        checkpoint.setTriggerMode(mode.triggerMode());
        checkpoint.setExecutionMode(mode.taskKey());
        return checkpoint;
    }
}
