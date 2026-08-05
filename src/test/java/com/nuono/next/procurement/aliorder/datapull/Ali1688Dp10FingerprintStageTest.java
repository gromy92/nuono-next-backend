package com.nuono.next.procurement.aliorder.datapull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.Ali1688Dp10StageMapper;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class Ali1688Dp10FingerprintStageTest {

    @Test
    void pageLocalDuplicatesBecomeOneAtomicDeltaPerFingerprintAndPass() {
        Ali1688Dp10StageMapper mapper = mock(Ali1688Dp10StageMapper.class);
        String repeated = fingerprint(1);
        String single = fingerprint(2);
        when(mapper.upsertFingerprintCount(101L, 3L, "CURRENT", repeated, 2L, 0L))
                .thenReturn(1);
        when(mapper.upsertFingerprintCount(101L, 3L, "CURRENT", single, 1L, 0L))
                .thenReturn(2);

        new Ali1688Dp10FingerprintStage(mapper).stagePage(
                101L, 3L, 1, Ali1688HistoricalOrderProvider.Partition.CURRENT,
                List.of(item(repeated), item(repeated), item(single)));

        verify(mapper).upsertFingerprintCount(101L, 3L, "CURRENT", repeated, 2L, 0L);
        verify(mapper).upsertFingerprintCount(101L, 3L, "CURRENT", single, 1L, 0L);
    }

    @Test
    void zeroAndExactly256RowsExhaustWhile257FetchesAdvanceByOnly256() {
        Ali1688Dp10SealBatch empty = Ali1688Dp10FingerprintStage.compareRows(List.of(), null);
        Ali1688Dp10SealBatch exact = Ali1688Dp10FingerprintStage.compareRows(rows(256), null);
        Ali1688Dp10SealBatch hasNext = Ali1688Dp10FingerprintStage.compareRows(rows(257), null);

        assertTrue(empty.isMatching());
        assertTrue(empty.isExhausted());
        assertEquals(0L, empty.getMatchedRawRows());
        assertTrue(exact.isExhausted());
        assertEquals(256L, exact.getMatchedRawRows());
        assertEquals(fingerprint(255), exact.getLastFingerprint());
        assertFalse(hasNext.isExhausted());
        assertEquals(256L, hasNext.getMatchedRawRows());
        assertEquals(257, hasNext.getCountRowsRead());
        assertEquals(fingerprint(255), hasNext.getLastFingerprint());
    }

    @Test
    void storeReadUsesOnePrimaryKeyRangeWithTheFixed257FetchLimit() {
        Ali1688Dp10StageMapper mapper = mock(Ali1688Dp10StageMapper.class);
        String after = fingerprint(9);
        when(mapper.selectFingerprintCounts(101L, 4L, "HISTORY", after, 257))
                .thenReturn(List.of(row(fingerprint(10), 7L, 7L)));

        Ali1688Dp10SealBatch result = new Ali1688Dp10FingerprintStage(mapper).readBatch(
                101L, 4L, Ali1688HistoricalOrderProvider.Partition.HISTORY, after);

        assertTrue(result.isMatching());
        assertTrue(result.isExhausted());
        assertEquals(7L, result.getMatchedRawRows());
        verify(mapper).selectFingerprintCounts(101L, 4L, "HISTORY", after, 257);
    }

    @Test
    void exactLargeDuplicateMultiplicityMatchesAndOneSidedMultiplicityDrifts() {
        Ali1688Dp10SealBatch exact = Ali1688Dp10FingerprintStage.compareRows(
                List.of(row(fingerprint(1), 3_000_000_000L, 3_000_000_000L)), null);
        Ali1688Dp10SealBatch drift = Ali1688Dp10FingerprintStage.compareRows(
                List.of(row(fingerprint(1), 2L, 1L)), null);

        assertTrue(exact.isMatching());
        assertEquals(3_000_000_000L, exact.getMatchedRawRows());
        assertFalse(drift.isMatching());
    }

    @Test
    void longAccumulationOverflowAndMalformedRangesFailClosed() {
        assertCode("DP10_SEAL_COUNT_OVERFLOW", () ->
                Ali1688Dp10FingerprintStage.compareRows(List.of(
                        row(fingerprint(1), Long.MAX_VALUE, Long.MAX_VALUE),
                        row(fingerprint(2), Long.MAX_VALUE, Long.MAX_VALUE)), null));
        assertCode("DP10_SEAL_FINGERPRINT_ORDER_INVALID", () ->
                Ali1688Dp10FingerprintStage.compareRows(List.of(
                        row(fingerprint(2), 1L, 1L), row(fingerprint(1), 1L, 1L)), null));
        assertCode("DP10_SEAL_BATCH_SIZE_INVALID", () ->
                Ali1688Dp10FingerprintStage.compareRows(rows(258), null));
    }

    @Test
    void replayingAnAlreadyCommittedPageNeverRecountsItsFingerprint() {
        Ali1688Dp10InMemoryStageStore store = new Ali1688Dp10InMemoryStageStore();
        Ali1688HistoricalOrderProvider.OrderSnapshot order =
                new Ali1688HistoricalOrderProvider.OrderSnapshot();
        order.setProviderOrderNo("ORDER-1");
        Ali1688Dp10ValidatedPage page = new Ali1688Dp10ValidatedPage(
                Ali1688HistoricalOrderProvider.Partition.CURRENT,
                1, 1, 1L, 1,
                List.of(new Ali1688Dp10ListEntry(
                        0, order, Ali1688Dp10ItemState.COMPLETE, null,
                        Ali1688Dp10RawOrderFingerprint.fingerprint(order))));

        store.stageList(null, 1L, 1, page, LocalDateTime.MIN);
        store.stageList(null, 1L, 1, page, LocalDateTime.MIN);
        store.stageList(null, 1L, 2, page, LocalDateTime.MIN);
        Ali1688Dp10SealBatch seal = store.readSealBatch(
                null, 1L, Ali1688HistoricalOrderProvider.Partition.CURRENT,
                null, LocalDateTime.MIN);

        assertTrue(seal.isMatching());
        assertTrue(seal.isExhausted());
        assertEquals(1L, seal.getMatchedRawRows());
        assertEquals(1, store.lastSealCountRowsRead);
    }

    private List<Ali1688Dp10FingerprintCountRow> rows(int size) {
        List<Ali1688Dp10FingerprintCountRow> values = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            values.add(row(fingerprint(index), 1L, 1L));
        }
        return values;
    }

    private Ali1688Dp10StageItemRow item(String fingerprint) {
        Ali1688Dp10StageItemRow row = new Ali1688Dp10StageItemRow();
        row.setListContentFingerprint(fingerprint);
        return row;
    }

    private Ali1688Dp10FingerprintCountRow row(String fingerprint, long first, long second) {
        Ali1688Dp10FingerprintCountRow row = new Ali1688Dp10FingerprintCountRow();
        row.setFingerprint(fingerprint);
        row.setPassOneCount(first);
        row.setPassTwoCount(second);
        return row;
    }

    private String fingerprint(int value) {
        return String.format(Locale.ROOT, "%064x", value);
    }

    private void assertCode(String expected, Runnable action) {
        Ali1688Dp10PageContractException failure = assertThrows(
                Ali1688Dp10PageContractException.class, action::run);
        assertEquals(expected, failure.getSanitizedCode());
    }
}
