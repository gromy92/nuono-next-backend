package com.nuono.next.datapull.report;

import static com.nuono.next.datapull.report.ReportJobTestSupport.SHA;
import static com.nuono.next.datapull.report.ReportJobTestSupport.context;
import static com.nuono.next.datapull.report.ReportJobTestSupport.task;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import org.junit.jupiter.api.Test;

class ExportReportCheckpointCodecTest {

    @Test
    void applyCheckpointRoundTripsOpaqueArtifactReference() {
        ExportReportCheckpointCodec codec = new ExportReportCheckpointCodec();
        ReportArtifactAuthority authority = ReportArtifactAuthority.restored(
                "request|中文", "export-42", 7L
        );
        ExportReportCheckpoint checkpoint = ExportReportCheckpoint
                .at(ExportReportCheckpoint.Phase.POLL, "request|中文")
                .download("locator:42", authority)
                .apply(DownloadedReportArtifact.complete("artifact|folder/文件", SHA, 99L));

        ExportReportCheckpoint decoded = codec.decode(codec.encode(checkpoint));

        assertEquals(ExportReportCheckpoint.Phase.APPLY, decoded.getPhase());
        assertEquals("request|中文", decoded.getStableRequestKey());
        assertEquals(null, decoded.getDownloadLocatorReference());
        assertEquals("artifact|folder/文件", decoded.getArtifact().getArtifactKey());
        assertEquals(99L, decoded.getArtifact().getContentLength());
        assertEquals(7L, decoded.getArtifactAuthority().getDeclaredRowCount());
        assertEquals("export-42", decoded.getArtifactAuthority().getRemoteHandle());
    }

    @Test
    void downloadCheckpointRoundTripsOnlySecretFreeLocatorReference() {
        ExportReportCheckpointCodec codec = new ExportReportCheckpointCodec();
        ReportArtifactAuthority authority = ReportArtifactAuthority.restored(
                "request", "export-42", 11L
        );
        ExportReportCheckpoint checkpoint = ExportReportCheckpoint
                .at(ExportReportCheckpoint.Phase.POLL, "request")
                .download("locator:42", authority);

        ExportReportCheckpoint decoded = codec.decode(codec.encode(checkpoint));

        assertEquals(ExportReportCheckpoint.Phase.DOWNLOAD, decoded.getPhase());
        assertEquals("locator:42", decoded.getDownloadLocatorReference());
        assertEquals(11L, decoded.getArtifactAuthority().getDeclaredRowCount());
    }

    @Test
    void corruptOrPartialCheckpointFailsClosed() {
        ExportReportCheckpointCodec codec = new ExportReportCheckpointCodec();

        assertThrows(IllegalArgumentException.class, () -> codec.decode("v3|CREATE|0|-|-|-|-|-"));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode("v2|APPLY|0|cmVxdWVzdA|-|YXJ0aWZhY3Q|-|10")
        );
    }

    @Test
    void unknownCreateOutcomeSurvivesRestartAndCannotBecomeCreateAgain() {
        ExportReportCheckpointCodec codec = new ExportReportCheckpointCodec();
        ExportReportCheckpoint checkpoint = ExportReportCheckpoint.at(
                ExportReportCheckpoint.Phase.RECONCILE_CREATE,
                "request-unknown"
        ).unknownCreateOutcome();

        ExportReportCheckpoint decoded = codec.decode(codec.encode(checkpoint));

        assertEquals(ExportReportCheckpoint.Phase.RECONCILE_CREATE, decoded.getPhase());
        assertEquals(true, decoded.isCreateOutcomeUnknown());
    }

    @Test
    void requestKeyIsStableAcrossTaskAndFenceIdentityButChangesWithWindow() {
        DataPullTask first = task(301L, OperationCode.DP01);
        DataPullTask restarted = task(999L, OperationCode.DP01);
        restarted.setFenceEpoch(42L);
        ExportReportIntent firstIntent = ExportReportIntent.from(context(first));
        ExportReportIntent restartedIntent = ExportReportIntent.from(context(restarted));

        assertEquals(firstIntent.getStableRequestKey(), restartedIntent.getStableRequestKey());

        restarted.setBusinessWindowKey("DP01:date-range:2026-07-01..2026-07-31");
        ExportReportIntent differentWindow = ExportReportIntent.from(context(restarted));
        assertNotEquals(firstIntent.getStableRequestKey(), differentWindow.getStableRequestKey());
    }
}
