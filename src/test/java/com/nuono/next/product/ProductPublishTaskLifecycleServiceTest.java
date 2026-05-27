package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;

class ProductPublishTaskLifecycleServiceTest {

    private final ProductManagementMapper mapper = Mockito.mock(ProductManagementMapper.class);
    private final ProductPublishTaskLifecycleService service = new ProductPublishTaskLifecycleService(mapper);

    @Test
    void loadTaskRecoversStaleRunningAndVerifiesOwner() {
        ProductPublishTaskRecord task = publishTask(64001L, 10002L, 52001L, "failed");
        when(mapper.recoverStaleRunningProductPublishTasks(1, 0L)).thenReturn(2);
        when(mapper.selectProductPublishTaskById(64001L)).thenReturn(task);

        ProductPublishTaskRecord loaded = service.loadTask(64001L, 10002L, 0);

        assertSame(task, loaded);
        verify(mapper).recoverStaleRunningProductPublishTasks(1, 0L);
    }

    @Test
    void retryTaskRejectsWhenAnotherActiveTaskExists() {
        ProductPublishTaskRecord task = publishTask(64001L, 10002L, 52001L, "failed");
        ProductPublishTaskRecord activeTask = publishTask(64002L, 10002L, 52001L, "queued");
        when(mapper.selectProductPublishTaskById(64001L)).thenReturn(task);
        when(mapper.selectActiveProductPublishTask(52001L)).thenReturn(activeTask);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.retryTask(64001L, 10002L, 15)
        );

