package com.nuono.next.noonpull;

import org.springframework.stereotype.Service;

@Service
public class NoonSalesReportPullService {
    private static final int SALES_REPORT_MAX_POLL_ATTEMPTS = 18;

    private final NoonPullFoundationService foundationService;
    private final NoonReportPuller reportPuller;
    private final NoonSalesReportAdapter salesReportAdapter;

    public NoonSalesReportPullService(
            NoonPullFoundationService foundationService,
            NoonReportPuller reportPuller,
            NoonSalesReportAdapter salesReportAdapter
    ) {
        this.foundationService = foundationService;
        this.reportPuller = reportPuller;
        this.salesReportAdapter = salesReportAdapter;
    }

    public NoonReportPullResult pullLatestDay(NoonSalesReportPullCommand command, NoonReportProvider provider) {
        NoonPullTriggerMode triggerMode = command.getTriggerMode() == null
                ? NoonPullTriggerMode.MANUAL_BACKFILL
                : command.getTriggerMode();
        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(command.getOwnerUserId())
                .storeCode(command.getStoreCode())
                .siteCode(command.getSiteCode())
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(triggerMode)
                .scheduleExpression(scheduleExpression(triggerMode))
                .build());
        NoonPullTaskRecord task = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(command.getOwnerUserId())
                .storeCode(command.getStoreCode())
                .siteCode(command.getSiteCode())
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(triggerMode)
                .targetIdentity("sales:" + command.getDate())
                .targetDateFrom(command.getDate())
                .targetDateTo(command.getDate())
                .build()).orElseThrow();
        return reportPuller.execute(
                task.getId(),
                NoonReportPullRequest.builder()
                        .ownerUserId(command.getOwnerUserId())
                        .storeCode(command.getStoreCode())
                        .siteCode(command.getSiteCode())
                        .dataDomain(NoonPullDataDomain.SALES)
                        .reportType("productviewsandsalesdata")
                        .dateFrom(command.getDate())
                        .dateTo(command.getDate())
                        .maxPollAttempts(SALES_REPORT_MAX_POLL_ATTEMPTS)
                        .build(),
                provider,
                salesReportAdapter::process
        );
    }

    private String scheduleExpression(NoonPullTriggerMode triggerMode) {
        return triggerMode == NoonPullTriggerMode.SCHEDULED_DAILY
                ? "latest-day after 08:00 Asia/Shanghai"
                : "manual";
    }
}
