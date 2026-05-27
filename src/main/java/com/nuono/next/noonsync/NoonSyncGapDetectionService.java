package com.nuono.next.noonsync;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NoonSyncGapDetectionService {

    private final NoonSyncFoundationService foundationService;

    public NoonSyncGapDetectionService(NoonSyncFoundationService foundationService) {
        this.foundationService = foundationService;
    }

    public NoonSyncReadinessView preview(NoonSyncGapDetectionInput input) {
        if (!input.isNoonBindingReady()) {
            return blocked(input, NoonSyncBlockedReason.NOON_BINDING_MISSING);
        }
        if (!input.isProviderConfigured()) {
            return blocked(input, NoonSyncBlockedReason.PROVIDER_NOT_CONFIGURED);
        }

        List<NoonSyncRequiredWork> requiredWork = new ArrayList<>();
        if (input.getProductWorkspaceState() == NoonProductWorkspaceState.MISSING
                || input.getProductWorkspaceState() == NoonProductWorkspaceState.WORKSPACE_EMPTY) {
            requiredWork.add(new NoonSyncRequiredWork(
                    "product_initialization",
                    NoonSyncDataDomain.PRODUCT,
                    NoonSyncTriggerMode.ONBOARDING,
                    NoonSyncTarget.identity(input.getScope().getStoreCode())
            ));
        }
        if (input.getSalesCoverageState() == NoonSalesCoverageState.MISSING
                || input.getSalesCoverageState() == NoonSalesCoverageState.GAPS) {
            requiredWork.add(new NoonSyncRequiredWork(
                    "sales_backfill",
                    NoonSyncDataDomain.SALES,
                    NoonSyncTriggerMode.GAP_BACKFILL,
                    NoonSyncTarget.dateRange(input.getSalesBackfillFrom(), input.getSalesBackfillTo())
            ));
        }

        return new NoonSyncReadinessView(resolveState(input, requiredWork), null, input.getScope(), requiredWork);
    }

    public List<NoonSyncTask> createRequiredTasks(NoonSyncGapDetectionInput input) {
        NoonSyncReadinessView readiness = preview(input);
        List<NoonSyncTask> tasks = new ArrayList<>();
        for (NoonSyncRequiredWork work : readiness.getRequiredWork()) {
            tasks.add(foundationService.createTask(new NoonSyncTaskRequest(
                    work.getDataDomain(),
                    work.getTriggerMode(),
                    input.getScope(),
                    work.getTarget(),
                    NoonSyncRetryPolicy.RETRYABLE
            )));
        }
        return tasks;
    }

    private NoonSyncReadinessView blocked(NoonSyncGapDetectionInput input, NoonSyncBlockedReason reason) {
        return new NoonSyncReadinessView(NoonSyncReadinessState.BLOCKED, reason, input.getScope(), List.of());
    }

    private NoonSyncReadinessState resolveState(
            NoonSyncGapDetectionInput input,
            List<NoonSyncRequiredWork> requiredWork
    ) {
        if (requiredWork.isEmpty()) {
            return NoonSyncReadinessState.NO_SYNC_NEEDED;
        }
        if (input.getAccountOrigin() == NoonSyncAccountOrigin.NEW_USER
                || input.getAccountOrigin() == NoonSyncAccountOrigin.LEGACY_IMPORTED) {
            return NoonSyncReadinessState.INITIALIZATION_NEEDED;
        }
        return NoonSyncReadinessState.BACKFILL_NEEDED;
    }
}
