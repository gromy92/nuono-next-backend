package com.nuono.next.noonsync;

import com.nuono.next.sales.SalesSyncTaskCommand;
import com.nuono.next.sales.SalesSyncTaskRecord;
import com.nuono.next.sales.SalesSyncTaskService;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class NoonSalesSyncBridgeService {

    private final NoonSyncFoundationService foundationService;
    private final SalesSyncTaskService salesSyncTaskService;

    public NoonSalesSyncBridgeService(
            NoonSyncFoundationService foundationService,
            SalesSyncTaskService salesSyncTaskService
    ) {
        this.foundationService = foundationService;
        this.salesSyncTaskService = salesSyncTaskService;
    }

    public NoonSalesSyncRunResult runScheduledDaily(NoonSalesDailySyncCommand command) {
        LocalDate targetDay = command.getLatestAvailableSalesDay();
        NoonSyncTask foundationTask = createAndRunFoundationTask(
                command.getScope(),
                NoonSyncTriggerMode.SCHEDULED_DAILY,
                NoonSyncTarget.dateRange(targetDay, targetDay)
        );
        SalesSyncTaskRecord salesTask = salesSyncTaskService.triggerAndRun(new SalesSyncTaskCommand(
                command.getScope().getOwnerUserId(),
                command.getScope().getLogicalStoreId(),
                command.getScope().getStoreCode(),
                command.getScope().getSiteCode(),
                targetDay,
                targetDay,
                command.getRequestedBy(),
                "scheduled_daily"
        ));
        return new NoonSalesSyncRunResult(finishFoundationTask(foundationTask, salesTask), salesTask);
    }

    public NoonSalesSyncRunResult runHistoricalBackfill(NoonSalesBackfillSyncCommand command) {
        rejectScheduledCorrectionBackfill(command.getReason());
        NoonSyncTask foundationTask = createAndRunFoundationTask(
                command.getScope(),
                NoonSyncTriggerMode.GAP_BACKFILL,
                NoonSyncTarget.dateRange(command.getDateFrom(), command.getDateTo())
        );
        SalesSyncTaskRecord salesTask = salesSyncTaskService.triggerAndRun(new SalesSyncTaskCommand(
                command.getScope().getOwnerUserId(),
                command.getScope().getLogicalStoreId(),
                command.getScope().getStoreCode(),
                command.getScope().getSiteCode(),
                command.getDateFrom(),
                command.getDateTo(),
                command.getRequestedBy(),
                triggerTypeFor(command.getReason())
        ));
        return new NoonSalesSyncRunResult(finishFoundationTask(foundationTask, salesTask), salesTask);
    }

    public NoonSalesSyncRunResult runLowFrequencyCorrection(NoonSalesCorrectionSyncCommand command) {
        NoonSyncTask foundationTask = createAndRunFoundationTask(
                command.getScope(),
                NoonSyncTriggerMode.LOW_FREQUENCY_CORRECTION,
                NoonSyncTarget.dateRange(command.getDateFrom(), command.getDateTo())
        );
        SalesSyncTaskRecord salesTask = salesSyncTaskService.triggerAndRun(new SalesSyncTaskCommand(
                command.getScope().getOwnerUserId(),
                command.getScope().getLogicalStoreId(),
                command.getScope().getStoreCode(),
                command.getScope().getSiteCode(),
                command.getDateFrom(),
                command.getDateTo(),
                command.getRequestedBy(),
                "low_frequency_correction"
        ));
        return new NoonSalesSyncRunResult(finishFoundationTask(foundationTask, salesTask), salesTask);
    }

    private NoonSyncTask createAndRunFoundationTask(
            NoonSyncScope scope,
            NoonSyncTriggerMode triggerMode,
            NoonSyncTarget target
    ) {
        NoonSyncTask task = foundationService.createTask(new NoonSyncTaskRequest(
                NoonSyncDataDomain.SALES,
                triggerMode,
                scope,
                target,
                NoonSyncRetryPolicy.RETRYABLE
        ));
        return foundationService.markRunning(task);
    }

    private NoonSyncTask finishFoundationTask(NoonSyncTask foundationTask, SalesSyncTaskRecord salesTask) {
        String status = normalize(salesTask.getStatus());
        String failureReason = normalize(salesTask.getFailureReason());
        String diagnostic = diagnosticSummary(salesTask);

        if ("succeeded".equals(status)) {
            return foundationService.markSucceeded(foundationTask.getId());
        }
        if ("empty".equals(status) || "empty".equals(failureReason) || zeroRows(salesTask)) {
            return foundationService.markPartial(
                    foundationTask.getId(),
                    NoonSyncFailureReason.EMPTY_REPORT,
                    NoonSyncRetryPolicy.NON_RETRYABLE,
                    NoonSyncDiagnostic.safe(null, diagnostic)
            );
        }
        if (isMissingColumnFailure(failureReason)) {
            return foundationService.markFailed(
                    foundationTask.getId(),
                    NoonSyncFailureReason.MISSING_COLUMNS,
                    NoonSyncRetryPolicy.NON_RETRYABLE,
                    NoonSyncDiagnostic.safe(null, diagnostic)
            );
        }
        if ("imported_with_exceptions".equals(status) || "imported_with_exceptions".equals(failureReason)) {
            return foundationService.markPartial(
                    foundationTask.getId(),
                    NoonSyncFailureReason.MAPPING_FAILED,
                    NoonSyncRetryPolicy.NON_RETRYABLE,
                    NoonSyncDiagnostic.safe(null, diagnostic)
            );
        }
        if (isProviderUnavailable(failureReason)) {
            return foundationService.markFailed(
                    foundationTask.getId(),
                    NoonSyncFailureReason.PROVIDER_UNAVAILABLE,
                    NoonSyncRetryPolicy.RETRYABLE,
                    NoonSyncDiagnostic.safe(null, diagnostic)
            );
        }
        if (allRowsFailed(salesTask)) {
            return foundationService.markFailed(
                    foundationTask.getId(),
                    NoonSyncFailureReason.MAPPING_FAILED,
                    NoonSyncRetryPolicy.NON_RETRYABLE,
                    NoonSyncDiagnostic.safe(null, diagnostic)
            );
        }
        return foundationService.markFailed(
                foundationTask.getId(),
                NoonSyncFailureReason.UNKNOWN_FAILURE,
                NoonSyncRetryPolicy.RETRYABLE,
                NoonSyncDiagnostic.safe(null, diagnostic)
        );
    }

    private void rejectScheduledCorrectionBackfill(NoonSalesBackfillReason reason) {
        if (reason == NoonSalesBackfillReason.SCHEDULED_CORRECTION) {
            throw new IllegalArgumentException("Scheduled correction must use the low-frequency correction issue, not N04 backfill.");
        }
    }

    private String triggerTypeFor(NoonSalesBackfillReason reason) {
        if (reason == NoonSalesBackfillReason.ONBOARDING) {
            return "onboarding_backfill";
        }
        if (reason == NoonSalesBackfillReason.NO_DATA_SCOPE) {
            return "no_data_backfill";
        }
        return "gap_backfill";
    }

    private boolean isMissingColumnFailure(String failureReason) {
        return failureReason.contains("missing noon product views and sales report columns")
                || failureReason.contains("missing columns");
    }

    private boolean isProviderUnavailable(String failureReason) {
        return failureReason.contains("provider unavailable")
                || failureReason.contains("unavailable");
    }

    private boolean zeroRows(SalesSyncTaskRecord salesTask) {
        Integer totalRows = salesTask.getTotalRows();
        return totalRows != null && totalRows == 0;
    }

    private boolean allRowsFailed(SalesSyncTaskRecord salesTask) {
        Integer totalRows = salesTask.getTotalRows();
        Integer failureRows = salesTask.getFailureRows();
        return totalRows != null && totalRows > 0 && totalRows.equals(failureRows);
    }

    private String diagnosticSummary(SalesSyncTaskRecord salesTask) {
        if (salesTask.getFailureReason() != null && !salesTask.getFailureReason().isBlank()) {
            return salesTask.getFailureReason();
        }
        return "Sales sync task " + salesTask.getId() + " finished with status " + salesTask.getStatus() + ".";
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
