package com.nuono.next.datapull.schedule;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DataPullScheduleAnchorEvidenceTest {

    @Test
    void cutoverEvidenceBindsOperationScopeBoundaryAndExternalProof() {
        LocalDateTime admittedAt = LocalDateTime.parse("2026-08-03T12:00:00.000");
        DataPullScopeAdmission admission = DataPullScopeAdmission.cutoverExisting(
                scope(), "dp-runtime-" + "c".repeat(40), admittedAt
        );
        LocalDateTime boundary = LocalDateTime.parse("2026-08-02T16:00:00.000");
        String first = DataPullScheduleAnchorEvidence.cutoverSha256(
                OperationCode.DP01, admission, boundary,
                "SAFE_FALLBACK_PREVIOUS_BUSINESS_DAY", "a".repeat(64)
        );
        String changed = DataPullScheduleAnchorEvidence.cutoverSha256(
                OperationCode.DP01, admission, boundary.minusDays(1),
                "SAFE_FALLBACK_PREVIOUS_BUSINESS_DAY", "a".repeat(64)
        );

        assertTrue(first.matches("[0-9a-f]{64}"));
        assertNotEquals(first, changed);
        assertThrows(IllegalArgumentException.class, () ->
                DataPullScheduleAnchorEvidence.cutoverSha256(
                        OperationCode.DP01, admission, boundary,
                        "unsafe-kind", "a".repeat(64)
                )
        );
    }

    private static DataPullScope scope() {
        return new DataPullScope(
                "TEST", 307L, null, "account", null, null, null,
                "TEST-" + "b".repeat(64)
        );
    }
}
