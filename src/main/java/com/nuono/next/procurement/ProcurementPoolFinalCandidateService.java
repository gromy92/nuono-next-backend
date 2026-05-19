package com.nuono.next.procurement;

import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.ITEM_REMOVED_TERMINATED;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.PICK_BACKUP;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.PICK_PRIMARY;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.STATUS_FINAL_TWO_CONFIRMED;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.STATUS_POOL_INQUIRY_FINISHED;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.STATUS_SUMMARY_READY;

import com.nuono.next.infrastructure.mapper.ProcurementRequirementConfirmationMapper;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.ConfirmFinalCandidatesCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.GenerateSummaryCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.DemandDetailRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.FinalCandidateRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.PoolItemLockRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.PoolLockRow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
class ProcurementPoolFinalCandidateService {

    private final ProcurementRequirementConfirmationMapper mapper;
    private final LocalDbProcurementRequirementConfirmationService readService;
    private final ProcurementCandidatePoolAuditService auditService;
    private final ProcurementCandidatePoolIdGenerator idGenerator;
    private final ProcurementCandidatePoolPermissionGuard permissionGuard;

    ProcurementPoolFinalCandidateService(
            ProcurementRequirementConfirmationMapper mapper,
            LocalDbProcurementRequirementConfirmationService readService,
            ProcurementCandidatePoolAuditService auditService,
            ProcurementCandidatePoolIdGenerator idGenerator,
            ProcurementCandidatePoolPermissionGuard permissionGuard
    ) {
        this.mapper = mapper;
        this.readService = readService;
        this.auditService = auditService;
        this.idGenerator = idGenerator;
        this.permissionGuard = permissionGuard;
    }

    @Transactional
    ProcurementRequirementConfirmationDetailView confirmFinalCandidates(
            Long demandItemId,
            ConfirmFinalCandidatesCommand command
    ) {
        ProcurementCandidatePoolWriteContext context = permissionGuard.resolveWriteContext(command, "确认最终 2 个");
        requireDemandForUpdate(demandItemId, context.ownerUserId);
        PoolLockRow pool = requirePool(demandItemId);
        if (!STATUS_POOL_INQUIRY_FINISHED.equals(upper(pool.getStatus()))) {
            throw new IllegalStateException("请先完成自动询价收口。");
        }
        if (command.getPrimaryPoolItemId() == null || command.getBackupPoolItemId() == null) {
            throw new IllegalArgumentException("必须选择 1 个主选和 1 个备选。");
        }
        if (Objects.equals(command.getPrimaryPoolItemId(), command.getBackupPoolItemId())) {
            throw new IllegalArgumentException("主选和备选不能是同一个候选。");
        }

        PoolItemLockRow primaryItem = requireCurrentPoolItem(pool, command.getPrimaryPoolItemId(), "主选候选必须来自当前待选池。");
        PoolItemLockRow backupItem = requireCurrentPoolItem(pool, command.getBackupPoolItemId(), "备选候选必须来自当前待选池。");

        Map<String, Object> selected = new LinkedHashMap<>();
        selected.put("primaryPoolItemId", command.getPrimaryPoolItemId());
        selected.put("backupPoolItemId", command.getBackupPoolItemId());
        selected.put("decisionNote", normalize(command.getDecisionNote()));
        Long finalSnapshotId = auditService.createSnapshot(pool, "FINAL_TWO_CONFIRMED", STATUS_FINAL_TWO_CONFIRMED, "采购确认最终 2 个。", selected, context.operatorUserId);

        mapper.softDeleteFinalCandidates(pool.getPoolId(), demandItemId, context.operatorUserId);
        insertFinalCandidate(pool, primaryItem, PICK_PRIMARY, finalSnapshotId, command.getDecisionNote(), context);
        insertFinalCandidate(pool, backupItem, PICK_BACKUP, finalSnapshotId, command.getDecisionNote(), context);
        mapper.updatePoolFinalConfirmed(pool.getPoolId(), finalSnapshotId, context.operatorUserId);
        auditService.appendLog(
                pool,
                null,
                "FINAL_TWO_CONFIRMED",
                context,
                pool.getStatus(),
                STATUS_FINAL_TWO_CONFIRMED,
                finalSnapshotId,
                firstNonBlank(command.getDecisionNote(), "采购确认最终主选和备选。"),
                selected
        );

        generateSummaryAfterFinalConfirmed(pool, context, false);
        return detail(demandItemId, context.ownerUserId, "最终 2 个已确认，AI 总结已自动生成。");
    }

    @Transactional
    ProcurementRequirementConfirmationDetailView generateSummary(Long demandItemId, GenerateSummaryCommand command) {
        ProcurementCandidatePoolWriteContext context = permissionGuard.resolveWriteContext(command, "生成 AI 总结");
        requireDemandForUpdate(demandItemId, context.ownerUserId);
        PoolLockRow pool = requirePool(demandItemId);
        String poolStatus = upper(pool.getStatus());
        if (!STATUS_FINAL_TWO_CONFIRMED.equals(poolStatus) && !STATUS_SUMMARY_READY.equals(poolStatus)) {
            throw new IllegalStateException("请先确认最终 2 个，再生成 AI 总结。");
        }

        boolean regenerate = Boolean.TRUE.equals(command.getRegenerate());
        if (STATUS_SUMMARY_READY.equals(poolStatus) && !regenerate) {
            return detail(demandItemId, context.ownerUserId, "AI 总结已生成，未重复生成。");
        }

        generateSummaryAfterFinalConfirmed(pool, context, regenerate);
        return detail(demandItemId, context.ownerUserId, "AI 总结已生成。");
    }

