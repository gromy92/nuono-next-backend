package com.nuono.next.nooncompleteness;

import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonPullTaskRecord;
import com.nuono.next.noonpull.NoonPullTaskStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NoonGapTaskOutcomeReducer {
    private final NoonDataCompletenessRepository repository;
    private final Clock clock;

    @Autowired
    public NoonGapTaskOutcomeReducer(NoonDataCompletenessRepository repository) {
        this(repository, Clock.systemUTC());
    }

    public NoonGapTaskOutcomeReducer(NoonDataCompletenessRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public NoonDataGapWindowRecord reduce(NoonPullTaskRecord task) {
        if (task == null) {
            throw new IllegalArgumentException("缺少 pull task，无法收敛缺口状态。");
        }
        NoonDataGapWindowRecord gap = findGap(task);
        if (gap == null) {
            throw new IllegalArgumentException("未找到 pull task 对应的数据缺口：" + task.getId());
        }

        NoonDataGapWindowRecord updated = gap.copy();
        updated.setLinkedPullTaskId(task.getId());
        updated.setLinkedPullPlanId(task.getPlanId());
        updated.setLinkedSourceBatchId(task.getSourceBatchId());
        updated.setRowOrItemCount(rowOrItemCount(task));
        updated.setDiagnosticSummary(NoonDataCompletenessSafeText.redact(task.getDiagnosticSummary()));
        updated.setUpdatedAt(now());

        if (task.getStatus() == NoonPullTaskStatus.SUCCEEDED) {
            applySucceeded(task, updated);
        } else if (task.getStatus() == NoonPullTaskStatus.PARTIAL) {
            applyPartial(task, updated);
        } else if (task.getStatus() == NoonPullTaskStatus.FAILED) {
            applyFailed(task, updated);
        } else if (task.getStatus() == NoonPullTaskStatus.CANCELLED || task.getStatus() == NoonPullTaskStatus.SKIPPED) {
            updated.setStatus(NoonDataGapStatus.CANCELLED);
            updated.setRetryable(Boolean.FALSE);
        } else {
            updated.setStatus(NoonDataGapStatus.RUNNING);
        }

        repository.insertGapWindow(updated);
        updateCompleteness(task, updated);
        return updated.copy();
    }

    private void applySucceeded(NoonPullTaskRecord task, NoonDataGapWindowRecord gap) {
        if (rowOrItemCount(task) > 0) {
            gap.setStatus(NoonDataGapStatus.SUCCEEDED);
            gap.setFailureType(null);
            gap.setRetryable(Boolean.FALSE);
            gap.setRequiresManualAction(Boolean.FALSE);
            gap.setNextRetryAt(null);
            return;
        }
        if (gap.getWindowType() == NoonDataGapWindowType.LATEST_DAILY) {
            gap.setStatus(NoonDataGapStatus.PENDING_CONFIRMATION);
            gap.setFailureType("empty_report_pending_confirmation");
            gap.setRetryable(Boolean.TRUE);
            gap.setRequiresManualAction(Boolean.FALSE);
            gap.setNextRetryAt(now().plusHours(2));
            return;
        }
        gap.setStatus(NoonDataGapStatus.CONFIRMED_EMPTY);
        gap.setFailureType("confirmed_empty");
        gap.setRetryable(Boolean.FALSE);
        gap.setRequiresManualAction(Boolean.FALSE);
        gap.setNextRetryAt(null);
        gap.setCompletedEmptyEvidenceSummary("confirmed empty " + gap.getDateFrom() + ".." + gap.getDateTo());
    }

    private void applyPartial(NoonPullTaskRecord task, NoonDataGapWindowRecord gap) {
        gap.setStatus(NoonDataGapStatus.WAITING_RETRY);
        gap.setFailureType(task.getFailureType() == null ? "partial_success" : task.getFailureType());
        gap.setRetryable(Boolean.TRUE);
        gap.setRequiresManualAction(Boolean.FALSE);
        gap.setAttempts(nextAttempts(gap));
        gap.setNextRetryAt(now().plusMinutes(30));
    }

    private void applyFailed(NoonPullTaskRecord task, NoonDataGapWindowRecord gap) {
        String failureType = task.getFailureType();
        gap.setFailureType(failureType);
        gap.setAttempts(nextAttempts(gap));
        if ("provider_retention_limit".equals(failureType)) {
            gap.setStatus(NoonDataGapStatus.PROVIDER_RETENTION_LIMIT);
            gap.setRetryable(Boolean.FALSE);
            gap.setRequiresManualAction(Boolean.FALSE);
            gap.setNextRetryAt(null);
            return;
        }
        if (Boolean.TRUE.equals(task.getRetryable()) && !Boolean.TRUE.equals(task.getRequiresManualAction())) {
            gap.setStatus(NoonDataGapStatus.WAITING_RETRY);
            gap.setRetryable(Boolean.TRUE);
            gap.setRequiresManualAction(Boolean.FALSE);
            gap.setNextRetryAt(now().plusMinutes(30));
            return;
        }
        gap.setStatus(NoonDataGapStatus.FAILED);
        gap.setRetryable(Boolean.FALSE);
        gap.setRequiresManualAction(Boolean.TRUE.equals(task.getRequiresManualAction()));
        gap.setNextRetryAt(null);
    }

    private void updateCompleteness(NoonPullTaskRecord task, NoonDataGapWindowRecord gap) {
        NoonDataCompletenessRecord row = findCompleteness(gap);
        if (row == null) {
            return;
        }
        NoonDataCompletenessRecord updated = row.copy();
        updated.setLastTaskId(task.getId());
        updated.setLastSourceBatchId(task.getSourceBatchId());
        updated.setLastFailureType(gap.getFailureType());
        updated.setDiagnosticSummary(NoonDataCompletenessSafeText.redact(task.getDiagnosticSummary()));
        updated.setUpdatedAt(now());

        if (gap.getStatus() == NoonDataGapStatus.SUCCEEDED && rowOrItemCount(task) > 0) {
            if (gap.getWindowType() == NoonDataGapWindowType.LATEST_DAILY) {
                updated.setLatestStatus(NoonDataLatestStatus.READY);
                updated.setLatestDataDate(gap.getDateTo());
            } else if (gap.getWindowType() == NoonDataGapWindowType.HISTORY_BACKFILL) {
                updated.setHistoryCoveredFrom(minDate(updated.getHistoryCoveredFrom(), gap.getDateFrom()));
                updated.setHistoryCoveredTo(maxDate(updated.getHistoryCoveredTo(), gap.getDateTo()));
                updated.setHistoryStatus(hasActiveHistoryGap(gap)
                        ? NoonDataHistoryStatus.INCOMPLETE
                        : NoonDataHistoryStatus.COMPLETE);
            }
        } else if (gap.getStatus() == NoonDataGapStatus.CONFIRMED_EMPTY) {
            if (!hasActiveHistoryGap(gap)) {
                updated.setHistoryStatus(NoonDataHistoryStatus.CONFIRMED_EMPTY);
            }
        } else if (gap.getStatus() == NoonDataGapStatus.PROVIDER_RETENTION_LIMIT
                && gap.getWindowType() == NoonDataGapWindowType.HISTORY_BACKFILL) {
            updated.setHistoryStatus(NoonDataHistoryStatus.PROVIDER_RETENTION_LIMIT);
        }

        int activeGapCount = activeGapCount(gap);
        updated.setActiveGapCount(activeGapCount);
        updated.setPatrolEnabled(activeGapCount > 0);
        repository.insertCompleteness(updated);
    }

    private NoonDataGapWindowRecord findGap(NoonPullTaskRecord task) {
        NoonDataGapQuery query = new NoonDataGapQuery();
        query.setOwnerUserId(task.getOwnerUserId());
        query.setStoreCode(task.getStoreCode());
        query.setSiteCode(task.getSiteCode());
        query.setCategory(category(task));
        for (NoonDataGapWindowRecord gap : repository.listGapWindows(query)) {
            if (Objects.equals(gap.getLinkedPullTaskId(), task.getId())) {
                return gap;
            }
        }
        for (NoonDataGapWindowRecord gap : repository.listGapWindows(query)) {
            if (Objects.equals(gap.getDateFrom(), task.getTargetDateFrom())
                    && Objects.equals(gap.getDateTo(), task.getTargetDateTo())) {
                return gap;
            }
        }
        return null;
    }

    private NoonDataCompletenessRecord findCompleteness(NoonDataGapWindowRecord gap) {
        NoonDataCompletenessQuery query = new NoonDataCompletenessQuery();
        query.setOwnerUserId(gap.getOwnerUserId());
        query.setStoreCode(gap.getStoreCode());
        query.setSiteCode(gap.getSiteCode());
        query.setCategory(gap.getCategory());
        return repository.listCompleteness(query).stream().findFirst().orElse(null);
    }

    private boolean hasActiveHistoryGap(NoonDataGapWindowRecord current) {
        NoonDataGapQuery query = new NoonDataGapQuery();
        query.setOwnerUserId(current.getOwnerUserId());
        query.setStoreCode(current.getStoreCode());
        query.setSiteCode(current.getSiteCode());
        query.setCategory(current.getCategory());
        return repository.listGapWindows(query).stream()
                .filter((gap) -> gap.getWindowType() == NoonDataGapWindowType.HISTORY_BACKFILL)
                .filter((gap) -> !sameGap(gap, current))
                .anyMatch(this::isActive);
    }

    private int activeGapCount(NoonDataGapWindowRecord current) {
        NoonDataGapQuery query = new NoonDataGapQuery();
        query.setOwnerUserId(current.getOwnerUserId());
        query.setStoreCode(current.getStoreCode());
        query.setSiteCode(current.getSiteCode());
        query.setCategory(current.getCategory());
        List<NoonDataGapWindowRecord> gaps = repository.listGapWindows(query);
        int count = 0;
        for (NoonDataGapWindowRecord gap : gaps) {
            NoonDataGapWindowRecord effective = sameGap(gap, current) ? current : gap;
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

    private boolean sameGap(NoonDataGapWindowRecord left, NoonDataGapWindowRecord right) {
        return Objects.equals(left.getCompletenessId(), right.getCompletenessId())
                && left.getCategory() == right.getCategory()
                && left.getWindowType() == right.getWindowType()
                && Objects.equals(left.getDateFrom(), right.getDateFrom())
                && Objects.equals(left.getDateTo(), right.getDateTo());
    }

    private NoonDataCategory category(NoonPullTaskRecord task) {
        if (task.getDataDomain() == NoonPullDataDomain.ORDER) {
            return NoonDataCategory.SALES_ORDER;
        }
        if (task.getDataDomain() == NoonPullDataDomain.PRODUCT) {
            return NoonDataCategory.PRODUCT_LIST;
        }
        return NoonDataCategory.SALES_PRODUCT_VIEWS;
    }

    private int rowOrItemCount(NoonPullTaskRecord task) {
        return task.getProcessedItemCount() == null ? 0 : Math.max(task.getProcessedItemCount(), 0);
    }

    private int nextAttempts(NoonDataGapWindowRecord gap) {
        return gap.getAttempts() == null ? 1 : gap.getAttempts() + 1;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private java.time.LocalDate minDate(java.time.LocalDate left, java.time.LocalDate right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isBefore(right) ? left : right;
    }

    private java.time.LocalDate maxDate(java.time.LocalDate left, java.time.LocalDate right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }
}
