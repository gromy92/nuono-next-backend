package com.nuono.next.noonpull;

import org.springframework.stereotype.Service;

@Service
public class NoonOrderReportPullService {
    private static final int ORDER_REPORT_MAX_POLL_ATTEMPTS = 18;

    private final NoonPullFoundationService foundationService;
    private final NoonReportPuller reportPuller;
    private final NoonOrderReportAdapter orderReportAdapter;

    public NoonOrderReportPullService(
            NoonPullFoundationService foundationService,
            NoonReportPuller reportPuller,
            NoonOrderReportAdapter orderReportAdapter
    ) {
        this.foundationService = foundationService;
        this.reportPuller = reportPuller;
        this.orderReportAdapter = orderReportAdapter;
    }

    public NoonReportPullResult pullWindow(NoonOrderReportPullCommand command, NoonReportProvider provider) {
        NoonPullTriggerMode triggerMode = command.getTriggerMode() == null
                ? NoonPullTriggerMode.MANUAL_BACKFILL
                : command.getTriggerMode();
        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(command.getOwnerUserId())
                .storeCode(command.getStoreCode())
                .siteCode(command.getSiteCode())
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.ORDER)
                .triggerMode(triggerMode)
                .scheduleExpression(scheduleExpression(triggerMode))
                .build());
        NoonPullTaskRecord task = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(command.getOwnerUserId())
                .storeCode(command.getStoreCode())
                .siteCode(command.getSiteCode())
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.ORDER)
                .triggerMode(triggerMode)
                .targetIdentity("orders:" + command.getDateFrom() + ".." + command.getDateTo())
                .targetDateFrom(command.getDateFrom())
                .targetDateTo(command.getDateTo())
                .build()).orElseThrow();
        return reportPuller.execute(
                task.getId(),
                NoonReportPullRequest.builder()
                        .ownerUserId(command.getOwnerUserId())
                        .storeCode(command.getStoreCode())
                        .siteCode(command.getSiteCode())
                        .dataDomain(NoonPullDataDomain.ORDER)
                        .reportType(NoonOrderReportDescriptor.REPORT_TYPE)
                        .dateFrom(command.getDateFrom())
                        .dateTo(command.getDateTo())
                        .maxPollAttempts(ORDER_REPORT_MAX_POLL_ATTEMPTS)
                        .build(),
                provider,
                orderReportAdapter::process
        );
    }

    private String scheduleExpression(NoonPullTriggerMode triggerMode) {
        return triggerMode == NoonPullTriggerMode.SCHEDULED_DAILY ? "daily after 08:30 Asia/Shanghai" : "manual";
    }
}
