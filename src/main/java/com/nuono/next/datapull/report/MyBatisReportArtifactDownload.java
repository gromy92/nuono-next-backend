package com.nuono.next.datapull.report;

import com.nuono.next.infrastructure.mapper.DataPullReportArtifactChunkMapper;
import com.nuono.next.noon.NoonBinaryDownloadMetadata;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Objects;

/** One fenced streamed attempt that resumes at the last manifest-confirmed chunk boundary. */
final class MyBatisReportArtifactDownload implements ReportArtifactDownload {
    private final ExportReportIntent intent;
    private final RemoteExportHandle handle;
    private final String artifactKey;
    private final DataPullReportArtifactChunkMapper mapper;
    private final Clock clock;
    private final long fenceEpoch;
    private final ReportResumableSha256 digest;
    private int nextChunkNo;
    private long nextByteOffset;
    private Long expectedContentLength;
    private String sourceValidator;
    private boolean responseBound;
    private boolean partialChunkSeen;
    private DownloadedReportArtifact completed;

    MyBatisReportArtifactDownload(
            ExportReportIntent intent,
            RemoteExportHandle handle,
            String artifactKey,
            ReportArtifactRecord manifest,
            DataPullReportArtifactChunkMapper mapper,
            Clock clock
    ) {
        this.intent = Objects.requireNonNull(intent, "intent");
        this.handle = Objects.requireNonNull(handle, "handle");
        this.artifactKey = ReportContract.requireIdentity(artifactKey, "artifactKey");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.clock = Objects.requireNonNull(clock, "clock");
        ReportArtifactRecord row = Objects.requireNonNull(manifest, "manifest");
        requireManifestIdentity(row);
        if (!"DOWNLOADING".equals(row.getDownloadState())
                || row.getDownloadFenceEpoch() <= 0L
                || row.getDownloadedByteCount() < 0L
                || row.getDownloadedChunkCount() != expectedChunkCount(row.getDownloadedByteCount())) {
            throw contract("REPORT_ARTIFACT_RESUME_STATE_INVALID");
        }
        this.fenceEpoch = row.getDownloadFenceEpoch();
        this.nextChunkNo = row.getDownloadedChunkCount();
        this.nextByteOffset = row.getDownloadedByteCount();
        this.expectedContentLength = row.getExpectedContentLength();
        this.sourceValidator = row.getSourceValidator();
        try {
            this.digest = ReportResumableSha256.resume(row.getResumableSha256State());
        } catch (RuntimeException invalidState) {
            throw contract("REPORT_ARTIFACT_RESUME_STATE_INVALID");
        }
        if (digest.byteCount() != nextByteOffset
                || nextByteOffset > maximumBytes()
                || (expectedContentLength != null
                    && (expectedContentLength <= 0L
                        || expectedContentLength > maximumBytes()
                        || nextByteOffset > expectedContentLength))) {
            throw contract("REPORT_ARTIFACT_RESUME_STATE_INVALID");
        }
        partialChunkSeen = nextByteOffset % preferredChunkBytes() != 0L;
        recoverCompletedSuffix();
    }

    @Override public int preferredChunkBytes() { return MyBatisReportArtifactStore.CHUNK_BYTES; }
    @Override public long maximumBytes() { return MyBatisReportArtifactStore.MAX_ARTIFACT_BYTES; }
    @Override public synchronized long resumeByteOffset() { return nextByteOffset; }
    @Override public synchronized String resumeEntityValidator() { return sourceValidator; }
    @Override public synchronized boolean isComplete() { return completed != null; }

    @Override
    public synchronized void begin(NoonBinaryDownloadMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        requireOpen();
        if (responseBound
                || metadata.responseStart() != nextByteOffset
                || metadata.totalLength() > maximumBytes()
                || metadata.responseLength()
                    != metadata.totalLength() - metadata.responseStart()) {
            throw contract("REPORT_ARTIFACT_RESPONSE_BINDING_INVALID");
        }
        int updated = mapper.bindDownloadResponse(
                artifactKey,
                fenceEpoch,
                nextByteOffset,
                metadata.totalLength(),
                metadata.entityValidator(),
                nowUtc()
        );
        if (updated != 1) {
            throw contract("REPORT_ARTIFACT_RESPONSE_BINDING_CONFLICT");
        }
        ReportArtifactRecord persisted = requireManifest();
        if (!"DOWNLOADING".equals(persisted.getDownloadState())
                || persisted.getDownloadFenceEpoch() != fenceEpoch
                || persisted.getDownloadedByteCount() != nextByteOffset
                || persisted.getDownloadedChunkCount() != nextChunkNo
                || persisted.getExpectedContentLength() == null
                || persisted.getExpectedContentLength() != metadata.totalLength()
                || !sameNullable(persisted.getSourceValidator(), metadata.entityValidator())) {
            throw contract("REPORT_ARTIFACT_RESPONSE_BINDING_CONFLICT");
        }
        expectedContentLength = persisted.getExpectedContentLength();
        sourceValidator = persisted.getSourceValidator();
        responseBound = true;
    }

