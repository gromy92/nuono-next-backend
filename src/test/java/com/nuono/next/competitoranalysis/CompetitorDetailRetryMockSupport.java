package com.nuono.next.competitoranalysis;

import com.nuono.next.system.task.OperationalTask;
import org.mockito.stubbing.Answer;

final class CompetitorDetailRetryMockSupport {
    private CompetitorDetailRetryMockSupport() {
    }

    static Answer<CompetitorProductDetailRefreshResult> checkpointing(
            InMemoryOperationalTaskRepository taskRepository,
            CompetitorProductDetailRefreshResult result
    ) {
        return invocation -> {
            Long taskId = invocation.getArgument(3);
            CompetitorDetailRetrySession session = invocation.getArgument(5);
            for (CompetitorProductDetailTarget target : result.getSucceededTargets()) {
                session.beginRequest(target);
                String payloadJson = session.payloadAfterSuccess(target);
                OperationalTask task = taskRepository.selectById(taskId);
                task.setPayloadJson(payloadJson);
                taskRepository.update(task);
                session.successCommitted(payloadJson);
            }
            for (CompetitorProductDetailFailure failure : result.getFailures()) {
                session.beginRequest(failure.getTarget());
                session.recordFailure(
                        failure.getTarget(),
                        failure.getErrorCode(),
                        failure.getErrorMessage(),
                        true
                );
            }
            return result;
        };
    }
}
