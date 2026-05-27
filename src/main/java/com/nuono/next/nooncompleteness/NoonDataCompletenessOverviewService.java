package com.nuono.next.nooncompleteness;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class NoonDataCompletenessOverviewService {
    private final NoonDataCompletenessRepository repository;

    public NoonDataCompletenessOverviewService(NoonDataCompletenessRepository repository) {
        this.repository = repository;
    }

    public NoonDataCompletenessOverviewView overview(NoonDataCompletenessQuery query) {
        List<NoonDataCompletenessRecord> records = repository.listCompleteness(query);
        records.sort(Comparator
                .comparing(NoonDataCompletenessRecord::getOwnerUserId, Comparator.nullsLast(Long::compareTo))
                .thenComparing(NoonDataCompletenessRecord::getStoreCode, Comparator.nullsLast(String::compareTo))
                .thenComparing(NoonDataCompletenessRecord::getSiteCode, Comparator.nullsLast(String::compareTo))
                .thenComparing((record) -> record.getCategory() == null ? "" : record.getCategory().name()));

        NoonDataCompletenessOverviewView view = new NoonDataCompletenessOverviewView();
        view.setGeneratedAt(LocalDateTime.now());
        view.setMetrics(metrics(records));
        view.setCategoryDistribution(distribution(records, NoonDataCompletenessRecord::getCategory));
        view.setLatestStatusDistribution(distribution(records, NoonDataCompletenessRecord::getLatestStatus));
        view.setHistoryStatusDistribution(distribution(records, NoonDataCompletenessRecord::getHistoryStatus));
        view.setRows(records.stream().map(this::row).collect(Collectors.toList()));
        return view;
    }

    private List<NoonDataCompletenessOverviewView.Metric> metrics(List<NoonDataCompletenessRecord> records) {
        long total = records.size();
        long latestReady = records.stream().filter((record) -> record.getLatestStatus() == NoonDataLatestStatus.READY).count();
        long historyIncomplete = records.stream()
                .filter((record) -> record.getHistoryStatus() == NoonDataHistoryStatus.INCOMPLETE)
                .count();
        long activePatrol = records.stream().filter(NoonDataCompletenessRecord::isPatrolEnabled).count();
        return List.of(
                metric("total_categories", "数据分类", total, "项", total == 0 ? "empty" : "ready"),
                metric("latest_ready", "最近已就绪", latestReady, "项", latestReady == total && total > 0 ? "ready" : "warning"),
                metric("history_incomplete", "历史不完整", historyIncomplete, "项", historyIncomplete > 0 ? "warning" : "ready"),
                metric("active_patrol", "巡检中", activePatrol, "项", activePatrol > 0 ? "working" : "empty")
        );
    }

    private NoonDataCompletenessOverviewView.Metric metric(
            String key,
            String title,
            long value,
            String unit,
            String state
    ) {
        return new NoonDataCompletenessOverviewView.Metric(key, title, value, unit, state);
    }

    private <E extends Enum<E>> List<NoonDataCompletenessOverviewView.DistributionItem> distribution(
            List<NoonDataCompletenessRecord> records,
            Function<NoonDataCompletenessRecord, E> extractor
    ) {
        Map<E, Long> counts = new EnumMap<>(enumType(records, extractor));
        for (NoonDataCompletenessRecord record : records) {
            E value = extractor.apply(record);
            if (value != null) {
                counts.merge(value, 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map((entry) -> new NoonDataCompletenessOverviewView.DistributionItem(
                        key(entry.getKey()),
                        entry.getKey().name(),
                        entry.getValue()
                ))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private <E extends Enum<E>> Class<E> enumType(
            List<NoonDataCompletenessRecord> records,
            Function<NoonDataCompletenessRecord, E> extractor
    ) {
        for (NoonDataCompletenessRecord record : records) {
            E value = extractor.apply(record);
            if (value != null) {
                return (Class<E>) value.getDeclaringClass();
            }
        }
        return (Class<E>) NoonDataCategory.class;
    }

    private String key(Enum<?> value) {
        if (value instanceof NoonDataCategory) {
            return value.name();
        }
        return value.name().toLowerCase(Locale.ROOT);
    }

    private NoonDataCompletenessOverviewView.Row row(NoonDataCompletenessRecord record) {
        NoonDataCompletenessOverviewView.Row row = new NoonDataCompletenessOverviewView.Row();
        row.setId(record.getId());
        row.setOwnerUserId(record.getOwnerUserId());
        row.setStoreCode(record.getStoreCode());
        row.setSiteCode(record.getSiteCode());
        row.setCategory(record.getCategory());
        row.setLatestStatus(record.getLatestStatus());
        row.setHistoryStatus(record.getHistoryStatus());
        row.setLatestDataDate(record.getLatestDataDate());
        row.setHistoryCoveredFrom(record.getHistoryCoveredFrom());
        row.setHistoryCoveredTo(record.getHistoryCoveredTo());
        row.setPatrolEnabled(record.isPatrolEnabled());
        row.setNextPatrolAt(record.getNextPatrolAt());
        row.setActiveGapCount(record.getActiveGapCount());
        return row;
    }
}
