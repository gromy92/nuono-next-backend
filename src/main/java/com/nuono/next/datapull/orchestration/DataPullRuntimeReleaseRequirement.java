package com.nuono.next.datapull.orchestration;

/** Fixed set of independently verified prerequisites for enabling the DP runtime. */
public enum DataPullRuntimeReleaseRequirement {
    RUNTIME_SCHEMA("DP_RUNTIME_SCHEMA_NOT_INSTALLED"),
    LEGACY_CUTOVER_RECONCILIATION("DP_LEGACY_CUTOVER_NOT_RECONCILED"),
    LEGACY_TASK_DRAIN("DP_LEGACY_TASKS_NOT_DRAINED"),
    MANAGED_RELEASE_PROVENANCE("DP_MANAGED_RELEASE_PROVENANCE_UNVERIFIED"),
    REPORT_PROVIDER_CONTRACTS("DP_REPORT_PROVIDER_CONTRACTS_UNVERIFIED"),
    DP08_LEGACY_TASK_RECONCILIATION(
            "DP08_LEGACY_TASK_RECONCILIATION_UNVERIFIED"
    ),
    DP10_OPEN_API_EXECUTION_CONTRACT("DP10_OPEN_API_EXECUTION_CONTRACT_UNVERIFIED");

    private final String blockerCode;

    DataPullRuntimeReleaseRequirement(String blockerCode) {
        this.blockerCode = blockerCode;
    }

    public String blockerCode() {
        return blockerCode;
    }
}
