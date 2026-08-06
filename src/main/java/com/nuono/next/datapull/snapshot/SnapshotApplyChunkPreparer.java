package com.nuono.next.datapull.snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Decodes and validates one bounded canonical snapshot preparation chunk. */
final class SnapshotApplyChunkPreparer {

    <T> PreparedChunk<T> prepare(
            CompleteSnapshot<T> snapshot,
            SnapshotItemDescriptor<T> descriptor,
            SnapshotPayloadCodec<T> codec,
            SnapshotApplyProgressRow progress,
            List<SnapshotStageItemRow> rows,
            int maximumRows
    ) {
        if (rows == null || rows.isEmpty() || rows.size() > maximumRows) {
            throw new IllegalStateException("snapshot apply chunk exceeded its bound");
        }
        List<SnapshotApplyItem<T>> decoded = new ArrayList<>(rows.size());
        int absenceUnsafe = 0;
        SnapshotStageItemRow previous = null;
        for (SnapshotStageItemRow row : rows) {
            T item = decode(snapshot, descriptor, codec, progress, previous, row);
            decoded.add(new SnapshotApplyItem<>(row, item));
            if (!descriptor.isAbsenceReconciliationSafe(item)) {
                absenceUnsafe++;
            }
            previous = row;
        }
        return new PreparedChunk<>(decoded, rows.get(rows.size() - 1), absenceUnsafe);
    }

    private <T> T decode(
            CompleteSnapshot<T> snapshot,
            SnapshotItemDescriptor<T> descriptor,
            SnapshotPayloadCodec<T> codec,
            SnapshotApplyProgressRow progress,
            SnapshotStageItemRow previous,
            SnapshotStageItemRow row
    ) {
        if (row == null
                || !Objects.equals(row.getTaskId(), snapshot.getTaskId())
                || row.getPageNo() == null || row.getPageNo() < 1
                || row.getItemOrdinal() == null || row.getItemOrdinal() < 0
                || !after(progress, row)
                || (previous != null && !after(previous, row))) {
            throw new IllegalStateException("snapshot apply chunk order is invalid");
        }
        T item = Objects.requireNonNull(codec.decode(row.getPayload()), "decoded item");
        if (!Objects.equals(descriptor.stableIdentity(item), row.getStableIdentity())
                || !Objects.equals(
                        descriptor.stableContentFingerprint(item), row.getContentFingerprint()
                )
                || !Objects.equals(
                        descriptor.isValidatedIdentityCandidate(item),
                        row.getValidatedIdentityCandidate()
                )
                || !Objects.equals(
                        descriptor.isAbsenceReconciliationSafe(item),
                        row.getAbsenceReconciliationSafe()
                )) {
            throw new IllegalStateException("snapshot apply payload integrity drift");
        }
        return item;
    }

    private boolean after(SnapshotApplyProgressRow cursor, SnapshotStageItemRow row) {
        return row.getPageNo() > cursor.getCursorPageNo()
                || (Objects.equals(row.getPageNo(), cursor.getCursorPageNo())
                        && row.getItemOrdinal() > cursor.getCursorItemOrdinal());
    }

    private boolean after(SnapshotStageItemRow left, SnapshotStageItemRow right) {
        return right.getPageNo() > left.getPageNo()
                || (Objects.equals(right.getPageNo(), left.getPageNo())
                        && right.getItemOrdinal() > left.getItemOrdinal());
    }

    static final class PreparedChunk<T> {
        private final List<SnapshotApplyItem<T>> items;
        private final SnapshotStageItemRow lastRow;
        private final int absenceUnsafeCount;

        private PreparedChunk(
                List<SnapshotApplyItem<T>> items,
                SnapshotStageItemRow lastRow,
                int absenceUnsafeCount
        ) {
            this.items = List.copyOf(items);
            this.lastRow = lastRow;
            this.absenceUnsafeCount = absenceUnsafeCount;
        }

        List<SnapshotApplyItem<T>> getItems() { return items; }
        SnapshotStageItemRow getLastRow() { return lastRow; }
        int getAbsenceUnsafeCount() { return absenceUnsafeCount; }
    }
}
