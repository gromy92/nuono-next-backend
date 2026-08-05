package com.nuono.next.datapull.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.runtime.OperationCode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LegacyNoonReportImporterTest {

    @Test
    void stagesAtMostTwoHundredRowsPerAdvanceAndAppliesOnlyAfterSeal() {
        StringBuilder csv = new StringBuilder("id,note\n");
        for (int index = 1; index <= 201; index++) {
            csv.append(index).append(",ok\n");
        }
        byte[] content = csv.toString().getBytes(StandardCharsets.UTF_8);
        FakeStageStore stages = new FakeStageStore();
        TrackingArtifactStore artifacts = new TrackingArtifactStore(content);
        LegacyNoonReportImporter importer = importer(artifacts, stages);

        assertThat(importer.importComplete(intent(), artifact(content, 201L)).getStatus())
                .isEqualTo(ReportImportResult.Status.IN_PROGRESS);
        assertThat(stages.chunks).containsExactly(200);
        assertThat(stages.applyCalls.get()).isZero();
        assertThat(artifacts.completeVerifications.get()).isZero();
        assertThat(artifacts.maxBytes).containsExactly(LegacyNoonReportImporter.STAGE_READ_BYTES);

        assertThat(importer.importComplete(intent(), artifact(content, 201L)).getStatus())
                .isEqualTo(ReportImportResult.Status.IN_PROGRESS);
        assertThat(stages.chunks).containsExactly(200, 1);
        assertThat(stages.applyCalls.get()).isZero();
        assertThat(artifacts.completeVerifications.get()).isEqualTo(1);
        assertThat(artifacts.offsets).containsExactly(0L, stages.firstNextByteOffset);

        assertThat(importer.importComplete(intent(), artifact(content, 201L)).getStatus())
                .isEqualTo(ReportImportResult.Status.APPLIED);
        assertThat(stages.applyCalls.get()).isEqualTo(1);
        assertThat(artifacts.wholeReads.get()).isZero();
    }

    @Test
    void malformedLaterRowNeverReachesTheFactSeal() {
        byte[] content = "id,note\n1,ok\n2,bad,extra\n".getBytes(StandardCharsets.UTF_8);
        FakeStageStore stages = new FakeStageStore();

        ReportImportResult result = importer(content, stages)
                .importComplete(intent(), artifact(content, 2L));

        assertThat(result.getStatus()).isEqualTo(ReportImportResult.Status.CONTRACT_ERROR);
        assertThat(stages.chunks).isEmpty();
        assertThat(stages.applyCalls.get()).isZero();
    }

    @Test
    void immutableProviderAuthorityMustMatchTheStoredHandle() {
        byte[] content = "id,note\n1,ok\n".getBytes(StandardCharsets.UTF_8);
        FakeStageStore stages = new FakeStageStore();
        DownloadedReportArtifact wrongAuthority = DownloadedReportArtifact.complete(
                "artifact",
                ReportDigestSupport.sha256(content),
                content.length
        ).bind(ReportArtifactAuthority.proven(
                intent(),
                new RemoteExportHandle("different-export"),
                1L
        ));

        ReportImportResult result = importer(content, stages)
                .importComplete(intent(), wrongAuthority);

        assertThat(result.getStatus()).isEqualTo(ReportImportResult.Status.CONTRACT_ERROR);
        assertThat(stages.chunks).isEmpty();
    }

    private LegacyNoonReportImporter importer(byte[] content, FakeStageStore stages) {
        return importer(new TrackingArtifactStore(content), stages);
    }

    private LegacyNoonReportImporter importer(
            ReportArtifactStore artifacts,
            FakeStageStore stages
    ) {
        ObjectMapper objectMapper = new ObjectMapper();
        ReportFactPlanAdapter plans = new ReportFactPlanAdapter() {
            @Override
            public void requireHeader(String[] header) {
                if (header.length != 2 || !"id".equals(header[0]) || !"note".equals(header[1])) {
                    throw new IllegalArgumentException("invalid header");
                }
            }

            @Override
            public List<ReportPlannedRow> planRows(
                    com.nuono.next.noonpull.NoonReportDownloadedFile file,
                    String[] header,
                    List<String[]> rows,
                    long firstRowNumber
            ) {
                List<ReportPlannedRow> planned = new ArrayList<>();
                for (int index = 0; index < rows.size(); index++) {
                    planned.add(ReportPlannedRow.accepted(
                            firstRowNumber + index,
                            rows.get(index)[0],
                            "{\"id\":\"" + rows.get(index)[0] + "\"}"
                    ));
                }
                return planned;
            }
        };
        return new LegacyNoonReportImporter(
                ReportBridgeTestSupport.dp02(),
                artifacts,
                plans,
                stages,
                objectMapper
        );
    }

    private ExportReportIntent intent() {
        return ReportBridgeTestSupport.intent(OperationCode.DP02, "NOON_REPORT_ORDER");
    }

    private DownloadedReportArtifact artifact(byte[] content, long declaredRows) {
        ExportReportIntent intent = intent();
        return DownloadedReportArtifact.complete(
                "artifact",
                ReportDigestSupport.sha256(content),
                content.length
        ).bind(ReportArtifactAuthority.proven(
                intent,
                new RemoteExportHandle("sales-dashboard-export:2026-08-01..2026-08-01"),
                declaredRows
        ));
    }

    private static final class FakeStageStore implements ReportStageStore {
        private final List<Integer> chunks = new ArrayList<>();
        private final AtomicInteger applyCalls = new AtomicInteger();
        private long firstNextByteOffset;
        private ReportStageState state;

        @Override
        public ReportStageState load(long taskId) {
            return state;
        }

        @Override
        public ReportImportResult stage(ExportReportIntent intent, ReportStageChunk chunk) {
            chunks.add(chunk.getRows().size());
            if (chunks.size() == 1) {
                firstNextByteOffset = chunk.getNextByteOffset();
            }
            if (state == null) {
                state = new ReportStageState();
                state.setTaskId(intent.getTaskId());
                state.setOperationCode(intent.getOperationCode());
                state.setArtifactKey(chunk.getArtifactKey());
                state.setArtifactSha256(chunk.getArtifactSha256());
                state.setHeaderJson(chunk.getHeaderJson());
                state.setDeclaredRowCount(chunk.getDeclaredRowCount());
                state.setSourceRowCount(0L);
                state.setState("VALIDATING");
            }
            state.setNextByteOffset(chunk.getNextByteOffset());
            state.setSourceRowCount(state.getSourceRowCount() + chunk.getRows().size());
            if (chunk.isEndOfFile()) {
                state.setState("SEALED");
            }
            return ReportImportResult.inProgress();
        }

        @Override
        public ReportImportResult applySealed(ExportReportIntent intent) {
            applyCalls.incrementAndGet();
            return ReportImportResult.applied();
        }
    }

    private static final class TrackingArtifactStore implements ReportArtifactStore {
        private final byte[] content;
        private final List<Long> offsets = new ArrayList<>();
        private final List<Integer> maxBytes = new ArrayList<>();
        private final AtomicInteger completeVerifications = new AtomicInteger();
        private final AtomicInteger wholeReads = new AtomicInteger();

        private TrackingArtifactStore(byte[] content) {
            this.content = content.clone();
        }

        @Override
        public DownloadedReportArtifact store(
                ExportReportIntent intent,
                RemoteExportHandle handle,
                byte[] bytes
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StoredReportArtifact readVerified(
                ExportReportIntent intent,
                DownloadedReportArtifact artifact
        ) {
            wholeReads.incrementAndGet();
            throw new AssertionError("bounded importer must not materialize the whole artifact");
        }

        @Override
        public StoredReportArtifactSlice readVerifiedRange(
                ExportReportIntent intent,
                DownloadedReportArtifact artifact,
                long byteOffset,
                int requestedMaxBytes
        ) {
            offsets.add(byteOffset);
            maxBytes.add(requestedMaxBytes);
            int start = Math.toIntExact(byteOffset);
            int end = Math.min(content.length, start + requestedMaxBytes);
            return new StoredReportArtifactSlice(
                    "sales-dashboard-export:2026-08-01..2026-08-01",
                    byteOffset,
                    content.length,
                    Arrays.copyOfRange(content, start, end)
            );
        }

        @Override
        public void verifyComplete(
                ExportReportIntent intent,
                DownloadedReportArtifact artifact
        ) {
            completeVerifications.incrementAndGet();
            assertThat(artifact.getSha256()).isEqualTo(ReportDigestSupport.sha256(content));
        }
    }
}
