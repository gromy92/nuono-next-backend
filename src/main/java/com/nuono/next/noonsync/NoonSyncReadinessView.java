package com.nuono.next.noonsync;

import java.util.List;

public class NoonSyncReadinessView {

    private final NoonSyncReadinessState state;
    private final NoonSyncBlockedReason blockedReason;
    private final NoonSyncScope scope;
    private final List<NoonSyncRequiredWork> requiredWork;

    public NoonSyncReadinessView(
            NoonSyncReadinessState state,
            NoonSyncBlockedReason blockedReason,
            NoonSyncScope scope,
            List<NoonSyncRequiredWork> requiredWork
    ) {
        this.state = state;
        this.blockedReason = blockedReason;
        this.scope = scope;
        this.requiredWork = requiredWork == null ? List.of() : List.copyOf(requiredWork);
    }

    public NoonSyncReadinessState getState() {
        return state;
    }

    public NoonSyncBlockedReason getBlockedReason() {
        return blockedReason;
    }

    public NoonSyncScope getScope() {
        return scope;
    }

    public List<NoonSyncRequiredWork> getRequiredWork() {
        return requiredWork;
    }
}
