package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductListingAuthContinuationServiceTest {

    @Test
    void sharedWorkerAdvancesTheExactUnknownCreateTaskToReadOnlyLookup() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ProductListingMapper mapper = mock(ProductListingMapper.class);
        ProductListingWorkflowService workflowService = mock(ProductListingWorkflowService.class);
        BusinessAccessContext context = BusinessAccessContext.builder()
                .sessionUserId(90001L)
                .businessOwnerUserId(10002L)
                .build();
        ProductListingTaskRecord task = recoveredTask(objectMapper, 991L);
        when(mapper.selectTaskByIdForUpdate(88003L, 10002L)).thenReturn(task);
        when(workflowService.loadWorkflow(context, 77001L)).thenReturn(
                workflow(
                        ProductListingWorkflowView.NextAction.WAIT_FOR_AUTHORIZATION,
                        ProductListingWorkflowView.WriteCertainty.UNKNOWN,
                        88003L
                ),
                workflow(
                        ProductListingWorkflowView.NextAction.CHECK_CREATE_RESULT,
                        ProductListingWorkflowView.WriteCertainty.UNKNOWN,
                        88003L
                )
        );
        when(mapper.updateTaskResult(task)).thenReturn(1);
        ProductListingAuthContinuationService service =
                new ProductListingAuthContinuationService(mapper, workflowService, objectMapper);

        ProductListingWorkflowView result = service.advance(
                context,
                88003L,
                10002L,
                task.getNoonResultJson(),
                991L,
                ProductListingAuthContinuationService.ResumeAction.CHECK_CREATE_RESULT
        );

        assertEquals(ProductListingWorkflowView.NextAction.CHECK_CREATE_RESULT, result.getNextAction());
        assertEquals("noon_create_outcome_unknown", task.getFailureCode());
        verify(mapper).updateTaskResult(task);
    }

    @Test
    void staleRecoveryEvidenceCannotAdvanceAnyListingTask() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ProductListingMapper mapper = mock(ProductListingMapper.class);
        ProductListingWorkflowService workflowService = mock(ProductListingWorkflowService.class);
        ProductListingTaskRecord task = recoveredTask(objectMapper, 991L);
        when(mapper.selectTaskByIdForUpdate(88003L, 10002L)).thenReturn(task);
        ProductListingAuthContinuationService service =
                new ProductListingAuthContinuationService(mapper, workflowService, objectMapper);

        assertThrows(
                ProductListingAuthContinuationException.class,
                () -> service.advance(
                        BusinessAccessContext.builder()
                                .sessionUserId(90001L)
                                .businessOwnerUserId(10002L)
                                .build(),
                        88003L,
                        10002L,
                        "{\"recoveryId\":992}",
                        992L,
                        ProductListingAuthContinuationService.ResumeAction.CHECK_CREATE_RESULT
                )
        );

        verify(mapper, never()).updateTaskResult(any());
        verify(workflowService, never()).loadWorkflow(any(), any());
    }

    private ProductListingTaskRecord recoveredTask(ObjectMapper objectMapper, Long recoveryId)
            throws Exception {
        ProductListingNoonWriteResult noonResult = ProductListingNoonWriteResult.failed(
                "authorization",
                ProductListingWriteAuthRecovery.FAILURE_CODE,
                "waiting",
                List.of()
        );
        noonResult.setRecoveryId(recoveryId);
        noonResult.setWriteMayHaveOccurred(true);
        ProductListingTaskRecord task = new ProductListingTaskRecord();
        task.setId(88003L);
        task.setDraftId(77001L);
        task.setOwnerUserId(10002L);
        task.setMode("REAL_RUN");
        task.setStatus("written_verify_failed");
        task.setFailureCode(ProductListingWriteAuthRecovery.RECOVERED_CODE);
        task.setNoonResultJson(objectMapper.writeValueAsString(noonResult));
        return task;
    }

    private ProductListingWorkflowView workflow(
            ProductListingWorkflowView.NextAction action,
            ProductListingWorkflowView.WriteCertainty certainty,
            Long taskId
    ) {
        ProductListingWorkflowView workflow = new ProductListingWorkflowView();
        workflow.setNextAction(action);
        workflow.setWriteCertainty(certainty);
        ProductListingTaskView task = new ProductListingTaskView();
        task.setTaskId(taskId);
        workflow.setRealRunTask(task);
        return workflow;
    }
}
