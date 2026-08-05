package com.nuono.next.datapull.report;

import java.util.List;

/** One bounded, restart-safe validation write. */
public final class ReportStageChunk {
    private final String artifactKey;
    private final String artifactSha256;
    private final long declaredRowCount;
    private final String headerJson;
    private final long expectedByteOffset;
    private final long nextByteOffset;
    private final boolean endOfFile;
    private final List<ReportPlannedRow> rows;

    public ReportStageChunk(
            String artifactKey,
            String artifactSha256,
            long declaredRowCount,
            String headerJson,
            long expectedByteOffset,
            long nextByteOffset,
            boolean endOfFile,
            List<ReportPlannedRow> rows
    ) {
        this.artifactKey = artifactKey;
        this.artifactSha256 = artifactSha256;
        this.declaredRowCount = declaredRowCount;
        this.headerJson = headerJson;
        this.expectedByteOffset = expectedByteOffset;
        this.nextByteOffset = nextByteOffset;
        this.endOfFile = endOfFile;
        this.rows = List.copyOf(rows);
    }

    public String getArtifactKey() { return artifactKey; }
    public String getArtifactSha256() { return artifactSha256; }
    public long getDeclaredRowCount() { return declaredRowCount; }
    public String getHeaderJson() { return headerJson; }
    public long getExpectedByteOffset() { return expectedByteOffset; }
    public long getNextByteOffset() { return nextByteOffset; }
    public boolean isEndOfFile() { return endOfFile; }
    public List<ReportPlannedRow> getRows() { return rows; }
}
