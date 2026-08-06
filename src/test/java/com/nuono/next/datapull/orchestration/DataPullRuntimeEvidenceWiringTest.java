package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DataPullRuntimeEvidenceWiringTest {

    @Test
    void releaseConfigurationProvidesEveryPreviouslyUnresolvedRequirement() {
        Set<String> providerMethods = Arrays.stream(
                        DataPullRuntimeReleaseConfiguration.class.getDeclaredMethods()
                )
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertEquals(
                Set.of(
                        "dataPullRuntimeSchemaEvidence",
                        "dp04StableSnapshotEvidence",
                        "dp06CompleteCampaignEnumerationEvidence",
                        "dp07aStableSnapshotEvidence",
                        "dp10ModifiedTimeVisibilityEvidence"
                ),
                providerMethods.stream()
                        .filter((name) -> name.endsWith("Evidence"))
                        .filter((name) -> name.startsWith("dp04")
                                || name.startsWith("dp06")
                                || name.startsWith("dp07a")
                                || name.startsWith("dp10Modified")
                                || name.equals("dataPullRuntimeSchemaEvidence"))
                        .collect(Collectors.toSet())
        );
    }
}
