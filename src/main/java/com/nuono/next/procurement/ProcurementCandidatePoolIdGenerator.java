package com.nuono.next.procurement;

import com.nuono.next.infrastructure.mapper.ProcurementMapper;
import com.nuono.next.infrastructure.mapper.ProcurementRequirementConfirmationMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local-db")
class ProcurementCandidatePoolIdGenerator {

    private final ProcurementRequirementConfirmationMapper mapper;
    private final ProcurementMapper procurementMapper;

    ProcurementCandidatePoolIdGenerator(
            ProcurementRequirementConfirmationMapper mapper,
            ProcurementMapper procurementMapper
    ) {
        this.mapper = mapper;
        this.procurementMapper = procurementMapper;
    }

    synchronized Long nextPoolId() {
        return mapper.nextCandidatePoolId();
    }

    synchronized Long nextPoolItemId() {
        return mapper.nextCandidatePoolItemId();
    }

    synchronized Long nextSnapshotId() {
        return mapper.nextCandidatePoolSnapshotId();
    }

    synchronized Long nextFinalCandidateId() {
        return mapper.nextFinalCandidateId();
    }

    synchronized Long nextLogId() {
        return mapper.nextPoolOperationLogId();
    }

    synchronized Long nextAutoInquiryTaskId() {
        Long taskId = mapper.nextAutoInquiryTaskId();
        while (procurementMapper.selectAutoInquiryTask(taskId) != null) {
            taskId = taskId + 1;
        }
        return taskId;
    }
}
