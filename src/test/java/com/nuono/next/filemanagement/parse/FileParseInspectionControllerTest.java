package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class FileParseInspectionControllerTest extends FileParseHttpTestFixture {

    @Test
    void delegatesInspectionQueries() {
        FileParseSourceRowsView rows = new FileParseSourceRowsView();
        FileParseWorkflowView workflow = new FileParseWorkflowView();
        FileParseAiChunksView chunks = new FileParseAiChunksView();
        FileParseValidationIssuesView issues = new FileParseValidationIssuesView();
        when(service.listSourceRows(session, 20L, 10L, "xlsx", "sku", 1, 30)).thenReturn(rows);
        when(service.getWorkflow(session, 20L)).thenReturn(workflow);
        when(service.listAiChunks(session, 20L, "done", 2, 30)).thenReturn(chunks);
        when(service.listValidationIssues(session, 20L, "error", "sku", "open", 3, 30)).thenReturn(issues);
        FileParseInspectionController controller = controller();

        assertEquals(rows, controller.sourceRows(20L, 10L, "xlsx", "sku", 1, 30, request));
        assertEquals(workflow, controller.workflow(20L, request));
        assertEquals(chunks, controller.aiChunks(20L, "done", 2, 30, request));
        assertEquals(issues, controller.validationIssues(20L, "error", "sku", "open", 3, 30, request));
    }

    @Test
    void delegatesProcessingOverviewAndCompareQueries() {
        FileParseProcessingItemsView processing = new FileParseProcessingItemsView();
        FileParseOverviewItemsView overview = new FileParseOverviewItemsView();
        FileParseItemCompareView compare = new FileParseItemCompareView();
        when(service.listProcessingItems(session, 20L, "pending", "changed", 1, 100)).thenReturn(processing);
        when(service.listOverviewItems(session, 20L, 2, 1000)).thenReturn(overview);
        when(service.compareResultItem(session, 20L, 50L)).thenReturn(compare);
        FileParseInspectionController controller = controller();

        assertEquals(processing, controller.processingItems(20L, "pending", "changed", 1, 100, request));
        assertEquals(overview, controller.overviewItems(20L, 2, 1000, request));
        assertEquals(compare, controller.compareItem(20L, 50L, request));
    }

    @Test
    void returnsExportWithOriginalTransportContract() throws Exception {
        FileParseExportFile export = new FileParseExportFile(
                "义特 FBN 解析总览.xlsx",
                FileParseQueryViewService.OVERVIEW_EXPORT_CONTENT_TYPE,
                new byte[] {1, 2, 3}
        );
        when(service.exportOverviewItems(session, 20L)).thenReturn(export);

        ResponseEntity<Resource> response = controller().exportOverviewItems(20L, request);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(CacheControl.noStore().getHeaderValue(), response.getHeaders().getCacheControl());
        assertEquals(3L, response.getHeaders().getContentLength());
        assertEquals(FileParseQueryViewService.OVERVIEW_EXPORT_CONTENT_TYPE,
                response.getHeaders().getContentType().toString());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
                .contains("filename*=UTF-8''%E4%B9%89%E7%89%B9%20FBN%20%E8%A7%A3%E6%9E%90%E6%80%BB%E8%A7%88.xlsx"));
        assertEquals(3L, response.getBody().contentLength());
        verify(service).exportOverviewItems(session, 20L);
    }

    private FileParseInspectionController controller() {
        return new FileParseInspectionController(support);
    }
}
