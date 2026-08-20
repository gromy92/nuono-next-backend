package com.nuono.next.noonpull.datapull;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.snapshot.CompleteSnapshot;
import com.nuono.next.datapull.snapshot.CompleteSnapshotWriter;
import com.nuono.next.datapull.snapshot.SnapshotApplyItem;
import com.nuono.next.datapull.snapshot.SnapshotCarryForwardResult;
import com.nuono.next.datapull.snapshot.SnapshotFactApplyGuard;
import com.nuono.next.noonpull.NoonOrderFactWriter;
import com.nuono.next.noonpull.NoonOrderLineFact;
import java.util.List;
import java.util.Objects;

/** Fenced bounded upsert for a completeness-proven DP02 page collection. */
public final class Dp02OrderPageWriter
        implements CompleteSnapshotWriter<NoonOrderLineFact> {
    private final SnapshotFactApplyGuard applyGuard;
    private final Dp02OrderFactCodec codec;
    private final NoonOrderFactWriter factWriter;

    public Dp02OrderPageWriter(
            SnapshotFactApplyGuard applyGuard,
            Dp02OrderFactCodec codec,
            NoonOrderFactWriter factWriter
    ) {
        this.applyGuard = Objects.requireNonNull(applyGuard, "applyGuard");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.factWriter = Objects.requireNonNull(factWriter, "factWriter");
    }

    @Override
    public ReplaceResult replace(CompleteSnapshot<NoonOrderLineFact> snapshot) {
        CompleteSnapshot<NoonOrderLineFact> value = requireSnapshot(snapshot);
        return applyGuard.advance(
                value,
                codec,
                codec,
                this::upsertFacts,
                (sourceTaskId, mode, cursor, limit) ->
                        SnapshotCarryForwardResult.complete(),
                ignored -> { }
        );
    }

    private int upsertFacts(List<SnapshotApplyItem<NoonOrderLineFact>> items) {
        List<NoonOrderLineFact> facts = items.stream()
                .map(SnapshotApplyItem::getValue)
                .collect(java.util.stream.Collectors.toUnmodifiableList());
        factWriter.upsertLines(facts);
        return facts.size();
    }

    private CompleteSnapshot<NoonOrderLineFact> requireSnapshot(
            CompleteSnapshot<NoonOrderLineFact> snapshot
    ) {
        CompleteSnapshot<NoonOrderLineFact> value = Objects.requireNonNull(
                snapshot, "snapshot"
        );
        if (value.getOperationCode() != OperationCode.DP02
                || !Dp02OrderPageProvider.CHANNEL.equals(value.getProviderChannel())
                || value.getOwnerUserId() < 1L
                || value.getLogicalStoreId() == null || value.getLogicalStoreId() < 1L
                || !stable(value.getProjectCode())
                || !value.getProjectCode().equals(value.getAccountKey())
                || !stable(value.getStoreCode())
                || !stable(value.getSiteCode())) {
            throw new IllegalArgumentException("DP02 page collection scope is invalid");
        }
        return value;
    }

    private boolean stable(String value) {
        return value != null && !value.isEmpty() && value.equals(value.trim());
    }
}
