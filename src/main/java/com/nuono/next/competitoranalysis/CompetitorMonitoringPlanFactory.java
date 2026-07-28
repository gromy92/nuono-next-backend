package com.nuono.next.competitoranalysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

final class CompetitorMonitoringPlanFactory {
    private static final String STORE_NATURAL_KEY_PREFIX = "store:";
    private static final String CYCLE_NATURAL_KEY_PREFIX = "cycle:";
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Clock clock;

    CompetitorMonitoringPlanFactory(Clock clock) {
        this.clock = clock;
    }

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

    CompetitorMonitoringCheckpoint cycleCheckpoint(
            String batchKey,
            CompetitorRefreshExecutionMode mode,
            CompetitorMonitoringBoundaryRow boundary,
            CompetitorWatchProductScopeRow upper
    ) {
        CompetitorMonitoringCheckpoint checkpoint = baseCheckpoint("CYCLE", batchKey, mode);
        checkpoint.setEligibleScopeTotal(eligibleTotal(boundary));
        checkpoint.setUpperWatchProductId(boundary.getUpperWatchProductId());
        checkpoint.setUpperScopeOwnerUserId(upper.getOwnerUserId());
        checkpoint.setUpperScopeStoreCode(upper.getStoreCode());
        checkpoint.setUpperScopeSiteCode(upper.getSiteCode());
        return checkpoint;
    }

    String cycleNaturalKey(CompetitorRefreshExecutionMode mode) {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(SHANGHAI);
        String slot;
        if (mode == CompetitorRefreshExecutionMode.SCHEDULED_DETAIL) {
            slot = now.toLocalDate().toString();
        } else {
            int slotHour = (now.getHour() / 6) * 6;
            slot = now.withHour(slotHour).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH"));
        }
        return CYCLE_NATURAL_KEY_PREFIX + mode.taskKey() + ":" + slot;
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

    int completedScopes(String json) {
        try {
            return (int) CompetitorMonitoringCheckpoint.fromJson(json).getCompletedScopeCount();
        } catch (RuntimeException exception) {
            return 0;
        }
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
