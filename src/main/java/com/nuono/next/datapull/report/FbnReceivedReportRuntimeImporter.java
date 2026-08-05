package com.nuono.next.datapull.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.runtime.OperationCode;
import java.util.Objects;

/** DP-07-B adapter over the same bounded report staging pipeline as DP-01/02/03. */
public final class FbnReceivedReportRuntimeImporter implements ExportReportImporter {
    private final LegacyNoonReportImporter delegate;

    public FbnReceivedReportRuntimeImporter(
            NoonReportDefinition definition,
            ReportArtifactStore artifactStore,
            ReportFactPlanAdapter planAdapter,
            ReportStageStore stageStore,
            ObjectMapper objectMapper
    ) {
        NoonReportDefinition safeDefinition = Objects.requireNonNull(definition, "definition");
        if (safeDefinition.getOperationCode() != OperationCode.DP07B) {
            throw new IllegalArgumentException("DP07B_IMPORT_DEFINITION_INVALID");
        }
        this.delegate = new LegacyNoonReportImporter(
                safeDefinition,
                artifactStore,
                planAdapter,
                stageStore,
                objectMapper
        );
    }

    @Override
    public ReportImportResult importComplete(
            ExportReportIntent intent,
            DownloadedReportArtifact artifact
    ) {
        if (intent == null || intent.getOperationCode() != OperationCode.DP07B) {
            return ReportImportResult.contractError("DP07B_IMPORT_INTENT_INVALID");
        }
        return delegate.importComplete(intent, artifact);
    }
}
