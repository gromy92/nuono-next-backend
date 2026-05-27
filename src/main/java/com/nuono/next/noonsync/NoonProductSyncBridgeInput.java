package com.nuono.next.noonsync;

public class NoonProductSyncBridgeInput {

    private final NoonProductWorkspaceState workspaceState;
    private final NoonSyncTask productInitializationTask;
    private final NoonSyncTask explicitRefreshTask;
    private final boolean hasLocalDraft;

    public NoonProductSyncBridgeInput(
            NoonProductWorkspaceState workspaceState,
            NoonSyncTask productInitializationTask,
            NoonSyncTask explicitRefreshTask,
            boolean hasLocalDraft
    ) {
        this.workspaceState = workspaceState;
        this.productInitializationTask = productInitializationTask;
        this.explicitRefreshTask = explicitRefreshTask;
        this.hasLocalDraft = hasLocalDraft;
    }

    public NoonProductWorkspaceState getWorkspaceState() {
        return workspaceState;
    }

    public NoonSyncTask getProductInitializationTask() {
        return productInitializationTask;
    }

    public NoonSyncTask getExplicitRefreshTask() {
        return explicitRefreshTask;
    }

    public boolean hasLocalDraft() {
        return hasLocalDraft;
    }
}
