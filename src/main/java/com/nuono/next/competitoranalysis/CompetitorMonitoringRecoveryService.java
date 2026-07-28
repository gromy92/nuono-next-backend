package com.nuono.next.competitoranalysis;

import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CompetitorMonitoringRecoveryService {
    private final OperationalTaskService operationalTaskService;

    CompetitorMonitoringRecoveryService(OperationalTaskService operationalTaskService) {
        this.operationalTaskService = operationalTaskService;
    }

    @Transactional
    public OperationalTask replaceStale(
            OperationalTask staleTask,
            LocalDateTime staleBefore,
            String errorCode,
            String staleMessage,
            String queuedMessage
    ) {
        if (staleTask == null || !operationalTaskService.failStaleRunning(
                staleTask.getId(),
                staleBefore,
                errorCode,
                staleMessage
        )) {
            return null;
        }
        return operationalTaskService.queue(
                staleTask.getTaskType(),
                staleTask.getNaturalKey(),
                OperationalTaskPayload.builder()
                        .ownerUserId(staleTask.getOwnerUserId())
                        .storeCode(staleTask.getStoreCode())
                        .siteCode(staleTask.getSiteCode())
                        .payloadJson(staleTask.getPayloadJson())
                        .message(queuedMessage)
                        .build()
        );
    }
}
