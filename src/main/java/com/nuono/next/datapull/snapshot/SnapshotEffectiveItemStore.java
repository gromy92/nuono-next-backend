package com.nuono.next.datapull.snapshot;

import com.nuono.next.infrastructure.mapper.SnapshotEffectiveItemMapper;
import com.nuono.next.infrastructure.mapper.SnapshotEffectiveItemMapper.EffectiveItemInsert;
import java.util.List;
import java.util.Objects;

/** Builds one fully materialized DP-04 effective generation in bounded transactions. */
public final class SnapshotEffectiveItemStore<T> {
    private final SnapshotEffectiveItemMapper mapper;
    private final SnapshotPayloadCodec<T> codec;

    public SnapshotEffectiveItemStore(
            SnapshotEffectiveItemMapper mapper,
            SnapshotPayloadCodec<T> codec
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public int materialize(long taskId, List<SnapshotApplyItem<T>> items) {
        if (taskId < 1L || items == null || items.isEmpty() || items.size() > 20) {
            throw new IllegalArgumentException("snapshot effective chunk is invalid");
        }
        int inserted = 0;
        for (SnapshotApplyItem<T> item : items) {
            SnapshotApplyItem<T> value = Objects.requireNonNull(item, "effective item");
            if (!value.isValidatedIdentityCandidate()) continue;
            requireOne(mapper.insertEffectiveItem(
                    taskId, new EffectiveItemInsert(value, codec.encode(value.getValue()))
            ));
            inserted++;
        }
        return inserted;
    }

    public SnapshotCarryForwardResult carry(
            long targetTaskId,
            long sourceTaskId,
            SnapshotCarryMode mode,
            String afterStableIdentity,
            int limit
    ) {
        if (targetTaskId < 1L || sourceTaskId < 1L || sourceTaskId >= targetTaskId
                || mode == null || mode == SnapshotCarryMode.NONE
                || limit < 1 || limit > 20) {
            throw new IllegalArgumentException("snapshot effective carry is invalid");
        }
        List<SnapshotStageItemRow> rows = mode == SnapshotCarryMode.FULL
                ? mapper.selectFullCarryChunk(
                        sourceTaskId, targetTaskId, afterStableIdentity, limit
                )
                : mapper.selectTargetedCarryChunk(
                        sourceTaskId, targetTaskId, afterStableIdentity, limit
                );
        if (rows == null || rows.size() > limit) {
            throw new IllegalStateException("snapshot effective carry chunk is invalid");
        }
        if (rows.isEmpty()) return SnapshotCarryForwardResult.complete();
        String previous = afterStableIdentity;
        for (SnapshotStageItemRow row : rows) {
            requireCarryRow(targetTaskId, previous, row);
            requireOne(mapper.insertEffectiveItem(targetTaskId, new EffectiveItemInsert(row)));
            previous = row.getStableIdentity();
        }
        return SnapshotCarryForwardResult.advanced(previous, rows.size());
    }

    private void requireCarryRow(long taskId, String previous, SnapshotStageItemRow row) {
        if (row == null || !Objects.equals(row.getTaskId(), taskId)
                || row.getPageNo() == null || row.getPageNo() < 1
                || row.getItemOrdinal() == null || row.getItemOrdinal() < 0
                || !stable(row.getStableIdentity()) || !stable(row.getContentFingerprint())
                || row.getPayload() == null
                || Objects.equals(previous, row.getStableIdentity())) {
            throw new IllegalStateException("snapshot carried effective item is invalid");
        }
    }

    private boolean stable(String value) {
        return value != null && !value.isEmpty() && value.equals(value.trim());
    }

    private void requireOne(int changed) {
        if (changed != 1) {
            throw new IllegalStateException("snapshot effective insert must affect one row");
        }
    }
}
