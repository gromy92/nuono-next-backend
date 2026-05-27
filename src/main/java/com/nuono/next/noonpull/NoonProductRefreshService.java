package com.nuono.next.noonpull;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NoonProductRefreshService {
    private final NoonPullFoundationService foundationService;
    private final NoonInterfacePuller puller;
    private final NoonProductDetailBackfillPlanner detailBackfillPlanner;

    public NoonProductRefreshService(
            NoonPullFoundationService foundationService,
            NoonInterfacePuller puller,
            NoonProductDetailBackfillPlanner detailBackfillPlanner
    ) {
        this.foundationService = foundationService;
        this.puller = puller;
        this.detailBackfillPlanner = detailBackfillPlanner;
    }

    public NoonProductRefreshResult refresh(NoonProductRefreshCommand command, NoonInterfacePullProvider provider) {
        NoonProductRefreshResult result = new NoonProductRefreshResult();
        result.setPreserveDrafts(command.getMergePolicy() != NoonProductRefreshMergePolicy.USE_NOON_OVERWRITE);
        result.setPublishFlowTriggered(false);
        if (command.isHasLocalDraft() && command.getMergePolicy() == null) {
            result.setState("CONFIRMATION_REQUIRED");
            return result;
        }

        NoonPullTriggerMode triggerMode = triggerMode(command.getReason());
        String targetIdentity = "detail:" + command.getSkuParent();
        NoonProductDetailBackfillPlan detailPlan = detailBackfillPlan(command);
        result.setRequestedDetailSkuParents(detailPlan.getSkuParents());
        result.setBlindFullStoreDetailFetch(detailPlan.isBlindFullStoreFetch());

        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(command.getOwnerUserId())
                .storeCode(command.getStoreCode())
                .siteCode(command.getSiteCode())
                .pullType(NoonPullType.INTERFACE)
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .triggerMode(triggerMode)
                .scheduleExpression("manual")
                .maxDetailFetchesPerRun(1)
                .maxRequestsPerRun(1)
                .build());
        NoonPullTaskRecord task = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(command.getOwnerUserId())
                .storeCode(command.getStoreCode())
                .siteCode(command.getSiteCode())
                .pullType(NoonPullType.INTERFACE)
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .triggerMode(triggerMode)
                .targetIdentity(targetIdentity)
                .build()).orElseThrow();
        NoonInterfacePullResult pullResult = puller.execute(
                task.getId(),
                NoonInterfacePullRequest.builder()
                        .ownerUserId(command.getOwnerUserId())
                        .storeCode(command.getStoreCode())
                        .siteCode(command.getSiteCode())
                        .dataDomain(NoonPullDataDomain.PRODUCT)
                        .requestName("product-detail-refresh")
                        .targetIdentity(targetIdentity)
                        .timeoutSeconds(30)
                        .budget(NoonPullRequestBudget.builder().maxDetailFetchesPerRun(1).maxRequestsPerRun(1).build())
                        .build(),
                provider
        );
        result.setSourceBatchId(pullResult.getSourceBatchId());
        result.setState(pullResult.getStatus() == NoonPullTaskStatus.SUCCEEDED ? "SUCCEEDED" : "FAILED");
        return result;
    }

    private NoonProductDetailBackfillPlan detailBackfillPlan(NoonProductRefreshCommand command) {
        if (command.getReason() == NoonProductRefreshReason.PUBLISH_READBACK_DETAIL_REFRESH) {
            return detailBackfillPlanner.plan(NoonProductDetailBackfillRequest.builder()
                    .allSkuParents(command.getAllSkuParents())
                    .publishReadbackSkuParents(List.of(command.getSkuParent()))
                    .maxDetailFetches(1)
                    .build());
        }
        return detailBackfillPlanner.plan(NoonProductDetailBackfillRequest.builder()
                .allSkuParents(command.getAllSkuParents())
                .openedSkuParent(command.getSkuParent())
                .maxDetailFetches(1)
                .build());
    }

    private NoonPullTriggerMode triggerMode(NoonProductRefreshReason reason) {
        if (reason == NoonProductRefreshReason.MISSING_BASELINE_RECOVERY) {
            return NoonPullTriggerMode.GAP_BACKFILL;
        }
        if (reason == NoonProductRefreshReason.PUBLISH_READBACK_DETAIL_REFRESH) {
            return NoonPullTriggerMode.READBACK_CHECK;
        }
        return NoonPullTriggerMode.MANUAL_REFRESH;
    }
}
