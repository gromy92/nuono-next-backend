package com.nuono.next.datapull.snapshot;

import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.infrastructure.mapper.SnapshotFactApplyMapper;
import com.nuono.next.infrastructure.mapper.SnapshotCarryProgressMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.function.LongConsumer;
import java.util.function.ToIntFunction;
import org.springframework.transaction.annotation.Transactional;

/** Fenced bounded preparation and atomic current-generation seal. */
public class SnapshotFactApplyGuard {
    static final int APPLY_BATCH_SIZE = 20;

    private final SnapshotFactApplyMapper mapper;
    private final SnapshotCarryProgressMapper carryMapper;
    private final Clock clock;
    private final SnapshotApplyChunkPreparer chunkPreparer;
    private final SnapshotCurrentHeadSealer headSealer;
    private final SnapshotCarryModeResolver carryModeResolver;
    private final SnapshotCarrySourceGuard carrySourceGuard;
    private final SnapshotApplyProgressValidator progressValidator;

    public SnapshotFactApplyGuard(
            SnapshotFactApplyMapper mapper,
            SnapshotCarryProgressMapper carryMapper
    ) {
        this(mapper, carryMapper, Clock.systemUTC());
    }

    SnapshotFactApplyGuard(
            SnapshotFactApplyMapper mapper,
            SnapshotCarryProgressMapper carryMapper,
            Clock clock
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.carryMapper = Objects.requireNonNull(carryMapper, "carryMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.chunkPreparer = new SnapshotApplyChunkPreparer();
        this.headSealer = new SnapshotCurrentHeadSealer(mapper);
        this.carryModeResolver = new SnapshotCarryModeResolver();
        this.carrySourceGuard = new SnapshotCarrySourceGuard();
        this.progressValidator = new SnapshotApplyProgressValidator();
    }

    /**
     * Prepares at most twenty canonical rows. Only a later empty-chunk advance invokes the short
     * domain seal and atomically moves the DP-owned current-generation head.
     */
    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public <T> CompleteSnapshotWriter.ReplaceResult advance(
            CompleteSnapshot<T> snapshot,
            SnapshotItemDescriptor<T> descriptor,
            SnapshotPayloadCodec<T> codec,
            ToIntFunction<List<SnapshotApplyItem<T>>> prepareChunk,
            SnapshotCarryForward carryForward,
            LongConsumer domainSeal
    ) {
        CompleteSnapshot<T> value = Objects.requireNonNull(snapshot, "snapshot");
        SnapshotItemDescriptor<T> itemDescriptor = Objects.requireNonNull(
                descriptor, "descriptor"
        );
        SnapshotPayloadCodec<T> payloadCodec = Objects.requireNonNull(codec, "codec");
        ToIntFunction<List<SnapshotApplyItem<T>>> prepare = Objects.requireNonNull(
                prepareChunk, "prepareChunk"
        );
        SnapshotCarryForward carry = Objects.requireNonNull(carryForward, "carryForward");
        LongConsumer seal = Objects.requireNonNull(domainSeal, "domainSeal");
        LocalDateTime nowUtc = nowUtc();
        SnapshotApplyTaskRow task = mapper.selectTaskForUpdate(value.getTaskId());
        if (!isCurrent(task, value, nowUtc)) {
            return CompleteSnapshotWriter.ReplaceResult.STALE_FENCE;
        }
        SnapshotApplyMarkerRow marker = mapper.selectMarker(value.getTaskId());
        if (marker != null) {
            requireSameMarker(marker, value);
            return CompleteSnapshotWriter.ReplaceResult.ALREADY_APPLIED;
        }

        SnapshotApplyProgressRow progress = currentProgress(value, nowUtc);
        if ("CARRYING".equals(progress.getState())) {
            return carry(value, progress, carry, seal, nowUtc);
        }
        List<SnapshotStageItemRow> rows = mapper.selectCanonicalChunk(
                value.getTaskId(),
                progress.getCursorPageNo(),
                progress.getCursorItemOrdinal(),
                APPLY_BATCH_SIZE
        );
        if (rows == null) {
            throw new IllegalStateException("snapshot apply chunk query returned null");
        }
        if (!rows.isEmpty()) {
            return prepare(value, itemDescriptor, payloadCodec, prepare, progress, rows, nowUtc);
        }
        SnapshotCarryMode carryMode = carryModeResolver.resolve(value, progress);
        if (carryMode != SnapshotCarryMode.NONE) {
            SnapshotCurrentHeadRow source = mapper.selectCurrentHeadForUpdate(value);
            if (source != null) {
                carrySourceGuard.requireValid(value, source);
                if (carrySourceGuard.newer(source, value)) {
                    return CompleteSnapshotWriter.ReplaceResult.STALE_FENCE;
                }
                if (carryMapper.startCarry(
                        value, carryMode, source.getTaskId(), source.getVersionNo(), nowUtc
                ) != 1) {
                    throw new IllegalStateException("snapshot carry start lost its live fence");
                }
                return CompleteSnapshotWriter.ReplaceResult.MORE_WORK;
            }
        }
        return headSealer.seal(value, progress, carryMode, seal, nowUtc);
    }

    private <T> CompleteSnapshotWriter.ReplaceResult prepare(
            CompleteSnapshot<T> snapshot,
            SnapshotItemDescriptor<T> descriptor,
            SnapshotPayloadCodec<T> codec,
            ToIntFunction<List<SnapshotApplyItem<T>>> prepareChunk,
            SnapshotApplyProgressRow progress,
            List<SnapshotStageItemRow> rows,
            LocalDateTime nowUtc
    ) {
        SnapshotApplyChunkPreparer.PreparedChunk<T> prepared = chunkPreparer.prepare(
                snapshot, descriptor, codec, progress, rows, APPLY_BATCH_SIZE
        );
        int effectiveDelta = prepareChunk.applyAsInt(prepared.getItems());
        if (effectiveDelta < 0 || effectiveDelta > rows.size()) {
            throw new IllegalStateException("snapshot effective preparation count is invalid");
        }
        SnapshotStageItemRow last = prepared.getLastRow();
        int changed = mapper.advanceProgress(
                snapshot,
                progress.getCursorPageNo(),
                progress.getCursorItemOrdinal(),
                last.getPageNo(),
                last.getItemOrdinal(),
                rows.size(),
                prepared.getAbsenceUnsafeCount(),
                effectiveDelta,
                nowUtc
        );
        if (changed != 1) {
            throw new IllegalStateException("snapshot apply progress lost its live fence");
        }
        return CompleteSnapshotWriter.ReplaceResult.MORE_WORK;
    }

    private CompleteSnapshotWriter.ReplaceResult carry(
            CompleteSnapshot<?> snapshot,
            SnapshotApplyProgressRow progress,
            SnapshotCarryForward carryForward,
            LongConsumer domainSeal,
            LocalDateTime nowUtc
    ) {
        SnapshotCurrentHeadRow source = mapper.selectCurrentHeadForUpdate(snapshot);
        if (!carrySourceGuard.same(progress, source)) {
            return CompleteSnapshotWriter.ReplaceResult.STALE_FENCE;
        }
        SnapshotCarryForwardResult result = Objects.requireNonNull(
                carryForward.carry(
                        progress.getCarrySourceTaskId(),
                        progress.getCarryMode(),
                        progress.getCarryCursorIdentity(),
                        APPLY_BATCH_SIZE
                ),
                "snapshot carry result"
        );
        if (result.isComplete()) {
            return headSealer.seal(
                    snapshot, progress, progress.getCarryMode(), domainSeal, nowUtc
            );
        }
        if (result.getMaterializedItemCount() > APPLY_BATCH_SIZE
                || Objects.equals(
                        result.getLastStableIdentity(), progress.getCarryCursorIdentity()
                )) {
            throw new IllegalStateException("snapshot carry result exceeded its bound");
        }
        if (carryMapper.advanceCarry(
                snapshot,
                progress.getCarrySourceTaskId(),
                progress.getCarrySourceHeadVersion(),
                progress.getCarryCursorIdentity(),
                result.getLastStableIdentity(),
                result.getMaterializedItemCount(),
                nowUtc
        ) != 1) {
            throw new IllegalStateException("snapshot carry progress lost its live fence");
        }
        return CompleteSnapshotWriter.ReplaceResult.MORE_WORK;
    }

    private SnapshotApplyProgressRow currentProgress(
            CompleteSnapshot<?> snapshot,
            LocalDateTime nowUtc
    ) {
        int inserted = mapper.insertProgressIfAbsent(snapshot, nowUtc);
        if (inserted < 0 || inserted > 1) {
            throw new IllegalStateException("snapshot apply progress insert count is invalid");
        }
        SnapshotApplyProgressRow progress = mapper.selectProgressForUpdate(snapshot.getTaskId());
        if (progress != null && progress.getActiveFenceEpoch() != null
                && progress.getActiveFenceEpoch() < snapshot.getFenceEpoch()) {
            if (mapper.adoptProgressFence(
                    snapshot.getTaskId(), snapshot.getFenceEpoch(), nowUtc
            ) != 1) {
                throw new IllegalStateException("snapshot apply progress fence adoption failed");
            }
            progress = mapper.selectProgressForUpdate(snapshot.getTaskId());
        }
        if (progress == null
                || !Objects.equals(progress.getTaskId(), snapshot.getTaskId())
                || !Objects.equals(progress.getActiveFenceEpoch(), snapshot.getFenceEpoch())
                || !("PREPARING".equals(progress.getState())
                        || "CARRYING".equals(progress.getState()))
                || progress.getCursorPageNo() == null || progress.getCursorPageNo() < 0
                || progress.getCursorItemOrdinal() == null || progress.getCursorItemOrdinal() < -1
                || progress.getPreparedItemCount() == null || progress.getPreparedItemCount() < 0L
                || progress.getPreparedItemCount() > snapshot.getAppliedItemCount()
                || progress.getAbsenceUnsafeItemCount() == null
                || progress.getAbsenceUnsafeItemCount() < 0L
                || progress.getAbsenceUnsafeItemCount() > progress.getPreparedItemCount()
                || progress.getEffectiveItemCount() == null
                || progress.getEffectiveItemCount() < 0L
                || !progressValidator.validCarryState(progress)) {
            throw new IllegalStateException("snapshot apply progress state is invalid");
        }
        return progress;
    }

    private boolean isCurrent(
            SnapshotApplyTaskRow task,
            CompleteSnapshot<?> snapshot,
            LocalDateTime nowUtc
    ) {
        return task != null
                && Objects.equals(task.getTaskId(), snapshot.getTaskId())
                && task.getOperationCode() == snapshot.getOperationCode()
                && Objects.equals(task.getScopeKey(), snapshot.getScopeKey())
                && Objects.equals(task.getBusinessWindowKey(), snapshot.getBusinessWindowKey())
                && Objects.equals(task.getFenceEpoch(), snapshot.getFenceEpoch())
                && "RUNNING".equals(task.getState())
                && Objects.equals(task.getLeaseOwner(), snapshot.getLeaseOwner())
                && task.getLeaseUntil() != null && task.getLeaseUntil().isAfter(nowUtc);
    }

    private void requireSameMarker(SnapshotApplyMarkerRow marker, CompleteSnapshot<?> snapshot) {
        if (!Objects.equals(marker.getTaskId(), snapshot.getTaskId())
                || marker.getOperationCode() != snapshot.getOperationCode()
                || !Objects.equals(marker.getScopeKey(), snapshot.getScopeKey())
                || !Objects.equals(marker.getBusinessWindowKey(), snapshot.getBusinessWindowKey())
                || marker.getAppliedFenceEpoch() == null || marker.getAppliedFenceEpoch() < 1L
                || marker.getAppliedFenceEpoch() > snapshot.getFenceEpoch()
                || marker.getAuthorityKind() != snapshot.getAuthority().getKind()
                || !Objects.equals(marker.getAuthorityTokenSha256(),
                        snapshot.getAuthority().getGenerationTokenSha256())
                || !Objects.equals(marker.getSnapshotAsOfUtc(),
                        snapshot.getAuthority().getProviderAsOfUtc())
                || !Objects.equals(marker.getDeclaredCollectionCount(),
                        snapshot.getAuthority().getDeclaredCollectionCount())
                || !Objects.equals(marker.getSourceItemCount(), snapshot.getSourceItemCount())
                || !Objects.equals(marker.getAppliedItemCount(), snapshot.getAppliedItemCount())
                || !Objects.equals(marker.getIdentitySkippedItemCount(),
                        (long) snapshot.getSkippedIdentityCount())
                || !Objects.equals(marker.getBusinessSkippedItemCount(),
                        snapshot.getBusinessSkippedItemCount())
                || !Objects.equals(marker.getLastPage(), snapshot.getLastPage())
                || marker.getEffectiveItemCount() == null
                || marker.getEffectiveItemCount() < 0L
                || marker.getCarryMode() == null
                || (marker.getCarryMode() == SnapshotCarryMode.NONE
                        && marker.getCarriedFromTaskId() != null)
                || (marker.getCarriedFromTaskId() != null
                        && (marker.getCarriedFromTaskId() < 1L
                        || marker.getCarriedFromTaskId() >= snapshot.getTaskId()))
                || (snapshot.getBusinessSkippedItemCount() > 0L
                        && marker.getCarryMode() != SnapshotCarryMode.FULL)) {
            throw new IllegalStateException("snapshot fact marker identity drift");
        }
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
