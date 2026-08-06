package com.nuono.next.datapull.snapshot;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.OperationHandler;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderOutcomeType;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import java.time.Duration;
import java.util.Objects;

/** One-call FETCH/VERIFY and bounded local COMPARE/APPLY complete-snapshot engine. */
public final class CompleteSnapshotEngine<T> implements OperationHandler<DataPullTask> {
    private final OperationCode operationCode;
    private final SnapshotPageProvider<T> provider;
    private final SnapshotStageStore<T> stageStore;
    private final CompleteSnapshotWriter<T> writer;
    private final SnapshotCheckpointCodec checkpointCodec;
    private final SnapshotProviderFailureHandler providerFailures;

    public CompleteSnapshotEngine(
            OperationCode operationCode,
            SnapshotPageProvider<T> provider,
            SnapshotStageStore<T> stageStore,
            CompleteSnapshotWriter<T> writer,
            SnapshotCheckpointCodec checkpointCodec,
            ProviderWaitTransition providerWaitTransition
    ) {
        this.operationCode = Objects.requireNonNull(operationCode, "operationCode");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.stageStore = Objects.requireNonNull(stageStore, "stageStore");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.checkpointCodec = Objects.requireNonNull(checkpointCodec, "checkpointCodec");
        this.providerFailures = new SnapshotProviderFailureHandler(
                operationCode,
                checkpointCodec,
                Objects.requireNonNull(providerWaitTransition, "providerWaitTransition")
        );
    }

    @Override
    public OperationCode operationCode() {
        return operationCode;
    }

    @Override
    public AdvanceResult advance(DataPullTask task) {
        String taskError = SnapshotEngineTaskGuard.validate(task, operationCode);
        if (taskError != null) {
            return AdvanceResult.failed(task == null ? null : task.getCheckpoint(), taskError);
        }

        SnapshotCheckpoint checkpoint;
        try {
            checkpoint = checkpointCodec.decode(task.getCheckpoint());
        } catch (RuntimeException invalidCheckpoint) {
            return AdvanceResult.failed(task.getCheckpoint(), "SNAPSHOT_CHECKPOINT_INVALID");
        }

        switch (checkpoint.getPhase()) {
            case VERIFY:
                return verify(task, checkpoint);
            case COMPARE:
                return compare(task, checkpoint);
            case APPLY:
                return apply(task, checkpoint);
            case RESET:
                return reset(task);
            case FETCH:
            default:
                return fetch(task, checkpoint);
        }
    }

    private AdvanceResult fetch(DataPullTask task, SnapshotCheckpoint checkpoint) {
        String encodedCheckpoint = checkpointCodec.encode(checkpoint);
        SnapshotPageRequest request;
        try {
            request = SnapshotPageRequest.from(task, checkpoint.getNextPage());
        } catch (RuntimeException invalidContext) {
            return AdvanceResult.failed(encodedCheckpoint, "SNAPSHOT_TASK_CONTEXT_INVALID");
        }

        ProviderOutcome<SnapshotPage<T>> outcome;
        try {
            outcome = Objects.requireNonNull(provider.fetchPage(request), "provider outcome");
        } catch (RuntimeException untypedProviderFailure) {
            return retryExact(task, checkpoint, "SNAPSHOT_PROVIDER_UNTYPED_FAILURE");
        }
        if (outcome.getType() != ProviderOutcomeType.SUCCESS) {
            return providerFailures.fromOutcome(task, checkpoint, outcome);
        }

        SnapshotPage<T> page = outcome.getValue();
        if (page == null) {
            return retryExact(task, checkpoint, "SNAPSHOT_SUCCESS_WITHOUT_PAGE");
        }
        if (page.getPageNo() != checkpoint.getNextPage()) {
            return retryExact(task, checkpoint, "SNAPSHOT_PAGE_NUMBER_MISMATCH");
        }
        if (page.getAuthorityMode() == SnapshotPage.AuthorityMode.PROVIDER_AUTHORITY
                && !page.getAuthority().isPresent()) {
            return retryExact(task, checkpoint, "SNAPSHOT_AUTHORITY_MISSING");
        }

        SnapshotStageResult staged;
        try {
            staged = stageStore.stagePage(task.getId(), task.getFenceEpoch(), page);
        } catch (RuntimeException stagingFailure) {
            return retryExact(task, checkpoint, "SNAPSHOT_STAGE_UNTYPED_FAILURE");
        }
        if (!staged.isAccepted()) {
            return restartSnapshot(task, staged.getSanitizedCode());
        }
        if (staged.reachedKnownLastPage(page.getPageNo())) {
            int lastPage = staged.getKnownLastPage().orElseThrow();
            SnapshotCheckpoint next = page.getAuthorityMode()
                    == SnapshotPage.AuthorityMode.TWO_PASS_REQUIRED
                    ? SnapshotCheckpoint.verify(1, lastPage)
                    : SnapshotCheckpoint.apply(lastPage);
            return AdvanceResult.queued(checkpointCodec.encode(next));
        }
        if (!staged.getNextPage().isPresent()) {
            return retryExact(task, checkpoint, "SNAPSHOT_LAST_PAGE_UNKNOWN");
        }

        Integer knownLastPage = staged.getKnownLastPage().isPresent()
                ? staged.getKnownLastPage().getAsInt()
                : null;
        SnapshotCheckpoint next = SnapshotCheckpoint.fetch(
                staged.getNextPage().getAsInt(),
                knownLastPage
        );
        return AdvanceResult.queued(checkpointCodec.encode(next));
    }

