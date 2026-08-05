package com.nuono.next.datapull.schedule;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataPullScheduleAnchorManifestTest {

    private static final String CUTOVER = "release-20260802";
    private static final LocalDateTime FRONTIER = LocalDateTime.of(
            2026, 8, 1, 15, 59, 59, 999_000_000
    );
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 8, 2, 1, 55);

    @Test
    void manifestBindsCanonicalAdmissionAndIndependentFrontierEvidence() {
        DataPullScope original = scope("PRJ307");
        DataPullScope drifted = scope("PRJ-DIFFERENT");
        DataPullScheduleAnchor baseline = anchor(original, "a".repeat(64));
        DataPullScheduleAnchor identityDrift = anchor(drifted, "a".repeat(64));
        DataPullScheduleAnchor evidenceDrift = anchor(original, "b".repeat(64));

        String expected = manifest(baseline);

        assertNotEquals(expected, manifest(identityDrift));
        assertNotEquals(expected, manifest(evidenceDrift));
    }

    private String manifest(DataPullScheduleAnchor anchor) {
        return DataPullScheduleAnchorManifest.sha256(
                OperationCode.DP04,
                CUTOVER,
                List.of(anchor)
        );
    }

    private DataPullScheduleAnchor anchor(DataPullScope scope, String evidence) {
        DataPullScopeAdmission admission = DataPullScopeAdmission.cutoverExisting(
                scope,
                CUTOVER,
                CREATED
        );
        return DataPullScheduleAnchor.cutover(
                OperationCode.DP04,
                admission,
                FRONTIER,
                CREATED,
                evidence
        );
    }

    private DataPullScope scope(String project) {
        return new DataPullScope(
                "NOON", 307L, 108065L, project, null,
                project, "STR108065-NSA", "SA", "NOON-same-scope"
        );
    }
}
