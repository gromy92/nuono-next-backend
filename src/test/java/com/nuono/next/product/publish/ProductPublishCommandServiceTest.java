package com.nuono.next.product.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.product.ProductPublishTaskRecord;
import com.nuono.next.product.ProductPublishTaskView;
import com.nuono.next.product.noon.NoonProductError;
import com.nuono.next.product.noon.NoonProductErrorCode;
import com.nuono.next.product.noon.NoonProductException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

class ProductPublishCommandServiceTest {

    private final ProductManagementMapper productManagementMapper = mock(ProductManagementMapper.class);
    private final ProductPublishCommandService service = new ProductPublishCommandService(productManagementMapper);

    @Test
    void shouldRejectStatusUpdateWhenFenceNoLongerMatches() {
        ProductPublishTaskRecord task = runningTask();

        assertThrows(
                ProductPublishTaskFenceLostException.class,
                () -> service.updateStatus(task, "synced", null, null, null, null, null, null)
        );
        assertEquals("running", task.getStatus());
        assertEquals("claim-token-1", task.getLockedBy());
        assertEquals(Integer.valueOf(7), task.getVersionNo());
    }

    @Test
    void shouldMutateTaskAfterFencedStatusUpdateSucceeds() {
        ProductPublishTaskRecord task = runningTask();
        when(productManagementMapper.updateProductPublishTaskStatus(
                any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), anyBoolean(), any()
        )).thenReturn(1);

        service.updateStatus(task, "synced", null, null, "{\"status\":\"synced\"}", null, null, 2);

