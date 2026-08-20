package com.nuono.next.noon;

/** Safe aggregate evidence for the configured Noon Project session scope. */
public final class NoonAccountSessionAuditResult {
    private final String scopeMode;
    private final String status;
    private final int totalProjects;
    private final int scopedProjects;
    private final int verifiedProjects;
    private final int excludedProjects;
    private final int unverifiedProjects;

    private NoonAccountSessionAuditResult(
            String scopeMode,
            String status,
            int totalProjects,
            int scopedProjects,
            int verifiedProjects,
            int excludedProjects,
            int unverifiedProjects
    ) {
        this.scopeMode = scopeMode;
        this.status = status;
        this.totalProjects = totalProjects;
        this.scopedProjects = scopedProjects;
        this.verifiedProjects = verifiedProjects;
        this.excludedProjects = excludedProjects;
        this.unverifiedProjects = unverifiedProjects;
    }

    static NoonAccountSessionAuditResult notRun(String scopeMode) {
        return new NoonAccountSessionAuditResult(scopeMode, "NOT_RUN", 0, 0, 0, 0, 0);
    }

    static NoonAccountSessionAuditResult of(
            String scopeMode,
            String status,
            int totalProjects,
            int scopedProjects,
            int verifiedProjects
    ) {
        return new NoonAccountSessionAuditResult(
                scopeMode,
                status,
                totalProjects,
                scopedProjects,
                verifiedProjects,
                Math.max(0, totalProjects - scopedProjects),
                Math.max(0, scopedProjects - verifiedProjects)
        );
    }

    public boolean isReady() {
        return "READY".equals(status)
                && scopedProjects > 0
                && verifiedProjects == scopedProjects
                && ("ALL".equals(scopeMode) ? excludedProjects == 0 : true);
    }

    public String getScopeMode() { return scopeMode; }
    public String getStatus() { return status; }
    public int getTotalProjects() { return totalProjects; }
    public int getScopedProjects() { return scopedProjects; }
    public int getVerifiedProjects() { return verifiedProjects; }
    public int getExcludedProjects() { return excludedProjects; }
    public int getUnverifiedProjects() { return unverifiedProjects; }
}
