package com.nuono.next.procurement;

import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.CANDIDATE_SOURCE_LIMIT;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.ITEM_NO_REPLY_HANDOFF;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.ITEM_PARTIAL_REPLY;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.ITEM_REMOVED_TERMINATED;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.ITEM_REPLIED;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.ITEM_REPLY_PARSE_FAILED;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.ITEM_WAITING_SEND;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.JOIN_BUYER_MANUAL;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.JOIN_SYSTEM_AUTO;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.MAX_POOL_SIZE;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.PICK_BACKUP;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.PICK_PRIMARY;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.STATUS_FINAL_TWO_CONFIRMED;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.STATUS_POOL_EMPTY_REQUIRES_ACTION;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.STATUS_POOL_INQUIRY_FINISHED;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.STATUS_POOL_INQUIRY_RUNNING;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.STATUS_POOL_PARTIAL_HANDOFF;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.STATUS_SUMMARY_READY;

import com.nuono.next.infrastructure.mapper.ProcurementMapper;
import com.nuono.next.infrastructure.mapper.ProcurementRequirementConfirmationMapper;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.AddPoolCandidateCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.AdvancePoolItemFollowUpCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.ConfirmFinalCandidatesCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.FinishPoolInquiryCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.GenerateSummaryCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.InitializePoolCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.MarkPoolItemExceptionCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.OperatorCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.RecordPoolItemReplyCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.RemovePoolItemCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.CandidateRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.DemandDetailRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.FinalCandidateRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.PoolItemLockRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.PoolLockRow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbProcurementCandidatePoolService {

    private final ProcurementRequirementConfirmationMapper mapper;
    private final ProcurementMapper procurementMapper;
    private final LocalDbProcurementRequirementConfirmationService readService;
    private final ProcurementAutoInquiryEventLogger autoInquiryEventLogger;
    private final ProcurementCandidatePoolAuditService auditService;
    private final ProcurementCandidatePoolIdGenerator idGenerator;
    private final ProcurementCandidatePoolPermissionGuard permissionGuard;
    private final ProcurementPoolFinalCandidateService finalCandidateService;
    private final ProcurementPoolInquiryService inquiryService;
    private final ProcurementPoolTaskService taskService;

    public LocalDbProcurementCandidatePoolService(
            ProcurementRequirementConfirmationMapper mapper,
            ProcurementMapper procurementMapper,
            LocalDbProcurementRequirementConfirmationService readService,
            ProcurementAutoInquiryEventLogger autoInquiryEventLogger,
            ProcurementCandidatePoolAuditService auditService,
            ProcurementCandidatePoolIdGenerator idGenerator,
            ProcurementCandidatePoolPermissionGuard permissionGuard,
            ProcurementPoolFinalCandidateService finalCandidateService,
            ProcurementPoolInquiryService inquiryService,
            ProcurementPoolTaskService taskService
    ) {
        this.mapper = mapper;
        this.procurementMapper = procurementMapper;
        this.readService = readService;
        this.autoInquiryEventLogger = autoInquiryEventLogger;
        this.auditService = auditService;
        this.idGenerator = idGenerator;
        this.permissionGuard = permissionGuard;
        this.finalCandidateService = finalCandidateService;
        this.inquiryService = inquiryService;
        this.taskService = taskService;
    }

    @Transactional
    public ProcurementRequirementConfirmationDetailView initializePool(Long demandItemId, InitializePoolCommand command) {
        ProcurementCandidatePoolWriteContext context = permissionGuard.resolveWriteContext(command, "初始化待选池");
        DemandDetailRow demand = requireDemandForUpdate(demandItemId, context.ownerUserId);

        PoolLockRow existingPool = mapper.selectCurrentPoolForUpdate(demandItemId);
        if (existingPool != null) {
            return detail(demandItemId, context.ownerUserId, "当前采购需求已经存在待选池，未重复初始化。");
        }

        List<CandidateRow> topCandidates = mapper.listTopCandidates(demandItemId, CANDIDATE_SOURCE_LIMIT);
        List<CandidateRow> initialCandidates = topCandidates.size() > MAX_POOL_SIZE
                ? topCandidates.subList(0, MAX_POOL_SIZE)
                : topCandidates;
        String initialStatus = initialCandidates.isEmpty()
                ? STATUS_POOL_EMPTY_REQUIRES_ACTION
                : STATUS_POOL_INQUIRY_RUNNING;
        Long poolId = idGenerator.nextPoolId();
        String poolNo = "POOL-" + demandItemId + "-" + poolId;
        mapper.insertCandidatePool(
                poolId,
                demand.getOwnerUserId(),
                demandItemId,
                poolNo,
                initialStatus,
                MAX_POOL_SIZE,
                CANDIDATE_SOURCE_LIMIT,
                demand.getAssignedBuyerId(),
                context.operatorUserId
        );
        mapper.updateDemandCurrentPool(demandItemId, poolId, context.operatorUserId);

        PoolLockRow pool = lockPool(demandItemId);
        List<ProcurementPoolCreatedTask> createdTasks = new ArrayList<>();
        int poolRankNo = 1;
        for (CandidateRow candidate : initialCandidates) {
            createdTasks.add(taskService.addPoolItemAndTask(
                    pool,
                    candidate,
                    poolRankNo,
                    JOIN_SYSTEM_AUTO,
                    context,
                    "系统默认前 5 条候选进入待选池。"
            ));
            poolRankNo++;
        }

        if (taskService.shouldDispatch(command.getTriggerInquiry())) {
            taskService.dispatchTasks(createdTasks, context);
        }

        String finalStatus = resolvePoolStatusAfterCurrentItems(pool.getPoolId(), initialStatus);
        Long snapshotId = auditService.createSnapshot(pool, "POOL_AUTO_CREATED", finalStatus, "系统初始化待选池。", null, context.operatorUserId);
        mapper.updatePoolAfterItemChange(pool.getPoolId(), finalStatus, snapshotId, context.operatorUserId);
        auditService.appendLog(
                pool,
                null,
                "POOL_AUTO_CREATED",
                context,
                null,
                finalStatus,
                snapshotId,
                initialCandidates.isEmpty() ? "当前没有可入池候选。" : "系统默认候选已入池并创建询价任务。",
                Map.of("candidateCount", initialCandidates.size())
        );

        return detail(demandItemId, context.ownerUserId, "待选池已初始化。");
    }

    @Transactional
    public ProcurementRequirementConfirmationDetailView removePoolItem(
            Long demandItemId,
            Long poolItemId,
            RemovePoolItemCommand command
    ) {
        ProcurementCandidatePoolWriteContext context = permissionGuard.resolveWriteContext(command, "移出待选池候选");
        requireDemandForUpdate(demandItemId, context.ownerUserId);
        PoolLockRow pool = requirePool(demandItemId);
        requirePoolItemChangeAllowed(pool.getStatus(), "当前阶段不允许移出待选池候选。");

        PoolItemLockRow item = mapper.selectPoolItemForUpdate(pool.getPoolId(), poolItemId);
        if (item == null || !Objects.equals(item.getDemandItemId(), demandItemId)) {
            throw new IllegalArgumentException("该候选不在当前待选池中。");
        }
        String beforeStatus = upper(item.getStatus());
        if (ITEM_REMOVED_TERMINATED.equals(beforeStatus)) {
            throw new IllegalStateException("该候选已经被移出待选池。");
        }
        if ("CLOSED".equals(beforeStatus)) {
            throw new IllegalStateException("该候选询价已关闭，当前不允许移出。");
        }

        String reason = firstNonBlank(command.getReason(), "采购手动终止并移出待选池。");
        mapper.terminatePoolItem(pool.getPoolId(), poolItemId, reason, context.operatorUserId);
        if (item.getInquiryTaskId() != null) {
            mapper.terminateAutoInquiryTask(item.getInquiryTaskId(), "待选池候选已被移出，自动询价任务终止。", context.operatorUserId);
            mapper.releaseAutoInquirySessionByTask(item.getInquiryTaskId(), "待选池候选已移出，释放询价会话。", context.operatorUserId);
            autoInquiryEventLogger.append(
                    item.getInquiryTaskId(),
                    "TASK_TERMINATED_BY_POOL_REMOVE",
                    beforeStatus,
                    ProcurementAutoInquiryLifecycle.STATUS_CLOSED,
                    "TERMINATED",
                    "待选池候选已被移出，自动询价任务终止。",
                    "poolItemId=" + poolItemId,
                    context.operatorUserId
            );
        }

        String nextStatus = resolvePoolStatusAfterCurrentItems(pool.getPoolId(), pool.getStatus());
        Long snapshotId = auditService.createSnapshot(pool, "POOL_ITEM_CHANGED", nextStatus, "采购移出待选池候选。", null, context.operatorUserId);
        mapper.updatePoolAfterItemChange(pool.getPoolId(), nextStatus, snapshotId, context.operatorUserId);
        auditService.appendLog(
                pool,
                item,
                "POOL_CANDIDATE_REMOVED",
                context,
                beforeStatus,
                ITEM_REMOVED_TERMINATED,
                snapshotId,
                reason,
                Map.of("poolItemId", poolItemId)
        );

        return detail(demandItemId, context.ownerUserId, "候选已终止询价并移出待选池。");
    }

    @Transactional
    public ProcurementRequirementConfirmationDetailView addCandidateToPool(
            Long demandItemId,
            Long candidateId,
            AddPoolCandidateCommand command
    ) {
        ProcurementCandidatePoolWriteContext context = permissionGuard.resolveWriteContext(command, "补入待选池候选");
        requireDemandForUpdate(demandItemId, context.ownerUserId);
        PoolLockRow pool = requirePool(demandItemId);
        requirePoolItemChangeAllowed(pool.getStatus(), "当前阶段不允许补入备选候选。");

        List<PoolItemLockRow> currentItems = mapper.listCurrentPoolItemsForUpdate(pool.getPoolId());
        int maxPoolSize = defaultInt(pool.getMaxPoolSize(), MAX_POOL_SIZE);
        if (currentItems.size() >= maxPoolSize) {
            throw new IllegalStateException("待选池最多 5 个，请先终止并移出一个候选。");
        }
        if (defaultInt(mapper.countCurrentPoolItemByCandidate(pool.getPoolId(), candidateId)) > 0) {
            throw new IllegalStateException("该候选已经在待选池中。");
        }

        CandidateRow candidate = findTopCandidate(demandItemId, candidateId);
        if (candidate == null) {
            throw new IllegalArgumentException("该候选不在当前前 10 条备选池中。");
        }

        int poolRankNo = firstAvailablePoolRank(currentItems, maxPoolSize);
        ProcurementPoolCreatedTask createdTask = taskService.addPoolItemAndTask(
                pool,
                candidate,
                poolRankNo,
                JOIN_BUYER_MANUAL,
                context,
                firstNonBlank(command.getReason(), "采购从备选池补入待选池。")
        );

        if (taskService.shouldDispatch(command.getTriggerInquiry())) {
            taskService.dispatchTasks(List.of(createdTask), context);
        }

        String nextStatus = resolvePoolStatusAfterCurrentItems(pool.getPoolId(), pool.getStatus());
        Long snapshotId = auditService.createSnapshot(pool, "POOL_ITEM_CHANGED", nextStatus, "采购补入备选候选。", null, context.operatorUserId);
        mapper.updatePoolAfterItemChange(pool.getPoolId(), nextStatus, snapshotId, context.operatorUserId);
        auditService.appendLog(
                pool,
                createdTask.itemLock,
                "POOL_CANDIDATE_ADDED",
                context,
                null,
                ITEM_WAITING_SEND,
                snapshotId,
                firstNonBlank(command.getReason(), "采购从备选池补入待选池。"),
                Map.of("candidateId", candidateId, "poolRankNo", poolRankNo)
        );

        return detail(demandItemId, context.ownerUserId, "备选候选已补入待选池。");
    }

    @Transactional
    public ProcurementRequirementConfirmationDetailView finishInquiry(Long demandItemId, FinishPoolInquiryCommand command) {
        return inquiryService.finishInquiry(demandItemId, command);
    }

    @Transactional
    public ProcurementRequirementConfirmationDetailView recordPoolItemReply(
            Long demandItemId,
            Long poolItemId,
            RecordPoolItemReplyCommand command
    ) {
        return inquiryService.recordPoolItemReply(demandItemId, poolItemId, command);
    }

    @Transactional
    public ProcurementRequirementConfirmationDetailView advancePoolItemFollowUp(
            Long demandItemId,
            Long poolItemId,
            AdvancePoolItemFollowUpCommand command
    ) {
        return inquiryService.advancePoolItemFollowUp(demandItemId, poolItemId, command);
    }

    @Transactional
    public ProcurementRequirementConfirmationDetailView advancePoolItemFollowUpBySystem(
            Long ownerUserId,
            Long demandItemId,
            Long poolItemId,
            String expectedStatus,
            String note
    ) {
        return inquiryService.advancePoolItemFollowUpBySystem(ownerUserId, demandItemId, poolItemId, expectedStatus, note);
    }

    @Transactional
    public ProcurementRequirementConfirmationDetailView markNoReplyHandoff(
            Long demandItemId,
            Long poolItemId,
            MarkPoolItemExceptionCommand command
    ) {
        return inquiryService.markNoReplyHandoff(demandItemId, poolItemId, command);
    }

    @Transactional
    public ProcurementRequirementConfirmationDetailView markNoReplyHandoffBySystem(
            Long ownerUserId,
            Long demandItemId,
            Long poolItemId,
            String expectedStatus,
            String reason,
            String replySummary,
            String riskNote
    ) {
        return inquiryService.markNoReplyHandoffBySystem(ownerUserId, demandItemId, poolItemId, expectedStatus, reason, replySummary, riskNote);
    }

    @Transactional
    public ProcurementRequirementConfirmationDetailView markReplyParseFailure(
            Long demandItemId,
            Long poolItemId,
            MarkPoolItemExceptionCommand command
    ) {
        return inquiryService.markReplyParseFailure(demandItemId, poolItemId, command);
    }

    @Transactional
    public ProcurementRequirementConfirmationDetailView confirmFinalCandidates(
            Long demandItemId,
            ConfirmFinalCandidatesCommand command
    ) {
        return finalCandidateService.confirmFinalCandidates(demandItemId, command);
    }

    @Transactional
    public ProcurementRequirementConfirmationDetailView generateSummary(Long demandItemId, GenerateSummaryCommand command) {
        return finalCandidateService.generateSummary(demandItemId, command);
    }

    private PoolLockRow requirePool(Long demandItemId) {
        PoolLockRow pool = mapper.selectCurrentPoolForUpdate(demandItemId);
        if (pool == null) {
            throw new IllegalStateException("当前采购需求还没有待选池，请先初始化待选池。");
        }
        return pool;
    }

    private PoolLockRow lockPool(Long demandItemId) {
        PoolLockRow pool = mapper.selectCurrentPoolForUpdate(demandItemId);
        if (pool == null) {
            throw new IllegalStateException("待选池初始化失败，请重试。");
        }
        return pool;
    }

    private DemandDetailRow requireDemandForUpdate(Long demandItemId, Long ownerUserId) {
        if (demandItemId == null) {
            throw new IllegalArgumentException("缺少采购需求 ID。");
        }
        DemandDetailRow demand = mapper.selectDemandDetailForUpdate(demandItemId, ownerUserId);
        if (demand == null) {
            throw new IllegalArgumentException("采购需求不存在或当前账号无权查看。");
        }
        return demand;
    }

    private CandidateRow findTopCandidate(Long demandItemId, Long candidateId) {
        if (candidateId == null) {
            return null;
        }
        for (CandidateRow candidate : mapper.listTopCandidates(demandItemId, CANDIDATE_SOURCE_LIMIT)) {
            if (Objects.equals(candidate.getCandidateId(), candidateId)) {
                return candidate;
            }
        }
        return null;
    }

    private String resolvePoolStatusAfterCurrentItems(Long poolId, String fallbackStatus) {
        return ProcurementCandidatePoolStatusPolicy.resolvePoolStatusAfterCurrentItems(mapper.listCurrentPoolItems(poolId), fallbackStatus);
    }

    private void requirePoolItemChangeAllowed(String poolStatus, String message) {
        if (!ProcurementCandidatePoolStatusPolicy.canChangePoolItem(poolStatus)) {
            throw new IllegalStateException(message);
        }
    }

    private void requireExpectedStatus(String expectedStatus, String actualStatus) {
        String normalizedExpected = upper(expectedStatus);
        if (StringUtils.hasText(normalizedExpected) && !normalizedExpected.equals(upper(actualStatus))) {
            throw new IllegalStateException("当前候选状态已变化，请刷新后重试。");
        }
    }

    private int firstAvailablePoolRank(List<PoolItemLockRow> currentItems, int maxPoolSize) {
        Set<Integer> used = new LinkedHashSet<>();
        for (PoolItemLockRow item : currentItems) {
            if (item.getPoolRankNo() != null) {
                used.add(item.getPoolRankNo());
            }
        }
        for (int rankNo = 1; rankNo <= maxPoolSize; rankNo++) {
            if (!used.contains(rankNo)) {
                return rankNo;
            }
        }
        return currentItems.size() + 1;
    }

    private ProcurementRequirementConfirmationDetailView detail(Long demandItemId, Long ownerUserId, String message) {
        ProcurementRequirementConfirmationDetailView view = readService.getDemandDetail(demandItemId, ownerUserId);
        view.setMessage(message);
        return view;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String upper(String value) {
        String normalized = normalize(value);
        return normalized == null ? "" : normalized.toUpperCase(Locale.ROOT);
    }

    private Integer defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private Integer defaultInt(Integer value, Integer fallback) {
        return value == null ? fallback : value;
    }

    private String firstNonBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

}