        assertEquals("synced", task.getStatus());
        assertEquals("{\"status\":\"synced\"}", task.getResultJson());
        assertEquals(Integer.valueOf(8), task.getVersionNo());
        assertEquals(Integer.valueOf(2), task.getVerifyAttemptCount());
        assertNotNull(task.getFinishedAt());
        assertEquals(null, task.getLockedBy());
    }

    @Test
    void shouldReturnDuplicateTaskWhenCreateHitsIdempotency() {
        ProductPublishTaskRecord duplicate = new ProductPublishTaskRecord();
        duplicate.setId(64002L);
        duplicate.setStatus("queued");
        when(productManagementMapper.nextProductPublishTaskId()).thenReturn(64001L);
        doThrow(new DuplicateKeyException("duplicate"))
                .when(productManagementMapper).insertProductPublishTask(any(ProductPublishTaskRecord.class));
        when(productManagementMapper.selectProductPublishTaskByIdempotency("501:hash:SA")).thenReturn(duplicate);

        ProductPublishCommandService.ProductPublishTaskCreateCommand command =
                new ProductPublishCommandService.ProductPublishTaskCreateCommand();
        command.setOwnerUserId(10002L);
        command.setProductMasterId(501L);
        command.setStoreCode("STR245027-NAE");
        command.setCurrentSiteCode("SA");
        command.setDraftHash("hash");
        command.setIdempotencyKey("501:hash:SA");

        ProductPublishCommandService.ProductPublishTaskCreateResult result =
                service.createPublishCurrentTask(command);

        assertTrue(result.isDuplicate());
        assertEquals(64002L, result.getTask().getId());
    }

    @Test
    void shouldRenderPendingManualCheckMessageWithChangedDomainLabels() {
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(64003L);
        task.setStatus("pending_manual_check");

        ProductPublishTaskView view = service.buildTaskView(
                task,
                false,
                null,
                ignored -> java.util.List.of("content", "site_offer")
        );

        assertTrue(view.getMessage().contains("图文内容"));
        assertTrue(view.getMessage().contains("当前站点经营"));
        assertFalse(view.getChangedDomains().isEmpty());
    }

    @Test
    void shouldTreatNoonHttpFiveHundredWriteFailureAsRetryable() {
        assertTrue(service.isRetryableNoonWriteFailure(
                new IllegalStateException("请求 Noon 失败：HTTP 500 {\"error\":\"Sorry, something wrong on our side\"}")
        ));
    }

    @Test
    void shouldTreatNoonHttpRequestTimeoutWriteFailureAsRetryable() {
        assertTrue(service.isRetryableNoonWriteFailure(
                new IllegalStateException("请求 Noon 失败：HTTP 408 request timeout")
        ));
    }

    @Test
    void shouldTreatNestedRetryableNoonWriteFailureAsRetryable() {
        NoonProductException rateLimited = new NoonProductException(
                new NoonProductError(
                        NoonProductErrorCode.NOON_RATE_LIMITED,
                        true,
                        "Noon 请求被限流，请稍后重试。"
                ),
                new IllegalStateException("rate limited")
        );

        assertTrue(service.isRetryableNoonWriteFailure(
                new IllegalStateException("发布写入失败", rateLimited)
        ));
    }

    @Test
    void shouldNotTreatValidationFailureAsRetryableNoonWriteFailure() {
        assertFalse(service.isRetryableNoonWriteFailure(
                new IllegalStateException("SA / STR353172-NSA 缺少有效售价。")
        ));
    }

    @Test
    void shouldScheduleTransparentWriteRetryWhenRetryBudgetRemains() {
        ProductPublishTaskRecord task = runningTask();
        task.setRetryCount(0);
        task.setMaxRetryCount(3);
        when(productManagementMapper.updateProductPublishTaskStatus(
                any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), anyBoolean(), any()
        )).thenReturn(1);

        boolean scheduled = service.scheduleNoonWriteRetryOrManualCheck(
                task,
                "noon_write_failed",
                "Noon 发布返回错误：HTTP 500",
                "{\"status\":\"write_retry_scheduled\"}"
        );

        assertTrue(scheduled);
        assertEquals("write_retry_scheduled", task.getStatus());
        assertEquals("noon_write_failed", task.getErrorCode());
        assertEquals(Integer.valueOf(1), task.getRetryCount());
        assertNotNull(task.getNextRunAt());
        assertNull(task.getFinishedAt());
        assertNull(task.getLockedBy());
        assertNull(task.getLockedAt());
    }

    @Test
    void shouldMoveRetryableWriteFailureToManualCheckWhenRetryBudgetIsExhausted() {
        ProductPublishTaskRecord task = runningTask();
        task.setRetryCount(3);
        task.setMaxRetryCount(3);
        when(productManagementMapper.updateProductPublishTaskStatus(
                any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), anyBoolean(), any()
        )).thenReturn(1);

        boolean scheduled = service.scheduleNoonWriteRetryOrManualCheck(
                task,
                "noon_write_failed",
                "Noon 发布返回错误：HTTP 500",
                "{\"status\":\"pending_manual_check\"}"
        );

        assertFalse(scheduled);
        assertEquals("pending_manual_check", task.getStatus());
        assertEquals("noon_write_retry_exhausted", task.getErrorCode());
        assertNotNull(task.getFinishedAt());
        assertNull(task.getLockedBy());
    }

    @Test
    void shouldRecoverStaleAndLegacyRetryableFailedWriteTasksTogether() {
        ReflectionTestUtils.setField(service, "staleRunningMinutes", 15);
        ReflectionTestUtils.setField(service, "legacyRetryableFailedRecoveryHours", 24);
        when(productManagementMapper.recoverStaleRunningProductPublishTasks(15, 0L)).thenReturn(1);
        when(productManagementMapper.recoverRetryableFailedNoonWriteProductPublishTasks(24, 0L)).thenReturn(2);

        assertEquals(3, service.recoverStaleRunningTasks());
        verify(productManagementMapper).recoverRetryableFailedNoonWriteProductPublishTasks(24, 0L);
    }

    private ProductPublishTaskRecord runningTask() {
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(1001L);
        task.setOwnerUserId(10002L);
        task.setStatus("running");
        task.setLockedBy("claim-token-1");
        task.setLockedAt(LocalDateTime.now());
        task.setVersionNo(7);
        return task;
    }
}
