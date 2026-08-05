package com.nuono.next.datapull.snapshot;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.SnapshotCurrentFactMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/** Typed bounded query Seam over the current sealed snapshot head. */
public final class SnapshotCurrentFactStore<T> {
    private final SnapshotCurrentFactMapper mapper;
    private final SnapshotItemDescriptor<T> descriptor;
    private final SnapshotPayloadCodec<T> codec;

    public SnapshotCurrentFactStore(
            SnapshotCurrentFactMapper mapper,
            SnapshotItemDescriptor<T> descriptor,
            SnapshotPayloadCodec<T> codec
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    @Transactional(readOnly = true)
    public Optional<SnapshotCurrentFactPage<T>> readChunk(
            OperationCode operationCode,
            String scopeKey,
            String afterStableIdentity,
            int limit
    ) {
        OperationCode operation = Objects.requireNonNull(operationCode, "operationCode");
        if (operation != OperationCode.DP04) {
            throw new IllegalArgumentException("current effective item facts only support DP04");
        }
        if (scopeKey == null || scopeKey.isEmpty() || !scopeKey.equals(scopeKey.trim())
                || (afterStableIdentity != null
                        && (afterStableIdentity.isEmpty()
                        || !afterStableIdentity.equals(afterStableIdentity.trim())))
                || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("current snapshot query is invalid");
        }
        SnapshotCurrentHeadRow head = mapper.selectHead(operation, scopeKey);
        if (head == null) return Optional.empty();
        requireHead(operation, scopeKey, head);
        List<SnapshotStageItemRow> rows = mapper.selectCurrentChunk(
                operation, scopeKey, head.getTaskId(), afterStableIdentity, limit
        );
        if (rows == null || rows.size() > limit) {
            throw new IllegalStateException("current snapshot chunk is invalid");
        }
        List<SnapshotApplyItem<T>> items = new ArrayList<>(rows.size());
        for (SnapshotStageItemRow row : rows) {
            T value = Objects.requireNonNull(codec.decode(row.getPayload()), "decoded fact");
            if (!Objects.equals(row.getTaskId(), head.getTaskId())
                    || !Objects.equals(row.getStableIdentity(), descriptor.stableIdentity(value))
                    || !Objects.equals(row.getContentFingerprint(),
                            descriptor.stableContentFingerprint(value))
                    || !Objects.equals(row.getValidatedIdentityCandidate(),
                            descriptor.isValidatedIdentityCandidate(value))
                    || !Objects.equals(row.getAbsenceReconciliationSafe(),
                            descriptor.isAbsenceReconciliationSafe(value))) {
                throw new IllegalStateException("current snapshot fact integrity drift");
            }
            items.add(new SnapshotApplyItem<>(row, value));
        }
        return Optional.of(new SnapshotCurrentFactPage<>(
                head.getTaskId(), head.getRetireMissing(), items
        ));
    }

    private void requireHead(
            OperationCode operation,
            String scopeKey,
            SnapshotCurrentHeadRow head
    ) {
        if (head.getOperationCode() != operation
                || !Objects.equals(head.getScopeKey(), scopeKey)
                || head.getTaskId() == null || head.getTaskId() < 1L
                || head.getRetireMissing() == null || head.getVersionNo() == null
                || head.getVersionNo() < 0L || head.getScheduleSlot() == null) {
            throw new IllegalStateException("current snapshot head is invalid");
        }
    }
}
