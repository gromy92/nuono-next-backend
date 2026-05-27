package com.nuono.next.noonpull;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NoonBusinessReadinessService {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final NoonPullRepository repository;
    private final Clock clock;

    @Autowired
    public NoonBusinessReadinessService(NoonPullRepository repository) {
        this(repository, Clock.system(SHANGHAI));
    }

    public NoonBusinessReadinessService(NoonPullRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock == null ? Clock.system(SHANGHAI) : clock.withZone(SHANGHAI);
    }

    public NoonBusinessReadinessView readiness(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            NoonPullDataDomain dataDomain
    ) {
        NoonPullTaskRecord latest = repository.listTasks().stream()
                .filter((task) -> ownerUserId != null && ownerUserId.equals(task.getOwnerUserId()))
                .filter((task) -> equalsNormalized(storeCode, task.getStoreCode()))
                .filter((task) -> equalsNormalized(siteCode, task.getSiteCode()))
                .filter((task) -> task.getDataDomain() == dataDomain)
                .max(Comparator.comparing(NoonPullTaskRecord::getId))
                .orElse(null);
        NoonBusinessReadinessView view = new NoonBusinessReadinessView();
        view.setDataDomain(dataDomain);
        if (latest == null) {
            view.setQualityState("backfill_required");
            view.setMetricsAllowed(false);
            return view;
        }
        view.setTaskStatus(latest.getStatus() == null ? null : latest.getStatus().name());
        view.setFailureType(latest.getFailureType());
        view.setSourceBatchId(latest.getSourceBatchId());
        view.setQualityState(toQualityState(latest));
        view.setMetricsAllowed("ready".equals(view.getQualityState()));
        return view;
    }

    private String toQualityState(NoonPullTaskRecord task) {
        if (task.getStatus() == NoonPullTaskStatus.QUEUED || task.getStatus() == NoonPullTaskStatus.RUNNING) {
            return isBackfill(task.getTriggerMode()) ? "backfill_running" : "sync_in_progress";
        }
        if (task.getStatus() == NoonPullTaskStatus.PARTIAL) {
            if (StringUtils.hasText(task.getFailureType())) {
                return task.getFailureType();
            }
            if (StringUtils.hasText(task.getReadinessState())) {
                return task.getReadinessState();
            }
            return "partial_success";
        }
        if (task.getStatus() == NoonPullTaskStatus.SUCCEEDED) {
            return isStale(task) ? "stale" : "ready";
        }
        if (task.getStatus() == NoonPullTaskStatus.FAILED) {
            String failureType = task.getFailureType();
            if (isBusinessQualityState(failureType)) {
                return failureType;
            }
            if (isBackfill(task.getTriggerMode())) {
                return "backfill_failed";
            }
            return StringUtils.hasText(failureType) ? failureType : "failed";
        }
        return "backfill_required";
    }

    private boolean isStale(NoonPullTaskRecord task) {
        if (task.getDataDomain() != NoonPullDataDomain.SALES && task.getDataDomain() != NoonPullDataDomain.ORDER) {
            return false;
        }
        LocalDate expectedLatestDate = LocalDate.now(clock).minusDays(1);
        return task.getTargetDateTo() != null && task.getTargetDateTo().isBefore(expectedLatestDate);
    }

    private boolean isBackfill(NoonPullTriggerMode triggerMode) {
        return triggerMode == NoonPullTriggerMode.GAP_BACKFILL || triggerMode == NoonPullTriggerMode.MANUAL_BACKFILL;
    }

    private boolean isBusinessQualityState(String failureType) {
        return "empty_report".equals(failureType)
                || "empty_report_pending_confirmation".equals(failureType)
                || "missing_columns".equals(failureType)
                || "mapping_failed".equals(failureType)
                || "provider_unavailable".equals(failureType)
                || "report_not_ready".equals(failureType)
                || "provider_retention_limit".equals(failureType)
                || "partial_success".equals(failureType);
    }

    private boolean equalsNormalized(String expected, String actual) {
        if (expected == null) {
            return actual == null;
        }
        return expected.trim().equalsIgnoreCase(actual == null ? null : actual.trim());
    }
}
