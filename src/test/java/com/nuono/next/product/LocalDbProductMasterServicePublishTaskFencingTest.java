package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class LocalDbProductMasterServicePublishTaskFencingTest {

    private final ProductManagementMapper productManagementMapper = mock(ProductManagementMapper.class);
    private final LocalDbProductMasterService service = new LocalDbProductMasterService(
            productManagementMapper,
            null,
            null,
            new ObjectMapper(),
            null,
            null,
            null,
            null,
            null,
            null,
            null
    );

    @Test
    void shouldRejectPublishTaskStatusUpdateWhenFenceNoLongerMatches() throws Exception {
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(1001L);
        task.setOwnerUserId(10002L);
        task.setStatus("running");
        task.setLockedBy("claim-token-1");
        task.setVersionNo(7);

        assertThrows(IllegalStateException.class, () -> invokeUpdatePublishTaskStatus(task, "synced"));
        assertEquals("running", task.getStatus());
        assertEquals("claim-token-1", task.getLockedBy());
        assertEquals(Integer.valueOf(7), task.getVersionNo());
    }

    private void invokeUpdatePublishTaskStatus(ProductPublishTaskRecord task, String status) throws Exception {
        Method method = LocalDbProductMasterService.class.getDeclaredMethod(
                "updatePublishTaskStatus",
                ProductPublishTaskRecord.class,
                String.class,
                String.class,
                String.class,
                String.class,
                LocalDateTime.class,
                LocalDateTime.class,
                Integer.class
        );
        method.setAccessible(true);
        try {
            method.invoke(service, task, status, null, null, null, null, null, null);
        } catch (InvocationTargetException exception) {
            Throwable targetException = exception.getTargetException();
            if (targetException instanceof RuntimeException) {
                throw (RuntimeException) targetException;
            }
            throw exception;
        }
    }
}
