package com.nuono.next.officialwarehouse.datapull;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.snapshot.CompleteSnapshot;
import com.nuono.next.datapull.snapshot.CompleteSnapshotWriter;
import com.nuono.next.datapull.snapshot.SnapshotFactApplyGuard;
import java.util.Objects;

/** Bounded DP-07-A preparation plus atomic inventory visibility seal. */
public final class Dp07InventorySnapshotWriter
        implements CompleteSnapshotWriter<Dp07InventorySnapshotItem> {

    private static final String PROVIDER_CHANNEL = "NOON_FBN_INVENTORY";

    private final SnapshotFactApplyGuard applyGuard;
    private final Dp07InventorySnapshotCodec codec;
    private final Dp07InventorySnapshotBatchWriter batchWriter;

    public Dp07InventorySnapshotWriter(
            SnapshotFactApplyGuard applyGuard,
            Dp07InventorySnapshotCodec codec,
            Dp07InventorySnapshotBatchWriter batchWriter
    ) {
        this.applyGuard = Objects.requireNonNull(applyGuard, "applyGuard");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.batchWriter = Objects.requireNonNull(batchWriter, "batchWriter");
    }

    @Override
    public ReplaceResult replace(CompleteSnapshot<Dp07InventorySnapshotItem> snapshot) {
        CompleteSnapshot<Dp07InventorySnapshotItem> value = requireSnapshot(snapshot);
        return applyGuard.advance(
                value,
                codec,
                codec,
                items -> batchWriter.prepare(value, items),
                (sourceTaskId, mode, cursor, limit) -> batchWriter.carry(
                        value, sourceTaskId, mode, cursor, limit
                ),
                effectiveItemCount -> batchWriter.seal(value, effectiveItemCount)
        );
    }

    private CompleteSnapshot<Dp07InventorySnapshotItem> requireSnapshot(
            CompleteSnapshot<Dp07InventorySnapshotItem> snapshot
    ) {
        CompleteSnapshot<Dp07InventorySnapshotItem> value = Objects.requireNonNull(
                snapshot, "snapshot"
        );
        if (value.getOperationCode() != OperationCode.DP07A
                || !PROVIDER_CHANNEL.equals(value.getProviderChannel())
                || value.getOwnerUserId() < 1L
                || value.getLogicalStoreId() == null || value.getLogicalStoreId() < 1L
                || !stable(value.getProjectCode())
                || !value.getProjectCode().equals(value.getAccountKey())
                || !stable(value.getStoreCode()) || !stable(value.getSiteCode())) {
            throw new IllegalArgumentException("DP-07-A complete snapshot scope is invalid");
        }
        return value;
    }

    private boolean stable(String value) {
        return value != null && !value.isEmpty() && value.equals(value.trim());
    }
}
