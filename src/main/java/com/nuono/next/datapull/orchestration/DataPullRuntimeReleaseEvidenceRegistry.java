package com.nuono.next.datapull.orchestration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Fixes provider identity once, then rechecks every mutable evidence source fail-closed. */
public final class DataPullRuntimeReleaseEvidenceRegistry {
    private final Map<DataPullRuntimeReleaseRequirement, List<DataPullRuntimeReleaseEvidence>>
            providers;

    public DataPullRuntimeReleaseEvidenceRegistry(
            List<DataPullRuntimeReleaseEvidence> evidenceProviders
    ) {
        Objects.requireNonNull(evidenceProviders, "evidenceProviders");
        Map<DataPullRuntimeReleaseRequirement, List<DataPullRuntimeReleaseEvidence>> indexed =
                new EnumMap<>(DataPullRuntimeReleaseRequirement.class);
        for (DataPullRuntimeReleaseEvidence evidence : evidenceProviders) {
            DataPullRuntimeReleaseEvidence provider = Objects.requireNonNull(evidence, "evidence");
            DataPullRuntimeReleaseRequirement requirement = Objects.requireNonNull(
                    provider.requirement(),
                    "evidence requirement"
            );
            indexed.computeIfAbsent(requirement, ignored -> new ArrayList<>()).add(provider);
        }
        Map<DataPullRuntimeReleaseRequirement, List<DataPullRuntimeReleaseEvidence>> immutable =
                new EnumMap<>(DataPullRuntimeReleaseRequirement.class);
        indexed.forEach((requirement, matches) ->
                immutable.put(requirement, List.copyOf(matches))
        );
        providers = Map.copyOf(immutable);
    }

    public List<DataPullRuntimeReleaseRequirement> unresolvedRequirements() {
        List<DataPullRuntimeReleaseRequirement> unresolved = new ArrayList<>();
        for (DataPullRuntimeReleaseRequirement requirement
                : DataPullRuntimeReleaseRequirement.values()) {
            List<DataPullRuntimeReleaseEvidence> matches = providers.getOrDefault(
                    requirement,
                    List.of()
            );
            if (matches.size() != 1 || !isVerified(matches.get(0))) {
                unresolved.add(requirement);
            }
        }
        return List.copyOf(unresolved);
    }

    private static boolean isVerified(DataPullRuntimeReleaseEvidence evidence) {
        try {
            return evidence.verified();
        } catch (RuntimeException failure) {
            return false;
        }
    }

}
