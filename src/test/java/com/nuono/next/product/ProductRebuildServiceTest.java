package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.productlisting.ProductListingDraftCommand;
import com.nuono.next.productlisting.ProductListingRealRunSubmission;
import com.nuono.next.productlisting.ProductListingService;
import com.nuono.next.productlisting.ProductListingTaskRecord;
import com.nuono.next.productlisting.ProductListingTaskView;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProductRebuildServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSubmitListingAfterCompletedRebuildDeleteTask() throws Exception {
        ProductManagementMapper productManagementMapper = mock(ProductManagementMapper.class);
        ProductListingMapper productListingMapper = mock(ProductListingMapper.class);
        ProductListingService productListingService = mock(ProductListingService.class);
        ProductRebuildService service = new ProductRebuildService(
                productManagementMapper,
                productListingMapper,
                productListingService,
                objectMapper
        );
        ProductPublishTaskRecord deleteTask = productRebuildDeleteTask();
        when(productManagementMapper.selectProductRebuildDeleteTasksReadyForListing(10)).thenReturn(java.util.List.of(deleteTask));
        when(productManagementMapper.claimProductRebuildDeleteTaskForListing(eq(77001L), eq(10002L), any()))
                .thenReturn(1);
        when(productListingMapper.selectLatestRealRunTaskByDraftSource(
                10002L,
                "STR245027-NAE",
                "PRODUCT_REBUILD",
                64001L
        )).thenReturn(null);
        ProductListingTaskView dryRun = new ProductListingTaskView();
        dryRun.setTaskId(88002L);
        dryRun.setStatus("validated");
        ProductListingTaskView realRun = new ProductListingTaskView();
        realRun.setTaskId(88003L);
        realRun.setStatus("submitted");
        ProductListingTaskRecord completedRealRun = new ProductListingTaskRecord();
        completedRealRun.setId(88003L);
        completedRealRun.setStatus("succeeded");
        when(productListingService.submitConfirmedRealRunFromDraft(
                any(BusinessAccessContext.class),
                any(ProductListingDraftCommand.class),
                eq("confirmed by product rebuild after delete task 77001")
        )).thenReturn(new ProductListingRealRunSubmission(null, dryRun, realRun));
        when(productListingMapper.selectTaskById(88003L, 10002L)).thenReturn(completedRealRun);

        int submitted = service.processReadyRebuildDeletes(10);

        assertEquals(1, submitted);
        ArgumentCaptor<ProductListingDraftCommand> draftCaptor =
                ArgumentCaptor.forClass(ProductListingDraftCommand.class);
        verify(productListingService).submitConfirmedRealRunFromDraft(
                any(BusinessAccessContext.class),
                draftCaptor.capture(),
                eq("confirmed by product rebuild after delete task 77001")
        );
        verify(productListingService, never()).saveDraft(any(), any());
        ProductListingDraftCommand submittedDraft = draftCaptor.getValue();
        assertEquals("PRODUCT_REBUILD", submittedDraft.getSourceType());
        assertEquals(64001L, submittedDraft.getSourceRefId());
        assertEquals(64001L, submittedDraft.getRebuildSourceProductMasterId());
        assertEquals(0, new BigDecimal("49.90").compareTo(submittedDraft.getPurchasePrice()));
        assertEquals("PRODUCT_REBUILD", submittedDraft.getSupplyEvidenceType());
        assertEquals("2026-03-12 00:00:00", submittedDraft.getInheritedListingStartedAt());
        ArgumentCaptor<String> resultCaptor = ArgumentCaptor.forClass(String.class);
        verify(productManagementMapper).updateProductRebuildDeleteTaskResult(
                eq(77001L),
                eq(10002L),
                resultCaptor.capture()
        );
        JsonNode result = objectMapper.readTree(resultCaptor.getValue());
        assertEquals("synced", result.path("status").asText());
        assertEquals("listing_succeeded", result.path("rebuild").path("status").asText());
        assertEquals(88002L, result.path("rebuild").path("listingDryRunTaskId").asLong());
        assertEquals(88003L, result.path("rebuild").path("listingRealRunTaskId").asLong());
        assertEquals("succeeded", result.path("rebuild").path("listingStatus").asText());
    }

    @Test
    void shouldSkipWhenRealRunAlreadyExistsForRebuildSource() throws Exception {
        ProductManagementMapper productManagementMapper = mock(ProductManagementMapper.class);
        ProductListingMapper productListingMapper = mock(ProductListingMapper.class);
        ProductListingService productListingService = mock(ProductListingService.class);
        ProductRebuildService service = new ProductRebuildService(
                productManagementMapper,
                productListingMapper,
                productListingService,
                objectMapper
        );
        ProductPublishTaskRecord deleteTask = productRebuildDeleteTask();
        when(productManagementMapper.selectProductRebuildDeleteTasksReadyForListing(10)).thenReturn(java.util.List.of(deleteTask));
        when(productManagementMapper.claimProductRebuildDeleteTaskForListing(eq(77001L), eq(10002L), any()))
                .thenReturn(1);
        ProductListingTaskRecord existing = new ProductListingTaskRecord();
        existing.setId(88003L);
        existing.setMode("REAL_RUN");
        existing.setStatus("submitted");
        when(productListingMapper.selectLatestRealRunTaskByDraftSource(
                10002L,
                "STR245027-NAE",
                "PRODUCT_REBUILD",
                64001L
        )).thenReturn(existing);

        int submitted = service.processReadyRebuildDeletes(10);

        assertEquals(0, submitted);
        verify(productListingService, never()).saveDraft(any(), any());
        ArgumentCaptor<String> resultCaptor = ArgumentCaptor.forClass(String.class);
        verify(productManagementMapper).updateProductRebuildDeleteTaskResult(
                eq(77001L),
                eq(10002L),
                resultCaptor.capture()
        );
        JsonNode result = objectMapper.readTree(resultCaptor.getValue());
        assertEquals("listing_already_submitted", result.path("rebuild").path("status").asText());
        assertEquals(88003L, result.path("rebuild").path("listingRealRunTaskId").asLong());
    }

    @Test
    void shouldRecordSucceededWhenExistingRebuildRealRunSucceeded() throws Exception {
        ProductManagementMapper productManagementMapper = mock(ProductManagementMapper.class);
        ProductListingMapper productListingMapper = mock(ProductListingMapper.class);
        ProductListingService productListingService = mock(ProductListingService.class);
        ProductRebuildService service = new ProductRebuildService(
                productManagementMapper,
                productListingMapper,
                productListingService,
                objectMapper
        );
        ProductPublishTaskRecord deleteTask = productRebuildDeleteTask();
        when(productManagementMapper.selectProductRebuildDeleteTasksReadyForListing(10)).thenReturn(java.util.List.of(deleteTask));
        when(productManagementMapper.claimProductRebuildDeleteTaskForListing(eq(77001L), eq(10002L), any()))
                .thenReturn(1);
        ProductListingTaskRecord existing = new ProductListingTaskRecord();
        existing.setId(88003L);
        existing.setMode("REAL_RUN");
        existing.setStatus("succeeded");
        when(productListingMapper.selectLatestRealRunTaskByDraftSource(
                10002L,
                "STR245027-NAE",
                "PRODUCT_REBUILD",
                64001L
        )).thenReturn(existing);

        int submitted = service.processReadyRebuildDeletes(10);

        assertEquals(0, submitted);
        verify(productListingService, never()).submitConfirmedRealRunFromDraft(any(), any(), any());
        ArgumentCaptor<String> resultCaptor = ArgumentCaptor.forClass(String.class);
        verify(productManagementMapper).updateProductRebuildDeleteTaskResult(
                eq(77001L),
                eq(10002L),
                resultCaptor.capture()
        );
        JsonNode result = objectMapper.readTree(resultCaptor.getValue());
        assertEquals("listing_succeeded", result.path("rebuild").path("status").asText());
        assertEquals("succeeded", result.path("rebuild").path("listingStatus").asText());
        assertEquals(88003L, result.path("rebuild").path("listingRealRunTaskId").asLong());
    }

    @Test
    void shouldReconcileSucceededExistingRealRunForSubmittedRebuildListing() throws Exception {
        ProductManagementMapper productManagementMapper = mock(ProductManagementMapper.class);
        ProductListingMapper productListingMapper = mock(ProductListingMapper.class);
        ProductListingService productListingService = mock(ProductListingService.class);
        ProductRebuildService service = new ProductRebuildService(
                productManagementMapper,
                productListingMapper,
                productListingService,
                objectMapper
        );
        ProductPublishTaskRecord deleteTask = productRebuildDeleteTask();
        deleteTask.setResultJson(objectMapper.writeValueAsString(java.util.Map.of(
                "status", "synced",
                "rebuild", java.util.Map.of(
                        "status", "listing_submitted",
                        "listingRealRunTaskId", 88003L,
                        "listingStatus", "submitted"
                )
        )));
        when(productManagementMapper.selectProductRebuildDeleteTasksPendingListingReconciliation(10))
                .thenReturn(java.util.List.of(deleteTask));
        ProductListingTaskRecord existing = new ProductListingTaskRecord();
        existing.setId(88003L);
        existing.setMode("REAL_RUN");
        existing.setStatus("succeeded");
        when(productListingMapper.selectLatestRealRunTaskByDraftSource(
                10002L,
                "STR245027-NAE",
                "PRODUCT_REBUILD",
                64001L
        )).thenReturn(existing);

        int reconciled = service.reconcileSubmittedRebuildListings(10);

        assertEquals(1, reconciled);
        verify(productListingService).replaySuccessfulProjectionBackfill(any(BusinessAccessContext.class), eq(88003L));
        ArgumentCaptor<String> resultCaptor = ArgumentCaptor.forClass(String.class);
        verify(productManagementMapper).updateProductRebuildDeleteTaskResult(
                eq(77001L),
                eq(10002L),
                resultCaptor.capture()
        );
        JsonNode result = objectMapper.readTree(resultCaptor.getValue());
        assertEquals("listing_succeeded", result.path("rebuild").path("status").asText());
        assertEquals("succeeded", result.path("rebuild").path("listingStatus").asText());
        assertEquals(88003L, result.path("rebuild").path("listingRealRunTaskId").asLong());
    }

    private ProductPublishTaskRecord productRebuildDeleteTask() throws Exception {
        ProductListingDraftCommand draft = new ProductListingDraftCommand();
        draft.setStoreCode("STR245027-NAE");
        draft.setSourceType("PRODUCT_REBUILD");
        draft.setSourceRefId(64001L);
        draft.setRebuildSourceProductMasterId(64001L);
        draft.setPsku("MILKYWAYA17");
        draft.setPrice(new BigDecimal("49.90"));
        draft.setInheritedListingStartedAt("2026-03-12 00:00:00");
        draft.setInheritedListingStartedSource("pv");
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(77001L);
        task.setOwnerUserId(10002L);
        task.setProductMasterId(64001L);
        task.setStoreCode("STR245027-NAE");
        task.setTaskType("product-delete");
        task.setStatus("synced");
        task.setRequestJson(objectMapper.writeValueAsString(java.util.Map.of(
                "action", "product-delete",
                "rebuildAction", "product-rebuild",
                "rebuildSourceProductMasterId", 64001L,
                "rebuildListingDraft", draft
        )));
        task.setResultJson("{\"status\":\"synced\",\"stage\":\"synced\"}");
        return task;
    }
}
