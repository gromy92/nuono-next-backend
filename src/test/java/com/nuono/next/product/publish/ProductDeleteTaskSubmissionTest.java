package com.nuono.next.product.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.product.ProductPublishTaskRecord;
import com.nuono.next.product.ProductPublishTaskView;
import com.nuono.next.product.publish.ProductDeleteTaskSubmissionResult.Disposition;
import com.nuono.next.product.publish.ProductPublishCommandService.ProductPublishTaskCreateCommand;
import com.nuono.next.product.publish.ProductPublishCommandService.ProductPublishTaskCreateResult;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class ProductDeleteTaskSubmissionTest {
    private final ProductManagementMapper mapper = mock(ProductManagementMapper.class);
    private final ProductPublishCommandService commandService = mock(ProductPublishCommandService.class);
    private final ProductDeleteTaskSubmission submission = new ProductDeleteTaskSubmission(mapper, commandService);

    @Test
    void firstDeleteShouldCreateOneStableIntent() {
        ProductPublishTaskCreateCommand command = command();
        ProductPublishTaskRecord created = task(77001L, "product_delete_queued");
        when(commandService.createProductDeleteTask(command)).thenReturn(ProductPublishTaskCreateResult.created(created));

        ProductDeleteTaskSubmissionResult result = submission.submit(command);

        assertEquals(Disposition.CREATED, result.getDisposition());
        assertSame(created, result.getTask());
        assertEquals("delete:64001:after:0", command.getIdempotencyKey());
    }

    @Test
    void repeatedDeleteWhileRunningShouldReturnSameTask() {
        ProductPublishTaskRecord running = task(77001L, "product_delete_running");
        when(mapper.selectLatestProductPublishTask(64001L)).thenReturn(running);
        when(commandService.isActiveStatus("product_delete_running")).thenReturn(true);

        ProductDeleteTaskSubmissionResult result = submission.submit(command());

        assertEquals(Disposition.EXISTING, result.getDisposition());
        assertSame(running, result.getTask());
        verify(commandService, never()).createProductDeleteTask(any());
        verify(mapper, never()).retryProductPublishTask(any(), any());
    }

    @Test
    void pendingDeleteShouldResumeTheSameTask() {
        ProductPublishTaskRecord pending = task(77001L, "pending_manual_check");
        ProductPublishTaskRecord queued = task(77001L, "product_delete_queued");
        when(mapper.selectLatestProductPublishTask(64001L)).thenReturn(pending);
        when(mapper.selectProductPublishTaskById(77001L)).thenReturn(queued);
        when(commandService.retryTask(eq(77001L), eq(10002L), eq(null), any()))
                .thenReturn(new ProductPublishTaskView());

        ProductDeleteTaskSubmissionResult result = submission.submit(command());

        assertEquals(Disposition.RESUMED, result.getDisposition());
        assertSame(queued, result.getTask());
        verify(commandService).retryTask(eq(77001L), eq(10002L), eq(null), any());
        verify(commandService, never()).createProductDeleteTask(any());
    }

    @Test
    void unsafePendingDeleteShouldNotCreateAnotherTask() {
        ProductPublishTaskRecord pending = task(77001L, "pending_manual_check");
        when(mapper.selectLatestProductPublishTask(64001L)).thenReturn(pending);
        when(commandService.retryTask(eq(77001L), eq(10002L), eq(null), any()))
                .thenThrow(new IllegalStateException("缺少可证明安全的恢复检查点"));

        assertThrows(IllegalStateException.class, () -> submission.submit(command()));

        verify(commandService, never()).createProductDeleteTask(any());
    }

    @Test
    void activeLockRaceWithAnotherTaskShouldFailInsteadOfMislabelingItAsDelete() {
        ProductPublishTaskRecord publishTask = task(77002L, "queued");
        publishTask.setTaskType("publish-current");
        publishTask.setRequestJson("{\"action\":\"publish-current\"}");
        when(commandService.createProductDeleteTask(any()))
                .thenReturn(ProductPublishTaskCreateResult.duplicate(publishTask));

        assertThrows(IllegalStateException.class, () -> submission.submit(command()));

        verify(commandService, never()).retryTask(any(), any(), any(), any());
    }

    @Test
    void mapperLookupShouldSelectLatestTaskByProductId() throws Exception {
        Method method = ProductManagementMapper.class.getMethod("selectLatestProductPublishTask", Long.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value()).replaceAll("\\s+", " ");

        assertEquals(true, sql.contains("product_master_id = #{productMasterId}"));
        assertEquals(true, sql.contains("ORDER BY id DESC LIMIT 1"));
        assertEquals(
                "com.nuono.next.infrastructure.mapper.ProductManagementMapper.ProductPublishTaskRecordMap",
                method.getAnnotation(ResultMap.class).value()[0]
        );
    }

    private ProductPublishTaskCreateCommand command() {
        ProductPublishTaskCreateCommand command = new ProductPublishTaskCreateCommand();
        command.setOwnerUserId(10002L);
        command.setProductMasterId(64001L);
        command.setStoreCode("STR245027-NAE");
        command.setPartnerSku("MILKYWAYA17");
        return command;
    }

    private ProductPublishTaskRecord task(Long id, String status) {
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(id);
        task.setOwnerUserId(10002L);
        task.setProductMasterId(64001L);
        task.setTaskType("product-delete");
        task.setStatus(status);
        task.setRequestJson("{\"action\":\"product-delete\"}");
        return task;
    }
}