    @Override
    public synchronized void accept(byte[] bytes, int offset, int length) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.checkFromIndexSize(offset, length, bytes.length);
        requireOpen();
        if (!responseBound) {
            throw contract("REPORT_ARTIFACT_RESPONSE_NOT_BOUND");
        }
        if (length <= 0 || length > preferredChunkBytes() || partialChunkSeen) {
            throw contract("REPORT_ARTIFACT_CHUNK_BOUND_INVALID");
        }
        long nextLength = Math.addExact(nextByteOffset, length);
        if (nextLength > maximumBytes()
                || expectedContentLength == null
                || nextLength > expectedContentLength) {
            throw contract("REPORT_ARTIFACT_LIMIT_EXCEEDED");
        }
        byte[] content = Arrays.copyOfRange(bytes, offset, offset + length);
        String chunkSha = ReportDigestSupport.sha256(content);
        ReportArtifactChunkRecord candidate = chunk(content, chunkSha);
        mapper.insertChunkIfCurrentWriter(candidate, fenceEpoch);

        // Connector/J found-row semantics make the upsert count ambiguous. The durable row is
        // therefore always reread under the same manifest fence and compared byte-for-byte.
        ReportArtifactChunkRecord persisted = mapper.selectCurrentWriterChunk(
                artifactKey, nextChunkNo, nextByteOffset, fenceEpoch
        );
        requireSameChunk(persisted, content, chunkSha);

        digest.update(content);
        String nextDigestState = digest.snapshot();
        int updated = mapper.advanceDownloadProgress(
                artifactKey,
                fenceEpoch,
                nextByteOffset,
                nextChunkNo,
                nextLength,
                nextChunkNo + 1,
                nextDigestState,
                nowUtc()
        );
        if (updated != 1) {
            throw contract("REPORT_ARTIFACT_WRITER_FENCED");
        }
        nextByteOffset = nextLength;
        nextChunkNo++;
        partialChunkSeen = length < preferredChunkBytes();
    }

    @Override
    public synchronized void complete() {
        requireOpen();
        if (!responseBound
                || expectedContentLength == null
                || nextByteOffset != expectedContentLength
                || nextByteOffset <= 0L
                || nextChunkNo <= 0) {
            throw contract("REPORT_ARTIFACT_DOWNLOAD_INCOMPLETE");
        }
        completePersistedProgress();
    }

    @Override
    public synchronized DownloadedReportArtifact completedArtifact() {
        if (completed == null) {
            throw contract("REPORT_ARTIFACT_DOWNLOAD_INCOMPLETE");
        }
        return completed;
    }

    private void recoverCompletedSuffix() {
        if (expectedContentLength != null
                && expectedContentLength > 0L
                && nextByteOffset == expectedContentLength
                && nextChunkNo > 0) {
            completePersistedProgress();
        }
    }

    private void completePersistedProgress() {
        String contentSha = digest.finishHex();
        String digestState = digest.snapshot();
        mapper.completeDownload(
                artifactKey,
                fenceEpoch,
                contentSha,
                nextByteOffset,
                nextChunkNo,
                digestState,
                nowUtc()
        );
        ReportArtifactRecord manifest = requireManifest();
        if (!"COMPLETE".equals(manifest.getDownloadState())
                || !contentSha.equals(manifest.getContentSha256())
                || nextByteOffset != manifest.getContentLength()
                || nextChunkNo != manifest.getPersistedChunkCount()
                || nextByteOffset != manifest.getDownloadedByteCount()
                || nextChunkNo != manifest.getDownloadedChunkCount()) {
            throw contract("REPORT_ARTIFACT_COMPLETION_CONFLICT");
        }
        completed = DownloadedReportArtifact.complete(
                artifactKey, contentSha, nextByteOffset
        );
    }

    private ReportArtifactChunkRecord chunk(byte[] content, String contentSha) {
        ReportArtifactChunkRecord row = new ReportArtifactChunkRecord();
        row.setArtifactKey(artifactKey);
        row.setChunkNo(nextChunkNo);
        row.setByteOffset(nextByteOffset);
        row.setContentLength(content.length);
        row.setContentSha256(contentSha);
        row.setContentBytes(content);
        row.setCreatedAt(nowUtc());
        return row;
    }

    private void requireSameChunk(
            ReportArtifactChunkRecord row,
            byte[] expectedContent,
            String expectedSha
    ) {
        if (row == null
                || !artifactKey.equals(row.getArtifactKey())
                || row.getChunkNo() != nextChunkNo
                || row.getByteOffset() != nextByteOffset
                || row.getContentLength() != expectedContent.length
                || !expectedSha.equals(row.getContentSha256())
                || !Arrays.equals(expectedContent, row.getContentBytes())) {
            throw contract("REPORT_ARTIFACT_RESUME_CONFLICT");
        }
    }

    private ReportArtifactRecord requireManifest() {
        ReportArtifactRecord row = mapper.selectMetadata(artifactKey);
        requireManifestIdentity(row);
        return row;
    }

    private void requireManifestIdentity(ReportArtifactRecord row) {
        if (row == null
                || intent.getTaskId() != row.getTaskId()
                || !intent.getStableRequestKey().equals(row.getStableRequestKey())
                || !handle.getValue().equals(row.getRemoteHandle())
                || !artifactKey.equals(row.getArtifactKey())) {
            throw contract("REPORT_ARTIFACT_IDEMPOTENCY_CONFLICT");
        }
    }

    private int expectedChunkCount(long contentLength) {
        return contentLength == 0L ? 0 : Math.toIntExact(
                (contentLength + preferredChunkBytes() - 1L) / preferredChunkBytes()
        );
    }

    private boolean sameNullable(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    private void requireOpen() {
        if (completed != null) {
            throw contract("REPORT_ARTIFACT_DOWNLOAD_ALREADY_COMPLETE");
        }
    }

    private ReportArtifactContractException contract(String code) {
        return new ReportArtifactContractException(code);
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