        assertEquals("当前商品已有发布任务正在执行，请等待完成后再重试。", exception.getMessage());
        verify(mapper, never()).retryProductPublishTask(eq(64001L), eq(10002L));
    }

    @Test
    void cancelTaskOnlyAllowsQueuedRows() {
        ProductPublishTaskRecord task = publishTask(64001L, 10002L, 52001L, "queued");
        when(mapper.selectProductPublishTaskById(64001L)).thenReturn(task);
        when(mapper.cancelQueuedProductPublishTask(64001L, 10002L)).thenReturn(0);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.cancelTask(64001L, 10002L, 15)
        );

        assertEquals("只有尚未执行的发布任务可以取消。", exception.getMessage());
    }

    @Test
    void createQueuedTaskReturnsDuplicateTaskWhenIdempotencyCollides() {
        ProductPublishTaskRecord duplicateTask = publishTask(64002L, 10002L, 52001L, "queued");
        ProductPublishTaskLifecycleService.CreateQueuedPublishTaskRequest request =
                new ProductPublishTaskLifecycleService.CreateQueuedPublishTaskRequest();
        request.setOwnerUserId(10002L);
        request.setProductMasterId(52001L);
        request.setStoreCode("STR245027-NAE");
        request.setProjectCode("PRJ245027");
        request.setSkuParent("ZTEST001");
        request.setPartnerSku("PARTNER-001");
        request.setPskuCode("PSKU-001");
        request.setCurrentSiteCode("STR245027-NAE");
        request.setBaselineJson("{\"baseline\":true}");
        request.setDraftJson("{\"draft\":true}");
        request.setDraftHash("draft-hash");
        request.setChangedDomainsJson("[\"content\"]");
        request.setRequestJson("{\"action\":\"publish-current\"}");
        when(mapper.nextProductPublishTaskId()).thenReturn(64001L);
        Mockito.doThrow(new DuplicateKeyException("duplicate"))
                .when(mapper)
                .insertProductPublishTask(Mockito.any(ProductPublishTaskRecord.class));
        when(mapper.selectProductPublishTaskByIdempotency("52001:draft-hash:STR245027-NAE"))
                .thenReturn(duplicateTask);

        ProductPublishTaskLifecycleService.CreateQueuedPublishTaskResult result = service.createQueuedTask(request);

        assertSame(duplicateTask, result.getTask());
        assertTrue(result.isDuplicate());
        assertEquals("52001:draft-hash:STR245027-NAE", result.getIdempotencyKey());
    }

    @Test
    void claimTaskUsesVersionFenceAndMutatesClaimState() {
        ProductPublishTaskRecord task = publishTask(64001L, 10002L, 52001L, "queued");
        task.setVersionNo(3);
        when(mapper.tryStartProductPublishTask(64001L, "queued", 3, "claim-token", 10002L)).thenReturn(1);

        assertTrue(service.claimTask(task, "claim-token"));

        assertEquals("running", task.getStatus());
        assertEquals("claim-token", task.getLockedBy());
        assertEquals(Integer.valueOf(4), task.getVersionNo());
    }

    @Test
    void updateStatusRequiresClaimFenceAndReleasesTerminalLock() {
        ProductPublishTaskRecord task = publishTask(64001L, 10002L, 52001L, "running");
        task.setLockedBy("claim-token");
        task.setVersionNo(7);
        when(mapper.updateProductPublishTaskStatus(
                eq(64001L),
                eq("running"),
                eq("claim-token"),
                eq(7),
                eq("synced"),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                Mockito.any(LocalDateTime.class),
                Mockito.any(LocalDateTime.class),
                eq(null),
                eq(true),
                eq(10002L)
        )).thenReturn(1);

        service.updateTaskStatus(task, ProductPublishTaskLifecycleService.StatusUpdate.to("synced"));

        assertEquals("synced", task.getStatus());
        assertNull(task.getLockedBy());
        assertNull(task.getLockedAt());
        assertEquals(Integer.valueOf(8), task.getVersionNo());
    }

    @Test
    void updateStatusThrowsWhenClaimFenceIsLost() {
        ProductPublishTaskRecord task = publishTask(64001L, 10002L, 52001L, "running");
        task.setLockedBy("claim-token");
        task.setVersionNo(7);

        assertThrows(
                ProductPublishTaskFenceLostException.class,
                () -> service.updateTaskStatus(task, ProductPublishTaskLifecycleService.StatusUpdate.to("synced"))
        );
    }

    @Test
    void createQueuedTaskBuildsExpectedTaskRecord() {
        ProductPublishTaskLifecycleService.CreateQueuedPublishTaskRequest request =
                new ProductPublishTaskLifecycleService.CreateQueuedPublishTaskRequest();
        request.setOwnerUserId(10002L);
        request.setProductMasterId(52001L);
        request.setStoreCode("STR245027-NAE");
        request.setProjectCode("PRJ245027");
        request.setSkuParent("ZTEST001");
        request.setPartnerSku("PARTNER-001");
        request.setPskuCode("PSKU-001");
        request.setCurrentSiteCode("STR245027-NAE");
        request.setBaselineJson("{\"baseline\":true}");
        request.setDraftJson("{\"draft\":true}");
        request.setDraftHash("draft-hash");
        request.setChangedDomainsJson("[\"content\"]");
        request.setRequestJson("{\"action\":\"publish-current\"}");
        when(mapper.nextProductPublishTaskId()).thenReturn(64001L);
        when(mapper.insertProductPublishTask(Mockito.any(ProductPublishTaskRecord.class))).thenReturn(1);
        ArgumentCaptor<ProductPublishTaskRecord> captor = ArgumentCaptor.forClass(ProductPublishTaskRecord.class);

        ProductPublishTaskLifecycleService.CreateQueuedPublishTaskResult result = service.createQueuedTask(request);

        verify(mapper).insertProductPublishTask(captor.capture());
        ProductPublishTaskRecord inserted = captor.getValue();
        assertEquals(Long.valueOf(64001L), inserted.getId());
        assertEquals("queued", inserted.getStatus());
        assertEquals("publish-current", inserted.getTaskType());
        assertEquals("product:52001", inserted.getActiveLockKey());
        assertEquals("52001:draft-hash:STR245027-NAE", inserted.getIdempotencyKey());
        assertEquals(Integer.valueOf(0), inserted.getRetryCount());
        assertEquals(Integer.valueOf(0), inserted.getVerifyAttemptCount());
        assertEquals(Integer.valueOf(3), inserted.getMaxRetryCount());
        assertEquals(Integer.valueOf(1), inserted.getVersionNo());
        assertSame(inserted, result.getTask());
    }

    private ProductPublishTaskRecord publishTask(Long id, Long ownerUserId, Long productMasterId, String status) {
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(id);
        task.setOwnerUserId(ownerUserId);
        task.setProductMasterId(productMasterId);
        task.setStatus(status);
        task.setStoreCode("STR245027-NAE");
        task.setSkuParent("ZTEST001");
        return task;
    }
}
