package com.nuono.next.datapull.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnReceivedReportCsvParser;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnStageClassifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FbnReceivedReportRuntimeImporterTest {

    @Test
    void dp07bUsesTheBoundedSharedStageAndNeverTheFormerPerRowWriter() {
        byte[] content = ("partner_sku,sku,asn,qty_expected,received_qty,qc_failed_qty,"
                + "unidentified_qty,asn_schedule_date,country_code\n"
                + "P-1,N-1,ASN-1,10,8,1,1,2026-08-01,SA\n")
                .getBytes(StandardCharsets.UTF_8);
        FakeStageStore stages = new FakeStageStore();
        FbnReceivedReportRuntimeImporter importer = importer(content, stages);

        ReportImportResult staged = importer.importComplete(
                intent(),
                artifact(content, 1L)
        );
        ReportImportResult applied = importer.importComplete(
                intent(),
                artifact(content, 1L)
        );

        assertThat(staged.getStatus()).isEqualTo(ReportImportResult.Status.IN_PROGRESS);
        assertThat(stages.decisions).containsExactly("ACCEPTED");
        assertThat(stages.applyCalls.get()).isEqualTo(1);
        assertThat(applied.getStatus()).isEqualTo(ReportImportResult.Status.APPLIED);
    }

    @Test
    void rejectsNonDp07bIntentBeforeAnyArtifactWork() {
        byte[] content = "unused".getBytes(StandardCharsets.UTF_8);

        ReportImportResult result = importer(content, new FakeStageStore()).importComplete(
                ReportBridgeTestSupport.intent(OperationCode.DP02, "NOON_REPORT_ORDER"),
                null
        );

        assertThat(result.getSanitizedCode()).isEqualTo("DP07B_IMPORT_INTENT_INVALID");
    }

    @Test
    void acceptedOversizedFactPoisonsTheContainerAndNeverApplies() {
        byte[] content = ("partner_sku,sku,asn,qty_expected,received_qty,qc_failed_qty,"
                + "unidentified_qty,asn_schedule_date,country_code,unexpected\n"
                + "P-1,N-1,ASN-1,10,8,1,1,2026-08-01,SA,"
                + "X".repeat(1_000_000) + "\n").getBytes(StandardCharsets.UTF_8);
        FakeStageStore stages = new FakeStageStore();

        ReportImportResult result = importer(content, stages).importComplete(
                intent(), artifact(content, 1L)
        );

        assertThat(result.getStatus()).isEqualTo(ReportImportResult.Status.CONTRACT_ERROR);
        assertThat(result.getSanitizedCode()).isEqualTo("REPORT_ROW_OUTSIDE_CONTAINER");
        assertThat(stages.decisions).containsExactly("CONTAINER_CONTRACT_ERROR");
        assertThat(stages.state.getState()).isEqualTo("POISONED");
        assertThat(stages.applyCalls.get()).isZero();
        assertThat(stages.markerWrites.get()).isZero();
    }

    private FbnReceivedReportRuntimeImporter importer(
            byte[] content,
            FakeStageStore stages
    ) {
        ObjectMapper objectMapper = new ObjectMapper();
        OfficialWarehouseFbnStageClassifier classifier =
                new OfficialWarehouseFbnStageClassifier(
                        new OfficialWarehouseFbnReceivedReportCsvParser()
                );
        NoonReportDefinition definition = new NoonReportDefinitions(null).dp07b();
        return new FbnReceivedReportRuntimeImporter(
                definition,
                artifactStore(content),
                new JsonReportFactPlanAdapter<>(
                        classifier::requireHeader,
                        classifier::classify,
                        classifier::identity,
                        objectMapper
                ),
                stages,
                objectMapper
        );
    }

    private ReportArtifactStore artifactStore(byte[] content) {
        return new ReportArtifactStore() {
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
                return new StoredReportArtifact("export-1", content);
            }
        };
    }

    private ExportReportIntent intent() {
        return ReportBridgeTestSupport.intent(OperationCode.DP07B, "NOON_FBN_REPORT");
    }

    private DownloadedReportArtifact artifact(byte[] content, long declaredRows) {
        ExportReportIntent intent = intent();
        return DownloadedReportArtifact.complete(
                "artifact",
                ReportDigestSupport.sha256(content),
                content.length
        ).bind(ReportArtifactAuthority.proven(
                intent,
                new RemoteExportHandle("export-1"),
                declaredRows
        ));
    }

    private static final class FakeStageStore implements ReportStageStore {
        private final List<String> decisions = new ArrayList<>();
        private final AtomicInteger applyCalls = new AtomicInteger();
        private final AtomicInteger markerWrites = new AtomicInteger();
        private ReportStageState state;

        @Override
        public ReportStageState load(long taskId) {
            return state;
        }

        @Override
        public ReportImportResult stage(ExportReportIntent intent, ReportStageChunk chunk) {
            for (ReportPlannedRow row : chunk.getRows()) {
                decisions.add(row.getDecision().name());
                if (row.getDecision()
                        == ReportPlannedRow.Decision.CONTAINER_CONTRACT_ERROR) {
                    state = new ReportStageState();
                    state.setState("POISONED");
                    return ReportImportResult.contractError("REPORT_ROW_OUTSIDE_CONTAINER");
                }
            }
            state = new ReportStageState();
            state.setTaskId(intent.getTaskId());
            state.setOperationCode(intent.getOperationCode());
            state.setArtifactKey(chunk.getArtifactKey());
            state.setArtifactSha256(chunk.getArtifactSha256());
            state.setHeaderJson(chunk.getHeaderJson());
            state.setDeclaredRowCount(chunk.getDeclaredRowCount());
            state.setSourceRowCount((long) chunk.getRows().size());
            state.setNextByteOffset(chunk.getNextByteOffset());
            state.setState(chunk.isEndOfFile() ? "SEALED" : "VALIDATING");
            return ReportImportResult.inProgress();
        }

        @Override
        public ReportImportResult applySealed(ExportReportIntent intent) {
            if (state != null && "POISONED".equals(state.getState())) {
                return ReportImportResult.contractError("REPORT_ROW_OUTSIDE_CONTAINER");
            }
            applyCalls.incrementAndGet();
            markerWrites.incrementAndGet();
            return ReportImportResult.applied();
        }
    }
}
