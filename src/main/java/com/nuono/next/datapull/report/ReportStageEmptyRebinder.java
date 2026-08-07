package com.nuono.next.datapull.report;

import com.nuono.next.infrastructure.mapper.ReportStageMapper;
import java.time.LocalDateTime;
import java.util.Objects;

/** Reopens a zero-row unproven stage when the same intent later resolves to a new artifact. */
final class ReportStageEmptyRebinder {
    private ReportStageEmptyRebinder() {
    }

    static ReportStageState rebindIfRequired(
            ReportStageMapper mapper,
            ExportReportIntent intent,
            ReportStageChunk chunk,
            LocalDateTime nowUtc,
            ReportStageState state
    ) {
        if (!requiresRebind(state, chunk)) {
            return state;
        }
        if (mapper.rebindUnprovenEmpty(
                intent,
                state.getArtifactKey(),
                state.getArtifactSha256(),
                chunk.getArtifactKey(),
                chunk.getArtifactSha256(),
                chunk.getHeaderJson(),
                chunk.getExpectedByteOffset(),
                chunk.getDeclaredRowCount(),
                nowUtc
        ) != 1) {
            throw new IllegalStateException("report empty stage rebind CAS was rejected");
        }
        return Objects.requireNonNull(
                mapper.selectStageForUpdate(intent.getTaskId()),
                "rebound report stage"
        );
    }

    private static boolean requiresRebind(ReportStageState state, ReportStageChunk chunk) {
        return "EMPTY_UNPROVEN".equals(state.getState())
                && (!Objects.equals(state.getArtifactKey(), chunk.getArtifactKey())
                    || !Objects.equals(state.getArtifactSha256(), chunk.getArtifactSha256()));
    }
}
