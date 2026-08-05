package com.nuono.next.datapull.report;

import com.nuono.next.infrastructure.mapper.DataPullReportArtifactChunkMapper;
import com.nuono.next.noon.NoonBinaryDownloadMetadata;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Durable chunked report store; no production operation materializes a complete report. */
public final class MyBatisReportArtifactStore implements ReportArtifactStore {
    static final String KEY_PREFIX = "rpt-art-v2-";
    static final int CHUNK_BYTES = 1024 * 1024;
    static final int MAX_RANGE_BYTES = 4 * 1024 * 1024;
    // Implementation ceiling only: INT chunk count multiplied by the fixed 1 MiB chunk size.
    // Business report size is not capped at a forecasted daily volume.
    static final long MAX_ARTIFACT_BYTES = (long) Integer.MAX_VALUE * CHUNK_BYTES;
    private static final int MAXIMUM_FENCE_CLAIM_ATTEMPTS = 8;
    private final DataPullReportArtifactChunkMapper mapper;
    private final Clock clock;
    public MyBatisReportArtifactStore(DataPullReportArtifactChunkMapper mapper) {
        this(mapper, Clock.systemUTC());
    }
    MyBatisReportArtifactStore(DataPullReportArtifactChunkMapper mapper, Clock clock) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }
    @Override
    public DownloadedReportArtifact store(
            ExportReportIntent intent,
            RemoteExportHandle handle,
            byte[] content
    ) {
        byte[] bytes = Objects.requireNonNull(content, "content");
        if (bytes.length == 0) {
            throw contract("REPORT_ARTIFACT_EMPTY_DOWNLOAD");
        }
        ReportArtifactDownload download = openDownload(intent, handle);
        if (download.isComplete()) {
            return download.completedArtifact();
        }
        int offset = Math.toIntExact(download.resumeByteOffset());
        if (offset > bytes.length) {
            throw contract("REPORT_ARTIFACT_RESUME_CONFLICT");
        }
        try {
            download.begin(new NoonBinaryDownloadMetadata(
                    offset, bytes.length - (long) offset, bytes.length, null
            ));
            while (offset < bytes.length) {
                int length = Math.min(CHUNK_BYTES, bytes.length - offset);
                download.accept(bytes, offset, length);
                offset += length;
            }
            download.complete();
            return download.completedArtifact();
        } catch (RuntimeException failure) {
            download.abort(failure);
            throw failure;
        }
    }

    @Override
    public Optional<DownloadedReportArtifact> findCompleted(
            ExportReportIntent intent,
            RemoteExportHandle handle
    ) {
        String key = artifactKey(intent, handle);
        ReportArtifactRecord row = mapper.selectMetadata(key);
        if (row == null || !"COMPLETE".equals(row.getDownloadState())) {
            return Optional.empty();
        }
        requireManifestIdentity(intent, handle, key, row);
        requireCompleteShape(row);
        return Optional.of(DownloadedReportArtifact.complete(
                key, row.getContentSha256(), row.getContentLength()
        ));
    }

    @Override
    public ReportArtifactDownload openDownload(
            ExportReportIntent intent,
            RemoteExportHandle handle
    ) {
        ExportReportIntent safeIntent = Objects.requireNonNull(intent, "intent");
        RemoteExportHandle safeHandle = Objects.requireNonNull(handle, "handle");
        String key = artifactKey(safeIntent, safeHandle);
        LocalDateTime now = nowUtc();
        ReportArtifactRecord candidate = manifest(
                safeIntent, safeHandle, key, now
        );
        mapper.insertDownloadingIfAbsent(candidate);
        for (int attempt = 0; attempt < MAXIMUM_FENCE_CLAIM_ATTEMPTS; attempt++) {
            ReportArtifactRecord persisted = mapper.selectMetadata(key);
            requireManifestIdentity(safeIntent, safeHandle, key, persisted);
            if ("COMPLETE".equals(persisted.getDownloadState())) {
                requireCompleteShape(persisted);
                return new CompletedReportArtifactDownload(
                        DownloadedReportArtifact.complete(
                                key, persisted.getContentSha256(), persisted.getContentLength()
                        )
                );
            }
            if (!"DOWNLOADING".equals(persisted.getDownloadState())) {
                throw contract("REPORT_ARTIFACT_STATE_INVALID");
            }
            long expectedFence = persisted.getDownloadFenceEpoch();
            if (expectedFence == Long.MAX_VALUE) {
                throw contract("REPORT_ARTIFACT_FENCE_EXHAUSTED");
            }
            if (mapper.claimDownloadFence(key, expectedFence, nowUtc()) != 1) {
                continue;
            }
            ReportArtifactRecord claimed = mapper.selectMetadata(key);
            requireManifestIdentity(safeIntent, safeHandle, key, claimed);
            if (!"DOWNLOADING".equals(claimed.getDownloadState())
                    || claimed.getDownloadFenceEpoch() != expectedFence + 1L) {
                continue;
            }
            return new MyBatisReportArtifactDownload(
                    safeIntent, safeHandle, key, claimed, mapper, clock
            );
        }
        throw contract("REPORT_ARTIFACT_FENCE_CONTENTION");
    }

    @Override
    public StoredReportArtifact readVerified(
            ExportReportIntent intent,
            DownloadedReportArtifact artifact
    ) {
        if (artifact.getContentLength() > MAX_RANGE_BYTES) {
            throw contract("REPORT_ARTIFACT_WHOLE_READ_LIMIT_EXCEEDED");
        }
        StoredReportArtifactSlice slice = readVerifiedRange(
                intent, artifact, 0L, Math.toIntExact(artifact.getContentLength())
        );
        if (!slice.isEndOfArtifact()) {
            throw contract("REPORT_ARTIFACT_RANGE_INTEGRITY_FAILED");
        }
        return new StoredReportArtifact(slice.getRemoteHandle(), slice.getContent());
    }

    @Override
    public StoredReportArtifactSlice readVerifiedRange(
            ExportReportIntent intent,
            DownloadedReportArtifact artifact,
            long byteOffset,
            int maxBytes
    ) {
        ReportArtifactRecord metadata = requireCompleted(intent, artifact);
        if (byteOffset < 0L || byteOffset > metadata.getContentLength()
                || maxBytes <= 0 || maxBytes > MAX_RANGE_BYTES) {
            throw contract("REPORT_ARTIFACT_RANGE_INVALID");
        }
        long rangeEnd = Math.min(
                metadata.getContentLength(), Math.addExact(byteOffset, maxBytes)
        );
        if (rangeEnd == byteOffset) {
            return new StoredReportArtifactSlice(
                    metadata.getRemoteHandle(), byteOffset, metadata.getContentLength(), new byte[0]
            );
        }
        int maximumChunks = Math.addExact(maxBytes / CHUNK_BYTES, 2);
        List<ReportArtifactChunkRecord> chunks = mapper.selectOverlappingChunks(
                artifact.getArtifactKey(), byteOffset, rangeEnd, maximumChunks
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream(
                Math.toIntExact(rangeEnd - byteOffset)
        );
        long cursor = byteOffset;
        for (ReportArtifactChunkRecord chunk : chunks) {
            byte[] content = requireValidChunk(chunk, artifact.getArtifactKey());
            long chunkEnd = Math.addExact(chunk.getByteOffset(), chunk.getContentLength());
            int start = Math.toIntExact(Math.max(cursor, chunk.getByteOffset()) - chunk.getByteOffset());
            int end = Math.toIntExact(Math.min(rangeEnd, chunkEnd) - chunk.getByteOffset());
            if (start >= end || Math.max(cursor, chunk.getByteOffset()) != cursor) {
                throw contract("REPORT_ARTIFACT_CHUNK_GAP");
            }
            output.write(content, start, end - start);
            cursor = Math.min(rangeEnd, chunkEnd);
            if (cursor == rangeEnd) {
                break;
            }
        }
        if (cursor != rangeEnd) {
            throw contract("REPORT_ARTIFACT_CHUNK_GAP");
        }
        return new StoredReportArtifactSlice(
                metadata.getRemoteHandle(), byteOffset, metadata.getContentLength(), output.toByteArray()
        );
    }

    @Override
    public void verifyComplete(
            ExportReportIntent intent,
            DownloadedReportArtifact artifact
    ) {
        // Download completion persisted the incremental full-stream SHA-256 only after every
        // fixed chunk matched durable storage. Each validation advance rehashes its bounded range.
        // EOF therefore reads only the immutable manifest; it never scans the full chunk set.
        requireCompleted(intent, artifact);
    }

    private ReportArtifactRecord requireCompleted(
            ExportReportIntent intent,
            DownloadedReportArtifact artifact
    ) {
        ReportArtifactRecord row = mapper.selectMetadata(artifact.getArtifactKey());
        if (row == null || !"COMPLETE".equals(row.getDownloadState())) {
            throw contract("REPORT_ARTIFACT_NOT_COMPLETE");
        }
        requireManifestIdentity(
                intent, new RemoteExportHandle(row.getRemoteHandle()), artifact.getArtifactKey(), row
        );
        requireCompleteShape(row);
        if (!artifact.getSha256().equals(row.getContentSha256())
                || artifact.getContentLength() != row.getContentLength()) {
            throw contract("REPORT_ARTIFACT_INTEGRITY_FAILED");
        }
        return row;
    }

    private void requireCompleteShape(ReportArtifactRecord row) {
        if (row.getContentLength() <= 0L || row.getContentLength() > MAX_ARTIFACT_BYTES
                || row.getContentSha256() == null || row.getContentSha256().length() != 64
                || row.getPersistedChunkCount() <= 0
                || row.getPersistedChunkCount() != expectedChunkCount(row.getContentLength())) {
            throw contract("REPORT_ARTIFACT_INTEGRITY_FAILED");
        }
    }

    private int expectedChunkCount(long contentLength) {
        return Math.toIntExact((contentLength + CHUNK_BYTES - 1L) / CHUNK_BYTES);
    }

    private byte[] requireValidChunk(ReportArtifactChunkRecord chunk, String artifactKey) {
        if (chunk == null || !artifactKey.equals(chunk.getArtifactKey())) {
            throw contract("REPORT_ARTIFACT_CHUNK_MISSING");
        }
        byte[] bytes = chunk.getContentBytes();
        if (bytes == null || bytes.length == 0 || bytes.length != chunk.getContentLength()
                || bytes.length > CHUNK_BYTES
                || !ReportDigestSupport.sha256(bytes).equals(chunk.getContentSha256())) {
            throw contract("REPORT_ARTIFACT_CHUNK_INTEGRITY_FAILED");
        }
        return bytes;
    }

    static String artifactKey(ExportReportIntent intent, RemoteExportHandle handle) {
        return KEY_PREFIX + ReportDigestSupport.sha256(
                Objects.requireNonNull(intent, "intent").getStableRequestKey()
                        + "\n" + Objects.requireNonNull(handle, "handle").getValue()
        );
    }

    private void requireManifestIdentity(
            ExportReportIntent intent,
            RemoteExportHandle handle,
            String artifactKey,
            ReportArtifactRecord row
    ) {
        if (row == null
                || intent.getTaskId() != row.getTaskId()
                || !intent.getStableRequestKey().equals(row.getStableRequestKey())
                || !handle.getValue().equals(row.getRemoteHandle())
                || !artifactKey.equals(row.getArtifactKey())) {
            throw contract("REPORT_ARTIFACT_IDEMPOTENCY_CONFLICT");
        }
    }

    private ReportArtifactContractException contract(String code) {
        return new ReportArtifactContractException(code);
    }

    private ReportArtifactRecord manifest(
            ExportReportIntent intent,
            RemoteExportHandle handle,
            String key,
            LocalDateTime now
    ) {
        ReportArtifactRecord row = new ReportArtifactRecord();
        row.setArtifactKey(key);
        row.setTaskId(intent.getTaskId());
        row.setStableRequestKey(intent.getStableRequestKey());
        row.setRemoteHandle(handle.getValue());
        row.setDownloadState("DOWNLOADING");
        row.setResumableSha256State(new ReportResumableSha256().snapshot());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        return row;
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
