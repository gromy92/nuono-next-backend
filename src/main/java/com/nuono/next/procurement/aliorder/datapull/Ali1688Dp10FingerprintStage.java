package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.infrastructure.mapper.Ali1688Dp10StageMapper;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Page-bounded fingerprint aggregation and primary-key-range multiset comparison. */
final class Ali1688Dp10FingerprintStage {
    static final int SEAL_BATCH_SIZE = 256;
    static final int SEAL_FETCH_SIZE = SEAL_BATCH_SIZE + 1;

    private final Ali1688Dp10StageMapper mapper;

    Ali1688Dp10FingerprintStage(Ali1688Dp10StageMapper mapper) {
        this.mapper = mapper;
    }

    void stagePage(
            long taskId,
            long generationNo,
            int scanPass,
            Ali1688HistoricalOrderProvider.Partition partition,
            List<Ali1688Dp10StageItemRow> items
    ) {
        if (scanPass != 1 && scanPass != 2) throw invalid("DP10_STAGE_SCAN_PASS_INVALID");
        Map<String, Long> multiplicities = new LinkedHashMap<>();
        for (Ali1688Dp10StageItemRow item : items) {
            String fingerprint = item.getListContentFingerprint();
            requireFingerprint(fingerprint, false);
            try {
                multiplicities.merge(fingerprint, 1L, Math::addExact);
            } catch (ArithmeticException overflow) {
                throw invalid("DP10_STAGE_FINGERPRINT_COUNT_OVERFLOW");
            }
        }
        for (Map.Entry<String, Long> entry : multiplicities.entrySet()) {
            int changed = mapper.upsertFingerprintCount(
                    taskId, generationNo, partition.name(), entry.getKey(),
                    scanPass == 1 ? entry.getValue() : 0L,
                    scanPass == 2 ? entry.getValue() : 0L);
            if (changed <= 0 || changed > 2) {
                throw new IllegalStateException(
                        "DP10 fingerprint count upsert affected invalid rows");
            }
        }
    }

    Ali1688Dp10SealBatch readBatch(
            long taskId,
            long generationNo,
            Ali1688HistoricalOrderProvider.Partition partition,
            String afterFingerprint
    ) {
        requireFingerprint(afterFingerprint, true);
        List<Ali1688Dp10FingerprintCountRow> rows = mapper.selectFingerprintCounts(
                taskId, generationNo, partition.name(), afterFingerprint, SEAL_FETCH_SIZE);
        return compareRows(rows, afterFingerprint);
    }

    static Ali1688Dp10SealBatch compareRows(
            List<Ali1688Dp10FingerprintCountRow> rows,
            String afterFingerprint
    ) {
        requireFingerprint(afterFingerprint, true);
        if (rows == null || rows.size() > SEAL_FETCH_SIZE) {
            throw invalid("DP10_SEAL_BATCH_SIZE_INVALID");
        }
        int compared = Math.min(rows.size(), SEAL_BATCH_SIZE);
        String previous = afterFingerprint;
        long matchedRawRows = 0L;
        for (int index = 0; index < compared; index++) {
            Ali1688Dp10FingerprintCountRow row = rows.get(index);
            if (row == null) throw invalid("DP10_SEAL_COUNT_ROW_INVALID");
            String fingerprint = row.getFingerprint();
            requireFingerprint(fingerprint, false);
            if (previous != null && fingerprint.compareTo(previous) <= 0) {
                throw invalid("DP10_SEAL_FINGERPRINT_ORDER_INVALID");
            }
            Long passOne = row.getPassOneCount();
            Long passTwo = row.getPassTwoCount();
            if (passOne == null || passTwo == null || passOne < 0L || passTwo < 0L
                    || passOne == 0L && passTwo == 0L) {
                throw invalid("DP10_SEAL_COUNT_ROW_INVALID");
            }
            if (!passOne.equals(passTwo)) return Ali1688Dp10SealBatch.drift(rows.size());
            try {
                matchedRawRows = Math.addExact(matchedRawRows, passOne);
            } catch (ArithmeticException overflow) {
                throw invalid("DP10_SEAL_COUNT_OVERFLOW");
            }
            previous = fingerprint;
        }
        return Ali1688Dp10SealBatch.matching(
                rows.size() <= SEAL_BATCH_SIZE,
                compared == 0 ? afterFingerprint : previous,
                matchedRawRows,
                rows.size());
    }

    private static void requireFingerprint(String value, boolean nullable) {
        if (value == null && nullable) return;
        if (value == null || value.length() != 64) {
            throw invalid("DP10_SEAL_FINGERPRINT_INVALID");
        }
        for (int index = 0; index < value.length(); index++) {
            char item = value.charAt(index);
            if (!(item >= '0' && item <= '9') && !(item >= 'a' && item <= 'f')) {
                throw invalid("DP10_SEAL_FINGERPRINT_INVALID");
            }
        }
    }

    private static Ali1688Dp10PageContractException invalid(String code) {
        return new Ali1688Dp10PageContractException(code);
    }
}
