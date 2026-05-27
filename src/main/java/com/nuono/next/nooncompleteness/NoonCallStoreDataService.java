package com.nuono.next.nooncompleteness;

import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonPullTaskRecord;
import com.nuono.next.noonpull.NoonPullTaskLookupQuery;
import com.nuono.next.noonpull.NoonPullTaskLookupService;
import com.nuono.next.noonpull.NoonPullTaskStatus;
import com.nuono.next.noonpull.NoonPullType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class NoonCallStoreDataService {
    private static final List<NoonDataCategory> CATEGORY_ORDER = List.of(
            NoonDataCategory.PRODUCT_LIST,
            NoonDataCategory.PRODUCT_DETAIL,
            NoonDataCategory.SALES_ORDER,
            NoonDataCategory.SALES_PRODUCT_VIEWS
    );

    private final NoonDataCompletenessRepository completenessRepository;
    private final NoonPullTaskLookupService taskLookupService;

    public NoonCallStoreDataService(
            NoonDataCompletenessRepository completenessRepository,
            NoonPullTaskLookupService taskLookupService
    ) {
        this.completenessRepository = completenessRepository;
        this.taskLookupService = taskLookupService;
    }

    public NoonCallStoreDataView view(NoonDataCompletenessQuery query) {
        List<NoonDataCompletenessRecord> records = completenessRepository.listCompleteness(query == null ? new NoonDataCompletenessQuery() : query);
        List<NoonDataGapWindowRecord> gaps = completenessRepository.listGapWindows(new NoonDataGapQuery());
        Map<ScopeKey, List<NoonDataCompletenessRecord>> grouped = records.stream()
                .collect(Collectors.groupingBy(ScopeKey::from, LinkedHashMap::new, Collectors.toList()));

        NoonCallStoreDataView view = new NoonCallStoreDataView();
        view.setGeneratedAt(LocalDateTime.now());
        List<NoonCallStoreDataView.Row> rows = new ArrayList<>();
        for (Map.Entry<ScopeKey, List<NoonDataCompletenessRecord>> entry : grouped.entrySet()) {
            rows.add(row(entry.getKey(), entry.getValue(), gaps));
        }
        rows.sort(Comparator
                .comparing(NoonCallStoreDataView.Row::getOwnerUserId, Comparator.nullsLast(Long::compareTo))
                .thenComparing(NoonCallStoreDataView.Row::getStoreCode, Comparator.nullsLast(String::compareTo))
                .thenComparing(NoonCallStoreDataView.Row::getSiteCode, Comparator.nullsLast(String::compareTo)));
        view.setRows(rows);
        view.setMetrics(List.of(
                new NoonDataCompletenessOverviewView.Metric("store_count", "店铺站点", rows.size(), "个", rows.isEmpty() ? "empty" : "ready"),
                new NoonDataCompletenessOverviewView.Metric("complete_cells", "完整数据项", countCells(rows, "COMPLETE"), "项", "ready"),
                new NoonDataCompletenessOverviewView.Metric("pending_cells", "待同步数据项", countPending(rows), "项", countPending(rows) > 0 ? "warning" : "ready"),
                new NoonDataCompletenessOverviewView.Metric("failed_cells", "失败数据项", countCells(rows, "FAILED"), "项", countCells(rows, "FAILED") > 0 ? "warning" : "ready")
        ));
        return view;
    }

    private NoonCallStoreDataView.Row row(
            ScopeKey key,
            List<NoonDataCompletenessRecord> records,
            List<NoonDataGapWindowRecord> gaps
    ) {
        NoonCallStoreDataView.Row row = new NoonCallStoreDataView.Row();
        row.setOwnerUserId(key.ownerUserId);
        row.setStoreName(storeName(records));
        row.setStoreCode(key.storeCode);
        row.setSiteCode(key.siteCode);
        List<NoonCallStoreDataView.CategoryCell> cells = new ArrayList<>();
        for (NoonDataCategory category : CATEGORY_ORDER) {
            NoonDataCompletenessRecord record = records.stream()
                    .filter((candidate) -> candidate.getCategory() == category)
                    .findFirst()
                    .orElse(null);
            if (record != null) {
                cells.add(cell(record, gaps));
            }
        }
        row.setCategories(cells);
        row.setLastSyncAt(cells.stream()
                .map(NoonCallStoreDataView.CategoryCell::getLastSyncAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null));
        row.setOverallMarker(overallMarker(cells));
        return row;
    }

    private String storeName(List<NoonDataCompletenessRecord> records) {
        return records.stream()
                .map(NoonDataCompletenessRecord::getStoreName)
                .filter((value) -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private NoonCallStoreDataView.CategoryCell cell(
            NoonDataCompletenessRecord record,
            List<NoonDataGapWindowRecord> gaps
    ) {
        NoonPullTaskRecord latestTask = latestTask(record);
        boolean manualAction = gaps.stream()
                .filter((gap) -> sameScope(record, gap) && record.getCategory() == gap.getCategory())
                .anyMatch((gap) -> Boolean.TRUE.equals(gap.getRequiresManualAction()));
        NoonCallStoreDataView.CategoryCell cell = new NoonCallStoreDataView.CategoryCell();
        cell.setCategory(record.getCategory());
        cell.setLabel(label(record.getCategory()));
        cell.setCompletenessId(record.getId());
        cell.setLatestStatus(record.getLatestStatus());
        cell.setHistoryStatus(record.getHistoryStatus());
        cell.setLatestDataDate(record.getLatestDataDate());
        cell.setHistoryCoveredFrom(record.getHistoryCoveredFrom());
        cell.setHistoryCoveredTo(record.getHistoryCoveredTo());
        cell.setActiveGapCount(record.getActiveGapCount());
        cell.setRequiresManualAction(manualAction);
        cell.setDiagnosticSummary(NoonDataCompletenessSafeText.redact(record.getDiagnosticSummary()));
        cell.setFailureType(record.getLastFailureType());
        if (latestTask != null) {
            cell.setLatestTaskId(latestTask.getId());
            cell.setLatestTaskStatus(latestTask.getStatus() == null ? null : latestTask.getStatus().name());
            cell.setReadinessState(latestTask.getReadinessState());
            cell.setFailureType(latestTask.getFailureType() == null ? record.getLastFailureType() : latestTask.getFailureType());
            cell.setProcessedItemCount(latestTask.getProcessedItemCount());
            cell.setRequestCount(latestTask.getRequestCount());
            cell.setLastSyncAt(taskLookupService.taskTimestamp(latestTask));
            cell.setDiagnosticSummary(NoonDataCompletenessSafeText.redact(latestTask.getDiagnosticSummary()));
        }
        cell.setMarker(marker(record, latestTask, manualAction));
        cell.setSyncable(!"NOT_INTEGRATED".equals(cell.getMarker()) && !"MANUAL_ACTION".equals(cell.getMarker()));
        return cell;
    }

    private NoonPullTaskRecord latestTask(NoonDataCompletenessRecord record) {
        NoonPullTaskLookupQuery query = taskQuery(record, true);
        if (query == null) {
            return null;
        }
        Optional<NoonPullTaskRecord> latestTask = taskLookupService.latestTask(query);
        if (latestTask.isPresent() || record.getCategory() != NoonDataCategory.SALES_PRODUCT_VIEWS) {
            return latestTask.orElse(null);
        }
        return taskLookupService.latestTask(taskQuery(record, false)).orElse(null);
    }

    private NoonPullTaskLookupQuery taskQuery(NoonDataCompletenessRecord record, boolean includeTargetPrefix) {
        NoonPullTaskLookupQuery.Builder builder = NoonPullTaskLookupQuery.builder()
                .ownerUserId(record.getOwnerUserId())
                .storeCode(record.getStoreCode())
                .siteCode(record.getSiteCode());
        if (record.getCategory() == NoonDataCategory.PRODUCT_LIST) {
            return builder
                    .pullType(NoonPullType.INTERFACE)
                    .dataDomain(NoonPullDataDomain.PRODUCT)
                    .targetIdentityPrefix(includeTargetPrefix ? "product-list:" : null)
                    .build();
        }
        if (record.getCategory() == NoonDataCategory.PRODUCT_DETAIL) {
            return builder
                    .pullType(NoonPullType.INTERFACE)
                    .dataDomain(NoonPullDataDomain.PRODUCT)
                    .targetIdentityPrefix(includeTargetPrefix ? "product-detail:" : null)
                    .build();
        }
        if (record.getCategory() == NoonDataCategory.SALES_ORDER) {
            return builder
                    .dataDomain(NoonPullDataDomain.ORDER)
                    .build();
        }
        if (record.getCategory() == NoonDataCategory.SALES_PRODUCT_VIEWS) {
            return builder
                    .pullType(NoonPullType.REPORT)
                    .dataDomain(NoonPullDataDomain.SALES)
                    .targetIdentityPrefix(includeTargetPrefix ? "sales-product-views:" : null)
                    .build();
        }
        return null;
    }

    private String marker(NoonDataCompletenessRecord record, NoonPullTaskRecord task, boolean manualAction) {
        if (task != null && (task.getStatus() == NoonPullTaskStatus.QUEUED || task.getStatus() == NoonPullTaskStatus.RUNNING)) {
            return "SYNCING";
        }
        if (record.getLatestStatus() == NoonDataLatestStatus.NOT_INTEGRATED
                || record.getHistoryStatus() == NoonDataHistoryStatus.NOT_INTEGRATED) {
            return "NOT_INTEGRATED";
        }
        if (manualAction) {
            return "MANUAL_ACTION";
        }
        if (record.getLatestStatus() == NoonDataLatestStatus.PENDING_CONFIRMATION) {
            return "PENDING_CONFIRMATION";
        }
        if ((task != null && task.getStatus() == NoonPullTaskStatus.FAILED)
                || record.getLatestStatus() == NoonDataLatestStatus.FAILED
                || record.getHistoryStatus() == NoonDataHistoryStatus.FAILED) {
            return "FAILED";
        }
        if (record.getLatestStatus() == NoonDataLatestStatus.READY
                && (record.getHistoryStatus() == NoonDataHistoryStatus.COMPLETE
                || record.getHistoryStatus() == NoonDataHistoryStatus.NOT_REQUIRED
                || record.getHistoryStatus() == NoonDataHistoryStatus.CONFIRMED_EMPTY)) {
            return "COMPLETE";
        }
        return "PENDING_SYNC";
    }

    private String overallMarker(List<NoonCallStoreDataView.CategoryCell> cells) {
        if (cells.stream().anyMatch((cell) -> "SYNCING".equals(cell.getMarker()))) {
            return "SYNCING";
        }
        if (cells.stream().anyMatch((cell) -> "FAILED".equals(cell.getMarker()))) {
            return "FAILED";
        }
        if (cells.stream().anyMatch((cell) -> "MANUAL_ACTION".equals(cell.getMarker()))) {
            return "MANUAL_ACTION";
        }
        if (cells.stream().allMatch((cell) -> "COMPLETE".equals(cell.getMarker()))) {
            return "COMPLETE";
        }
        return "PENDING_SYNC";
    }

    private static long countCells(List<NoonCallStoreDataView.Row> rows, String marker) {
        return rows.stream()
                .flatMap((row) -> row.getCategories().stream())
                .filter((cell) -> marker.equals(cell.getMarker()))
                .count();
    }

    private static long countPending(List<NoonCallStoreDataView.Row> rows) {
        return rows.stream()
                .flatMap((row) -> row.getCategories().stream())
                .filter((cell) -> "PENDING_SYNC".equals(cell.getMarker()) || "PENDING_CONFIRMATION".equals(cell.getMarker()))
                .count();
    }

    private boolean sameScope(NoonDataCompletenessRecord record, NoonDataGapWindowRecord gap) {
        return Objects.equals(record.getOwnerUserId(), gap.getOwnerUserId())
                && Objects.equals(record.getStoreCode(), gap.getStoreCode())
                && Objects.equals(record.getSiteCode(), gap.getSiteCode());
    }

    private String label(NoonDataCategory category) {
        if (category == NoonDataCategory.PRODUCT_LIST) {
            return "商品列表信息";
        }
        if (category == NoonDataCategory.PRODUCT_DETAIL) {
            return "商品信息";
        }
        if (category == NoonDataCategory.SALES_ORDER) {
            return "订单数据";
        }
        if (category == NoonDataCategory.SALES_PRODUCT_VIEWS) {
            return "销量数据";
        }
        return category == null ? "-" : category.name();
    }

    private static final class ScopeKey {
        private final Long ownerUserId;
        private final String storeCode;
        private final String siteCode;

        private ScopeKey(Long ownerUserId, String storeCode, String siteCode) {
            this.ownerUserId = ownerUserId;
            this.storeCode = storeCode;
            this.siteCode = siteCode;
        }

        private static ScopeKey from(NoonDataCompletenessRecord record) {
            return new ScopeKey(record.getOwnerUserId(), record.getStoreCode(), record.getSiteCode());
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ScopeKey)) {
                return false;
            }
            ScopeKey scopeKey = (ScopeKey) other;
            return Objects.equals(ownerUserId, scopeKey.ownerUserId)
                    && Objects.equals(storeCode, scopeKey.storeCode)
                    && Objects.equals(siteCode, scopeKey.siteCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ownerUserId, storeCode, siteCode);
        }
    }
}
