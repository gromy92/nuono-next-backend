package com.nuono.next.noonpull;

import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportQueryService;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnReceivedReportImportService;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.FbnExportCreateCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.FbnReceivedImportCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.FbnExportCreateView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.FbnExportStatusView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.FbnReceivedImportResultView;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.time.Duration;
import java.util.Optional;
import org.springframework.util.StringUtils;

/** Executes export, poll and import for legacy official-warehouse FBN receipts. */
final class LegacyNoonFbnReceivedTaskExecutor implements LegacyNoonTaskExecutor {
    private static final int MAX_POLL_ATTEMPTS = 18;
    private static final Duration POLL_DELAY = Duration.ofMinutes(20);
    private static final String REPORT_TYPE = "fbn_inbound_fbnreceivedreport";

    private final NoonPullFoundationService foundationService;
    private final OfficialWarehouseFbnExportQueryService exportService;
    private final OfficialWarehouseFbnReceivedReportImportService importService;
    private final NoonRiskBackoffGuard riskBackoffGuard;
    private final LegacyNoonPullFailureRecorder failureRecorder;

    LegacyNoonFbnReceivedTaskExecutor(
            NoonPullFoundationService foundationService,
            OfficialWarehouseFbnExportQueryService exportService,
            OfficialWarehouseFbnReceivedReportImportService importService,
            NoonRiskBackoffGuard riskBackoffGuard,
            LegacyNoonPullFailureRecorder failureRecorder
    ) {
        this.foundationService = foundationService;
        this.exportService = exportService;
        this.importService = importService;
        this.riskBackoffGuard = riskBackoffGuard == null
                ? NoonRiskBackoffGuard.disabled() : riskBackoffGuard;
        this.failureRecorder = failureRecorder;
    }

    @Override
    public boolean accepts(NoonPullTaskRecord task) {
        return task != null
                && task.getPullType() == NoonPullType.REPORT
                && task.getDataDomain()
                    == NoonPullDataDomain.OFFICIAL_WAREHOUSE_FBN_RECEIVED;
    }

    @Override
    public void execute(
            NoonPullTaskRecord task,
            NoonPullScheduledExecutionResult result
    ) {
        if (exportService == null || importService == null) {
            foundationService.markFailedWithPolicy(
                    task.getId(),
                    "provider not configured: scheduled official warehouse FBN received report service is disabled",
                    1
            );
            result.failed();
            return;
        }
        NoonReportPullRequest request = request(task);
        NoonRiskBackoffScope riskScope = NoonRiskBackoffScope.report(request);
        Optional<NoonRiskBackoffHold> activeHold = riskBackoffGuard.currentHold(riskScope);
        if (activeHold.isPresent()) {
            foundationService.recordReportRiskBackoffDelay(
                    task.getId(), activeHold.get(), request.descriptor()
            );
            result.failed();
            return;
        }
        NoonPullTaskRecord running = foundationService.markRunning(
                task.getId(), "official-warehouse-fbn-received-report"
        );
        if (running.getStatus() == NoonPullTaskStatus.BLOCKED_AUTH) {
            result.skipped();
            return;
        }
        String exportCode = running.getReportExportId();
        int pollAttempts = running.getReportPollAttempts() == null
                ? 0 : running.getReportPollAttempts();
        try {
            BusinessAccessContext access = LegacyNoonTaskContext.businessAccess(task);
            if (!StringUtils.hasText(exportCode)) {
                exportCode = createExport(task, access, result);
                if (!StringUtils.hasText(exportCode)) {
                    return;
                }
                running = foundationService.recordReportExportCreated(
                        task.getId(), exportCode,
                        request.descriptor() + "; exportCreated=true; exportCode="
                                + exportCode
                );
                pollAttempts = running.getReportPollAttempts() == null
                        ? 0 : running.getReportPollAttempts();
            }
            pollAttempts++;
            NoonReportExportStatus status = poll(
                    task, access, exportCode, pollAttempts
            );
            if (status.isFailed()) {
                foundationService.markFailedWithPolicy(
                        task.getId(),
                        "provider unavailable: FBN received export failed "
                                + status.getMessage(),
                        pollAttempts
                );
                result.failed();
                return;
            }
            if (!status.isReady()) {
                result.failed();
                return;
            }
            importReadyExport(task, access, exportCode);
            riskBackoffGuard.recordSuccess(riskScope, task.getDataDomain().name());
            result.executed();
        } catch (RuntimeException exception) {
            failureRecorder.reportFailure(
                    task, request, exportCode, Math.max(1, pollAttempts),
                    failureRecorder.safeMessage(exception)
            );
            result.failed();
        }
    }

