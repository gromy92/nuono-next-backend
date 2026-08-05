package com.nuono.next.datapull.report;

import com.nuono.next.datapull.runtime.OperationCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Explicit provider-contract evidence used by the report runtime release gate. */
public final class ReportProviderCapabilities {

    public enum CreateReadbackEvidence {
        EXACT_IMMUTABLE_HANDLE_AND_INTENT,
        UNAVAILABLE,
        PAGINATION_UNSAFE,
        STABLE_REQUEST_KEY_UNAVAILABLE
    }

    public enum EmptyProofEvidence {
        AUTHORITATIVE_ROW_COUNT_FOR_EXACT_HANDLE_AND_INTENT,
        UNAVAILABLE
    }

    public enum ArtifactCompletenessEvidence {
        AUTHORITATIVE_ROW_COUNT_FOR_EXACT_HANDLE_AND_INTENT,
        UNAVAILABLE
    }

    private final OperationCode operationCode;
    private final CreateReadbackEvidence createReadbackEvidence;
    private final EmptyProofEvidence emptyProofEvidence;
    private final ArtifactCompletenessEvidence artifactCompletenessEvidence;

    public ReportProviderCapabilities(
            OperationCode operationCode,
            CreateReadbackEvidence createReadbackEvidence,
            EmptyProofEvidence emptyProofEvidence,
            ArtifactCompletenessEvidence artifactCompletenessEvidence
    ) {
        this.operationCode = Objects.requireNonNull(operationCode, "operationCode");
        this.createReadbackEvidence = Objects.requireNonNull(
                createReadbackEvidence,
                "createReadbackEvidence"
        );
        this.emptyProofEvidence = Objects.requireNonNull(
                emptyProofEvidence,
                "emptyProofEvidence"
        );
        this.artifactCompletenessEvidence = Objects.requireNonNull(
                artifactCompletenessEvidence,
                "artifactCompletenessEvidence"
        );
    }

    public List<String> releaseBlockers() {
        List<String> blockers = new ArrayList<>();
        String prefix = operationCode.name();
        if (createReadbackEvidence == CreateReadbackEvidence.UNAVAILABLE) {
            blockers.add(prefix + "_CREATE_READBACK_UNAVAILABLE");
        } else if (createReadbackEvidence == CreateReadbackEvidence.PAGINATION_UNSAFE) {
            blockers.add(prefix + "_CREATE_READBACK_PAGINATION_UNSAFE");
        } else if (createReadbackEvidence
                == CreateReadbackEvidence.STABLE_REQUEST_KEY_UNAVAILABLE) {
            blockers.add(prefix + "_CREATE_READBACK_STABLE_REQUEST_KEY_UNAVAILABLE");
        }
        if (emptyProofEvidence == EmptyProofEvidence.UNAVAILABLE) {
            blockers.add(prefix + "_EMPTY_PROOF_UNAVAILABLE");
        }
        if (artifactCompletenessEvidence == ArtifactCompletenessEvidence.UNAVAILABLE) {
            blockers.add(prefix + "_ARTIFACT_COMPLETENESS_UNAVAILABLE");
        }
        return List.copyOf(blockers);
    }

    public OperationCode getOperationCode() {
        return operationCode;
    }

    public CreateReadbackEvidence getCreateReadbackEvidence() {
        return createReadbackEvidence;
    }

    public EmptyProofEvidence getEmptyProofEvidence() {
        return emptyProofEvidence;
    }

    public ArtifactCompletenessEvidence getArtifactCompletenessEvidence() {
        return artifactCompletenessEvidence;
    }
}
