package com.nuono.next.noonpull;

import org.springframework.stereotype.Service;

@Service
public class NoonProductListInitializationService {
    private static final String PRODUCT_LIST_TARGET = "catalog:list";

    private final NoonPullFoundationService foundationService;
    private final NoonInterfacePuller puller;
    private final NoonProductListPullAdapter adapter;

    public NoonProductListInitializationService(
            NoonPullFoundationService foundationService,
            NoonInterfacePuller puller,
            NoonProductListPullAdapter adapter
    ) {
        this.foundationService = foundationService;
        this.puller = puller;
        this.adapter = adapter;
    }

    public NoonProductListInitializationResult initialize(
            NoonProductListInitializationCommand command,
            NoonInterfacePullProvider provider
    ) {
        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(command.getOwnerUserId())
                .storeCode(command.getStoreCode())
                .siteCode(command.getSiteCode())
                .pullType(NoonPullType.INTERFACE)
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .triggerMode(NoonPullTriggerMode.ONBOARDING)
                .scheduleExpression("manual")
                .build());
        NoonPullTaskRecord task = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(command.getOwnerUserId())
                .storeCode(command.getStoreCode())
                .siteCode(command.getSiteCode())
                .pullType(NoonPullType.INTERFACE)
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .triggerMode(NoonPullTriggerMode.ONBOARDING)
                .targetIdentity(PRODUCT_LIST_TARGET)
                .build()).orElseThrow();
        NoonInterfacePullResult pullResult = puller.execute(
                task.getId(),
                NoonInterfacePullRequest.builder()
                        .ownerUserId(command.getOwnerUserId())
                        .storeCode(command.getStoreCode())
                        .siteCode(command.getSiteCode())
                        .dataDomain(NoonPullDataDomain.PRODUCT)
                        .requestName("product-list")
                        .targetIdentity(PRODUCT_LIST_TARGET)
                        .timeoutSeconds(30)
                        .budget(command.getRequestBudget())
                        .requestSummary(command.getRequestSummary())
                        .build(),
                provider
        );

        NoonProductListInitializationResult result = new NoonProductListInitializationResult();
        result.setTaskStatus(pullResult.getStatus());
        result.setSourceBatchId(pullResult.getSourceBatchId());
        result.setFailureType(pullResult.getFailureType());
        if (pullResult.getStatus() == NoonPullTaskStatus.SUCCEEDED) {
            NoonProductListApplyResult applyResult = adapter.apply(NoonProductListApplyCommand.builder()
                    .ownerUserId(command.getOwnerUserId())
                    .projectCode(command.getProjectCode())
                    .projectName(command.getProjectName())
                    .storeCode(command.getStoreCode())
                    .siteCode(command.getSiteCode())
                    .sourceBatchId(pullResult.getSourceBatchId())
                    .items(pullResult.getItems())
                    .build());
            result.setAcceptedProductCount(applyResult.getAcceptedCount());
        }
        return result;
    }
}
