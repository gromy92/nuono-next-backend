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

    @Test
    void fencedUnknownCreatesAndValidatedLocalCountingAreReleaseSafe() {
        ReportRuntimeReleaseEvidence evidence = new ReportRuntimeReleaseEvidence(
                noReplay(OperationCode.DP01),
                locallyValidated(OperationCode.DP02),
                noReplay(OperationCode.DP03),
                noReplay(OperationCode.DP07B)
        );

        assertTrue(evidence.verified());
        assertEquals(List.of(), evidence.getBlockers());
    }

    @Test
    void exactWindowTwoPassPageQueryIsReleaseSafe() {
        ReportRuntimeReleaseEvidence evidence = new ReportRuntimeReleaseEvidence(
                supported(OperationCode.DP01),
                exactWindowPage(OperationCode.DP02),
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

    private ReportProviderCapabilitySource noReplay(OperationCode operation) {
        return () -> new ReportProviderCapabilities(
                operation,
                ReportProviderCapabilities.CreateReadbackEvidence
                        .READ_ONLY_EXPORT_RETRY_AFTER_PERSISTED_BACKOFF,
                ReportProviderCapabilities.EmptyProofEvidence
                        .AUTHORITATIVE_ROW_COUNT_FOR_EXACT_HANDLE_AND_INTENT,
                ReportProviderCapabilities.ArtifactCompletenessEvidence
                        .AUTHORITATIVE_ROW_COUNT_FOR_EXACT_HANDLE_AND_INTENT
        );
    }

    private ReportProviderCapabilitySource locallyValidated(OperationCode operation) {
        return () -> new ReportProviderCapabilities(
                operation,
                ReportProviderCapabilities.CreateReadbackEvidence
                        .SAME_INTENT_POLL_WITH_CONTAINER_VALIDATION,
                ReportProviderCapabilities.EmptyProofEvidence
                        .UNPROVEN_EMPTY_REMAINS_WAITING,
                ReportProviderCapabilities.ArtifactCompletenessEvidence
                        .COMPLETE_DOWNLOAD_WITH_LOCAL_ROW_COUNT_AND_CONTAINER_VALIDATION
        );
    }

    private ReportProviderCapabilitySource exactWindowPage(OperationCode operation) {
        return () -> new ReportProviderCapabilities(
                operation,
                ReportProviderCapabilities.CreateReadbackEvidence
                        .DIRECT_EXACT_WINDOW_PAGE_QUERY,
                ReportProviderCapabilities.EmptyProofEvidence
                        .AUTHORITATIVE_TOTAL_FOR_EXACT_WINDOW,
                ReportProviderCapabilities.ArtifactCompletenessEvidence
                        .EXACT_PAGE_EXTENT_WITH_TWO_PASS_VALIDATION
        );
    }
}
