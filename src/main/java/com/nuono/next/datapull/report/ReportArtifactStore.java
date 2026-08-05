package com.nuono.next.datapull.report;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/** Durable report-byte store; every read revalidates the persisted digest and binding. */
public interface ReportArtifactStore {
    DownloadedReportArtifact store(
            ExportReportIntent intent,
            RemoteExportHandle handle,
            byte[] content
    );

    StoredReportArtifact readVerified(
            ExportReportIntent intent,
            DownloadedReportArtifact artifact
    );

    /** Returns a previously completed artifact after a crash between download and checkpoint CAS. */
    default Optional<DownloadedReportArtifact> findCompleted(
            ExportReportIntent intent,
            RemoteExportHandle handle
    ) {
        return Optional.empty();
    }

    /**
     * Opens a durable streaming sink. Production stores override this method; the bounded fallback
     * exists for isolated legacy test doubles and refuses responses above 16 MiB.
     */
    default ReportArtifactDownload openDownload(
            ExportReportIntent intent,
            RemoteExportHandle handle
    ) {
        ReportArtifactStore owner = this;
        ExportReportIntent safeIntent = Objects.requireNonNull(intent, "intent");
        RemoteExportHandle safeHandle = Objects.requireNonNull(handle, "handle");
        return new ReportArtifactDownload() {
            private static final int CHUNK_BYTES = 256 * 1024;
            private static final long MAXIMUM_BYTES = 16L * 1024L * 1024L;
            private final ByteArrayOutputStream output = new ByteArrayOutputStream();
            private DownloadedReportArtifact completed;

            @Override public int preferredChunkBytes() { return CHUNK_BYTES; }
            @Override public long maximumBytes() { return MAXIMUM_BYTES; }

            @Override
            public void accept(byte[] bytes, int offset, int length) {
                Objects.checkFromIndexSize(offset, length, bytes.length);
                if ((long) output.size() + length > MAXIMUM_BYTES) {
                    throw new ReportArtifactContractException(
                            "REPORT_ARTIFACT_FALLBACK_LIMIT_EXCEEDED"
                    );
                }
                output.write(bytes, offset, length);
            }

            @Override
            public void complete() {
                if (completed != null) {
                    throw new ReportArtifactContractException(
                            "REPORT_ARTIFACT_DOWNLOAD_ALREADY_COMPLETE"
                    );
                }
                completed = owner.store(safeIntent, safeHandle, output.toByteArray());
            }

            @Override
            public DownloadedReportArtifact completedArtifact() {
                if (completed == null) {
                    throw new ReportArtifactContractException(
                            "REPORT_ARTIFACT_DOWNLOAD_INCOMPLETE"
                    );
                }
                return completed;
            }
        };
    }

    /** Final complete-container digest fence before a stage can become sealed. */
    default void verifyComplete(
            ExportReportIntent intent,
            DownloadedReportArtifact artifact
    ) {
        readVerified(intent, artifact);
    }

    /**
     * Reads at most {@code maxBytes} after verifying the complete persisted artifact binding.
     * Production stores override this so a staging advance never materializes the whole BLOB.
     */
    default StoredReportArtifactSlice readVerifiedRange(
            ExportReportIntent intent,
            DownloadedReportArtifact artifact,
            long byteOffset,
            int maxBytes
    ) {
        if (byteOffset < 0L || maxBytes <= 0) {
            throw new IllegalArgumentException("report artifact range is invalid");
        }
        StoredReportArtifact whole = readVerified(intent, artifact);
        byte[] content = whole.getContent();
        if (byteOffset > content.length) {
            throw new IllegalArgumentException("report artifact range is outside the artifact");
        }
        int start = Math.toIntExact(byteOffset);
        int end = (int) Math.min(content.length, (long) start + maxBytes);
        return new StoredReportArtifactSlice(
                whole.getRemoteHandle(),
                byteOffset,
                content.length,
                Arrays.copyOfRange(content, start, end)
        );
    }
}
