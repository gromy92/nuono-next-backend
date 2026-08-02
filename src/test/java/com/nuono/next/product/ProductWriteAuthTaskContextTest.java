package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.noonauth.NoonAuthResumePolicy;
import com.nuono.next.noonauth.NoonAuthWaitQueue;
import com.nuono.next.noonauth.NoonAuthWaitRequest;
import com.nuono.next.noonpull.NoonPullProjectAuthGate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProductWriteAuthTaskContextTest {
    private final NoonAuthWaitQueue queue = mock(NoonAuthWaitQueue.class);
    private final ProductWriteAuthRecovery recovery = new ProductWriteAuthRecovery(
            queue,
            mock(NoonPullProjectAuthGate.class)
    );

    @Test
    void safeDeleteTaskJoinsTheCommonQueueWithItsCheckpoint() {
        ProductPublishTaskRecord task = task(77001L, "product-delete");
        NoonAuthWaitRequest request = NoonAuthWaitRequest.task(
                307L, "PRJ-1", "STR108065-NAE", "AE", "PRODUCT_DELETE", 77001L,
                "retry_scheduled", NoonAuthResumePolicy.AUTO_RESUME);
        when(queue.enqueue(request)).thenReturn(Optional.of(993L));

        assertEquals(Optional.of(993L), recovery.enqueueTask(task, "retry_scheduled", false));
        verify(queue).enqueue(request);
    }

    @Test
    void scopedDeleteFailureNeverFallsBackToSourceLessBindingRecovery() {
        ProductPublishTaskRecord task = task(77002L, "product-delete");
        NoonAuthWaitRequest request = NoonAuthWaitRequest.task(
                307L, "PRJ-1", "STR108065-NAE", "AE", "PRODUCT_DELETE", 77002L,
                "PROVIDER_CALL", NoonAuthResumePolicy.AUTO_RESUME);
        when(queue.enqueue(request)).thenReturn(Optional.of(994L));

        try (ProductWriteAuthRecovery.TaskScope ignored = recovery.openTaskScope(task)) {
            ProductWriteAuthRequiredException exception = recovery.suspendIfAuthFailure(
                    307L, "PRJ-1", "STR108065-NAE",
                    new IllegalStateException("auth_required: map HTTP 307"), false);
            assertEquals(994L, exception.getRecoveryId());
        }

        verify(queue).enqueue(request);
        verify(queue, never()).enqueue(NoonAuthWaitRequest.binding(307L, "PRJ-1", "STR108065-NAE"));
    }

    @Test
    void imagePublishScopeAlwaysQueuesTheExactAttemptForReadback() {
        NoonAuthWaitRequest request = NoonAuthWaitRequest.task(
                307L, "PRJ-1", "STR108065-NAE", "AE", "PRODUCT_IMAGE_SUITE", 9901L,
                "attempt-01", NoonAuthResumePolicy.READBACK_REQUIRED);
        when(queue.enqueue(request)).thenReturn(Optional.of(995L));

        ProductWriteAuthRequiredException exception;
        try (ProductWriteAuthRecovery.TaskScope ignored = recovery.openTaskScope(
                307L, "PRJ-1", "STR108065-NAE", "AE",
                "PRODUCT_IMAGE_SUITE", 9901L, "attempt-01", true)) {
            exception = recovery.suspendIfAuthFailure(
                    307L, "PRJ-1", "STR108065-NAE",
                    new IllegalStateException("auth_required: WHOAMI HTTP 401"), false);
        }

        assertNotNull(exception);
        assertTrue(exception.isWriteMayHaveOccurred());
        assertEquals(995L, exception.getRecoveryId());
        verify(queue).enqueue(request);
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
