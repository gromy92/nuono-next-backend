package com.nuono.next.datapull.report;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.ReportStageMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportStageEmptyRebinderTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 7, 0, 0);
    private final ExportReportIntent intent = ReportBridgeTestSupport.intent(
            OperationCode.DP02,
            "NOON_REPORT_ORDER"
    );
    private final ReportStageMapper mapper = mock(ReportStageMapper.class);

    @Test
    void newerArtifactRebindsTheZeroRowStage() {
        ReportStageState empty = stage("artifact-old", "sha-old", "EMPTY_UNPROVEN");
        ReportStageState rebound = stage("artifact-new", "sha-new", "VALIDATING");
        ReportStageChunk chunk = chunk("artifact-new", "sha-new");
        when(mapper.rebindUnprovenEmpty(
                intent, "artifact-old", "sha-old", "artifact-new", "sha-new",
                "[\"id\"]", 7L, Long.MAX_VALUE, NOW
        )).thenReturn(1);
        when(mapper.selectStageForUpdate(intent.getTaskId())).thenReturn(rebound);

        ReportStageState result = ReportStageEmptyRebinder.rebindIfRequired(
                mapper, intent, chunk, NOW, empty
        );

        assertSame(rebound, result);
        verify(mapper).rebindUnprovenEmpty(
                intent, "artifact-old", "sha-old", "artifact-new", "sha-new",
                "[\"id\"]", 7L, Long.MAX_VALUE, NOW
        );
    }

    @Test
    void sameArtifactKeepsWaitingWithoutResettingTheStage() {
        ReportStageState empty = stage("artifact", "sha", "EMPTY_UNPROVEN");

        ReportStageState result = ReportStageEmptyRebinder.rebindIfRequired(
                mapper, intent, chunk("artifact", "sha"), NOW, empty
        );

        assertSame(empty, result);
        verifyNoInteractions(mapper);
    }

    @Test
    void rejectedRebindFailsClosed() {
        ReportStageState empty = stage("artifact-old", "sha-old", "EMPTY_UNPROVEN");
        ReportStageChunk chunk = chunk("artifact-new", "sha-new");

        assertThrows(IllegalStateException.class, () ->
                ReportStageEmptyRebinder.rebindIfRequired(
                        mapper, intent, chunk, NOW, empty
                ));
    }

    private ReportStageState stage(String key, String sha, String state) {
        ReportStageState result = new ReportStageState();
        result.setTaskId(intent.getTaskId());
        result.setArtifactKey(key);
        result.setArtifactSha256(sha);
        result.setState(state);
        return result;
    }

    private ReportStageChunk chunk(String key, String sha) {
        return new ReportStageChunk(
                key, sha, Long.MAX_VALUE, true, "[\"id\"]",
                7L, 10L, true, List.of()
        );
    }
}
