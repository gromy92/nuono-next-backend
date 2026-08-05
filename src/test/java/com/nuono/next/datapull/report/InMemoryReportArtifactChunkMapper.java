package com.nuono.next.datapull.report;

import com.nuono.next.infrastructure.mapper.DataPullReportArtifactChunkMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Deterministic restart/concurrency simulation for the chunked artifact persistence seam. */
final class InMemoryReportArtifactChunkMapper implements DataPullReportArtifactChunkMapper {
    private ReportArtifactRecord manifest;
    private final Map<Integer, ReportArtifactChunkRecord> chunks = new LinkedHashMap<>();
    private int metadataSelects;
    private int aggregateSelects;
    private int overlapSelects;
    private int maximumOverlapRows;
    private boolean duplicateInsertReturnsOne;
    private boolean failNextAdvance;

    @Override
    public synchronized int insertDownloadingIfAbsent(ReportArtifactRecord row) {
        if (manifest != null) {
            return 0;
        }
        row.setDownloadState("DOWNLOADING");
        row.setContentLength(0L);
        row.setPersistedChunkCount(0);
        row.setDownloadFenceEpoch(0L);
        row.setDownloadedByteCount(0L);
        row.setDownloadedChunkCount(0);
        row.setExpectedContentLength(null);
        row.setSourceValidator(null);
        if (row.getResumableSha256State() == null) {
            row.setResumableSha256State(new ReportResumableSha256().snapshot());
        }
        manifest = row;
        return 1;
    }

    @Override
    public synchronized ReportArtifactRecord selectMetadata(String artifactKey) {
        metadataSelects++;
        return manifest != null && artifactKey.equals(manifest.getArtifactKey())
                ? manifest : null;
    }

    @Override
    public synchronized int claimDownloadFence(
            String artifactKey,
            long expectedFenceEpoch,
            LocalDateTime updatedAt
    ) {
        if (!currentManifest(artifactKey, expectedFenceEpoch)) {
            return 0;
        }
        manifest.setDownloadFenceEpoch(expectedFenceEpoch + 1L);
        manifest.setUpdatedAt(updatedAt);
        return 1;
    }

    @Override
    public synchronized int bindDownloadResponse(
            String artifactKey,
            long fenceEpoch,
            long responseStart,
            long totalLength,
            String validator,
            LocalDateTime updatedAt
    ) {
        if (!currentManifest(artifactKey, fenceEpoch)
                || manifest.getDownloadedByteCount() != responseStart
                || (manifest.getExpectedContentLength() != null
                    && manifest.getExpectedContentLength() != totalLength)
                || (manifest.getSourceValidator() != null
                    && !manifest.getSourceValidator().equals(validator))) {
            return 0;
        }
        manifest.setExpectedContentLength(totalLength);
        if (manifest.getSourceValidator() == null) {
            manifest.setSourceValidator(validator);
        }
        manifest.setUpdatedAt(updatedAt);
        return 1;
    }

    @Override
    public synchronized int insertChunkIfCurrentWriter(
            ReportArtifactChunkRecord row,
            long fenceEpoch
    ) {
        if (!currentManifest(row.getArtifactKey(), fenceEpoch)
                || manifest.getDownloadedChunkCount() != row.getChunkNo()
                || manifest.getDownloadedByteCount() != row.getByteOffset()) {
            return 0;
        }
        boolean inserted = chunks.putIfAbsent(row.getChunkNo(), row) == null;
        return inserted || duplicateInsertReturnsOne ? 1 : 0;
    }

    @Override
    public synchronized ReportArtifactChunkRecord selectCurrentWriterChunk(
            String artifactKey,
            int chunkNo,
            long byteOffset,
            long fenceEpoch
    ) {
        if (!currentManifest(artifactKey, fenceEpoch)
                || manifest.getDownloadedChunkCount() != chunkNo
                || manifest.getDownloadedByteCount() != byteOffset) {
            return null;
        }
        return selectChunk(artifactKey, chunkNo);
    }

    @Override
    public synchronized ReportArtifactChunkRecord selectChunk(String artifactKey, int chunkNo) {
        ReportArtifactChunkRecord row = chunks.get(chunkNo);
        return row != null && artifactKey.equals(row.getArtifactKey()) ? row : null;
    }

