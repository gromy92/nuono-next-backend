package com.nuono.next.datapull.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.DataPullReportLocatorMapper;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ReportPersistenceBridgeTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-02T00:00:00Z"), ZoneOffset.UTC
    );

    @Test
    void artifactSurvivesRestartAndIsRehashedBeforeImport() {
        InMemoryReportArtifactChunkMapper mapper = new InMemoryReportArtifactChunkMapper();
        MyBatisReportArtifactStore first = new MyBatisReportArtifactStore(mapper, CLOCK);
        ExportReportIntent intent = ReportBridgeTestSupport.intent(
                OperationCode.DP02, "NOON_REPORT_ORDER"
        );
        RemoteExportHandle handle = new RemoteExportHandle("sales-dashboard-export:2026-08-01..2026-08-01");
        byte[] content = "date,item_nr\n2026-08-01,1\n".getBytes(StandardCharsets.UTF_8);

        DownloadedReportArtifact artifact = first.store(intent, handle, content);
        StoredReportArtifact restored = new MyBatisReportArtifactStore(mapper, CLOCK)
                .readVerified(intent, artifact);

        assertEquals(intent.getTaskId(), mapper.manifest().getTaskId());
        assertArrayEquals(content, restored.getContent());
        assertEquals(handle.getValue(), restored.getRemoteHandle());
        ExportReportIntent differentTask = ReportBridgeTestSupport.intent(
                9002L, OperationCode.DP02, "NOON_REPORT_ORDER"
        );
        assertEquals(intent.getStableRequestKey(), differentTask.getStableRequestKey());
        assertThrows(
                IllegalStateException.class,
                () -> first.readVerified(differentTask, artifact)
        );

        mapper.tamperChunk(0, "tampered".getBytes(StandardCharsets.UTF_8));
        assertThrows(
                IllegalStateException.class,
                () -> first.readVerified(intent, artifact)
        );
    }

    @Test
    void zeroByteDownloadIsNotAcceptedAsAnAuthoritativeEmptyReport() {
        InMemoryReportArtifactChunkMapper mapper = new InMemoryReportArtifactChunkMapper();
        MyBatisReportArtifactStore store = new MyBatisReportArtifactStore(
                mapper, CLOCK
        );
        ExportReportIntent intent = ReportBridgeTestSupport.intent(
                OperationCode.DP02, "NOON_REPORT_ORDER"
        );

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> store.store(intent, new RemoteExportHandle("export-empty"), new byte[0])
        );

        assertEquals("REPORT_ARTIFACT_EMPTY_DOWNLOAD", failure.getMessage());
        assertEquals("DOWNLOADING", mapper.manifest().getDownloadState());
    }

    @Test
    void locatorReferenceIsSecretFreeAndCiphertextIsBoundToIntentAndHandle() {
        AtomicReference<ReportDownloadLocatorRecord> row = new AtomicReference<>();
        DataPullReportLocatorMapper mapper = locatorMapper(row);
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        AesGcmReportDownloadLocatorVault vault = new AesGcmReportDownloadLocatorVault(
                mapper, key, new SecureRandom(), CLOCK
        );
        ExportReportIntent intent = ReportBridgeTestSupport.intent(
                OperationCode.DP02, "NOON_REPORT_ORDER"
        );
        RemoteExportHandle handle = new RemoteExportHandle("sales-dashboard-export:2026-08-01..2026-08-01");
        String raw = "https://download.example.test/report.csv?signature=secret";

        String reference = vault.store(intent, handle, raw);

        assertEquals(intent.getTaskId(), row.get().getTaskId());
        assertFalse(reference.contains("http"));
        assertFalse(reference.contains("secret"));
        assertFalse(new String(row.get().getEncryptedLocator(), StandardCharsets.UTF_8).contains(raw));
        assertEquals(raw, vault.resolve(intent, handle, reference));
        assertThrows(
                IllegalStateException.class,
                () -> vault.resolve(intent, new RemoteExportHandle("different"), reference)
        );
        ExportReportIntent differentTask = ReportBridgeTestSupport.intent(
                9002L, OperationCode.DP02, "NOON_REPORT_ORDER"
        );
        assertThrows(
                IllegalStateException.class,
                () -> vault.resolve(differentTask, handle, reference)
        );
    }

    @Test
    void remoteHandleMatchesItsPersistenceColumn() {
        assertEquals(512, new RemoteExportHandle("h".repeat(512)).getValue().length());
        assertThrows(
                IllegalArgumentException.class,
                () -> new RemoteExportHandle("h".repeat(513))
        );
    }

    @Test
    void locatorPlaintextHasABoundedUtf8UrlContract() {
        AtomicReference<ReportDownloadLocatorRecord> row = new AtomicReference<>();
        AesGcmReportDownloadLocatorVault vault = new AesGcmReportDownloadLocatorVault(
                locatorMapper(row),
                Base64.getEncoder().encodeToString(new byte[32]),
                new SecureRandom(),
                CLOCK
        );
        ExportReportIntent intent = ReportBridgeTestSupport.intent(
                OperationCode.DP02, "NOON_REPORT_ORDER"
        );
        RemoteExportHandle handle = new RemoteExportHandle("export-boundary");
        String maximum = "é".repeat(8_192);

        vault.store(intent, handle, maximum);

        assertEquals(16_400, row.get().getEncryptedLocator().length);
        row.set(null);
        assertThrows(
                IllegalArgumentException.class,
                () -> vault.store(intent, handle, "a" + maximum)
        );
        assertNull(row.get());
    }

    @Test
    void missingEncryptionKeyFailsClosed() {
        assertThrows(
                IllegalStateException.class,
                () -> new AesGcmReportDownloadLocatorVault(locatorMapper(new AtomicReference<>()), "")
        );
    }

    private DataPullReportLocatorMapper locatorMapper(
            AtomicReference<ReportDownloadLocatorRecord> stored
    ) {
        return new DataPullReportLocatorMapper() {
            @Override
            public int insert(ReportDownloadLocatorRecord row) {
                stored.set(row);
                return 1;
            }

            @Override
            public ReportDownloadLocatorRecord selectByReference(String reference) {
                ReportDownloadLocatorRecord value = stored.get();
                return value != null && reference.equals(value.getLocatorReference()) ? value : null;
            }

            @Override
            public int deleteTerminalBatch(java.time.LocalDateTime cutoffUtc, int batchSize) {
                return 0;
            }

            @Override
            public int deleteAbandonedBatch(
                    java.time.LocalDateTime cutoffUtc, int batchSize
            ) {
                return 0;
            }
        };
    }
}
