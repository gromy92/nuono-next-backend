package com.nuono.next.nooncompleteness;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class NoonDataGapPatrolViewService {
    private final NoonDataCompletenessRepository repository;

    public NoonDataGapPatrolViewService(NoonDataCompletenessRepository repository) {
        this.repository = repository;
    }

    public NoonDataGapPatrolView gaps(NoonDataGapQuery query) {
        List<NoonDataGapWindowRecord> gaps = repository.listGapWindows(query);
        gaps.sort(Comparator
                .comparing(NoonDataGapWindowRecord::getOwnerUserId, Comparator.nullsLast(Long::compareTo))
                .thenComparing(NoonDataGapWindowRecord::getStoreCode, Comparator.nullsLast(String::compareTo))
                .thenComparing(NoonDataGapWindowRecord::getSiteCode, Comparator.nullsLast(String::compareTo))
                .thenComparing((gap) -> gap.getCategory() == null ? "" : gap.getCategory().name()));
        NoonDataGapPatrolView view = new NoonDataGapPatrolView();
        view.setGeneratedAt(LocalDateTime.now());
        view.setRows(gaps.stream().map(this::row).collect(Collectors.toList()));
        view.setMetrics(List.of(
                new NoonDataCompletenessOverviewView.Metric("total_gaps", "缺口窗口", gaps.size(), "个", gaps.isEmpty() ? "empty" : "warning"),
                new NoonDataCompletenessOverviewView.Metric("retryable_gaps", "可重试", count(gaps, Boolean.TRUE), "个", "working"),
                new NoonDataCompletenessOverviewView.Metric("manual_action_gaps", "需人工处理", manual(gaps), "个", manual(gaps) > 0 ? "warning" : "ready")
        ));
        view.setStatusDistribution(statusDistribution(gaps));
        view.setFailureDistribution(failureDistribution(gaps));
        return view;
    }

    private long count(List<NoonDataGapWindowRecord> gaps, Boolean retryable) {
        return gaps.stream().filter((gap) -> retryable.equals(gap.getRetryable())).count();
    }

    private long manual(List<NoonDataGapWindowRecord> gaps) {
        return gaps.stream().filter((gap) -> Boolean.TRUE.equals(gap.getRequiresManualAction())).count();
    }

    private List<NoonDataCompletenessOverviewView.DistributionItem> statusDistribution(
            List<NoonDataGapWindowRecord> gaps
    ) {
        Map<NoonDataGapStatus, Long> counts = new EnumMap<>(NoonDataGapStatus.class);
        for (NoonDataGapWindowRecord gap : gaps) {
            if (gap.getStatus() != null) {
                counts.merge(gap.getStatus(), 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map((entry) -> new NoonDataCompletenessOverviewView.DistributionItem(
                        entry.getKey().name().toLowerCase(Locale.ROOT),
                        entry.getKey().name(),
                        entry.getValue()
                ))
                .collect(Collectors.toList());
    }

    private List<NoonDataCompletenessOverviewView.DistributionItem> failureDistribution(
            List<NoonDataGapWindowRecord> gaps
    ) {
        Map<String, Long> counts = gaps.stream()
                .filter((gap) -> gap.getFailureType() != null && !gap.getFailureType().isBlank())
                .collect(Collectors.groupingBy(NoonDataGapWindowRecord::getFailureType, Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map((entry) -> new NoonDataCompletenessOverviewView.DistributionItem(
                        entry.getKey(),
                        entry.getKey(),
                        entry.getValue()
                ))
                .collect(Collectors.toList());
    }

    private NoonDataGapPatrolView.Row row(NoonDataGapWindowRecord record) {
        NoonDataGapPatrolView.Row row = new NoonDataGapPatrolView.Row();
        row.setId(record.getId());
        row.setCompletenessId(record.getCompletenessId());
        row.setOwnerUserId(record.getOwnerUserId());
        row.setStoreCode(record.getStoreCode());
        row.setSiteCode(record.getSiteCode());
        row.setCategory(record.getCategory());
        row.setWindowType(record.getWindowType());
        row.setDateFrom(record.getDateFrom());
        row.setDateTo(record.getDateTo());
        row.setStatus(record.getStatus());
        row.setAttempts(record.getAttempts());
        row.setNextRetryAt(record.getNextRetryAt());
        row.setLinkedPullTaskId(record.getLinkedPullTaskId());
        row.setLinkedSourceBatchId(record.getLinkedSourceBatchId());
        row.setRowOrItemCount(record.getRowOrItemCount());
        row.setFailureType(record.getFailureType());
        row.setRetryable(record.getRetryable());
        row.setRequiresManualAction(record.getRequiresManualAction());
        row.setDiagnosticSummary(NoonDataCompletenessSafeText.redact(record.getDiagnosticSummary()));
        return row;
    }
}
