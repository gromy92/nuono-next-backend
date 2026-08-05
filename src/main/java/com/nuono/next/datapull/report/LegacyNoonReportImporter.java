package com.nuono.next.datapull.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.noonpull.NoonReportDownloadedFile;
import com.nuono.next.noonpull.NoonReportPullRequest;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataAccessException;

/** Bounded complete-artifact importer for DP-01/02/03. */
public final class LegacyNoonReportImporter implements ExportReportImporter {
    static final int STAGE_CHUNK_ROWS = 200;
    static final int STAGE_READ_BYTES = 4 * 1024 * 1024;

    private final NoonReportDefinition definition;
    private final ReportArtifactStore artifactStore;
    private final ReportFactPlanAdapter planAdapter;
    private final ReportStageStore stageStore;
    private final ObjectMapper objectMapper;

    public LegacyNoonReportImporter(
            NoonReportDefinition definition,
            ReportArtifactStore artifactStore,
            ReportFactPlanAdapter planAdapter,
            ReportStageStore stageStore,
            ObjectMapper objectMapper
    ) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.artifactStore = Objects.requireNonNull(artifactStore, "artifactStore");
        this.planAdapter = Objects.requireNonNull(planAdapter, "planAdapter");
        this.stageStore = Objects.requireNonNull(stageStore, "stageStore");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public ReportImportResult importComplete(
            ExportReportIntent intent,
            DownloadedReportArtifact artifact
    ) {
        final ReportArtifactAuthority authority;
        final NoonReportPullRequest request;
        try {
            request = NoonReportIntentSupport.request(intent, definition);
            authority = Objects.requireNonNull(
                    artifact.getAuthority(),
                    "report artifact authority"
            );
        } catch (RuntimeException invalidArtifact) {
            return ReportImportResult.contractError("REPORT_ARTIFACT_INVALID");
        }

        ReportStageState existing = stageStore.load(intent.getTaskId());
        if (existing != null && !"VALIDATING".equals(existing.getState())) {
            return stageStore.applySealed(intent);
        }
        final String[] header;
        final String headerJson;
        final long expectedByteOffset;
        final ReportStageChunk staged;
        try {
            long rangeStart = existing == null ? 0L : existing.getNextByteOffset();
            StoredReportArtifactSlice slice = artifactStore.readVerifiedRange(
                    intent,
                    artifact,
                    rangeStart,
                    STAGE_READ_BYTES
            );
            RemoteExportHandle handle = new RemoteExportHandle(slice.getRemoteHandle());
            if (!authority.proves(intent, handle)) {
                throw new IllegalArgumentException("report artifact authority mismatch");
            }
            byte[] content = slice.getContent();
            long rowOffset;
            if (existing == null) {
                ReportCsvCursor.Header parsedHeader = ReportCsvCursor.readHeader(
                        content,
                        slice.isEndOfArtifact()
                );
                header = parsedHeader.values();
                planAdapter.requireHeader(header);
                headerJson = objectMapper.writeValueAsString(header);
                rowOffset = parsedHeader.nextByteOffset();
                expectedByteOffset = Math.addExact(slice.getStartByteOffset(), rowOffset);
            } else {
                requireStageArtifact(existing, intent, artifact, authority);
                headerJson = existing.getHeaderJson();
                header = objectMapper.readValue(headerJson, String[].class);
                planAdapter.requireHeader(header);
                rowOffset = 0L;
                expectedByteOffset = existing.getNextByteOffset();
            }
            ReportCsvCursor.Chunk chunk = ReportCsvCursor.readRows(
                    content,
                    rowOffset,
                    header.length,
                    STAGE_CHUNK_ROWS,
                    slice.isEndOfArtifact()
            );
            if (chunk.endOfFile()) {
                // O(1) durable manifest/chunk-aggregate fence. Every bounded range has already
                // rehashed the chunks it consumed, while download completion owns full-stream SHA.
                artifactStore.verifyComplete(intent, artifact);
            }
            long firstRowNumber = existing == null
                    ? 1L
                    : Math.addExact(existing.getSourceRowCount(), 1L);
            NoonReportDownloadedFile file = new NoonReportDownloadedFile(
                    request,
                    slice.getRemoteHandle(),
                    slice.getRemoteHandle(),
                    artifact.getSha256(),
                    new byte[0]
            );
            List<ReportPlannedRow> rows = planAdapter.planRows(
                    file,
                    header,
                    chunk.rows(),
                    firstRowNumber
            );
            staged = new ReportStageChunk(
                    artifact.getArtifactKey(),
                    artifact.getSha256(),
                    authority.getDeclaredRowCount(),
                    headerJson,
                    expectedByteOffset,
                    Math.addExact(slice.getStartByteOffset(), chunk.nextByteOffset()),
                    chunk.endOfFile(),
                    rows
            );
        } catch (DataAccessException persistenceFailure) {
            throw persistenceFailure;
        } catch (JsonProcessingException invalidHeader) {
            return ReportImportResult.contractError("REPORT_STAGE_HEADER_INVALID");
        } catch (RuntimeException invalidContainer) {
            return ReportImportResult.contractError("REPORT_ARTIFACT_INVALID");
        }
        // Persistence failures and CAS drift are runtime failures, not bad provider rows.
        return stageStore.stage(intent, staged);
    }

    private void requireStageArtifact(
            ReportStageState stage,
            ExportReportIntent intent,
            DownloadedReportArtifact artifact,
            ReportArtifactAuthority authority
    ) {
        if (stage.getTaskId() == null
                || stage.getTaskId() != intent.getTaskId()
                || stage.getOperationCode() != intent.getOperationCode()
                || !Objects.equals(stage.getArtifactKey(), artifact.getArtifactKey())
                || !Objects.equals(stage.getArtifactSha256(), artifact.getSha256())
                || stage.getDeclaredRowCount() == null
                || stage.getDeclaredRowCount() != authority.getDeclaredRowCount()
                || stage.getNextByteOffset() == null
                || stage.getSourceRowCount() == null) {
            throw new IllegalStateException("report stage artifact binding drift");
        }
    }
}
