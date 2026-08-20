package com.nuono.next.product.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.noonauth.NoonAuthWaitQueue;
import com.nuono.next.noonpull.NoonPullProjectAuthGate;
import com.nuono.next.product.ProductPublishTaskRecord;
import com.nuono.next.product.ProductPublishTaskView;
import com.nuono.next.product.ProductWriteAuthRecovery;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProductPublishAuthCommandServiceTest {
    private final ProductManagementMapper mapper = mock(ProductManagementMapper.class);
    private final ProductPublishCommandService service = new ProductPublishCommandService(mapper);

    @Test
    void deleteTaskShouldUseFiniteAutomaticRetryBudget() {
        when(mapper.nextProductPublishTaskId()).thenReturn(77001L);
        ProductPublishCommandService.ProductPublishTaskCreateCommand command =
                new ProductPublishCommandService.ProductPublishTaskCreateCommand();
        command.setOwnerUserId(10002L);
        command.setProductMasterId(64001L);
        command.setStoreCode("STR245027-NAE");
        command.setSkuParent("MILKYWAYA17");
        command.setPskuCode("PSKU-1");
        command.setCurrentSiteCode("AE");
        command.setDraftHash("delete-hash");
        command.setChangedDomainsJson("[\"delete\"]");
        command.setIdempotencyKey("product-delete:64001:delete-hash");

        ProductPublishTaskRecord task = service.createProductDeleteTask(command).getTask();

        assertEquals(77001L, task.getId());
        assertEquals(ProductPublishCommandService.PRODUCT_DELETE_STATUS_QUEUED, task.getStatus());
        assertEquals(48, task.getMaxRetryCount());
        ProductPublishTaskView view =
                service.buildTaskView(task, false, null, ignored -> java.util.List.of("delete"));
        assertTrue(view.getMessage().contains("删除"));
    }

    @Test
    void authRecoveryMessageShouldNotPromiseAutomaticReplay() {
        ProductPublishTaskRecord task = authTask(false);
        ProductPublishTaskView view = service.buildTaskView(
                task, false, null, ignored -> java.util.List.of("content", "site_offer")
        );

        assertTrue(view.getMessage().contains("授权恢复"));
        assertTrue(view.getMessage().contains("不会自动"));
        assertTrue(view.getMessage().contains("人工"));
    }

    @Test
    void activeSharedRecoveryShouldPreventManualRetry() {
        ProductPublishTaskRecord task = authTask(false);
        when(mapper.selectProductPublishTaskById(1001L)).thenReturn(task);
        NoonAuthWaitQueue recoveryQueue = mock(NoonAuthWaitQueue.class);
        NoonPullProjectAuthGate authGate = mock(NoonPullProjectAuthGate.class);
        when(authGate.isBlocked(10002L, task.getProjectCode())).thenReturn(true);
        service.setProductWriteAuthRecovery(new ProductWriteAuthRecovery(
                recoveryQueue, authGate));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.retryTask(1001L, 10002L, null, ignored -> java.util.List.of())
        );

        assertTrue(failure.getMessage().contains("授权恢复中"));
        verify(mapper, never()).retryProductPublishTask(1001L, 10002L);
    }

    @Test
    void priorWriteShouldPreventOriginalPublishTaskReplay() {
        ProductPublishTaskRecord task = authTask(true);
        when(mapper.selectProductPublishTaskById(1001L)).thenReturn(task);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.retryTask(1001L, 10002L, null, ignored -> java.util.List.of())
        );

        assertTrue(failure.getMessage().contains("不能直接重放原任务"));
        assertTrue(failure.getMessage().contains("从 Noon 同步"));
        verify(mapper, never()).retryProductPublishTask(1001L, 10002L);
    }

    @Test
    void unknownProviderWriteShouldPreventOriginalPublishTaskReplay() {
        ProductPublishTaskRecord task = runningTask();
        task.setStatus("pending_manual_check");
        task.setErrorCode("product_write_outcome_unknown");
        task.setResultJson("{\"writeMayHaveOccurred\":false}");
        when(mapper.selectProductPublishTaskById(1001L)).thenReturn(task);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.retryTask(1001L, 10002L, null, ignored -> java.util.List.of())
        );

        assertTrue(failure.getMessage().contains("不能直接重放原任务"));
        assertTrue(failure.getMessage().contains("从 Noon 同步"));
        verify(mapper, never()).retryProductPublishTask(1001L, 10002L);
    }

    @Test
    void partialGroupWriteShouldPreventOriginalPublishTaskReplayWithoutLegacyFlag() {
        ProductPublishTaskRecord task = runningTask();
        task.setStatus("pending_manual_check");
        task.setErrorCode("group_partial_write_unknown");
        task.setResultJson("{\"status\":\"pending_manual_check\"}");
        when(mapper.selectProductPublishTaskById(1001L)).thenReturn(task);

        assertThrows(
                IllegalStateException.class,
                () -> service.retryTask(1001L, 10002L, null, ignored -> java.util.List.of())
        );

        verify(mapper, never()).retryProductPublishTask(1001L, 10002L);
    }

    @Test
    void nonAuthWriteCheckpointShouldPreventOriginalPublishTaskReplay() {
        ProductPublishTaskRecord task = runningTask();
        task.setStatus("pending_manual_check");
        task.setErrorCode("publish_manual_check");
        task.setResultJson("{\"writeMayHaveOccurred\":true}");
        when(mapper.selectProductPublishTaskById(1001L)).thenReturn(task);

        assertThrows(
                IllegalStateException.class,
                () -> service.retryTask(1001L, 10002L, null, ignored -> java.util.List.of())
        );

        verify(mapper, never()).retryProductPublishTask(1001L, 10002L);
    }

    @Test
    void semanticallyMalformedWriteCheckpointShouldFailClosed() {
        ProductPublishTaskRecord task = runningTask();
        task.setStatus("pending_manual_check");
        task.setErrorCode("publish_manual_check");
        task.setResultJson("{\"writeMayHaveOccurred\":\"garbled\"}");
        when(mapper.selectProductPublishTaskById(1001L)).thenReturn(task);

        assertThrows(
                IllegalStateException.class,
                () -> service.retryTask(1001L, 10002L, null, ignored -> java.util.List.of())
        );

        verify(mapper, never()).retryProductPublishTask(1001L, 10002L);
    }

    @Test
    void legacyDeleteAuthTaskShouldKeepDeleteSemanticsForManualRetry() {
        ProductPublishTaskRecord task = authTask(true);
        task.setTaskType(null);
        task.setRequestJson("{\"action\":\"product-delete\"}");
        task.setResultJson("{\"stage\":\"unmap_submitted\",\"writeMayHaveOccurred\":true}");
        when(mapper.selectProductPublishTaskById(1001L)).thenReturn(task);
        when(mapper.retryProductPublishTask(1001L, 10002L)).thenReturn(1);

        assertDoesNotThrow(
                () -> service.retryTask(1001L, 10002L, null, ignored -> java.util.List.of())
        );

        verify(mapper).retryProductPublishTask(1001L, 10002L);
        ProductPublishTaskView view =
                service.buildTaskView(task, false, null, ignored -> java.util.List.of());
        assertTrue(view.getMessage().contains("删除"));
        assertTrue(view.getRetryAllowed());
    }

    @Test
    void deleteTaskWithoutSafeStageShouldFailClosed() {
        ProductPublishTaskRecord task = authTask(true);
        task.setTaskType(ProductPublishCommandService.TASK_TYPE_PRODUCT_DELETE);
        task.setErrorCode("product_delete_result_unknown");
        task.setResultJson("{\"stage\":\"garbled\",\"writeMayHaveOccurred\":true}");
        when(mapper.selectProductPublishTaskById(1001L)).thenReturn(task);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.retryTask(1001L, 10002L, null, ignored -> java.util.List.of())
        );

        assertTrue(failure.getMessage().contains("安全的恢复检查点"));
        verify(mapper, never()).retryProductPublishTask(1001L, 10002L);
    }

    @Test
    void preWriteDeleteCheckpointShouldRemainManuallyRetryable() {
        ProductPublishTaskRecord task = authTask(false);
        task.setTaskType(ProductPublishCommandService.TASK_TYPE_PRODUCT_DELETE);
        task.setErrorCode("product_delete_retry_exhausted");
        task.setResultJson("{\"stage\":\"pre_delete_captured\"}");
        when(mapper.selectProductPublishTaskById(1001L)).thenReturn(task);
        when(mapper.retryProductPublishTask(1001L, 10002L)).thenReturn(1);

        assertDoesNotThrow(() -> service.retryTask(1001L, 10002L, null, ignored -> java.util.List.of()));

        verify(mapper).retryProductPublishTask(1001L, 10002L);
    }

    @Test
    void allHistoricalDeleteMarkersShouldUseDeleteStateMachine() {
        ProductPublishTaskRecord requestMarker = runningTask();
        requestMarker.setTaskType(null);
        requestMarker.setRequestJson("{\"action\":\"PRODUCT-DELETE\"}");
        ProductPublishTaskRecord idempotencyMarker = runningTask();
        idempotencyMarker.setTaskType(null);
        idempotencyMarker.setIdempotencyKey("DELETE:50001:PSKU-1:123");
        ProductPublishTaskRecord domainMarker = runningTask();
        domainMarker.setTaskType(null);
        domainMarker.setChangedDomainsJson("[\"DELETE\"]");
        ProductPublishTaskRecord draftMarker = runningTask();
        draftMarker.setTaskType(null);
        draftMarker.setDraftJson("{\"mode\":\"PRODUCT-DELETE-TASK\"}");
        ProductPublishTaskRecord baselineMarker = runningTask();
        baselineMarker.setTaskType(null);
        baselineMarker.setBaselineJson("{\"mode\":\"product-delete-task\"}");

        assertTrue(service.isProductDeleteTask(requestMarker));
        assertTrue(service.isProductDeleteTask(idempotencyMarker));
        assertTrue(service.isProductDeleteTask(domainMarker));
        assertTrue(service.isProductDeleteTask(draftMarker));
        assertTrue(service.isProductDeleteTask(baselineMarker));
    }

    @Test
    void historicalUnlimitedDeleteShouldStopAtConfiguredAutomaticBudget() {
        ReflectionTestUtils.setField(service, "transientAutomaticMaxRetryCount", 3);
        ProductPublishTaskRecord task = runningTask();
        task.setTaskType(ProductPublishCommandService.TASK_TYPE_PRODUCT_DELETE);
        task.setRetryCount(3);
        task.setMaxRetryCount(Integer.MAX_VALUE);
        when(mapper.updateProductPublishTaskStatus(
                any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), anyBoolean(), any()
        )).thenReturn(1);

        boolean scheduled = service.scheduleProductDeleteRetryOrManualCheck(
                task, "product_delete_failed", "retry",
                "product_delete_retry_exhausted", "manual",
                "{\"status\":\"pending_manual_check\"}"
        );

        assertFalse(scheduled);
        assertEquals("pending_manual_check", task.getStatus());
        assertEquals("product_delete_retry_exhausted", task.getErrorCode());
        assertNull(task.getNextRunAt());
    }

    @Test
    void runnableSelectionShouldQuarantineExhaustedDeletesBeforeReturningTasks() {
        when(mapper.stopExhaustedProductDeleteRetries(48, 0L)).thenReturn(8);
        when(mapper.selectRunnableProductPublishTasks(2)).thenReturn(java.util.List.of());

        assertTrue(service.selectRunnableTasks(2).isEmpty());

        verify(mapper).stopExhaustedProductDeleteRetries(48, 0L);
        verify(mapper).selectRunnableProductPublishTasks(2);
    }

    private ProductPublishTaskRecord authTask(boolean writeMayHaveOccurred) {
        ProductPublishTaskRecord task = runningTask();
        task.setStatus("pending_manual_check");
        task.setErrorCode(ProductPublishCommandService.ERROR_CODE_NOON_AUTH_RECOVERY_PENDING);
        task.setProjectCode("PRJ-1");
        task.setStoreCode("STR1-NSA");
        task.setResultJson("{\"writeMayHaveOccurred\":" + writeMayHaveOccurred + "}");
        return task;
    }

    private ProductPublishTaskRecord runningTask() {
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(1001L);
        task.setOwnerUserId(10002L);
        task.setProductMasterId(501L);
        task.setTaskType(ProductPublishCommandService.TASK_TYPE_PUBLISH_CURRENT);
        task.setStatus("running");
        task.setLockedBy("claim-token-1");
        task.setVersionNo(7);
        task.setRetryCount(0);
        task.setMaxRetryCount(3);
        return task;
    }
}
