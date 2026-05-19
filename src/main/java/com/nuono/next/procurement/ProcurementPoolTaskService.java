package com.nuono.next.procurement;

import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.ITEM_WAITING_SEND;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.JOIN_BUYER_MANUAL;

import com.nuono.next.infrastructure.mapper.ProcurementMapper;
import com.nuono.next.infrastructure.mapper.ProcurementRequirementConfirmationMapper;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.CandidateRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.PoolItemLockRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.PoolLockRow;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local-db")
class ProcurementPoolTaskService {

    private final ProcurementRequirementConfirmationMapper mapper;
    private final ProcurementMapper procurementMapper;
    private final ProcurementAutoInquiryEventLogger autoInquiryEventLogger;
    private final ProcurementAutoInquiryExecutor autoInquiryExecutor;
    private final ProcurementCandidatePoolAuditService auditService;
    private final ProcurementCandidatePoolIdGenerator idGenerator;

    @Value("${nuono.procurement.auto-inquiry.dispatch-enabled:false}")
    private boolean autoInquiryDispatchEnabled;

    ProcurementPoolTaskService(
            ProcurementRequirementConfirmationMapper mapper,
            ProcurementMapper procurementMapper,
            ProcurementAutoInquiryEventLogger autoInquiryEventLogger,
            ProcurementAutoInquiryExecutor autoInquiryExecutor,
            ProcurementCandidatePoolAuditService auditService,
            ProcurementCandidatePoolIdGenerator idGenerator
    ) {
        this.mapper = mapper;
        this.procurementMapper = procurementMapper;
        this.autoInquiryEventLogger = autoInquiryEventLogger;
        this.autoInquiryExecutor = autoInquiryExecutor;
        this.auditService = auditService;
        this.idGenerator = idGenerator;
    }

    ProcurementPoolCreatedTask addPoolItemAndTask(
            PoolLockRow pool,
            CandidateRow candidate,
            int poolRankNo,
            String joinSource,
            ProcurementCandidatePoolWriteContext context,
            String reason
    ) {
        Long poolItemId = idGenerator.nextPoolItemId();
        mapper.insertCandidatePoolItem(
                poolItemId,
                pool.getPoolId(),
                pool.getOwnerUserId(),
                pool.getDemandItemId(),
                candidate.getCandidateId(),
                candidate.getRankNo(),
                poolRankNo,
                ITEM_WAITING_SEND,
                joinSource,
                context.operatorUserId
        );
        Long taskId = idGenerator.nextAutoInquiryTaskId();
        mapper.insertPoolAutoInquiryTask(
                taskId,
                pool.getOwnerUserId(),
                pool.getDemandItemId(),
                candidate.getCandidateId(),
                pool.getPoolId(),
                poolItemId,
                ProcurementAutoInquiryLifecycle.PLATFORM_1688,
                ProcurementAutoInquiryLifecycle.STATUS_PENDING,
                ProcurementAutoInquiryLifecycle.STAGE_CREATED,
                0,
                3,
                "待选池成员已创建自动询价任务，等待执行器接手。",
                context.operatorUserId
        );
        mapper.updatePoolItemInquiryTask(poolItemId, taskId, context.operatorUserId);
        autoInquiryEventLogger.append(
                taskId,
                ProcurementAutoInquiryLifecycle.EVENT_TASK_CREATED,
                null,
                ProcurementAutoInquiryLifecycle.STATUS_PENDING,
                ProcurementAutoInquiryLifecycle.STAGE_CREATED,
                "待选池成员已创建自动询价任务。",
                "poolId=" + pool.getPoolId() + "；poolItemId=" + poolItemId + "；candidateId=" + candidate.getCandidateId(),
                context.operatorUserId
        );
        PoolItemLockRow itemLock = toItemLock(poolItemId, pool, candidate, poolRankNo, taskId);
        auditService.appendLog(
                pool,
                itemLock,
                "INQUIRY_TASK_CREATED",
                context,
                null,
                ProcurementAutoInquiryLifecycle.STATUS_PENDING,
                null,
                reason,
                Map.of("taskId", taskId)
        );
        return new ProcurementPoolCreatedTask(poolItemId, taskId, itemLock);
    }

    void dispatchTasks(List<ProcurementPoolCreatedTask> createdTasks, ProcurementCandidatePoolWriteContext context) {
        if (!autoInquiryDispatchEnabled) {
            return;
        }
        for (ProcurementPoolCreatedTask createdTask : createdTasks) {
            try {
                autoInquiryExecutor.execute(createdTask.taskId, context.operatorUserId);
            } catch (RuntimeException exception) {
                procurementMapper.markAutoInquiryTaskHandoff(
                        createdTask.taskId,
                        "DISPATCH_FAILED",
                        "POOL_AUTO_INQUIRY_DISPATCH_FAILED",
                        exception.getMessage(),
                        "DISPATCH_EXCEPTION",
                        "自动询价调度失败，等待人工复核。",
                        context.operatorUserId
                );
                autoInquiryEventLogger.append(
                        createdTask.taskId,
                        ProcurementAutoInquiryLifecycle.EVENT_TASK_HANDOFF,
                        ProcurementAutoInquiryLifecycle.STATUS_PENDING,
                        ProcurementAutoInquiryLifecycle.STATUS_HANDOFF,
                        "DISPATCH_FAILED",
                        "自动询价调度失败，等待人工复核。",
                        exception.getMessage(),
                        context.operatorUserId
                );
            } finally {
                mapper.syncPoolItemFromAutoInquiryTask(createdTask.poolItemId, createdTask.taskId, context.operatorUserId);
            }
        }
    }

    boolean shouldDispatch(Boolean triggerInquiry) {
        return !Boolean.FALSE.equals(triggerInquiry) && autoInquiryDispatchEnabled;
    }

    private PoolItemLockRow toItemLock(
            Long poolItemId,
            PoolLockRow pool,
            CandidateRow candidate,
            int poolRankNo,
            Long taskId
    ) {
        PoolItemLockRow item = new PoolItemLockRow();
        item.setPoolItemId(poolItemId);
        item.setPoolId(pool.getPoolId());
        item.setOwnerUserId(pool.getOwnerUserId());
        item.setDemandItemId(pool.getDemandItemId());
        item.setCandidateId(candidate.getCandidateId());
        item.setSourceRankNo(candidate.getRankNo());
        item.setPoolRankNo(poolRankNo);
        item.setStatus(ITEM_WAITING_SEND);
        item.setJoinSource(JOIN_BUYER_MANUAL);
        item.setInquiryTaskId(taskId);
        return item;
    }
}
