package com.nuono.next.procurement;

import com.nuono.next.infrastructure.mapper.ProcurementMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local-db")
public class ProcurementAutoInquiryEventLogger {

    private final ProcurementMapper procurementMapper;

    public ProcurementAutoInquiryEventLogger(ProcurementMapper procurementMapper) {
        this.procurementMapper = procurementMapper;
    }

    public void append(
            Long taskId,
            String eventType,
            String statusBefore,
            String statusAfter,
            String executionStage,
            String eventMessage,
            String eventPayload,
            Long operatorUserId
    ) {
        if (taskId == null) {
            return;
        }
        synchronized (this) {
            Long eventId = procurementMapper.nextAutoInquiryEventId();
            procurementMapper.insertAutoInquiryEvent(
                    eventId,
                    taskId,
                    eventType,
                    statusBefore,
                    statusAfter,
                    executionStage,
                    eventMessage,
                    eventPayload,
                    operatorUserId
            );
        }
    }
}
