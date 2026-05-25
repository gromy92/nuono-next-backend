package com.nuono.next.noonpull;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NoonSalesPageQueryPullService {
    private final NoonPullFoundationService foundationService;
    private final NoonInterfacePuller interfacePuller;

    public NoonSalesPageQueryPullService(
            NoonPullFoundationService foundationService,
            NoonInterfacePuller interfacePuller
    ) {
        this.foundationService = foundationService;
        this.interfacePuller = interfacePuller;
    }

    public NoonInterfacePullResult pullWindow(
            NoonSalesPageQueryPullCommand command,
            NoonSalesPageQueryProvider provider
    ) {
        requireCommand(command);
        if (provider == null) {
            throw new IllegalArgumentException("Noon sales page query provider is required.");
        }
        String targetIdentity = targetIdentity(command);
        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(command.getOwnerUserId())
                .storeCode(command.getStoreCode())
                .siteCode(command.getSiteCode())
                .pullType(NoonPullType.PAGE_QUERY)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.MANUAL_BACKFILL)
                .build());
        NoonPullTaskRecord task = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(command.getOwnerUserId())
                .storeCode(command.getStoreCode())
                .siteCode(command.getSiteCode())
                .pullType(NoonPullType.PAGE_QUERY)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.MANUAL_BACKFILL)
                .targetIdentity(targetIdentity)
                .targetDateFrom(command.getDateFrom())
                .targetDateTo(command.getDateTo())
                .build()).orElseThrow();
        return interfacePuller.execute(
                task.getId(),
                NoonInterfacePullRequest.builder()
                        .ownerUserId(command.getOwnerUserId())
                        .storeCode(command.getStoreCode())
                        .siteCode(command.getSiteCode())
                        .dataDomain(NoonPullDataDomain.SALES)
                        .requestName("sales-page-query")
                        .targetIdentity(targetIdentity)
                        .dateFrom(command.getDateFrom())
                        .dateTo(command.getDateTo())
                        .maxPages(command.getMaxPages())
                        .budget(command.getRequestBudget())
                        .requestSummary(command.getRequestSummary())
                        .build(),
                provider
        );
    }

    private void requireCommand(NoonSalesPageQueryPullCommand command) {
        if (command == null
                || command.getOwnerUserId() == null
                || !StringUtils.hasText(command.getStoreCode())
                || !StringUtils.hasText(command.getSiteCode())
                || command.getDateFrom() == null
                || command.getDateTo() == null) {
            throw new IllegalArgumentException("Noon sales page query owner, store, site and date window are required.");
        }
        if (command.getDateTo().isBefore(command.getDateFrom())) {
            throw new IllegalArgumentException("Noon sales page query dateTo cannot be before dateFrom.");
        }
    }

    private String targetIdentity(NoonSalesPageQueryPullCommand command) {
        return "sales-page-query:" + command.getDateFrom() + ".." + command.getDateTo();
    }
}