    private void generateSummaryAfterFinalConfirmed(PoolLockRow pool, ProcurementCandidatePoolWriteContext context, boolean regenerate) {
        List<FinalCandidateRow> finalCandidates = mapper.listFinalCandidates(pool.getDemandItemId());
        FinalCandidateRow primary = findFinalCandidate(finalCandidates, PICK_PRIMARY);
        FinalCandidateRow backup = findFinalCandidate(finalCandidates, PICK_BACKUP);
        if (primary == null || backup == null) {
            throw new IllegalStateException("必须先确认 1 个主选和 1 个备选，才能生成 AI 总结。");
        }

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("regenerate", regenerate);
        extra.put("primaryCandidateId", primary.getCandidateId());
        extra.put("backupCandidateId", backup.getCandidateId());
        Long snapshotId = auditService.createSnapshot(pool, "SUMMARY_GENERATED", STATUS_SUMMARY_READY, "系统生成 AI 总结。", extra, context.operatorUserId);
        String summaryText = buildSummaryText(primary, backup);
        mapper.updatePoolSummaryReady(pool.getPoolId(), snapshotId, summaryText, context.operatorUserId);
        auditService.appendLog(
                pool,
                null,
                "SUMMARY_GENERATED",
                context,
                STATUS_FINAL_TWO_CONFIRMED,
                STATUS_SUMMARY_READY,
                snapshotId,
                regenerate ? "系统重新生成 AI 总结。" : "最终 2 个确认后系统自动生成 AI 总结。",
                extra
        );
    }

    private void insertFinalCandidate(
            PoolLockRow pool,
            PoolItemLockRow item,
            String pickType,
            Long snapshotId,
            String decisionNote,
            ProcurementCandidatePoolWriteContext context
    ) {
        mapper.insertFinalCandidate(
                idGenerator.nextFinalCandidateId(),
                pool.getPoolId(),
                item.getPoolItemId(),
                pool.getOwnerUserId(),
                pool.getDemandItemId(),
                item.getCandidateId(),
                pickType,
                snapshotId,
                normalize(decisionNote),
                context.operatorUserId
        );
    }

    private PoolItemLockRow requireCurrentPoolItem(PoolLockRow pool, Long poolItemId, String message) {
        PoolItemLockRow item = mapper.selectPoolItemForUpdate(pool.getPoolId(), poolItemId);
        if (item == null || !Objects.equals(item.getDemandItemId(), pool.getDemandItemId())) {
            throw new IllegalArgumentException(message);
        }
        if (ITEM_REMOVED_TERMINATED.equals(upper(item.getStatus()))) {
            throw new IllegalStateException("已移出待选池的候选不能作为最终候选。");
        }
        List<PoolItemLockRow> currentItems = mapper.listCurrentPoolItemsForUpdate(pool.getPoolId());
        for (PoolItemLockRow currentItem : currentItems) {
            if (Objects.equals(currentItem.getPoolItemId(), poolItemId)) {
                return item;
            }
        }
        throw new IllegalArgumentException(message);
    }

    private PoolLockRow requirePool(Long demandItemId) {
        PoolLockRow pool = mapper.selectCurrentPoolForUpdate(demandItemId);
        if (pool == null) {
            throw new IllegalStateException("当前采购需求还没有待选池，请先初始化待选池。");
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

    private FinalCandidateRow findFinalCandidate(List<FinalCandidateRow> candidates, String pickType) {
        for (FinalCandidateRow candidate : candidates) {
            if (pickType.equals(upper(candidate.getFinalPickType()))) {
                return candidate;
            }
        }
        return null;
    }

    private String buildSummaryText(FinalCandidateRow primary, FinalCandidateRow backup) {
        return "主选：" + candidateLine(primary)
                + "\n备选：" + candidateLine(backup)
                + "\n结论：优先推进主选供应商确认样品、价格和交期；备选供应商保留为价格或交付异常时的替代方案。";
    }

    private String candidateLine(FinalCandidateRow candidate) {
        List<String> parts = new ArrayList<>();
        parts.add(firstNonBlank(candidate.getTitle(), "未命名候选"));
        if (StringUtils.hasText(candidate.getSupplierName())) {
            parts.add("供应商 " + candidate.getSupplierName());
        }
        if (StringUtils.hasText(candidate.getPriceText())) {
            parts.add("页面价 " + candidate.getPriceText());
        }
        if (StringUtils.hasText(candidate.getMoqText())) {
            parts.add("MOQ " + candidate.getMoqText());
        }
        if (StringUtils.hasText(candidate.getDeliveryTimelineText())) {
            parts.add("交期 " + candidate.getDeliveryTimelineText());
        }
        return String.join("，", parts);
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

    private String firstNonBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