    private AdvanceResult verify(DataPullTask task, SnapshotCheckpoint checkpoint) {
        String encoded = checkpointCodec.encode(checkpoint);
        SnapshotPageRequest request;
        try {
            request = SnapshotPageRequest.from(task, checkpoint.getNextPage());
        } catch (RuntimeException invalidContext) {
            return AdvanceResult.failed(encoded, "SNAPSHOT_TASK_CONTEXT_INVALID");
        }
        ProviderOutcome<SnapshotPage<T>> outcome;
        try {
            outcome = Objects.requireNonNull(provider.fetchPage(request), "provider outcome");
        } catch (RuntimeException untypedProviderFailure) {
            return retryExact(task, checkpoint, "SNAPSHOT_PROVIDER_UNTYPED_FAILURE");
        }
        if (outcome.getType() != ProviderOutcomeType.SUCCESS) {
            return providerFailures.fromOutcome(task, checkpoint, outcome);
        }
        SnapshotPage<T> page = outcome.getValue();
        if (page == null || page.getPageNo() != checkpoint.getNextPage()) {
            return retryExact(task, checkpoint, "SNAPSHOT_VERIFY_PAGE_MISMATCH");
        }
        SnapshotVerificationResult verified;
        try {
            verified = stageStore.verifyPage(task.getId(), task.getFenceEpoch(), page);
        } catch (RuntimeException failure) {
            return retryExact(task, checkpoint, "SNAPSHOT_VERIFY_UNTYPED_FAILURE");
        }
        if (!verified.isAccepted()) {
            return restartSnapshot(task, verified.getSanitizedCode());
        }
        int lastPage = checkpoint.getKnownLastPage().orElseThrow();
        if (verified.isComplete()) {
            return AdvanceResult.queued(checkpointCodec.encode(
                    SnapshotCheckpoint.compare(lastPage)
            ));
        }
        if (!verified.getNextPage().isPresent()) {
            return restartSnapshot(task, "SNAPSHOT_VERIFY_CURSOR_MISSING");
        }
        return AdvanceResult.queued(checkpointCodec.encode(SnapshotCheckpoint.verify(
                verified.getNextPage().getAsInt(), lastPage
        )));
    }

    private AdvanceResult compare(DataPullTask task, SnapshotCheckpoint checkpoint) {
        String encoded = checkpointCodec.encode(checkpoint);
        SnapshotComparisonResult compared;
        try {
            compared = stageStore.compareNext(task.getId(), task.getFenceEpoch(), 256);
        } catch (RuntimeException failure) {
            return AdvanceResult.waitingRemote(
                    encoded, Duration.ofMinutes(1), "SNAPSHOT_COMPARE_UNKNOWN"
            );
        }
        if (!compared.isAccepted()) {
            return restartSnapshot(task, compared.getSanitizedCode());
        }
        if (compared.isVerified()) {
            int lastPage = checkpoint.getKnownLastPage().orElseThrow();
            return AdvanceResult.queued(checkpointCodec.encode(
                    SnapshotCheckpoint.apply(lastPage)
            ));
        }
        return AdvanceResult.queued(encoded);
    }

