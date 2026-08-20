package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.noonauth.NoonAuthWaitQueue;
import com.nuono.next.noonpull.NoonPullProjectAuthGate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProductWriteAuthTaskContextTest {
    private final NoonAuthWaitQueue recoveryQueue = mock(NoonAuthWaitQueue.class);
    private final ProductWriteAuthRecovery recovery = new ProductWriteAuthRecovery(
            recoveryQueue,
            mock(NoonPullProjectAuthGate.class)
    );

    @Test
    void queuedTaskUsesProjectRecoveryQueue() {
        when(recoveryQueue.enqueue(any())).thenReturn(Optional.of(991L));
        ProductPublishTaskRecord task = task(77001L, "product-delete");

        assertTrue(recovery.enqueueTask(task, "retry_scheduled", false).orElseThrow() == 991L);
        verify(recoveryQueue).enqueue(any());
    }

    @Test
    void scopedDeleteFailureQueuesSafeAutomaticReplay() {
        when(recoveryQueue.enqueue(any())).thenReturn(Optional.of(991L));
        ProductPublishTaskRecord task = task(77002L, "product-delete");

        try (ProductWriteAuthRecovery.TaskScope ignored = recovery.openTaskScope(task)) {
            ProductWriteAuthRequiredException exception = recovery.suspendIfAuthFailure(
                    307L, "PRJ-1", "STR108065-NAE",
                    new IllegalStateException("auth_required: map HTTP 307"), false);
            assertTrue(exception.getRecoveryId() == 991L);
            assertTrue(exception.getMessage().contains("安全检查点自动继续"));
        }

        verify(recoveryQueue).enqueue(any());
    }

    @Test
    void imagePublishScopeRetainsReadbackRequirementWithoutCreatingARecoveryTask() {
        when(recoveryQueue.enqueue(any())).thenReturn(Optional.of(991L));
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
        verify(recoveryQueue).enqueue(any());
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
