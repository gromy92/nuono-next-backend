package com.nuono.next.product.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.product.ProductPublishTaskRecord;
import com.nuono.next.product.ProductPublishTaskView;
import org.junit.jupiter.api.Test;

class ProductPublishRetryIdempotencyTest {

    @Test
    void concurrentRetryAfterSameTaskWasQueuedShouldReturnCurrentTask() {
        ProductManagementMapper mapper = mock(ProductManagementMapper.class);
        ProductPublishCommandService service = new ProductPublishCommandService(mapper);
        ProductPublishTaskRecord queued = new ProductPublishTaskRecord();
        queued.setId(77001L);
        queued.setOwnerUserId(10002L);
        queued.setProductMasterId(64001L);
        queued.setTaskType("product-delete");
        queued.setStatus("product_delete_queued");
        when(mapper.selectProductPublishTaskById(77001L)).thenReturn(queued);
        when(mapper.selectActiveProductPublishTask(64001L)).thenReturn(queued);

        ProductPublishTaskView view = service.retryTask(
                77001L,
                10002L,
                null,
                ignored -> java.util.List.of("delete")
        );

        assertEquals(77001L, view.getTaskId());
        verify(mapper, never()).retryProductPublishTask(77001L, 10002L);
    }
}
