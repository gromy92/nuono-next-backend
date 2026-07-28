package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.nuono.next.productlisting.ProductListingWriteAuthRecovery;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class ProductRebuildAuthRecoveryTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void authPendingRealRunIsPersistedAndNeverCreatesAnotherRealRun() throws Exception {
        ProductManagementMapper managementMapper = mock(ProductManagementMapper.class);
        ProductListingMapper listingMapper = mock(ProductListingMapper.class);
        ProductListingService listingService = mock(ProductListingService.class);
        ProductRebuildService service =
                new ProductRebuildService(managementMapper, listingMapper, listingService, objectMapper);
        ProductPublishTaskRecord deleteTask = rebuildDeleteTask(
                ProductListingWriteAuthRecovery.FAILURE_CODE);
        when(managementMapper.selectProductRebuildDeleteTasksPendingListingReconciliation(10))
                .thenReturn(List.of(deleteTask));
        ProductListingTaskRecord realRun = new ProductListingTaskRecord();
        realRun.setId(88003L);
        realRun.setStatus("written_verify_failed");
        realRun.setFailureCode(ProductListingWriteAuthRecovery.FAILURE_CODE);
        realRun.setFailureMessage("Noon Project 授权恢复中");
        realRun.setNoonResultJson(objectMapper.writeValueAsString(Map.of(
                "failureCode", ProductListingWriteAuthRecovery.FAILURE_CODE,
                "recoveryId", 991L,
                "writeMayHaveOccurred", true
        )));
        when(listingMapper.selectLatestRealRunTaskByDraftSource(
                10002L, "STR245027-NAE", "PRODUCT_REBUILD", 64001L
        )).thenReturn(realRun);

        assertEquals(1, service.reconcileSubmittedRebuildListings(10));

        verify(listingService, never()).submitConfirmedRealRunFromDraft(any(), any(), any());
        verify(managementMapper, never()).claimProductRebuildDeleteTaskForListing(
                any(), any(), any(), any());
        ArgumentCaptor<String> resultCaptor = ArgumentCaptor.forClass(String.class);
        verify(managementMapper).updateProductRebuildDeleteTaskResult(
                eq(77001L), eq(10002L), resultCaptor.capture());
        JsonNode rebuild = objectMapper.readTree(resultCaptor.getValue()).path("rebuild");
        assertEquals(ProductListingWriteAuthRecovery.FAILURE_CODE, rebuild.path("status").asText());
        assertEquals(ProductListingWriteAuthRecovery.FAILURE_CODE, rebuild.path("failureCode").asText());
        assertEquals(991L, rebuild.path("recoveryId").asLong());
        assertTrue(rebuild.path("writeMayHaveOccurred").asBoolean());
    }

    @Test
    void supersededAuthRecoveryShouldBecomeTerminalRebuildState() throws Exception {
        ProductManagementMapper managementMapper = mock(ProductManagementMapper.class);
        ProductListingMapper listingMapper = mock(ProductListingMapper.class);
        ProductListingService listingService = mock(ProductListingService.class);
        ProductRebuildService service =
                new ProductRebuildService(
                        managementMapper, listingMapper, listingService, objectMapper);
        ProductPublishTaskRecord deleteTask = rebuildDeleteTask(
                ProductListingWriteAuthRecovery.FAILURE_CODE);
        when(managementMapper.selectProductRebuildDeleteTasksPendingListingReconciliation(10))
                .thenReturn(List.of(deleteTask));
        ProductListingTaskRecord superseded = new ProductListingTaskRecord();
        superseded.setId(88003L);
        superseded.setStatus("failed");
        superseded.setFailureCode("listing_auth_recovery_superseded");
        superseded.setFailureMessage("权威上架任务已替代当前任务。");
        when(listingMapper.selectLatestRealRunTaskByDraftSource(
                10002L, "STR245027-NAE", "PRODUCT_REBUILD", 64001L
        )).thenReturn(superseded);

        assertEquals(1, service.reconcileSubmittedRebuildListings(10));

        ArgumentCaptor<String> resultCaptor = ArgumentCaptor.forClass(String.class);
        verify(managementMapper).updateProductRebuildDeleteTaskResult(
                eq(77001L), eq(10002L), resultCaptor.capture());
        JsonNode rebuild = objectMapper.readTree(
                resultCaptor.getValue()).path("rebuild");
        assertEquals("listing_superseded", rebuild.path("status").asText());
        assertEquals(
                "listing_auth_recovery_superseded",
                rebuild.path("failureCode").asText()
        );
        verify(listingService, never()).submitConfirmedRealRunFromDraft(
                any(), any(), any());
    }

    @Test
    void successfulListingIgnoresHistoricalAuthRecoveryStep() throws Exception {
        ProductRebuildListingState state = new ProductRebuildListingState(objectMapper);
        String noonResultJson = objectMapper.writeValueAsString(Map.of(
                "success", true,
                "steps", List.of(Map.of(
                        "stepKey", "create_product",
                        "status", "failed",
                        "failureCode", ProductListingWriteAuthRecovery.FAILURE_CODE,
                        "recoveryId", 991L,
                        "writeMayHaveOccurred", true
                ), Map.of(
                        "stepKey", "verify_noon_readback",
                        "status", "succeeded"
                ))
        ));

        JsonNode rebuild = objectMapper.valueToTree(state.create(
                "listing_succeeded",
                88001L,
                88002L,
                88003L,
                "succeeded",
                null,
                null,
                noonResultJson
        ));

        assertEquals("listing_succeeded", rebuild.path("status").asText());
        assertFalse(rebuild.has("recoveryId"));
        assertFalse(rebuild.has("writeMayHaveOccurred"));
    }

    @Test
    void expiredClaimChecksExistingBeforeAndAfterCasThenSubmitsOnlyOnce() throws Exception {
        ProductManagementMapper managementMapper = mock(ProductManagementMapper.class);
        ProductListingMapper listingMapper = mock(ProductListingMapper.class);
        ProductListingService listingService = mock(ProductListingService.class);
        ProductRebuildService service =
                new ProductRebuildService(managementMapper, listingMapper, listingService, objectMapper);
        ProductPublishTaskRecord deleteTask = rebuildDeleteTask("listing_running");
        when(managementMapper.selectProductRebuildDeleteTasksReadyForListing(any(), eq(10)))
                .thenReturn(List.of(deleteTask));
        when(managementMapper.claimProductRebuildDeleteTaskForListing(
                eq(77001L), eq(10002L), any(), any()
        )).thenReturn(1);
        when(managementMapper.renewProductRebuildListingClaim(
                eq(77001L), eq(10002L), any(), any()
        )).thenReturn(1);
        when(managementMapper.completeProductRebuildListingClaim(
                eq(77001L), eq(10002L), any(), any()
        )).thenReturn(1);
        when(listingMapper.selectLatestRealRunTaskByDraftSource(
                10002L, "STR245027-NAE", "PRODUCT_REBUILD", 64001L
        )).thenReturn(null, null);
        ProductListingTaskView dryRun = new ProductListingTaskView();
        dryRun.setTaskId(88002L);
        dryRun.setStatus("validated");
        ProductListingTaskView submitted = new ProductListingTaskView();
        submitted.setTaskId(88003L);
        submitted.setStatus("submitted");
        when(listingService.submitConfirmedRealRunFromDraft(
                any(BusinessAccessContext.class), any(ProductListingDraftCommand.class), any()
        )).thenReturn(new ProductListingRealRunSubmission(null, dryRun, submitted));
        ProductListingTaskRecord stored = new ProductListingTaskRecord();
        stored.setId(88003L);
        stored.setStatus("submitted");
        when(listingMapper.selectTaskById(88003L, 10002L)).thenReturn(stored);

        assertEquals(1, service.processReadyRebuildDeletes(10));

        InOrder order = inOrder(listingMapper, managementMapper, listingService);
        order.verify(listingMapper).selectLatestRealRunTaskByDraftSource(
                10002L, "STR245027-NAE", "PRODUCT_REBUILD", 64001L);
        ArgumentCaptor<String> claimJson = ArgumentCaptor.forClass(String.class);
        order.verify(managementMapper).claimProductRebuildDeleteTaskForListing(
                eq(77001L), eq(10002L), any(), claimJson.capture());
        order.verify(listingMapper).selectLatestRealRunTaskByDraftSource(
                10002L, "STR245027-NAE", "PRODUCT_REBUILD", 64001L);
        ArgumentCaptor<String> renewedToken = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> renewedJson = ArgumentCaptor.forClass(String.class);
        order.verify(managementMapper).renewProductRebuildListingClaim(
                eq(77001L), eq(10002L), renewedToken.capture(), renewedJson.capture());
        order.verify(listingService).submitConfirmedRealRunFromDraft(any(), any(), any());
        ArgumentCaptor<String> completedToken = ArgumentCaptor.forClass(String.class);
        order.verify(managementMapper).completeProductRebuildListingClaim(
                eq(77001L), eq(10002L), completedToken.capture(), any());
        JsonNode claim = objectMapper.readTree(claimJson.getValue()).path("rebuild");
        JsonNode renewed = objectMapper.readTree(renewedJson.getValue()).path("rebuild");
        assertEquals("listing_running", claim.path("status").asText());
        assertFalse(claim.path("claimExpiresAt").asText().isBlank());
        assertFalse(claim.path("claimToken").asText().isBlank());
        assertEquals(claim.path("claimToken").asText(), renewedToken.getValue());
        assertEquals(claim.path("claimToken").asText(), renewed.path("claimToken").asText());
        assertEquals(claim.path("claimToken").asText(), completedToken.getValue());
        verify(listingService, times(1)).submitConfirmedRealRunFromDraft(any(), any(), any());
    }

    private ProductPublishTaskRecord rebuildDeleteTask(String rebuildStatus) throws Exception {
        ProductListingDraftCommand draft = new ProductListingDraftCommand();
        draft.setStoreCode("STR245027-NAE");
        draft.setSourceType("PRODUCT_REBUILD");
        draft.setSourceRefId(64001L);
        draft.setRebuildSourceProductMasterId(64001L);
        draft.setPsku("MILKYWAYA17");
        draft.setPrice(new BigDecimal("49.90"));
        draft.setInheritedListingStartedAt("2026-03-12 00:00:00");
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(77001L);
        task.setOwnerUserId(10002L);
        task.setProductMasterId(64001L);
        task.setStoreCode("STR245027-NAE");
        task.setTaskType("product-delete");
        task.setStatus("synced");
        task.setRequestJson(objectMapper.writeValueAsString(Map.of(
                "rebuildAction", "product-rebuild",
                "rebuildListingDraft", draft
        )));
        task.setResultJson(objectMapper.writeValueAsString(Map.of(
                "status", "synced",
                "rebuild", Map.of("status", rebuildStatus)
        )));
        return task;
    }
}
