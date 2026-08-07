package com.nuono.next.datapull.report;

import com.nuono.next.datapull.runtime.OperationCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Explicit provider-contract evidence used by the report runtime release gate. */
public final class ReportProviderCapabilities {

    public enum CreateReadbackEvidence {
        EXACT_IMMUTABLE_HANDLE_AND_INTENT,
        /** Read-only export creation may retry the persisted intent after a reconcile backoff. */
        READ_ONLY_EXPORT_RETRY_AFTER_PERSISTED_BACKOFF,
        /** The same requested window is read and the downloaded container validates every row. */
        SAME_INTENT_POLL_WITH_CONTAINER_VALIDATION,
        UNAVAILABLE,
        PAGINATION_UNSAFE,
        STABLE_REQUEST_KEY_UNAVAILABLE
    }

    public enum EmptyProofEvidence {
        AUTHORITATIVE_ROW_COUNT_FOR_EXACT_HANDLE_AND_INTENT,
        /** An unproven empty file remains non-terminal and therefore cannot erase facts. */
        UNPROVEN_EMPTY_REMAINS_WAITING,
        UNAVAILABLE
    }

    public enum ArtifactCompletenessEvidence {
        AUTHORITATIVE_ROW_COUNT_FOR_EXACT_HANDLE_AND_INTENT,
        /** Immutable full download plus bounded local row counting and container validation. */
        COMPLETE_DOWNLOAD_WITH_LOCAL_ROW_COUNT_AND_CONTAINER_VALIDATION,
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
