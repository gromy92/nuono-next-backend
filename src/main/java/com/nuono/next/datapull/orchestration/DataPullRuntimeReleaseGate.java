package com.nuono.next.datapull.orchestration;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Fail-closed startup gate for release evidence that cannot be inferred from task state. */
public final class DataPullRuntimeReleaseGate {
    private final DataPullRuntimeReleaseEvidenceRegistry evidenceRegistry;

    public DataPullRuntimeReleaseGate(DataPullRuntimeReleaseEvidenceRegistry evidenceRegistry) {
        this.evidenceRegistry = Objects.requireNonNull(
                evidenceRegistry,
                "evidenceRegistry"
        );
    }

    public void requireReady() {
        List<String> blockers = getBlockers();
        if (!blockers.isEmpty()) {
            throw new IllegalStateException(
                    "DP_RUNTIME_RELEASE_BLOCKED:" + String.join(",", blockers)
            );
        }
    }

    public List<String> getBlockers() {
        return evidenceRegistry.unresolvedRequirements().stream()
                .map(DataPullRuntimeReleaseRequirement::blockerCode)
                .collect(Collectors.toUnmodifiableList());
    }
}
