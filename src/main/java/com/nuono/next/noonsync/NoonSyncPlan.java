package com.nuono.next.noonsync;

public class NoonSyncPlan {

    private final long id;
    private final NoonSyncPlanDefinition definition;
    private final NoonSyncScope scope;
    private final boolean paused;
    private final String pauseReason;

    public NoonSyncPlan(
            long id,
            NoonSyncPlanDefinition definition,
            NoonSyncScope scope,
            boolean paused,
            String pauseReason
    ) {
        this.id = id;
        this.definition = definition;
        this.scope = scope;
        this.paused = paused;
        this.pauseReason = pauseReason;
    }

    public long getId() {
        return id;
    }

    public NoonSyncPlanDefinition getDefinition() {
        return definition;
    }

    public NoonSyncScope getScope() {
        return scope;
    }

    public boolean isPaused() {
        return paused;
    }

    public String getPauseReason() {
        return pauseReason;
    }

    NoonSyncPlan pause(String reason) {
        return new NoonSyncPlan(id, definition, scope, true, reason);
    }

    NoonSyncPlan resume() {
        return new NoonSyncPlan(id, definition, scope, false, null);
    }
}
