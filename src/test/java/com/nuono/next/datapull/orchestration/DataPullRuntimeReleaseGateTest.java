package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DataPullRuntimeReleaseGateTest {

    @Test
    void missingProvidersFailClosedWithEveryRequirementBlocker() {
        DataPullRuntimeReleaseGate gate = gate(List.of());

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                gate::requireReady
        );

        assertEquals(allBlockers(), gate.getBlockers());
        assertEquals(
                "DP_RUNTIME_RELEASE_BLOCKED:" + String.join(",", allBlockers()),
                failure.getMessage()
        );
    }

    @Test
    void oneUnverifiedProviderBlocksOnlyItsRequirement() {
        List<DataPullRuntimeReleaseEvidence> evidence = verifiedEvidence();
        evidence.set(
                Arrays.asList(DataPullRuntimeReleaseRequirement.values()).indexOf(
                        DataPullRuntimeReleaseRequirement.REPORT_PROVIDER_CONTRACTS
                ),
                evidence(DataPullRuntimeReleaseRequirement.REPORT_PROVIDER_CONTRACTS, false)
        );

        DataPullRuntimeReleaseGate gate = gate(evidence);

        assertEquals(List.of("DP_REPORT_PROVIDER_CONTRACTS_UNVERIFIED"), gate.getBlockers());
        assertThrows(IllegalStateException.class, gate::requireReady);
    }

    @Test
    void duplicateProvidersBlockTheirRequirementEvenWhenBothClaimVerified() {
        List<DataPullRuntimeReleaseEvidence> evidence = verifiedEvidence();
        evidence.add(evidence(DataPullRuntimeReleaseRequirement.RUNTIME_SCHEMA, true));

        DataPullRuntimeReleaseGate gate = gate(evidence);

        assertEquals(List.of("DP_RUNTIME_SCHEMA_NOT_INSTALLED"), gate.getBlockers());
        assertThrows(IllegalStateException.class, gate::requireReady);
    }

    @Test
    void failingEvidenceProviderClosesGateWithoutBreakingApplicationStartup() {
        List<DataPullRuntimeReleaseEvidence> evidence = verifiedEvidence();
        int schemaIndex = Arrays.asList(DataPullRuntimeReleaseRequirement.values()).indexOf(
                DataPullRuntimeReleaseRequirement.RUNTIME_SCHEMA
        );
        evidence.set(schemaIndex, new DataPullRuntimeReleaseEvidence() {
            @Override
            public DataPullRuntimeReleaseRequirement requirement() {
                return DataPullRuntimeReleaseRequirement.RUNTIME_SCHEMA;
            }

            @Override
            public boolean verified() {
                throw new IllegalStateException("evidence source unavailable");
            }
        });

        DataPullRuntimeReleaseGate gate = gate(evidence);

        assertEquals(List.of("DP_RUNTIME_SCHEMA_NOT_INSTALLED"), gate.getBlockers());
        assertThrows(IllegalStateException.class, gate::requireReady);
    }

    @Test
    void exactlyOneVerifiedProviderForEveryRequirementOpensGate() {
        DataPullRuntimeReleaseGate gate = gate(verifiedEvidence());

        gate.requireReady();
        assertEquals(List.of(), gate.getBlockers());
    }

    @Test
    void gateRechecksEvidenceInsteadOfCachingAStaleStartupDecision() {
        AtomicBoolean current = new AtomicBoolean(true);
        List<DataPullRuntimeReleaseEvidence> evidence = verifiedEvidence();
        int index = Arrays.asList(DataPullRuntimeReleaseRequirement.values()).indexOf(
                DataPullRuntimeReleaseRequirement.MANAGED_RELEASE_PROVENANCE
        );
        evidence.set(index, evidence(
                DataPullRuntimeReleaseRequirement.MANAGED_RELEASE_PROVENANCE,
                current::get
        ));
        DataPullRuntimeReleaseGate gate = gate(evidence);

        gate.requireReady();
        current.set(false);

        assertEquals(
                List.of("DP_MANAGED_RELEASE_PROVENANCE_UNVERIFIED"),
                gate.getBlockers()
        );
        assertThrows(IllegalStateException.class, gate::requireReady);
    }

    @Test
    void runtimeConfigurationWiresGateThroughEvidenceRegistry() {
        DataPullRuntimeReleaseConfiguration configuration =
                new DataPullRuntimeReleaseConfiguration();
        DataPullRuntimeReleaseEvidenceRegistry registry =
                configuration.dataPullRuntimeReleaseEvidenceRegistry(List.of());
        DataPullRuntimeReleaseGate gate = configuration.dataPullRuntimeReleaseGate(registry);

        assertEquals(allBlockers(), gate.getBlockers());
    }

    private static DataPullRuntimeReleaseGate gate(
            List<DataPullRuntimeReleaseEvidence> evidence
    ) {
        return new DataPullRuntimeReleaseGate(
                new DataPullRuntimeReleaseEvidenceRegistry(evidence)
        );
    }

    private static List<DataPullRuntimeReleaseEvidence> verifiedEvidence() {
        List<DataPullRuntimeReleaseEvidence> evidence = new ArrayList<>();
        for (DataPullRuntimeReleaseRequirement requirement
                : DataPullRuntimeReleaseRequirement.values()) {
            evidence.add(evidence(requirement, true));
        }
        return evidence;
    }

    private static DataPullRuntimeReleaseEvidence evidence(
            DataPullRuntimeReleaseRequirement requirement,
            boolean verified
    ) {
        return evidence(requirement, () -> verified);
    }

    private static DataPullRuntimeReleaseEvidence evidence(
            DataPullRuntimeReleaseRequirement requirement,
            java.util.function.BooleanSupplier verified
    ) {
        return new DataPullRuntimeReleaseEvidence() {
            @Override
            public DataPullRuntimeReleaseRequirement requirement() {
                return requirement;
            }

            @Override
            public boolean verified() {
                return verified.getAsBoolean();
            }
        };
    }

    private static List<String> allBlockers() {
        List<String> blockers = new ArrayList<>();
        Arrays.stream(DataPullRuntimeReleaseRequirement.values())
                .map(DataPullRuntimeReleaseRequirement::blockerCode)
                .forEach(blockers::add);
        return blockers;
    }
}
