package com.nuono.next.noonsync;

import com.nuono.next.sales.SalesDataQualityState;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class NoonBusinessSyncStatusService {

    private static final int STALE_AFTER_DAYS = 2;

    private final Clock clock;

    public NoonBusinessSyncStatusService() {
        this(Clock.systemDefaultZone());
    }

    public NoonBusinessSyncStatusService(Clock clock) {
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    public NoonSalesSyncReadModel describeSalesSurface(NoonSalesSurfaceSyncInput input) {
        List<NoonSyncTask> tasks = salesTasksFor(input.getScope());
        NoonSalesCorrectionSurfaceState correctionState = correctionState(input, tasks);
        List<NoonSyncRequiredWork> correctionWork = correctionRequiredWork(correctionState);
        NoonSyncTask latestBackfill = latestTask(tasks, NoonSyncTriggerMode.GAP_BACKFILL);
        if (latestBackfill != null && latestBackfill.getStatus() == NoonSyncTaskStatus.RUNNING) {
            return model(NoonSalesSyncSurfaceState.BACKFILL_RUNNING, correctionState, input, latestBackfill, false, false, correctionWork);
        }
        if (latestBackfill != null && latestBackfill.getStatus() == NoonSyncTaskStatus.QUEUED) {
            return model(NoonSalesSyncSurfaceState.BACKFILL_RUNNING, correctionState, input, latestBackfill, false, false, correctionWork);
        }
        if (latestBackfill != null && latestBackfill.getStatus() == NoonSyncTaskStatus.FAILED) {
            return model(NoonSalesSyncSurfaceState.BACKFILL_FAILED, correctionState, input, latestBackfill, false, false, withBackfillWork(correctionWork, input));
        }

        NoonSyncTask latestSalesTask = latestTask(tasks, null);
        if (input.getLatestAvailableSalesDate() == null) {
            NoonSalesSyncSurfaceState qualityState = qualityStateWithoutFacts(input, latestSalesTask);
            if (qualityState == NoonSalesSyncSurfaceState.EMPTY_REPORT
                    || qualityState == NoonSalesSyncSurfaceState.MISSING_MAPPING) {
                return model(qualityState, correctionState, input, latestSalesTask, false, false, correctionWork);
            }
            return model(
                    NoonSalesSyncSurfaceState.NO_DATA_BACKFILL_REQUIRED,
                    correctionState,
                    input,
                    latestSalesTask,
                    false,
                    false,
                    withBackfillWork(correctionWork, input)
            );
        }

        if (isStale(input.getLatestAvailableSalesDate(), input.getAnalysisDate())) {
            return model(NoonSalesSyncSurfaceState.STALE_LATEST_SALES, correctionState, input, latestSalesTask, false, false, correctionWork);
        }
        if (latestSalesTask != null && latestSalesTask.isActive()) {
            return model(NoonSalesSyncSurfaceState.SYNC_IN_PROGRESS, correctionState, input, latestSalesTask, true, true, correctionWork);
        }
        return model(NoonSalesSyncSurfaceState.READY, correctionState, input, latestSalesTask, true, true, correctionWork);
    }

    private NoonSalesSyncReadModel model(
            NoonSalesSyncSurfaceState state,
            NoonSalesCorrectionSurfaceState correctionState,
            NoonSalesSurfaceSyncInput input,
            NoonSyncTask task,
            boolean dataSufficient,
            boolean businessMetricsAllowed,
            List<NoonSyncRequiredWork> requiredWork
    ) {
        return new NoonSalesSyncReadModel(
                state,
                correctionState,
                input.getLatestAvailableSalesDate(),
                task == null ? null : task.getStatus(),
                task == null ? null : task.getFailureReason(),
                task == null ? null : task.getDiagnosticSummary(),
                dataSufficient,
                businessMetricsAllowed,
                requiredWork,
                sourceBatchIds(input)
        );
    }

    private List<Long> sourceBatchIds(NoonSalesSurfaceSyncInput input) {
        return input.getQualityStates().stream()
                .map(SalesDataQualityState::getSourceBatchId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private NoonSalesSyncSurfaceState qualityStateWithoutFacts(NoonSalesSurfaceSyncInput input, NoonSyncTask latestSalesTask) {
        if (latestSalesTask != null) {
            if (latestSalesTask.getFailureReason() == NoonSyncFailureReason.EMPTY_REPORT) {
                return NoonSalesSyncSurfaceState.EMPTY_REPORT;
            }
            if (latestSalesTask.getFailureReason() == NoonSyncFailureReason.MISSING_COLUMNS
                    || latestSalesTask.getFailureReason() == NoonSyncFailureReason.MAPPING_FAILED) {
                return NoonSalesSyncSurfaceState.MISSING_MAPPING;
            }
        }
        for (SalesDataQualityState qualityState : input.getQualityStates()) {
            if ("empty_report".equals(qualityState.getCode())) {
                return NoonSalesSyncSurfaceState.EMPTY_REPORT;
            }
            if ("import_failed".equals(qualityState.getCode())
                    || "data_quality_exceptions".equals(qualityState.getCode())) {
                return NoonSalesSyncSurfaceState.MISSING_MAPPING;
            }
        }
        return NoonSalesSyncSurfaceState.NO_DATA_BACKFILL_REQUIRED;
    }

    private NoonSalesCorrectionSurfaceState correctionState(NoonSalesSurfaceSyncInput input, List<NoonSyncTask> tasks) {
        NoonSyncTask correctionTask = latestTask(tasks, NoonSyncTriggerMode.LOW_FREQUENCY_CORRECTION);
        if (correctionTask != null && correctionTask.getStatus() == NoonSyncTaskStatus.FAILED) {
            return NoonSalesCorrectionSurfaceState.CORRECTION_FAILED;
        }
        if (input.isCorrectionOverdue()) {
            return NoonSalesCorrectionSurfaceState.CORRECTION_OVERDUE;
        }
        return NoonSalesCorrectionSurfaceState.OK;
    }

    private List<NoonSyncRequiredWork> withBackfillWork(List<NoonSyncRequiredWork> existing, NoonSalesSurfaceSyncInput input) {
        List<NoonSyncRequiredWork> work = new ArrayList<>(existing);
        work.add(new NoonSyncRequiredWork(
                "sales_backfill",
                NoonSyncDataDomain.SALES,
                NoonSyncTriggerMode.GAP_BACKFILL,
                NoonSyncTarget.dateRange(null, input.getAnalysisDate())
        ));
        return work;
    }

    private List<NoonSyncRequiredWork> correctionRequiredWork(NoonSalesCorrectionSurfaceState state) {
        if (state == NoonSalesCorrectionSurfaceState.OK) {
            return List.of();
        }
        return List.of(new NoonSyncRequiredWork(
                "sales_low_frequency_correction",
                NoonSyncDataDomain.SALES,
                NoonSyncTriggerMode.LOW_FREQUENCY_CORRECTION,
                null
        ));
    }

    private boolean isStale(LocalDate latestAvailableSalesDate, LocalDate analysisDate) {
        LocalDate reference = analysisDate == null ? LocalDate.now(clock) : analysisDate;
        return latestAvailableSalesDate.isBefore(reference.minusDays(STALE_AFTER_DAYS));
    }

    private List<NoonSyncTask> salesTasksFor(NoonSyncScope scope) {
        // NoonSyncFoundation 未接入真实写入链路（前端 0 引用、无生产写入方），原 listTasks() 在生产恒空。
        // 退耦后销量同步表面状态完全由真实 input（最新销量日期 / 数据质量 / 纠偏是否逾期）派生，
        // 行为与退耦前一致：task 相关分支（BACKFILL_*、SYNC_IN_PROGRESS、CORRECTION_FAILED）本就永不触发。
        return List.of();
    }

    private NoonSyncTask latestTask(List<NoonSyncTask> tasks, NoonSyncTriggerMode triggerMode) {
        for (NoonSyncTask task : tasks) {
            if (triggerMode == null || task.getTriggerMode() == triggerMode) {
                return task;
            }
        }
        return null;
    }
}
