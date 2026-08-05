package com.nuono.next.procurement.aliorder.datapull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class Ali1688Dp10CheckpointTest extends Ali1688Dp10JobTestSupport {

    @Test
    void firstRunAndIncrementalRunFreezeExactWindowAndStartWithCurrentPartition() {
        Ali1688Dp10Checkpoint first = Ali1688Dp10Checkpoint.initial(
                progress(false, null, 0L).current,
                NOW,
                20
        );
        Ali1688Dp10Checkpoint afterEmptyFull = Ali1688Dp10Checkpoint.initial(
                progress(true, null, 1L).current,
                NOW,
                20
        );

        assertEquals(Ali1688HistoricalOrderProvider.SyncMode.FULL, first.getMode());
        assertNull(first.windowStart());
        assertEquals(NOW.toInstant(java.time.ZoneOffset.UTC), first.windowEnd());
        assertEquals(Ali1688HistoricalOrderProvider.Partition.CURRENT, first.getPartition());
        assertEquals(1, first.getPageNo());
        assertEquals(20, first.getPageSize());
        assertNull(first.getExpectedTotal());
        assertEquals(
                Ali1688HistoricalOrderProvider.SyncMode.INCREMENTAL,
                afterEmptyFull.getMode()
        );
        assertEquals(Instant.EPOCH, afterEmptyFull.windowStart());
        assertEquals(first.windowEnd(), afterEmptyFull.windowEnd());
    }

    @Test
    void completedFullUsesCommittedOfficialModifiedHighWater() {
        Ali1688Dp10Checkpoint checkpoint = Ali1688Dp10Checkpoint.initial(
                progress(true, NEWEST, 4L).current,
                NOW,
                50
        );

        assertEquals(Ali1688HistoricalOrderProvider.SyncMode.INCREMENTAL, checkpoint.getMode());
        assertEquals(NEWEST.minusSeconds(24L * 60L * 60L), checkpoint.windowStart());
        assertEquals(NOW.toInstant(java.time.ZoneOffset.UTC), checkpoint.windowEnd());
        assertEquals(50, checkpoint.getPageSize());
        assertEquals(4L, checkpoint.getExpectedProgressVersion());
    }

    @Test
    void checkpointCodecPreservesOfficialLongTotalExactly() {
        Ali1688Dp10Checkpoint checkpoint = Ali1688Dp10Checkpoint.initial(
                progress(false, null, 0L).current,
                NOW,
                20
        ).bindContract(3_000_000_000L, 150_000_000);
        Ali1688Dp10CheckpointCodec codec = new Ali1688Dp10CheckpointCodec(new ObjectMapper());

        Ali1688Dp10Checkpoint restored = codec.decode(codec.encode(checkpoint));

        assertEquals(Long.valueOf(3_000_000_000L), restored.getExpectedTotal());
        assertEquals(Integer.valueOf(150_000_000), restored.getExpectedPages());
    }

    @Test
    void closesCurrentThenHistoryTwiceBeforeSealCanStart() {
        Ali1688Dp10Checkpoint checkpoint = Ali1688Dp10Checkpoint.initial(
                progress(false, null, 0L).current, NOW, 20);

        checkpoint = closeEmpty(checkpoint, Ali1688HistoricalOrderProvider.Partition.CURRENT, 1);
        assertEquals(Ali1688HistoricalOrderProvider.Partition.HISTORY, checkpoint.getPartition());
        checkpoint = closeEmpty(checkpoint, Ali1688HistoricalOrderProvider.Partition.HISTORY, 1);
        assertEquals(2, checkpoint.getScanPass());
        assertEquals(Ali1688HistoricalOrderProvider.Partition.CURRENT, checkpoint.getPartition());
        checkpoint = closeEmpty(checkpoint, Ali1688HistoricalOrderProvider.Partition.CURRENT, 2);
        checkpoint = closeEmpty(checkpoint, Ali1688HistoricalOrderProvider.Partition.HISTORY, 2);

        assertEquals(true, checkpoint.isScansClosed());
        assertEquals(Ali1688HistoricalOrderProvider.Partition.CURRENT,
                checkpoint.nextSealPartition());
    }

    @Test
    void dp10PageSizeAccepts100AndRejectsZeroOr101() {
        assertEquals(100, Ali1688Dp10Checkpoint.initial(
                progress(false, null, 0L).current, NOW, 100).getPageSize());
        assertThrows(IllegalArgumentException.class, () -> Ali1688Dp10Checkpoint.initial(
                progress(false, null, 0L).current, NOW, 0));
        assertThrows(IllegalArgumentException.class, () -> Ali1688Dp10Checkpoint.initial(
                progress(false, null, 0L).current, NOW, 101));
    }

    @Test
    void sealCursorIsReplayDeterministicAndOnlyCompletesAtExactRawTotal() {
        Ali1688Dp10Checkpoint closed = closed(2, 0);
        String firstCursor = "1".repeat(64);
        Ali1688Dp10SealBatch first = Ali1688Dp10SealBatch.matching(
                false, firstCursor, 1L, 257);

        Ali1688Dp10Checkpoint advanced = closed.afterSealBatch(
                Ali1688HistoricalOrderProvider.Partition.CURRENT, first);
        Ali1688Dp10Checkpoint replay = closed.afterSealBatch(
                Ali1688HistoricalOrderProvider.Partition.CURRENT, first);

        assertEquals(firstCursor, advanced.getSealAfterFingerprint());
        assertEquals(1L, advanced.getSealComparedRawRows());
        assertEquals(advanced.getSealAfterFingerprint(), replay.getSealAfterFingerprint());
        assertEquals(advanced.getSealComparedRawRows(), replay.getSealComparedRawRows());

        Ali1688Dp10Checkpoint currentSealed = advanced.afterSealBatch(
                Ali1688HistoricalOrderProvider.Partition.CURRENT,
                Ali1688Dp10SealBatch.matching(true, "2".repeat(64), 1L, 1));
        assertEquals(1, currentSealed.getSealedPartitions());
        assertNull(currentSealed.getSealAfterFingerprint());
        assertEquals(0L, currentSealed.getSealComparedRawRows());

        Ali1688Dp10Checkpoint fullySealed = currentSealed.afterSealBatch(
                Ali1688HistoricalOrderProvider.Partition.HISTORY,
                Ali1688Dp10SealBatch.matching(true, null, 0L, 0));
        assertTrue(fullySealed.isSealed());
    }

    @Test
    void exhaustedShortSealAndDetailBeforeBothSealsFailClosed() {
        Ali1688Dp10Checkpoint closed = closed(2, 0);
        Ali1688Dp10PageContractException shortSeal = assertThrows(
                Ali1688Dp10PageContractException.class,
                () -> closed.afterSealBatch(
                        Ali1688HistoricalOrderProvider.Partition.CURRENT,
                        Ali1688Dp10SealBatch.matching(true, "1".repeat(64), 1L, 1)));
        assertEquals("DP10_MULTIPASS_MULTISET_DRIFT", shortSeal.getSanitizedCode());
        assertThrows(IllegalArgumentException.class, () -> closed.atDetail(
                new Ali1688Dp10PendingItem(
                        1L, 2, Ali1688HistoricalOrderProvider.Partition.CURRENT, 1, 0)));
    }

    private Ali1688Dp10Checkpoint closed(int currentTotal, int historyTotal) {
        Ali1688Dp10Checkpoint checkpoint = Ali1688Dp10Checkpoint.initial(
                progress(false, null, 0L).current, NOW, 20);
        checkpoint = close(checkpoint, currentTotal);
        checkpoint = close(checkpoint, historyTotal);
        checkpoint = close(checkpoint, currentTotal);
        return close(checkpoint, historyTotal);
    }

    private Ali1688Dp10Checkpoint close(Ali1688Dp10Checkpoint checkpoint, int total) {
        Ali1688Dp10Checkpoint bound = checkpoint.bindContract(total, 1);
        List<Ali1688Dp10StagedOrder> orders = new ArrayList<>();
        for (int index = 0; index < total; index++) {
            orders.add(new Ali1688Dp10StagedOrder(
                    index, "ORDER-" + index, Ali1688Dp10ItemState.COMPLETE, null,
                    new Ali1688HistoricalOrderProvider.OrderSnapshot()));
        }
        return bound.afterPage(new Ali1688Dp10StagedPage(
                bound.getGenerationNo(), bound.getScanPass(), bound.getPartition(), 1, 20,
                total, 1, Ali1688Dp10StagedPage.State.LISTED, orders));
    }

    private Ali1688Dp10Checkpoint closeEmpty(
            Ali1688Dp10Checkpoint checkpoint,
            Ali1688HistoricalOrderProvider.Partition partition,
            int scanPass
    ) {
        Ali1688Dp10Checkpoint bound = checkpoint.bindContract(0L, 1);
        return bound.afterPage(new Ali1688Dp10StagedPage(
                bound.getGenerationNo(), scanPass, partition, 1, 20,
                0L, 1, Ali1688Dp10StagedPage.State.LISTED, List.of()));
    }
}
