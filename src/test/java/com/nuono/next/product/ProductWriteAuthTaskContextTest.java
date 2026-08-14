package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.nuono.next.noon.NoonAccountSessionAttentionPort;
import org.junit.jupiter.api.Test;

class ProductWriteAuthTaskContextTest {
    private final NoonAccountSessionAttentionPort attention = mock(NoonAccountSessionAttentionPort.class);
    private final ProductWriteAuthRecovery recovery = new ProductWriteAuthRecovery(
            attention
    );

    @Test
    void queuedLegacyTaskNowOnlyRequestsOneManualLogin() {
        ProductPublishTaskRecord task = task(77001L, "product-delete");

        assertTrue(recovery.enqueueTask(task, "retry_scheduled", false).isEmpty());
        verify(attention).requireManualLogin();
    }

    @Test
    void scopedDeleteFailureRequestsManualLoginAndNeverMarksWriteAsSafeToReplay() {
        ProductPublishTaskRecord task = task(77002L, "product-delete");

        try (ProductWriteAuthRecovery.TaskScope ignored = recovery.openTaskScope(task)) {
            ProductWriteAuthRequiredException exception = recovery.suspendIfAuthFailure(
                    307L, "PRJ-1", "STR108065-NAE",
                    new IllegalStateException("auth_required: map HTTP 307"), false);
            assertTrue(exception.getRecoveryId() == null);
            assertTrue(exception.getMessage().contains("不会自动发送验证码、重试或继续"));
        }

        verify(attention).requireManualLogin();
    }

    @Test
    void imagePublishScopeRetainsReadbackRequirementWithoutCreatingARecoveryTask() {
        ProductWriteAuthRequiredException exception;
        try (ProductWriteAuthRecovery.TaskScope ignored = recovery.openTaskScope(
                307L, "PRJ-1", "STR108065-NAE", "AE",
                "PRODUCT_IMAGE_SUITE", 9901L, "attempt-01", true)) {
            exception = recovery.suspendIfAuthFailure(
                    307L, "PRJ-1", "STR108065-NAE",
                    new IllegalStateException("auth_required: WHOAMI HTTP 401"), false);
        }

        assertTrue(exception.isWriteMayHaveOccurred());
        assertTrue(exception.getMessage().contains("先回读 Noon 结果"));
        verify(attention).requireManualLogin();
    }

    private ProductPublishTaskRecord task(long id, String taskType) {
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(id);
        task.setOwnerUserId(307L);
        task.setProjectCode("PRJ-1");
        task.setStoreCode("STR108065-NAE");
        task.setCurrentSiteCode("AE");
        task.setTaskType(taskType);
        return task;
    }
}
