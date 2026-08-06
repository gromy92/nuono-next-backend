package com.nuono.next.datapull.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.noon.NoonBinaryDownloadMetadata;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ReportArtifactChunkRecoveryTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC
    );

    @Test
    void partialDownloadSurvivesRestartAndMatchingReplayCompletesIt() {
        InMemoryReportArtifactChunkMapper mapper = new InMemoryReportArtifactChunkMapper();
        MyBatisReportArtifactStore first = new MyBatisReportArtifactStore(mapper, CLOCK);
        ExportReportIntent intent = ReportBridgeTestSupport.intent(
                OperationCode.DP02, "NOON_REPORT_ORDER"
        );
        RemoteExportHandle handle = new RemoteExportHandle("export-restart");
        byte[] firstChunk = bytes(MyBatisReportArtifactStore.CHUNK_BYTES, 7);
        byte[] lastChunk = bytes(123, 11);
        long totalLength = (long) firstChunk.length + lastChunk.length;

        ReportArtifactDownload interrupted = first.openDownload(intent, handle);
        interrupted.begin(new NoonBinaryDownloadMetadata(
                0L, totalLength, totalLength, "\"immutable-export\""
        ));
        interrupted.accept(firstChunk, 0, firstChunk.length);
        interrupted.abort(new IllegalStateException("connection reset"));

        MyBatisReportArtifactStore restarted = new MyBatisReportArtifactStore(mapper, CLOCK);
        ReportArtifactDownload resumed = restarted.openDownload(intent, handle);
        assertEquals(firstChunk.length, resumed.resumeByteOffset());
        assertEquals("\"immutable-export\"", resumed.resumeEntityValidator());
        resumed.begin(new NoonBinaryDownloadMetadata(
                firstChunk.length, lastChunk.length, totalLength, "\"immutable-export\""
        ));
        resumed.accept(lastChunk, 0, lastChunk.length);
        resumed.complete();
        DownloadedReportArtifact artifact = resumed.completedArtifact();

        assertTrue(restarted.findCompleted(intent, handle).isPresent());
        assertEquals((long) firstChunk.length + lastChunk.length, artifact.getContentLength());
        restarted.verifyComplete(intent, artifact);
        StoredReportArtifactSlice crossBoundary = restarted.readVerifiedRange(
                intent, artifact, firstChunk.length - 5L, 200
        );
        byte[] expected = new byte[5 + lastChunk.length];
        System.arraycopy(firstChunk, firstChunk.length - 5, expected, 0, 5);
        System.arraycopy(lastChunk, 0, expected, 5, lastChunk.length);
        assertArrayEquals(expected, crossBoundary.getContent());
    }

    @Test
    void ambiguousFoundRowCountStillRereadsAndRejectsAChangedCrashReplay() {
        InMemoryReportArtifactChunkMapper mapper = new InMemoryReportArtifactChunkMapper();
        MyBatisReportArtifactStore store = new MyBatisReportArtifactStore(mapper, CLOCK);
        ExportReportIntent intent = ReportBridgeTestSupport.intent(
                OperationCode.DP02, "NOON_REPORT_ORDER"
        );
        RemoteExportHandle handle = new RemoteExportHandle("export-conflict");
        byte[] original = bytes(MyBatisReportArtifactStore.CHUNK_BYTES, 3);
        ReportArtifactDownload interrupted = store.openDownload(intent, handle);
        interrupted.begin(new NoonBinaryDownloadMetadata(
                0L, original.length, original.length, null
        ));
        mapper.failNextAdvance();
        assertThrows(
                IllegalStateException.class,
                () -> interrupted.accept(original, 0, original.length)
        );

        byte[] changed = original.clone();
        changed[0]++;
        mapper.duplicateInsertReturnsOne(true);
        ReportArtifactDownload replay = store.openDownload(intent, handle);
        replay.begin(new NoonBinaryDownloadMetadata(
                0L, changed.length, changed.length, null
        ));
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> replay.accept(changed, 0, changed.length)
        );

        assertEquals("REPORT_ARTIFACT_RESUME_CONFLICT", failure.getMessage());
        assertArrayEquals(original, mapper.selectChunk(
                MyBatisReportArtifactStore.artifactKey(intent, handle), 0
        ).getContentBytes());
    }

    @Test
    void largeArtifactAdvancesThroughFixedRangesAndEofReadsOnlyMetadata() {
        InMemoryReportArtifactChunkMapper mapper = new InMemoryReportArtifactChunkMapper();
        MyBatisReportArtifactStore store = new MyBatisReportArtifactStore(mapper, CLOCK);
        ExportReportIntent intent = ReportBridgeTestSupport.intent(
                OperationCode.DP02, "NOON_REPORT_ORDER"
        );
        RemoteExportHandle handle = new RemoteExportHandle("export-large-bounded");
        byte[] oneChunk = bytes(MyBatisReportArtifactStore.CHUNK_BYTES, 13);
        ReportArtifactDownload download = store.openDownload(intent, handle);
        long totalLength = 10L * oneChunk.length;
        download.begin(new NoonBinaryDownloadMetadata(
                0L, totalLength, totalLength, null
        ));
        for (int chunkNo = 0; chunkNo < 10; chunkNo++) {
            download.accept(oneChunk, 0, oneChunk.length);
        }
        download.complete();
        DownloadedReportArtifact artifact = download.completedArtifact();

        mapper.resetReadCounters();
        long offset = 0L;
        while (offset < artifact.getContentLength()) {
            StoredReportArtifactSlice slice = store.readVerifiedRange(
                    intent, artifact, offset, MyBatisReportArtifactStore.MAX_RANGE_BYTES
            );
            assertTrue(slice.getContent().length <= MyBatisReportArtifactStore.MAX_RANGE_BYTES);
            offset += slice.getContent().length;
        }
        assertEquals(3, mapper.overlapSelects());
        assertTrue(mapper.maximumOverlapRows() <= 4);
        int metadataBeforeEof = mapper.metadataSelects();
        int aggregateBeforeEof = mapper.aggregateSelects();
        int overlapBeforeEof = mapper.overlapSelects();

        store.verifyComplete(intent, artifact);

        assertEquals(metadataBeforeEof + 1, mapper.metadataSelects());
        assertEquals(aggregateBeforeEof, mapper.aggregateSelects());
        assertEquals(overlapBeforeEof, mapper.overlapSelects());
    }

    @Test
    void laterFenceInvalidatesTheEarlierWriterAndCompleteRejectsAllFurtherChunks() {
        InMemoryReportArtifactChunkMapper mapper = new InMemoryReportArtifactChunkMapper();
        MyBatisReportArtifactStore store = new MyBatisReportArtifactStore(mapper, CLOCK);
        ExportReportIntent intent = ReportBridgeTestSupport.intent(
                OperationCode.DP02, "NOON_REPORT_ORDER"
        );
        RemoteExportHandle handle = new RemoteExportHandle("export-fenced");
        byte[] content = bytes(123, 17);

        ReportArtifactDownload stale = store.openDownload(intent, handle);
        stale.begin(new NoonBinaryDownloadMetadata(
                0L, content.length, content.length, null
        ));
        ReportArtifactDownload winner = store.openDownload(intent, handle);
        winner.begin(new NoonBinaryDownloadMetadata(
                0L, content.length, content.length, null
        ));

        IllegalStateException fenced = assertThrows(
                IllegalStateException.class,
                () -> stale.accept(content, 0, content.length)
        );
        assertEquals("REPORT_ARTIFACT_RESUME_CONFLICT", fenced.getMessage());
        winner.accept(content, 0, content.length);
        winner.complete();

        assertTrue(winner.isComplete());
        assertEquals("COMPLETE", mapper.manifest().getDownloadState());
        assertThrows(
                IllegalStateException.class,
                () -> stale.accept(content, 0, content.length)
        );
        assertEquals(1, mapper.selectChunkAggregate(
                MyBatisReportArtifactStore.artifactKey(intent, handle)
        ).getChunkCount());
    }

    private byte[] bytes(int length, int seed) {
        byte[] value = new byte[length];
        Arrays.fill(value, (byte) seed);
        return value;
    }
}
