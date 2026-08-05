package com.nuono.next.datapull.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.datapull.runtime.OperationCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportProviderCapabilitiesTest {
    @Test
    void exactReadbackEmptyAndArtifactAuthorityAreAllRequiredForRelease() {
        ReportRuntimeReleaseEvidence evidence = new ReportRuntimeReleaseEvidence(
                supported(OperationCode.DP01),
                supported(OperationCode.DP02),
                supported(OperationCode.DP03),
                supported(OperationCode.DP07B)
        );

        assertTrue(evidence.verified());
        assertEquals(List.of(), evidence.getBlockers());
    }

    private ReportProviderCapabilitySource supported(OperationCode operation) {
        return () -> new ReportProviderCapabilities(
                operation,
                ReportProviderCapabilities.CreateReadbackEvidence
                        .EXACT_IMMUTABLE_HANDLE_AND_INTENT,
                ReportProviderCapabilities.EmptyProofEvidence
                        .AUTHORITATIVE_ROW_COUNT_FOR_EXACT_HANDLE_AND_INTENT,
                ReportProviderCapabilities.ArtifactCompletenessEvidence
                        .AUTHORITATIVE_ROW_COUNT_FOR_EXACT_HANDLE_AND_INTENT
        );
    }
}
