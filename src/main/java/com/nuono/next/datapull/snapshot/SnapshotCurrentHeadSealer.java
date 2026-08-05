package com.nuono.next.datapull.snapshot;

import com.nuono.next.infrastructure.mapper.SnapshotFactApplyMapper;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.LongConsumer;

/** Monotonic, atomic current-generation head and domain visibility seal. */
final class SnapshotCurrentHeadSealer {
    private final SnapshotFactApplyMapper mapper;

    SnapshotCurrentHeadSealer(SnapshotFactApplyMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    CompleteSnapshotWriter.ReplaceResult seal(
            CompleteSnapshot<?> snapshot,
            SnapshotApplyProgressRow progress,
            SnapshotCarryMode carryMode,
            LongConsumer domainSeal,
            LocalDateTime nowUtc
    ) {
        requireCompleteAccounting(snapshot, progress);
        SnapshotCarryMode mode = Objects.requireNonNull(carryMode, "carryMode");
        SnapshotCurrentHeadRow head = mapper.selectCurrentHeadForUpdate(snapshot);
        if (head != null) requireHead(head, snapshot);
        if (!ownsCarrySource(progress, head)) {
            return CompleteSnapshotWriter.ReplaceResult.STALE_FENCE;
        }
        if (isNewer(head, snapshot)) return CompleteSnapshotWriter.ReplaceResult.STALE_FENCE;
        if (head != null && Objects.equals(head.getTaskId(), snapshot.getTaskId())) {
            throw new IllegalStateException("snapshot current head exists without its marker");
        }
        if (mode != SnapshotCarryMode.NONE && head != null
                && !"CARRYING".equals(progress.getState())) {
            throw new IllegalStateException("snapshot preservation skipped its effective carry");
        }
        if ("CARRYING".equals(progress.getState()) && progress.getCarryMode() != mode) {
            throw new IllegalStateException("snapshot carry mode drift");
        }
        boolean retireMissing = mode == SnapshotCarryMode.NONE;
        int changed = mapper.upsertCurrentHead(snapshot, retireMissing, nowUtc);
        if (changed < 0 || changed > 2) {
            throw new IllegalStateException("snapshot current head seal returned invalid count");
        }
        SnapshotCurrentHeadRow observed = mapper.selectCurrentHeadForUpdate(snapshot);
        if (!isExactWinner(observed, snapshot, retireMissing)) {
            if (observed != null) requireHead(observed, snapshot);
            if (isNewer(observed, snapshot)) {
                return CompleteSnapshotWriter.ReplaceResult.STALE_FENCE;
            }
            throw new IllegalStateException("snapshot current head rejected a non-stale seal");
        }
        domainSeal.accept(progress.getEffectiveItemCount());
        if (mapper.insertMarkerIfLive(
                snapshot,
                progress.getEffectiveItemCount(),
                mode,
                progress.getCarrySourceTaskId(),
                nowUtc
        ) != 1) {
            throw new IllegalStateException("snapshot marker rejected after domain seal");
        }
        if (mapper.markProgressSealed(
                snapshot.getTaskId(), snapshot.getFenceEpoch(),
                snapshot.getAppliedItemCount(), progress.getEffectiveItemCount(), mode, nowUtc
        ) != 1) {
            throw new IllegalStateException("snapshot apply progress seal lost its fence");
        }
        return CompleteSnapshotWriter.ReplaceResult.APPLIED;
    }

    private boolean isExactWinner(
            SnapshotCurrentHeadRow head,
            CompleteSnapshot<?> snapshot,
            boolean retireMissing
    ) {
        if (head == null) return false;
        requireHead(head, snapshot);
        return Objects.equals(head.getTaskId(), snapshot.getTaskId())
                && Objects.equals(head.getScheduleSlot(), snapshot.getScheduleSlot())
                && Objects.equals(head.getBusinessWindowKey(), snapshot.getBusinessWindowKey())
                && Objects.equals(head.getRetireMissing(), retireMissing);
    }

    private void requireCompleteAccounting(
            CompleteSnapshot<?> snapshot,
            SnapshotApplyProgressRow progress
    ) {
        if (!Objects.equals(progress.getPreparedItemCount(), snapshot.getAppliedItemCount())
                || progress.getAbsenceUnsafeItemCount() == null
                || progress.getAbsenceUnsafeItemCount() < 0L
                || progress.getAbsenceUnsafeItemCount() > progress.getPreparedItemCount()
                || progress.getEffectiveItemCount() == null
                || progress.getEffectiveItemCount() < 0L) {
            throw new IllegalStateException("snapshot apply progress accounting drift");
        }
    }

    private boolean ownsCarrySource(
            SnapshotApplyProgressRow progress,
            SnapshotCurrentHeadRow head
    ) {
        if (!"CARRYING".equals(progress.getState())) return true;
        return head != null
                && Objects.equals(head.getTaskId(), progress.getCarrySourceTaskId())
                && Objects.equals(head.getVersionNo(), progress.getCarrySourceHeadVersion());
    }

    private boolean isNewer(SnapshotCurrentHeadRow head, CompleteSnapshot<?> snapshot) {
        if (head == null) return false;
        int order = head.getScheduleSlot().compareTo(snapshot.getScheduleSlot());
        return order > 0 || (order == 0 && head.getTaskId() > snapshot.getTaskId());
    }

    private void requireHead(SnapshotCurrentHeadRow head, CompleteSnapshot<?> snapshot) {
        if (head.getOperationCode() != snapshot.getOperationCode()
                || !Objects.equals(head.getScopeKey(), snapshot.getScopeKey())
                || head.getTaskId() == null || head.getTaskId() < 1L
                || !stable(head.getBusinessWindowKey())
                || head.getScheduleSlot() == null || head.getRetireMissing() == null
                || head.getVersionNo() == null || head.getVersionNo() < 0L) {
            throw new IllegalStateException("snapshot current head is invalid");
        }
    }

    private boolean stable(String value) {
        return value != null && !value.isEmpty() && value.equals(value.trim());
    }
}