    private AdvanceResult apply(DataPullTask task, SnapshotCheckpoint checkpoint) {
        String encodedCheckpoint = checkpointCodec.encode(checkpoint);
        SnapshotStageProof<T> proof;
        try {
            proof = stageStore.proveCompleteMetadata(task.getId(), task.getFenceEpoch());
        } catch (RuntimeException proofFailure) {
            return retryExact(task, checkpoint, "SNAPSHOT_PROOF_UNTYPED_FAILURE");
        }
        if (!proof.isComplete()) {
            return restartSnapshot(task, proof.getSanitizedCode());
        }
        if (proof.getLastPage().orElseThrow() != checkpoint.getKnownLastPage().orElseThrow()) {
            return restartSnapshot(task, "SNAPSHOT_CHECKPOINT_LAST_PAGE_DRIFT");
        }
        SnapshotCollectionAuthority authority = proof.getAuthority().orElse(null);
        if (authority == null) {
            return restartSnapshot(task, "SNAPSHOT_AUTHORITY_MISSING");
        }
        if (authority.getDeclaredCollectionCount() != proof.getSourceItemCount()) {
            return restartSnapshot(task, "SNAPSHOT_AUTHORITY_EXTENT_DRIFT");
        }

        CompleteSnapshotWriter.ReplaceResult replaceResult;
        try {
            replaceResult = Objects.requireNonNull(
                    writer.replace(CompleteSnapshot.from(task, proof)),
                    "replace result"
            );
        } catch (RuntimeException applyFailure) {
            return retryExact(task, checkpoint, "SNAPSHOT_APPLY_UNKNOWN_RESULT");
        }
        if (replaceResult == CompleteSnapshotWriter.ReplaceResult.STALE_FENCE) {
            return AdvanceResult.failed(encodedCheckpoint, "SNAPSHOT_APPLY_STALE_FENCE");
        }
        if (replaceResult == CompleteSnapshotWriter.ReplaceResult.MORE_WORK) {
            return AdvanceResult.queued(encodedCheckpoint);
        }
        return AdvanceResult.succeeded();
    }

    private AdvanceResult retryExact(
            DataPullTask task,
            SnapshotCheckpoint checkpoint,
            String sanitizedCode
    ) {
        return providerFailures.transientFailure(task, checkpoint, sanitizedCode);
    }

    /** Queue a separate local-only reset advance; never combine a provider call with cleanup. */
    private AdvanceResult restartSnapshot(DataPullTask task, String sanitizedCode) {
        Objects.requireNonNull(sanitizedCode, "sanitizedCode");
        return AdvanceResult.queued(
                "SNAPSHOT_RESET",
                null,
                checkpointCodec.encode(SnapshotCheckpoint.resetting())
        );
    }

    private AdvanceResult reset(DataPullTask task) {
        String checkpoint = checkpointCodec.encode(SnapshotCheckpoint.resetting());
        SnapshotStageClearResult result;
        try {
            result = Objects.requireNonNull(
                    stageStore.clearBounded(task.getId(), task.getFenceEpoch()),
                    "snapshot stage clear result"
            );
        } catch (RuntimeException clearFailure) {
            return AdvanceResult.waitingRemote(
                    checkpoint,
                    Duration.ofMinutes(1),
                    "SNAPSHOT_STAGE_RESET_UNKNOWN"
            );
        }
        if (result == SnapshotStageClearResult.MORE_WORK) {
            return AdvanceResult.queued("SNAPSHOT_RESET", null, checkpoint);
        }
        if (result == SnapshotStageClearResult.STALE_FENCE) {
            return AdvanceResult.failed(checkpoint, "SNAPSHOT_STAGE_RESET_STALE_FENCE");
        }
        if (result == SnapshotStageClearResult.APPLY_ALREADY_STARTED) {
            return AdvanceResult.failed(checkpoint, "SNAPSHOT_STAGE_RESET_APPLY_STARTED");
        }
        return retryExact(
                task,
                SnapshotCheckpoint.initial(),
                "SNAPSHOT_CONTAINER_RESTARTED"
        );
    }

}
