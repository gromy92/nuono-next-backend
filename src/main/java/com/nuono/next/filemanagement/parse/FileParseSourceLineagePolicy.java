package com.nuono.next.filemanagement.parse;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class FileParseSourceLineagePolicy {

    void attachFallbackSourceRows(FileParseStructuredAiResult chunkResult, List<Long> chunkSourceRowIds) {
        if (chunkResult == null || chunkResult.getItems() == null || chunkResult.getItems().isEmpty()) {
            return;
        }
        List<Long> safeChunkSourceRowIds = chunkSourceRowIds == null ? List.of() : chunkSourceRowIds;
        Set<Long> allowedSourceRowIds = new LinkedHashSet<>(safeChunkSourceRowIds);
        for (int index = 0; index < chunkResult.getItems().size(); index++) {
            FileParseStructuredItem item = chunkResult.getItems().get(index);
            List<Long> validIds = item.getSourceRowIds().stream()
                    .filter(allowedSourceRowIds::contains)
                    .distinct()
                    .collect(Collectors.toList());
            if (validIds.isEmpty() && !safeChunkSourceRowIds.isEmpty()) {
                validIds = List.of(safeChunkSourceRowIds.get(Math.min(index, safeChunkSourceRowIds.size() - 1)));
            }
            item.setSourceRowIds(validIds);
        }
    }

    Map<Long, Long> aiChunkIdBySourceRowId(List<AiChunkSourceRows> chunks) {
        Map<Long, Long> aiChunkIdBySourceRowId = new LinkedHashMap<>();
        if (chunks == null || chunks.isEmpty()) {
            return aiChunkIdBySourceRowId;
        }
        for (AiChunkSourceRows chunk : chunks) {
            if (chunk == null || chunk.getAiChunkId() == null || chunk.getSourceRowIds().isEmpty()) {
                continue;
            }
            for (Long sourceRowId : chunk.getSourceRowIds()) {
                if (sourceRowId != null) {
                    aiChunkIdBySourceRowId.putIfAbsent(sourceRowId, chunk.getAiChunkId());
                }
            }
        }
        return aiChunkIdBySourceRowId;
    }

    Long resolveAiChunkIdForSourceRows(List<Long> sourceRowIds, Map<Long, Long> aiChunkIdBySourceRowId) {
        if (sourceRowIds == null || sourceRowIds.isEmpty() || aiChunkIdBySourceRowId == null || aiChunkIdBySourceRowId.isEmpty()) {
            return null;
        }
        for (Long sourceRowId : sourceRowIds) {
            Long aiChunkId = aiChunkIdBySourceRowId.get(sourceRowId);
            if (aiChunkId != null) {
                return aiChunkId;
            }
        }
        return null;
    }

    static class AiChunkSourceRows {

        private final Long aiChunkId;
        private final List<Long> sourceRowIds;

        AiChunkSourceRows(Long aiChunkId, List<Long> sourceRowIds) {
            this.aiChunkId = aiChunkId;
            this.sourceRowIds = sourceRowIds == null ? List.of() : sourceRowIds;
        }

        private Long getAiChunkId() {
            return aiChunkId;
        }

        private List<Long> getSourceRowIds() {
            return sourceRowIds;
        }
    }
}
