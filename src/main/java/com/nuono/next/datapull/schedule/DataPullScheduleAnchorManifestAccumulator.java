package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.runtime.OperationCode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.Objects;

/** Incremental form of the authoritative V2 cutover manifest digest. */
public final class DataPullScheduleAnchorManifestAccumulator {

    private final OperationCode operation;
    private final String cutoverKey;
    private final int expectedCount;
    private final ResumableSha256 digest;
    private int scannedCount;
    private String previousScope;

    private DataPullScheduleAnchorManifestAccumulator(
            OperationCode operation,
            String cutoverKey,
            int expectedCount,
            ResumableSha256 digest,
            int scannedCount,
            String previousScope
    ) {
        this.operation = Objects.requireNonNull(operation, "operation");
        this.cutoverKey = DataPullScheduleAnchor.requireIdentity(
                cutoverKey, "cutoverKey", 96
        );
        if (expectedCount < 0 || scannedCount < 0 || scannedCount > expectedCount) {
            throw new IllegalArgumentException("manifest scan count is invalid");
        }
        this.expectedCount = expectedCount;
        this.digest = Objects.requireNonNull(digest, "digest");
        this.scannedCount = scannedCount;
        this.previousScope = previousScope;
    }

    public static DataPullScheduleAnchorManifestAccumulator initial(
            OperationCode operation,
            String cutoverKey,
            int expectedCount
    ) {
        ResumableSha256 digest = new ResumableSha256();
        update(digest, "DP_SCHEDULE_ANCHOR_MANIFEST_V2");
        update(digest, Objects.requireNonNull(operation, "operation").name());
        update(digest, DataPullScheduleAnchor.requireIdentity(cutoverKey, "cutoverKey", 96));
        update(digest, ByteBuffer.allocate(Integer.BYTES).putInt(expectedCount).array());
        return new DataPullScheduleAnchorManifestAccumulator(
                operation, cutoverKey, expectedCount, digest, 0, null
        );
    }

    public static DataPullScheduleAnchorManifestAccumulator resume(
            OperationCode operation,
            String cutoverKey,
            int expectedCount,
            int scannedCount,
            String previousScope,
            String sha256State
    ) {
        if (scannedCount > 0) {
            DataPullScheduleAnchor.requireIdentity(previousScope, "previousScope", 96);
        } else if (previousScope != null) {
            throw new IllegalArgumentException("empty manifest scan cannot have a cursor");
        }
        return new DataPullScheduleAnchorManifestAccumulator(
                operation, cutoverKey, expectedCount,
                ResumableSha256.resume(sha256State), scannedCount, previousScope
        );
    }

    public void append(DataPullScheduleAnchor anchor) {
        DataPullScheduleAnchor item = Objects.requireNonNull(anchor, "anchor");
        item.validate();
        if (scannedCount >= expectedCount
                || item.getOperationCode() != operation
                || item.getAnchorKind() != DataPullScheduleAnchor.Kind.CUTOVER_RECONCILED
                || !cutoverKey.equals(item.getCutoverKey())) {
            throw new IllegalArgumentException("manifest anchor is outside the sealed cohort");
        }
        if (previousScope != null && previousScope.compareTo(item.getScopeKey()) >= 0) {
            throw new IllegalArgumentException("manifest anchors must be strictly scope-key ordered");
        }
        update(digest, item.getOperationCode().name());
        update(digest, item.getScopeKey());
        update(digest, item.getAnchorKind().name());
        update(digest, millis(item.getReconcileAfterUtc()));
        update(digest, item.getAdmissionKind().name());
        updateNullableTime(digest, item.getFirstEligibleAtUtc());
        update(digest, item.getSourceBindingSha256());
        update(digest, item.getAnchorEvidenceSha256());
        previousScope = item.getScopeKey();
        scannedCount++;
    }

    public String finishHex() {
        if (scannedCount != expectedCount) {
            throw new IllegalStateException("manifest scan is not complete");
        }
        return digest.finishHex();
    }

    public String snapshot() { return digest.snapshot(); }
    public int getScannedCount() { return scannedCount; }
    public String getPreviousScope() { return previousScope; }

    private static byte[] millis(java.time.LocalDateTime value) {
        return ByteBuffer.allocate(Long.BYTES)
                .putLong(value.toInstant(ZoneOffset.UTC).toEpochMilli()).array();
    }

    private static void updateNullableTime(ResumableSha256 digest, java.time.LocalDateTime value) {
        digest.update(new byte[]{(byte) (value == null ? 0 : 1)});
        if (value != null) update(digest, millis(value));
    }

    private static void update(ResumableSha256 digest, String value) {
        update(digest, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void update(ResumableSha256 digest, byte[] value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value.length).array());
        digest.update(value);
    }
}
