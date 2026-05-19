package com.nuono.next.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProcurementMapper;
import com.nuono.next.infrastructure.mapper.ProcurementRequirementConfirmationMapper;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.AddPoolCandidateCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.ConfirmFinalCandidatesCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.InitializePoolCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.CandidateRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.DemandDetailRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.OperatorContextRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.PoolItemLockRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.PoolItemRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.PoolLockRow;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalDbProcurementCandidatePoolServiceTest {

    @Mock
    private ProcurementRequirementConfirmationMapper mapper;

    @Mock
    private ProcurementMapper procurementMapper;

    @Mock
    private LocalDbProcurementRequirementConfirmationService readService;

    @Mock
    private ProcurementAutoInquiryEventLogger autoInquiryEventLogger;

    @Mock
    private ProcurementAutoInquiryExecutor autoInquiryExecutor;

    private LocalDbProcurementCandidatePoolService service;

    @BeforeEach
    void setUp() {
        ProcurementJsonSupport jsonSupport = new ProcurementJsonSupport(new ObjectMapper());
        ProcurementCandidatePoolIdGenerator idGenerator = new ProcurementCandidatePoolIdGenerator(mapper, procurementMapper);
        ProcurementCandidatePoolPermissionGuard permissionGuard = new ProcurementCandidatePoolPermissionGuard(mapper);
        ProcurementCandidatePoolAuditService auditService = new ProcurementCandidatePoolAuditService(mapper, idGenerator, jsonSupport);
        ProcurementPoolWriteSupport writeSupport = new ProcurementPoolWriteSupport(mapper, readService);
        ProcurementPoolTaskService taskService = new ProcurementPoolTaskService(
                mapper,
                procurementMapper,
                autoInquiryEventLogger,
                autoInquiryExecutor,
                auditService,
                idGenerator
        );
        ProcurementPoolFinalCandidateService finalCandidateService = new ProcurementPoolFinalCandidateService(
                mapper,
                readService,
                auditService,
                idGenerator,
                permissionGuard
        );
        ProcurementPoolInquiryService inquiryService = new ProcurementPoolInquiryService(
                mapper,
                procurementMapper,
                readService,
                autoInquiryEventLogger,
                auditService,
                permissionGuard,
                writeSupport
        );
        service = new LocalDbProcurementCandidatePoolService(
                mapper,
                procurementMapper,
                readService,
                autoInquiryEventLogger,
                auditService,
                idGenerator,
                permissionGuard,
                finalCandidateService,
                inquiryService,
                taskService
        );
    }

    @Test
    void shouldRejectSpoofedPurchaseRoleWhenDatabaseRoleIsNotPurchase() {
        InitializePoolCommand command = initializeCommand(10002L, 10002L, "PURCHASE", false);
        when(mapper.selectOperatorContext(10002L)).thenReturn(operator(10002L, "老板", "BOSS", 1));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.initializePool(41101L, command)
        );

        assertEquals("当前账号不是采购角色，不能初始化待选池。", exception.getMessage());
        verify(mapper, never()).selectDemandDetailForUpdate(anyLong(), anyLong());
        verify(autoInquiryExecutor, never()).execute(anyLong(), anyLong());
    }

    @Test
    void shouldRejectPublicSystemOperatorSpoofing() {
        InitializePoolCommand command = initializeCommand(10002L, 0L, "SYSTEM_TASK", false);
        when(mapper.selectOperatorContext(0L)).thenReturn(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.initializePool(41101L, command)
        );

        assertEquals("当前操作账号不存在或已停用，暂时不能初始化待选池。", exception.getMessage());
        verify(mapper, never()).selectDemandDetailForUpdate(anyLong(), anyLong());
        verify(autoInquiryExecutor, never()).execute(anyLong(), anyLong());
    }

    @Test
    void shouldUseDatabasePurchaseRoleAndKeepDispatchDisabledByDefaultWhenInitializingPool() {
        InitializePoolCommand command = initializeCommand(10002L, 90001L, "OPERATIONS", true);
        CandidateRow candidate = candidate(43101L);
        PoolItemRow poolItem = poolItem(91001L, 43101L, "IN_POOL_WAITING_SEND");

        when(mapper.selectOperatorContext(90001L)).thenReturn(operator(90001L, "采购", "PURCHASE", 1));
        when(mapper.selectDemandDetailForUpdate(41101L, 10002L)).thenReturn(demand(41101L, 10002L));
        when(mapper.selectCurrentPoolForUpdate(41101L)).thenReturn(null, pool(90001L, 41101L, 10002L, "POOL_INQUIRY_RUNNING"));
        when(mapper.listTopCandidates(41101L, 10)).thenReturn(List.of(candidate));
        when(mapper.nextCandidatePoolId()).thenReturn(90001L);
        when(mapper.nextCandidatePoolItemId()).thenReturn(91001L);
        when(mapper.nextAutoInquiryTaskId()).thenReturn(45001L);
        when(procurementMapper.selectAutoInquiryTask(45001L)).thenReturn(null);
        when(mapper.listCurrentPoolItems(90001L)).thenReturn(List.of(poolItem));
        when(mapper.nextCandidatePoolSnapshotId()).thenReturn(92001L);
        when(mapper.selectMaxSnapshotVersion(90001L, "POOL_AUTO_CREATED")).thenReturn(0);
        when(mapper.nextPoolOperationLogId()).thenReturn(94001L, 94002L);
        when(readService.getDemandDetail(41101L, 10002L)).thenReturn(new ProcurementRequirementConfirmationDetailView());

        ProcurementRequirementConfirmationDetailView result = service.initializePool(41101L, command);

        assertEquals("待选池已初始化。", result.getMessage());
        verify(autoInquiryExecutor, never()).execute(anyLong(), anyLong());
        verify(mapper, atLeastOnce()).insertPoolOperationLog(
                anyLong(),
                eq(90001L),
                any(),
                eq(10002L),
                eq(41101L),
                any(),
                any(),
                anyString(),
                eq(90001L),
                eq("PURCHASE"),
                any(),
                any(),
                any(),
                anyString(),
                any()
        );
    }

    @Test
    void shouldUseInternalSystemContextForSchedulerOnlyOperations() {
        PoolItemLockRow item = poolItemLock(91001L, 90001L, 41101L, 43101L, "IN_POOL_WAITING_REPLY");
        PoolItemRow currentItem = poolItem(91001L, 43101L, "FOLLOW_UP_1_SENT");

        when(mapper.selectDemandDetailForUpdate(41101L, 10002L)).thenReturn(demand(41101L, 10002L));
        when(mapper.selectCurrentPoolForUpdate(41101L)).thenReturn(pool(90001L, 41101L, 10002L, "POOL_INQUIRY_RUNNING"));
        when(mapper.selectPoolItemForUpdate(90001L, 91001L)).thenReturn(item);
        when(mapper.listCurrentPoolItemsForUpdate(90001L)).thenReturn(List.of(item));
        when(mapper.listCurrentPoolItems(90001L)).thenReturn(List.of(currentItem));
        when(mapper.nextCandidatePoolSnapshotId()).thenReturn(92001L);
        when(mapper.selectMaxSnapshotVersion(90001L, "INQUIRY_FOLLOW_UP_SENT")).thenReturn(0);
        when(mapper.nextPoolOperationLogId()).thenReturn(94001L);
        when(mapper.listTopCandidates(41101L, 10)).thenReturn(List.of(candidate(43101L)));
        when(readService.getDemandDetail(41101L, 10002L)).thenReturn(new ProcurementRequirementConfirmationDetailView());

        ProcurementRequirementConfirmationDetailView result = service.advancePoolItemFollowUpBySystem(
                10002L,
                41101L,
                91001L,
                "IN_POOL_WAITING_REPLY",
                "自动催发节点到期。"
        );

        assertEquals("催发状态已更新。", result.getMessage());
        verify(mapper, never()).selectOperatorContext(anyLong());
        verify(mapper).insertPoolOperationLog(
                eq(94001L),
                eq(90001L),
                eq(91001L),
                eq(10002L),
                eq(41101L),
                eq(43101L),
                eq("798448779771"),
                eq("FOLLOW_UP_ADVANCED"),
                eq(0L),
                eq("SYSTEM_TASK"),
                eq("IN_POOL_WAITING_REPLY"),
                eq("FOLLOW_UP_1_SENT"),
                eq(92001L),
                eq("自动催发节点到期。"),
                anyString()
        );
    }

    @Test
    void shouldRejectAddingCandidateWhenPoolIsAlreadyFull() {
        AddPoolCandidateCommand command = addCommand(10002L, 90001L, "PURCHASE", true);
        when(mapper.selectOperatorContext(90001L)).thenReturn(operator(90001L, "采购", "PURCHASE", 1));
        when(mapper.selectDemandDetailForUpdate(41101L, 10002L)).thenReturn(demand(41101L, 10002L));
        when(mapper.selectCurrentPoolForUpdate(41101L)).thenReturn(pool(90001L, 41101L, 10002L, "POOL_INQUIRY_RUNNING"));
        when(mapper.listCurrentPoolItemsForUpdate(90001L)).thenReturn(List.of(
                poolItemLock(91001L, 90001L, 41101L, 43101L, "IN_POOL_WAITING_REPLY"),
                poolItemLock(91002L, 90001L, 41101L, 43102L, "IN_POOL_WAITING_REPLY"),
                poolItemLock(91003L, 90001L, 41101L, 43103L, "IN_POOL_WAITING_REPLY"),
                poolItemLock(91004L, 90001L, 41101L, 43104L, "IN_POOL_WAITING_REPLY"),
                poolItemLock(91005L, 90001L, 41101L, 43105L, "IN_POOL_WAITING_REPLY")
        ));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.addCandidateToPool(41101L, 43106L, command)
        );

        assertEquals("待选池最多 5 个，请先终止并移出一个候选。", exception.getMessage());
        verify(mapper, never()).countCurrentPoolItemByCandidate(anyLong(), anyLong());
        verify(mapper, never()).nextCandidatePoolItemId();
        verify(autoInquiryExecutor, never()).execute(anyLong(), anyLong());
    }

    @Test
    void shouldRejectFinalCandidateConfirmationBeforeInquiryFinished() {
        ConfirmFinalCandidatesCommand command = finalCommand(10002L, 90001L, "PURCHASE", 91001L, 91002L);
        when(mapper.selectOperatorContext(90001L)).thenReturn(operator(90001L, "采购", "PURCHASE", 1));
        when(mapper.selectDemandDetailForUpdate(41101L, 10002L)).thenReturn(demand(41101L, 10002L));
        when(mapper.selectCurrentPoolForUpdate(41101L)).thenReturn(pool(90001L, 41101L, 10002L, "POOL_INQUIRY_RUNNING"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.confirmFinalCandidates(41101L, command)
        );

        assertEquals("请先完成自动询价收口。", exception.getMessage());
        verify(mapper, never()).selectPoolItemForUpdate(anyLong(), anyLong());
        verify(mapper, never()).softDeleteFinalCandidates(anyLong(), anyLong(), anyLong());
    }

    private InitializePoolCommand initializeCommand(Long ownerUserId, Long operatorUserId, String operatorRole, Boolean triggerInquiry) {
        InitializePoolCommand command = new InitializePoolCommand();
        command.setOwnerUserId(ownerUserId);
        command.setOperatorUserId(operatorUserId);
        command.setOperatorRole(operatorRole);
        command.setTriggerInquiry(triggerInquiry);
        return command;
    }

    private AddPoolCandidateCommand addCommand(Long ownerUserId, Long operatorUserId, String operatorRole, Boolean triggerInquiry) {
        AddPoolCandidateCommand command = new AddPoolCandidateCommand();
        command.setOwnerUserId(ownerUserId);
        command.setOperatorUserId(operatorUserId);
        command.setOperatorRole(operatorRole);
        command.setTriggerInquiry(triggerInquiry);
        command.setReason("测试补入待选池");
        return command;
    }

    private ConfirmFinalCandidatesCommand finalCommand(
            Long ownerUserId,
            Long operatorUserId,
            String operatorRole,
            Long primaryPoolItemId,
            Long backupPoolItemId
    ) {
        ConfirmFinalCandidatesCommand command = new ConfirmFinalCandidatesCommand();
        command.setOwnerUserId(ownerUserId);
        command.setOperatorUserId(operatorUserId);
        command.setOperatorRole(operatorRole);
        command.setPrimaryPoolItemId(primaryPoolItemId);
        command.setBackupPoolItemId(backupPoolItemId);
        command.setDecisionNote("测试确认最终 2 个");
        return command;
    }

    private OperatorContextRow operator(Long userId, String roleName, String roleCode, Integer status) {
        OperatorContextRow row = new OperatorContextRow();
        row.setUserId(userId);
        row.setAccountNo("operator-" + userId);
        row.setRoleName(roleName);
        row.setRoleCode(roleCode);
        row.setStatus(status);
        return row;
    }

    private DemandDetailRow demand(Long demandItemId, Long ownerUserId) {
        DemandDetailRow row = new DemandDetailRow();
        row.setDemandItemId(demandItemId);
        row.setOwnerUserId(ownerUserId);
        row.setAssignedBuyerId(null);
        return row;
    }

    private CandidateRow candidate(Long candidateId) {
        CandidateRow row = new CandidateRow();
        row.setCandidateId(candidateId);
        row.setRankNo(1);
        row.setTotalScore(92);
        row.setCandidateUrl("https://detail.1688.com/offer/798448779771.html");
        row.setTitle("测试 1688 候选");
        row.setSupplierName("测试供应商");
        row.setPriceText("12.80 RMB");
        row.setMoqText("50 件");
        row.setDeliveryTimelineText("3 天发货");
        return row;
    }

    private PoolLockRow pool(Long poolId, Long demandItemId, Long ownerUserId, String status) {
        PoolLockRow row = new PoolLockRow();
        row.setPoolId(poolId);
        row.setOwnerUserId(ownerUserId);
        row.setDemandItemId(demandItemId);
        row.setPoolNo("POOL-" + demandItemId + "-" + poolId);
        row.setStatus(status);
        row.setMaxPoolSize(5);
        row.setCandidateSourceLimit(10);
        return row;
    }

    private PoolItemLockRow poolItemLock(Long poolItemId, Long poolId, Long demandItemId, Long candidateId, String status) {
        PoolItemLockRow row = new PoolItemLockRow();
        row.setPoolItemId(poolItemId);
        row.setPoolId(poolId);
        row.setOwnerUserId(10002L);
        row.setDemandItemId(demandItemId);
        row.setCandidateId(candidateId);
        row.setSourceRankNo(1);
        row.setPoolRankNo(1);
        row.setStatus(status);
        return row;
    }

    private PoolItemRow poolItem(Long poolItemId, Long candidateId, String status) {
        PoolItemRow row = new PoolItemRow();
        row.setPoolItemId(poolItemId);
        row.setCandidateId(candidateId);
        row.setRankNo(1);
        row.setSourceRankNo(1);
        row.setPoolRankNo(1);
        row.setStatus(status);
        row.setCandidateUrl("https://detail.1688.com/offer/798448779771.html");
        row.setTitle("测试 1688 候选");
        row.setSupplierName("测试供应商");
        return row;
    }
}