    private String createExport(
            NoonPullTaskRecord task,
            BusinessAccessContext access,
            NoonPullScheduledExecutionResult result
    ) {
        FbnExportCreateCommand command = new FbnExportCreateCommand();
        command.storeCode = task.getStoreCode();
        command.siteCode = task.getSiteCode();
        command.exportCategoryCode = REPORT_TYPE;
        command.fromDate = task.getTargetDateFrom().toString();
        command.toDate = task.getTargetDateTo().toString();
        FbnExportCreateView view = exportService.createExport(access, command);
        String exportCode = view == null ? null : view.exportCode;
        if (!StringUtils.hasText(exportCode)) {
            foundationService.markFailedWithPolicy(
                    task.getId(), "mapping failed: missing FBN export code", 1
            );
            result.failed();
            return null;
        }
        return exportCode;
    }

    private NoonReportExportStatus poll(
            NoonPullTaskRecord task,
            BusinessAccessContext access,
            String exportCode,
            int pollAttempts
    ) {
        FbnExportStatusView view = exportService.exportStatus(
                access, task.getStoreCode(), task.getSiteCode(), exportCode, false
        );
        NoonReportExportStatus status = exportStatus(view);
        foundationService.recordReportExportPollResult(
                task.getId(), exportCode, status, pollAttempts,
                status.isReady() || status.isFailed() ? null : POLL_DELAY,
                "official warehouse FBN received export; status="
                        + status.getStatus()
        );
        return status;
    }

    private void importReadyExport(
            NoonPullTaskRecord task,
            BusinessAccessContext access,
            String exportCode
    ) {
        FbnReceivedImportCommand command = new FbnReceivedImportCommand();
        command.storeCode = task.getStoreCode();
        command.siteCode = task.getSiteCode();
        command.logStatus = false;
        FbnReceivedImportResultView imported = importService.importByExportCode(
                access, exportCode, command
        );
        String importId = imported == null ? null : imported.importId;
        String sourceBatchId = "official-warehouse-fbn-received-" + task.getId()
                + "-" + NoonPullScheduledExecutionSupport.valueOrUnknown(importId);
        foundationService.markSucceeded(
                task.getId(), sourceBatchId,
                "official warehouse FBN received imported; rows="
                        + (imported == null ? 0 : imported.insertedReceiptLines)
        );
    }

    private NoonReportPullRequest request(NoonPullTaskRecord task) {
        return NoonReportPullRequest.builder()
                .ownerUserId(task.getOwnerUserId())
                .storeCode(task.getStoreCode())
                .siteCode(task.getSiteCode())
                .dataDomain(task.getDataDomain())
                .reportType(REPORT_TYPE)
                .dateFrom(task.getTargetDateFrom())
                .dateTo(task.getTargetDateTo())
                .maxPollAttempts(MAX_POLL_ATTEMPTS)
                .build();
    }

    private NoonReportExportStatus exportStatus(FbnExportStatusView view) {
        if (view == null) {
            return NoonReportExportStatus.pending();
        }
        if (NoonPullScheduledExecutionSupport.isFbnExportComplete(view.status)) {
            return NoonReportExportStatus.ready(view.downloadUrl, view.totalRows);
        }
        if (NoonPullScheduledExecutionSupport.isFbnExportFailed(view.status)) {
            return NoonReportExportStatus.failed(view.message);
        }
        return NoonReportExportStatus.pending(view.status);
    }
}
