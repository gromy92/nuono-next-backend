package com.nuono.next.noonpull.datapull;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.snapshot.CompleteSnapshot;
import com.nuono.next.datapull.snapshot.CompleteSnapshotWriter;
import com.nuono.next.datapull.snapshot.SnapshotFactApplyGuard;
import com.nuono.next.datapull.snapshot.SnapshotEffectiveItemStore;
import java.util.Objects;

/** Seals DP-04's own staged generation; it never invokes product-domain projection writers. */
public final class Dp04ProductSnapshotWriter
        implements CompleteSnapshotWriter<Dp04ProductSnapshotItem> {

    private static final String PROVIDER_CHANNEL = "NOON_PARTNER_PRODUCT_LIST";

    private final SnapshotFactApplyGuard applyGuard;
    private final Dp04ProductSnapshotCodec codec;
    private final SnapshotEffectiveItemStore<Dp04ProductSnapshotItem> effectiveItems;

    public Dp04ProductSnapshotWriter(
            SnapshotFactApplyGuard applyGuard,
            Dp04ProductSnapshotCodec codec,
            SnapshotEffectiveItemStore<Dp04ProductSnapshotItem> effectiveItems
    ) {
        this.applyGuard = Objects.requireNonNull(applyGuard, "applyGuard");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.effectiveItems = Objects.requireNonNull(effectiveItems, "effectiveItems");
    }

    @Override
    public ReplaceResult replace(CompleteSnapshot<Dp04ProductSnapshotItem> snapshot) {
        CompleteSnapshot<Dp04ProductSnapshotItem> value = requireSnapshot(snapshot);
        return applyGuard.advance(
                value,
                codec,
                codec,
                items -> effectiveItems.materialize(value.getTaskId(), items),
                (sourceTaskId, mode, cursor, limit) -> effectiveItems.carry(
                        value.getTaskId(), sourceTaskId, mode, cursor, limit
                ),
                ignored -> { }
        );
    }

    private CompleteSnapshot<Dp04ProductSnapshotItem> requireSnapshot(
            CompleteSnapshot<Dp04ProductSnapshotItem> snapshot
    ) {
        CompleteSnapshot<Dp04ProductSnapshotItem> value = Objects.requireNonNull(
                snapshot, "snapshot"
        );
        if (value.getOperationCode() != OperationCode.DP04
                || !PROVIDER_CHANNEL.equals(value.getProviderChannel())
                || value.getOwnerUserId() < 1L
                || value.getLogicalStoreId() == null || value.getLogicalStoreId() < 1L
                || !stable(value.getProjectCode())
                || !value.getProjectCode().equals(value.getAccountKey())
                || !stable(value.getStoreCode()) || !stable(value.getSiteCode())) {
            throw new IllegalArgumentException("DP-04 complete snapshot scope is invalid");
        }
        return value;
    }

    private boolean stable(String value) {
        return value != null && !value.isEmpty() && value.equals(value.trim());
    }
}
