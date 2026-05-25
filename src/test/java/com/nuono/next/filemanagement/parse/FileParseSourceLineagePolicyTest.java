package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FileParseSourceLineagePolicyTest {

    private final FileParseSourceLineagePolicy policy = new FileParseSourceLineagePolicy();

    @Test
    void shouldFallbackMissingSourceRowsWithinCurrentChunkOnly() {
        FileParseStructuredAiResult result = resultWithItems(
                item(List.of()),
                item(List.of(99001L, 11002L)),
                item(List.of(99002L))
        );

        policy.attachFallbackSourceRows(result, List.of(11001L, 11002L));

        assertEquals(List.of(11001L), result.getItems().get(0).getSourceRowIds());
        assertEquals(List.of(11002L), result.getItems().get(1).getSourceRowIds());
        assertEquals(List.of(11002L), result.getItems().get(2).getSourceRowIds());
    }

    @Test
    void shouldResolveAiChunkIdBySourceRowAfterResultSortNoChanges() {
        Map<Long, Long> aiChunkIdBySourceRowId = policy.aiChunkIdBySourceRowId(List.of(
                new FileParseSourceLineagePolicy.AiChunkSourceRows(36001L, List.of(35001L, 35002L)),
                new FileParseSourceLineagePolicy.AiChunkSourceRows(36002L, List.of(35031L))
        ));

        assertEquals(36001L, policy.resolveAiChunkIdForSourceRows(List.of(35002L), aiChunkIdBySourceRowId));
        assertEquals(36002L, policy.resolveAiChunkIdForSourceRows(List.of(35031L), aiChunkIdBySourceRowId));
        assertEquals(36002L, aiChunkIdBySourceRowId.get(35031L));
    }

    private FileParseStructuredAiResult resultWithItems(FileParseStructuredItem... items) {
        FileParseStructuredAiResult result = new FileParseStructuredAiResult();
        result.setItems(List.of(items));
        return result;
    }

    private FileParseStructuredItem item(List<Long> sourceRowIds) {
        FileParseStructuredItem item = new FileParseStructuredItem();
        item.setSourceRowIds(sourceRowIds);
        return item;
    }
}
