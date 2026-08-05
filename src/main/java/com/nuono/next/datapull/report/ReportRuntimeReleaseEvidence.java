package com.nuono.next.datapull.report;

import com.nuono.next.datapull.orchestration.DataPullRuntimeReleaseEvidence;
import com.nuono.next.datapull.orchestration.DataPullRuntimeReleaseRequirement;
import com.nuono.next.datapull.runtime.OperationCode;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** One authoritative release-evidence provider for all report create and empty-proof contracts. */
public final class ReportRuntimeReleaseEvidence implements DataPullRuntimeReleaseEvidence {
    private final List<String> blockers;

    public ReportRuntimeReleaseEvidence(ReportProviderCapabilitySource... sources) {
        if (sources == null || sources.length == 0) {
            throw new IllegalArgumentException("report provider capabilities are required");
        }
        List<ReportProviderCapabilities> capabilities = java.util.Arrays.stream(sources)
                .map(source -> Objects.requireNonNull(source, "capabilitySource"))
                .map(ReportProviderCapabilitySource::reportProviderCapabilities)
                .map(value -> Objects.requireNonNull(value, "reportProviderCapabilities"))
                .collect(Collectors.toList());
        EnumSet<OperationCode> operations = EnumSet.noneOf(OperationCode.class);
        for (ReportProviderCapabilities capability : capabilities) {
            if (!operations.add(capability.getOperationCode())) {
                throw new IllegalArgumentException(
                        "duplicate report provider capability: " + capability.getOperationCode()
                );
            }
        }
        EnumSet<OperationCode> expected = EnumSet.of(
                OperationCode.DP01,
                OperationCode.DP02,
                OperationCode.DP03,
                OperationCode.DP07B
        );
        if (!operations.equals(expected)) {
            throw new IllegalArgumentException(
                    "report provider capability set must be exactly " + expected
            );
        }
        this.blockers = capabilities.stream()
                .flatMap(capability -> capability.releaseBlockers().stream())
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public DataPullRuntimeReleaseRequirement requirement() {
        return DataPullRuntimeReleaseRequirement.REPORT_PROVIDER_CONTRACTS;
    }

    @Override
    public boolean verified() {
        return blockers.isEmpty();
    }

    public List<String> getBlockers() {
        return blockers;
    }
}
