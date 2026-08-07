package com.nuono.next.datapull.report;

/** Exact provider-declared or complete-artifact locally counted row authority. */
final class ReportStageRowCountAuthority {
    private ReportStageRowCountAuthority() {
    }

    static boolean conflicts(ReportStageChunk chunk, long sourceRows) {
        return sourceRows > chunk.getDeclaredRowCount()
                || (!chunk.usesLocalRowCount()
                    && chunk.isEndOfFile()
                    && sourceRows != chunk.getDeclaredRowCount());
    }

    static boolean matches(ReportStageState stage, ReportStageChunk chunk) {
        long persisted = value(stage.getDeclaredRowCount());
        if (!chunk.usesLocalRowCount()) {
            return persisted == chunk.getDeclaredRowCount();
        }
        return ("VALIDATING".equals(stage.getState())
                && persisted == chunk.getDeclaredRowCount())
                || (!"VALIDATING".equals(stage.getState())
                    && persisted == value(stage.getSourceRowCount()));
    }

    private static long value(Long value) {
        return value == null ? 0L : value;
    }
}
