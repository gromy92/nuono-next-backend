package com.nuono.next.nooncompleteness;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NoonGapPatrolActionService {
    private static final Long GAP_ID_INITIAL_VALUE = 910000L;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final NoonDataCompletenessRepository repository;
    private final NoonGapPatrolPlanner planner;
    private final NoonDataAuditService auditService;
    private final Clock clock;

    @Autowired
    public NoonGapPatrolActionService(
            NoonDataCompletenessRepository repository,
            NoonGapPatrolPlanner planner,
            NoonDataAuditService auditService
    ) {
        this(repository, planner, auditService, Clock.systemUTC());
    }

    public NoonGapPatrolActionService(
            NoonDataCompletenessRepository repository,
            NoonGapPatrolPlanner planner,
            NoonDataAuditService auditService,
            Clock clock
    ) {
        this.repository = repository;
        this.planner = planner;
        this.auditService = auditService;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public NoonGapPatrolActionResult retry(Long gapId) {
        NoonDataGapWindowRecord gap = requireGap(gapId);
        if (!Boolean.TRUE.equals(gap.getRetryable()) || Boolean.TRUE.equals(gap.getRequiresManualAction())) {
            throw new IllegalStateException("该缺口不可自动重试。");
        }
        if (gap.getStatus() == NoonDataGapStatus.PROVIDER_RETENTION_LIMIT) {
            throw new IllegalStateException("该缺口超出 provider 保留期，不能重试。");
        }
        NoonDataGapWindowRecord retrying = gap.copy();
        retrying.setStatus(NoonDataGapStatus.PENDING);
        retrying.setRetryable(Boolean.TRUE);
        retrying.setRequiresManualAction(Boolean.FALSE);
        retrying.setNextRetryAt(null);
        retrying.setUpdatedAt(now());
        repository.insertGapWindow(retrying);
        updateCompletenessPatrol(retrying);

        NoonDataGapQuery query = scopedQuery(retrying);
        NoonGapPatrolPlanner.Result plannerResult = planner.planDueGaps(query, 1);
        NoonDataGapWindowRecord linked = requireGap(gapId);
        NoonGapPatrolActionResult result = result(linked, "已提交安全巡检任务。");
        result.setPlannedTaskCount(plannerResult.getPlannedTasks().size());
        return result;
    }

    public NoonGapPatrolActionResult pause(Long gapId, String reason) {
        NoonDataGapWindowRecord gap = requireGap(gapId);
        NoonDataGapWindowRecord paused = gap.copy();
        paused.setStatus(NoonDataGapStatus.PAUSED);
        paused.setRetryable(Boolean.FALSE);
        paused.setRequiresManualAction(Boolean.FALSE);
        paused.setDiagnosticSummary(NoonDataCompletenessSafeText.redact(reason));
        paused.setNextRetryAt(null);
        paused.setUpdatedAt(now());
        repository.insertGapWindow(paused);
        updateCompletenessPatrol(paused);
        return result(paused, "已暂停该缺口巡检。");
    }

    public NoonGapPatrolActionResult resume(Long gapId) {
        NoonDataGapWindowRecord gap = requireGap(gapId);
        NoonDataGapWindowRecord resumed = gap.copy();
        resumed.setStatus(NoonDataGapStatus.PENDING);
        resumed.setRetryable(Boolean.TRUE);
        resumed.setRequiresManualAction(Boolean.FALSE);
        resumed.setNextRetryAt(now());
        resumed.setUpdatedAt(now());
        repository.insertGapWindow(resumed);
        updateCompletenessPatrol(resumed);
        return result(resumed, "已恢复该缺口巡检。");
    }

    public NoonGapPatrolActionResult reAudit(Long gapId) {
        NoonDataGapWindowRecord gap = requireGap(gapId);
        NoonDataAuditCommand command = NoonDataAuditCommand.builder()
                .ownerUserId(gap.getOwnerUserId())
                .storeCode(gap.getStoreCode())
                .siteCode(gap.getSiteCode())
                .auditDate(LocalDate.now(clock))
                .build();
        if (gap.getCategory() == NoonDataCategory.PRODUCT_LIST || gap.getCategory() == NoonDataCategory.PRODUCT_DETAIL) {
            auditService.auditProductCompleteness(command);
        } else {
            auditService.auditSalesCompleteness(command);
        }
        return result(requireGap(gapId), "已触发范围内重审。");
    }

    public NoonGapPatrolActionResult syncCategory(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            NoonDataCategory category
    ) {
        NoonDataCompletenessRecord row = requireCompleteness(ownerUserId, storeCode, siteCode, category);
        NoonDataGapQuery query = scopedQuery(row);
        List<NoonDataGapWindowRecord> scopedGaps = repository.listGapWindows(query);
        if (scopedGaps.stream().anyMatch((gap) -> Boolean.TRUE.equals(gap.getRequiresManualAction()))) {
            throw new IllegalStateException("该数据项需要人工处理，不能直接同步。");
        }
        List<NoonDataGapWindowRecord> eligibleGaps = scopedGaps.stream()
                .filter(this::canManuallySync)
                .collect(Collectors.toList());
        if (eligibleGaps.isEmpty()) {
            NoonDataGapWindowRecord refreshGap = manualRefreshGap(row);
            repository.insertGapWindow(refreshGap);
        } else {
            for (NoonDataGapWindowRecord gap : eligibleGaps) {
                NoonDataGapWindowRecord pending = gap.copy();
                pending.setStatus(NoonDataGapStatus.PENDING);
                pending.setRetryable(Boolean.TRUE);
                pending.setRequiresManualAction(Boolean.FALSE);
                pending.setNextRetryAt(null);
                pending.setUpdatedAt(now());
                repository.insertGapWindow(pending);
            }
        }
        NoonGapPatrolPlanner.Result plannerResult = planner.planManualSyncGaps(query, 10);
        NoonGapPatrolActionResult result = new NoonGapPatrolActionResult();
        result.setPlannedTaskCount(plannerResult.getPlannedTasks().size());
        result.setPlannedTaskIds(plannerResult.getPlannedTasks().stream()
                .map((task) -> task.getId())
                .collect(Collectors.toList()));
        result.setMessage(result.getPlannedTaskCount() > 0 ? "已提交同步任务。" : "没有可提交的同步任务。");
        return result;
    }

    private NoonDataGapWindowRecord requireGap(Long gapId) {
        return repository.listGapWindows(new NoonDataGapQuery()).stream()
                .filter((gap) -> Objects.equals(gap.getId(), gapId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("数据缺口不存在：" + gapId));
    }

    private NoonDataCompletenessRecord requireCompleteness(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            NoonDataCategory category
    ) {
        NoonDataCompletenessQuery query = new NoonDataCompletenessQuery();
        query.setOwnerUserId(ownerUserId);
        query.setStoreCode(storeCode);
        query.setSiteCode(siteCode);
        query.setCategory(category);
        return repository.listCompleteness(query).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("数据完整性台账不存在。"));
    }

    private NoonDataGapQuery scopedQuery(NoonDataGapWindowRecord gap) {
        NoonDataGapQuery query = new NoonDataGapQuery();
        query.setOwnerUserId(gap.getOwnerUserId());
        query.setStoreCode(gap.getStoreCode());
        query.setSiteCode(gap.getSiteCode());
        query.setCategory(gap.getCategory());
        return query;
    }

    private NoonDataGapQuery scopedQuery(NoonDataCompletenessRecord row) {
        NoonDataGapQuery query = new NoonDataGapQuery();
        query.setOwnerUserId(row.getOwnerUserId());
        query.setStoreCode(row.getStoreCode());
        query.setSiteCode(row.getSiteCode());
        query.setCategory(row.getCategory());
        return query;
    }

    private void updateCompletenessPatrol(NoonDataGapWindowRecord changedGap) {
        NoonDataCompletenessRecord row = findCompleteness(changedGap);
        if (row == null) {
            return;
        }
        NoonDataCompletenessRecord updated = row.copy();
        int activeGapCount = activeGapCount(changedGap);
        updated.setActiveGapCount(activeGapCount);
        updated.setPatrolEnabled(activeGapCount > 0);
        updated.setUpdatedAt(now());
        repository.insertCompleteness(updated);
    }

    private NoonDataCompletenessRecord findCompleteness(NoonDataGapWindowRecord gap) {
        NoonDataCompletenessQuery query = new NoonDataCompletenessQuery();
        query.setOwnerUserId(gap.getOwnerUserId());
        query.setStoreCode(gap.getStoreCode());
        query.setSiteCode(gap.getSiteCode());
        query.setCategory(gap.getCategory());
        return repository.listCompleteness(query).stream().findFirst().orElse(null);
    }

    private int activeGapCount(NoonDataGapWindowRecord changedGap) {
        List<NoonDataGapWindowRecord> gaps = repository.listGapWindows(scopedQuery(changedGap));
        int count = 0;
        for (NoonDataGapWindowRecord gap : gaps) {
            NoonDataGapWindowRecord effective = sameGap(gap, changedGap) ? changedGap : gap;
            if (isActive(effective)) {
                count++;
            }
        }
        return count;
    }

    private boolean isActive(NoonDataGapWindowRecord gap) {
        return gap.getStatus() == NoonDataGapStatus.PENDING
                || gap.getStatus() == NoonDataGapStatus.RUNNING
                || gap.getStatus() == NoonDataGapStatus.WAITING_RETRY
                || gap.getStatus() == NoonDataGapStatus.PENDING_CONFIRMATION
                || gap.getStatus() == NoonDataGapStatus.FAILED;
    }

    private boolean canManuallySync(NoonDataGapWindowRecord gap) {
        if (gap == null || Boolean.TRUE.equals(gap.getRequiresManualAction())) {
            return false;
        }
        if (gap.getStatus() == NoonDataGapStatus.PENDING
                || gap.getStatus() == NoonDataGapStatus.PENDING_CONFIRMATION
                || gap.getStatus() == NoonDataGapStatus.WAITING_RETRY) {
            return true;
        }
        return gap.getStatus() == NoonDataGapStatus.FAILED && Boolean.TRUE.equals(gap.getRetryable());
    }

    private NoonDataGapWindowRecord manualRefreshGap(NoonDataCompletenessRecord row) {
        LocalDate targetDate = manualTargetDate(row.getCategory());
        NoonDataGapWindowRecord gap = new NoonDataGapWindowRecord();
        gap.setId(repository.nextId("noon_data_gap_window", GAP_ID_INITIAL_VALUE));
        gap.setCompletenessId(row.getId());
        gap.setOwnerUserId(row.getOwnerUserId());
        gap.setStoreCode(row.getStoreCode());
        gap.setSiteCode(row.getSiteCode());
        gap.setCategory(row.getCategory());
        gap.setWindowType(manualWindowType(row.getCategory()));
        gap.setDateFrom(targetDate);
        gap.setDateTo(targetDate);
        gap.setStatus(NoonDataGapStatus.PENDING);
        gap.setAttempts(0);
        gap.setRetryable(Boolean.TRUE);
        gap.setRequiresManualAction(Boolean.FALSE);
        gap.setNextRetryAt(null);
        gap.setCreatedAt(now());
        gap.setUpdatedAt(now());
        return gap;
    }

    private NoonDataGapWindowType manualWindowType(NoonDataCategory category) {
        if (category == NoonDataCategory.PRODUCT_LIST) {
            return NoonDataGapWindowType.PRODUCT_BASELINE;
        }
        if (category == NoonDataCategory.PRODUCT_DETAIL) {
            return NoonDataGapWindowType.PRODUCT_DETAIL_BASELINE;
        }
        return NoonDataGapWindowType.LATEST_DAILY;
    }

    private LocalDate manualTargetDate(NoonDataCategory category) {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        if (category == NoonDataCategory.SALES_ORDER || category == NoonDataCategory.SALES_PRODUCT_VIEWS) {
            return today.minusDays(1);
        }
        return today;
    }

    private boolean sameGap(NoonDataGapWindowRecord left, NoonDataGapWindowRecord right) {
        return Objects.equals(left.getCompletenessId(), right.getCompletenessId())
                && left.getCategory() == right.getCategory()
                && left.getWindowType() == right.getWindowType()
                && Objects.equals(left.getDateFrom(), right.getDateFrom())
                && Objects.equals(left.getDateTo(), right.getDateTo());
    }

    private NoonGapPatrolActionResult result(NoonDataGapWindowRecord gap, String message) {
        NoonGapPatrolActionResult result = new NoonGapPatrolActionResult();
        result.setGapId(gap.getId());
        result.setStatus(gap.getStatus());
        result.setMessage(message);
        return result;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