    @Override
    public synchronized int advanceDownloadProgress(
            String artifactKey,
            long fenceEpoch,
            long expectedByteOffset,
            int expectedChunkNo,
            long nextByteOffset,
            int nextChunkNo,
            String resumableSha256State,
            LocalDateTime updatedAt
    ) {
        if (failNextAdvance) {
            failNextAdvance = false;
            return 0;
        }
        if (!currentManifest(artifactKey, fenceEpoch)
                || manifest.getDownloadedByteCount() != expectedByteOffset
                || manifest.getDownloadedChunkCount() != expectedChunkNo
                || manifest.getExpectedContentLength() == null
                || nextByteOffset > manifest.getExpectedContentLength()) {
            return 0;
        }
        manifest.setDownloadedByteCount(nextByteOffset);
        manifest.setDownloadedChunkCount(nextChunkNo);
        manifest.setResumableSha256State(resumableSha256State);
        manifest.setUpdatedAt(updatedAt);
        return 1;
    }

    @Override
    public synchronized List<ReportArtifactChunkRecord> selectOverlappingChunks(
            String artifactKey,
            long rangeStart,
            long rangeEnd,
            int maximumChunks
    ) {
        overlapSelects++;
        List<ReportArtifactChunkRecord> result = new ArrayList<>();
        for (ReportArtifactChunkRecord row : ordered()) {
            long end = row.getByteOffset() + row.getContentLength();
            if (artifactKey.equals(row.getArtifactKey())
                    && row.getByteOffset() < rangeEnd && end > rangeStart) {
                result.add(row);
                if (result.size() == maximumChunks) {
                    break;
                }
            }
        }
        maximumOverlapRows = Math.max(maximumOverlapRows, result.size());
        return result;
    }

    @Override
    public synchronized ReportArtifactChunkAggregate selectChunkAggregate(String artifactKey) {
        aggregateSelects++;
        ReportArtifactChunkAggregate aggregate = new ReportArtifactChunkAggregate();
        long length = 0L;
        int count = 0;
        Integer maximum = null;
        for (ReportArtifactChunkRecord row : ordered()) {
            if (!artifactKey.equals(row.getArtifactKey())) {
                continue;
            }
            count++;
            length += row.getContentLength();
            maximum = row.getChunkNo();
        }
        aggregate.setChunkCount(count);
        aggregate.setContentLength(length);
        aggregate.setMaximumChunkNo(maximum);
        return aggregate;
    }

    @Override
    public synchronized int completeDownload(
            String artifactKey,
            long fenceEpoch,
            String contentSha256,
            long contentLength,
            int chunkCount,
            String resumableSha256State,
            LocalDateTime updatedAt
    ) {
        ReportArtifactChunkAggregate aggregate = selectChunkAggregate(artifactKey);
        if (!currentManifest(artifactKey, fenceEpoch)
                || manifest.getDownloadedByteCount() != contentLength
                || manifest.getDownloadedChunkCount() != chunkCount
                || manifest.getExpectedContentLength() == null
                || manifest.getExpectedContentLength() != contentLength
                || !resumableSha256State.equals(manifest.getResumableSha256State())
                || aggregate.getChunkCount() != chunkCount
                || aggregate.getContentLength() != contentLength
                || aggregate.getMaximumChunkNo() == null
                || aggregate.getMaximumChunkNo() != chunkCount - 1) {
            return 0;
        }
        manifest.setContentSha256(contentSha256);
        manifest.setContentLength(contentLength);
        manifest.setPersistedChunkCount(chunkCount);
        manifest.setDownloadState("COMPLETE");
        manifest.setUpdatedAt(updatedAt);
        return 1;
    }

    synchronized ReportArtifactRecord manifest() { return manifest; }

    synchronized void tamperChunk(int chunkNo, byte[] bytes) {
        chunks.get(chunkNo).setContentBytes(bytes);
    }

    synchronized void duplicateInsertReturnsOne(boolean value) {
        duplicateInsertReturnsOne = value;
    }

    synchronized void failNextAdvance() {
        failNextAdvance = true;
    }

    synchronized void resetReadCounters() {
        metadataSelects = 0;
        aggregateSelects = 0;
        overlapSelects = 0;
        maximumOverlapRows = 0;
    }

    synchronized int metadataSelects() { return metadataSelects; }
    synchronized int aggregateSelects() { return aggregateSelects; }
    synchronized int overlapSelects() { return overlapSelects; }
    synchronized int maximumOverlapRows() { return maximumOverlapRows; }

    private boolean currentManifest(String artifactKey, long fenceEpoch) {
        return manifest != null
                && artifactKey.equals(manifest.getArtifactKey())
                && "DOWNLOADING".equals(manifest.getDownloadState())
                && manifest.getDownloadFenceEpoch() == fenceEpoch;
    }

    private List<ReportArtifactChunkRecord> ordered() {
        return chunks.values().stream()
                .sorted(Comparator.comparingInt(ReportArtifactChunkRecord::getChunkNo))
                .collect(Collectors.toList());
    }
}
