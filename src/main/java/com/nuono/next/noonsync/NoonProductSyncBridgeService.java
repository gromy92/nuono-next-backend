package com.nuono.next.noonsync;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
@Deprecated // 未接入：包外 0 引用的死桥。保留待删除（#9）
public class NoonProductSyncBridgeService {

    private final NoonSyncFoundationService foundationService;

    public NoonProductSyncBridgeService(NoonSyncFoundationService foundationService) {
        this.foundationService = foundationService;
    }

    public List<NoonSyncPlanDefinition> productPlanCatalog() {
        return foundationService.planCatalog()
                .stream()
                .filter(plan -> plan.getDataDomain() == NoonSyncDataDomain.PRODUCT)
                .collect(Collectors.toList());
    }

    public NoonProductSyncReadModel describeProductSurface(NoonProductSyncBridgeInput input) {
        NoonSyncTask explicitRefreshTask = input.getExplicitRefreshTask();
        if (isFailed(explicitRefreshTask)) {
            return fromTask(NoonProductSyncSurfaceState.EXPLICIT_REFRESH_FAILED, explicitRefreshTask, List.of());
        }

        NoonSyncTask initializationTask = input.getProductInitializationTask();
        if (initializationTask != null && initializationTask.getStatus() == NoonSyncTaskStatus.RUNNING) {
            return fromTask(NoonProductSyncSurfaceState.INITIALIZATION_RUNNING, initializationTask, List.of());
        }
        if (isFailed(initializationTask)) {
            return fromTask(NoonProductSyncSurfaceState.INITIALIZATION_FAILED, initializationTask, List.of());
        }

        if (input.getWorkspaceState() == NoonProductWorkspaceState.MISSING
                || input.getWorkspaceState() == NoonProductWorkspaceState.WORKSPACE_EMPTY) {
            return new NoonProductSyncReadModel(
                    NoonProductSyncSurfaceState.INITIALIZATION_NEEDED,
                    null,
                    null,
                    null,
                    List.of(new NoonSyncRequiredWork(
                            "product_initialization",
                            NoonSyncDataDomain.PRODUCT,
                            NoonSyncTriggerMode.ONBOARDING,
                            null
                    )),
                    false
            );
        }

        return new NoonProductSyncReadModel(
                NoonProductSyncSurfaceState.NORMAL_LOCAL_MANAGEMENT,
                null,
                null,
                null,
                List.of(),
                false
        );
    }

    public NoonProductExplicitRefreshPolicy planExplicitRefresh(boolean hasLocalDraft, String requestedMergePolicy) {
        String mergePolicy = normalizeMergePolicy(requestedMergePolicy);
        NoonProductRefreshDraftHandling draftHandling = hasLocalDraft
                ? draftHandlingFor(mergePolicy)
                : NoonProductRefreshDraftHandling.NO_DRAFT_PRESENT;
        return new NoonProductExplicitRefreshPolicy(hasLocalDraft, mergePolicy, draftHandling, false);
    }

    private NoonProductSyncReadModel fromTask(
            NoonProductSyncSurfaceState surfaceState,
            NoonSyncTask task,
            List<NoonSyncRequiredWork> requiredWork
    ) {
        return new NoonProductSyncReadModel(
                surfaceState,
                task.getStatus(),
                task.getFailureReason(),
                task.getDiagnosticSummary(),
                requiredWork,
                false
        );
    }

    private boolean isFailed(NoonSyncTask task) {
        return task != null && task.getStatus() == NoonSyncTaskStatus.FAILED;
    }

    private String normalizeMergePolicy(String requestedMergePolicy) {
        if (requestedMergePolicy == null || requestedMergePolicy.isBlank()) {
            return "keep_draft";
        }
        String normalized = requestedMergePolicy.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if ("use_noon".equals(normalized) || "overwrite".equals(normalized) || "overwrite_local".equals(normalized)) {
            return "use_noon";
        }
        return "keep_draft";
    }

    private NoonProductRefreshDraftHandling draftHandlingFor(String mergePolicy) {
        if ("use_noon".equals(mergePolicy)) {
            return NoonProductRefreshDraftHandling.OVERWRITE_LOCAL_DRAFT_WITH_NOON;
        }
        return NoonProductRefreshDraftHandling.PRESERVE_LOCAL_DRAFT;
    }
}
