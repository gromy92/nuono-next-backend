package com.nuono.next.sales;

import com.nuono.next.nooncompleteness.NoonDataCategory;
import com.nuono.next.nooncompleteness.NoonDataCompletenessRepository;
import com.nuono.next.nooncompleteness.NoonDataGapQuery;
import com.nuono.next.nooncompleteness.NoonDataGapStatus;
import com.nuono.next.nooncompleteness.NoonDataGapWindowRecord;
import com.nuono.next.nooncompleteness.NoonDataGapWindowType;
import com.nuono.next.nooncompleteness.NoonGapPatrolActionResult;
import com.nuono.next.nooncompleteness.NoonGapPatrolActionService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SalesHistoryBackfillService {
    private static final List<NoonDataCategory> SALES_HISTORY_CATEGORIES = List.of(
            NoonDataCategory.SALES_PRODUCT_VIEWS,
            NoonDataCategory.SALES_ORDER
    );

    private final NoonDataCompletenessRepository completenessRepository;
    private final NoonGapPatrolActionService actionService;

    public SalesHistoryBackfillService(
            NoonDataCompletenessRepository completenessRepository,
            NoonGapPatrolActionService actionService
    ) {
        this.completenessRepository = completenessRepository;
        this.actionService = actionService;
    }

    public SalesHistoryCoverage coverage(
            SalesFactQuery query,
            List<DailySalesFact> facts,
            SalesPriceTrendResult priceTrend
    ) {
        LocalDate salesDateFrom = facts == null ? null : facts.stream()
                .map(DailySalesFact::getFactDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);
        LocalDate salesDateTo = facts == null ? null : facts.stream()
                .map(DailySalesFact::getFactDate)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
        List<SalesPriceTrendBucket> priceBuckets = priceTrend == null ? List.of() : priceTrend.getBuckets();
        LocalDate priceDateFrom = priceBuckets.stream()
                .filter((bucket) -> bucket.getBucketStart() != null && bucket.getOrderLineCount() > 0)
                .map(SalesPriceTrendBucket::getBucketStart)
                .min(LocalDate::compareTo)
                .orElse(null);
        LocalDate priceDateTo = priceBuckets.stream()
                .filter((bucket) -> bucket.getBucketStart() != null && bucket.getOrderLineCount() > 0)
                .map(SalesPriceTrendBucket::getBucketStart)
                .max(LocalDate::compareTo)
                .orElse(null);

        boolean salesCovered = covers(query, salesDateFrom, salesDateTo);
        boolean priceCovered = covers(query, priceDateFrom, priceDateTo);
        SalesHistoryBackfillStatus backfill = backfillStatus(query, salesCovered, priceCovered);
        return new SalesHistoryCoverage(
                query.getDateFrom(),
                query.getDateTo(),
                salesDateFrom,
                salesDateTo,
                priceDateFrom,
                priceDateTo,
                salesCovered,
                priceCovered,
                backfill
        );
    }

    public SalesHistoryBackfillResult requestBackfill(SalesHistoryBackfillCommand command) {
        if (actionService == null) {
            throw new IllegalStateException("历史补全动作服务未启用。");
        }
        List<Long> plannedTaskIds = new ArrayList<>();
        List<Long> gapIds = new ArrayList<>();
        List<String> categories = new ArrayList<>();
        for (NoonDataCategory category : SALES_HISTORY_CATEGORIES) {
            NoonGapPatrolActionResult result = actionService.syncHistoryRange(
                    command.getOwnerUserId(),
                    command.getStoreCode(),
                    command.getSiteCode(),
                    category,
                    command.getDateFrom(),
                    command.getDateTo()
            );
            categories.add(category.name());
            if (result.getGapId() != null) {
                gapIds.add(result.getGapId());
            }
            plannedTaskIds.addAll(result.getPlannedTaskIds());
        }
        return new SalesHistoryBackfillResult(
                plannedTaskIds.size(),
                plannedTaskIds,
                gapIds,
                categories,
                plannedTaskIds.isEmpty() ? "历史补全缺口已记录，等待调度。" : "已提交历史补全任务。"
        );
    }

    private boolean covers(SalesFactQuery query, LocalDate dateFrom, LocalDate dateTo) {
        if (query == null || query.getDateFrom() == null || query.getDateTo() == null) {
            return true;
        }
        return dateFrom != null
                && dateTo != null
                && !dateFrom.isAfter(query.getDateFrom())
                && !dateTo.isBefore(query.getDateTo());
    }

    private SalesHistoryBackfillStatus backfillStatus(SalesFactQuery query, boolean salesCovered, boolean priceCovered) {
        Set<NoonDataCategory> requiredCategories = requiredCategories(salesCovered, priceCovered);
        if (requiredCategories.isEmpty()) {
            return SalesHistoryBackfillStatus.covered();
        }
        List<NoonDataGapWindowRecord> gaps = dropStaleGapsCoveredByCompletedHistory(overlappingSalesHistoryGaps(query, requiredCategories));
        if (completedHistoryCoversRequiredCategories(gaps, requiredCategories, query)) {
            return SalesHistoryBackfillStatus.covered();
        }
        List<NoonDataGapWindowRecord> openGaps = gaps.stream()
                .filter((gap) -> !isCompletedHistoryGap(gap))
                .collect(Collectors.toList());
        if (openGaps.isEmpty()) {
            return SalesHistoryBackfillStatus.needsBackfill();
        }
        String state = statusState(openGaps);
        return SalesHistoryBackfillStatus.of(state, gapIds(openGaps), taskIds(openGaps), categories(openGaps));
    }

    private Set<NoonDataCategory> requiredCategories(boolean salesCovered, boolean priceCovered) {
        Set<NoonDataCategory> categories = new LinkedHashSet<>();
        if (!salesCovered) {
            categories.add(NoonDataCategory.SALES_PRODUCT_VIEWS);
        }
        if (!priceCovered) {
            categories.add(NoonDataCategory.SALES_ORDER);
        }
        return categories;
    }

    private List<NoonDataGapWindowRecord> overlappingSalesHistoryGaps(
            SalesFactQuery query,
            Set<NoonDataCategory> requiredCategories
    ) {
        if (completenessRepository == null || query == null) {
            return List.of();
        }
        NoonDataGapQuery gapQuery = new NoonDataGapQuery();
        gapQuery.setOwnerUserId(query.getOwnerUserId());
        gapQuery.setStoreCode(query.getStoreCode());
        gapQuery.setSiteCode(query.getSiteCode());
        return completenessRepository.listGapWindows(gapQuery).stream()
                .filter((gap) -> requiredCategories.contains(gap.getCategory()))
                .filter((gap) -> gap.getWindowType() == NoonDataGapWindowType.HISTORY_BACKFILL)
                .filter((gap) -> overlaps(gap, query.getDateFrom(), query.getDateTo()))
                .sorted(Comparator.comparing(NoonDataGapWindowRecord::getId, Comparator.nullsLast(Long::compareTo)))
                .collect(Collectors.toList());
    }

    private List<NoonDataGapWindowRecord> dropStaleGapsCoveredByCompletedHistory(List<NoonDataGapWindowRecord> gaps) {
        return gaps.stream()
                .filter((gap) -> isCompletedHistoryGap(gap) || !coveredByCompletedHistoryGap(gap, gaps))
                .collect(Collectors.toList());
    }

    private boolean coveredByCompletedHistoryGap(NoonDataGapWindowRecord gap, List<NoonDataGapWindowRecord> gaps) {
        return gaps.stream()
                .filter(this::isCompletedHistoryGap)
                .filter((completed) -> completed.getCategory() == gap.getCategory())
                .anyMatch((completed) -> coversRange(completed, gap.getDateFrom(), gap.getDateTo()));
    }

    private boolean completedHistoryCoversRequiredCategories(
            List<NoonDataGapWindowRecord> gaps,
            Set<NoonDataCategory> requiredCategories,
            SalesFactQuery query
    ) {
        for (NoonDataCategory category : requiredCategories) {
            boolean covered = gaps.stream()
                    .filter(this::isCompletedHistoryGap)
                    .filter((gap) -> gap.getCategory() == category)
                    .anyMatch((gap) -> coversRange(gap, query.getDateFrom(), query.getDateTo()));
            if (!covered) {
                return false;
            }
        }
        return true;
    }

    private boolean isCompletedHistoryGap(NoonDataGapWindowRecord gap) {
        return gap.getStatus() == NoonDataGapStatus.SUCCEEDED
                || gap.getStatus() == NoonDataGapStatus.CONFIRMED_EMPTY;
    }

    private boolean coversRange(NoonDataGapWindowRecord gap, LocalDate dateFrom, LocalDate dateTo) {
        if (gap.getDateFrom() == null || gap.getDateTo() == null || dateFrom == null || dateTo == null) {
            return false;
        }
        return !gap.getDateFrom().isAfter(dateFrom) && !gap.getDateTo().isBefore(dateTo);
    }

    private boolean overlaps(NoonDataGapWindowRecord gap, LocalDate dateFrom, LocalDate dateTo) {
        if (gap.getDateFrom() == null || gap.getDateTo() == null || dateFrom == null || dateTo == null) {
            return false;
        }
        return !gap.getDateFrom().isAfter(dateTo) && !gap.getDateTo().isBefore(dateFrom);
    }

    private String statusState(List<NoonDataGapWindowRecord> gaps) {
        if (gaps.stream().anyMatch((gap) -> gap.getStatus() == NoonDataGapStatus.PROVIDER_RETENTION_LIMIT)) {
            return SalesHistoryBackfillStatus.RETENTION_LIMITED;
        }
        if (gaps.stream().anyMatch((gap) -> Boolean.TRUE.equals(gap.getRequiresManualAction()))) {
            return SalesHistoryBackfillStatus.MANUAL_ACTION;
        }
        if (gaps.stream().anyMatch((gap) -> gap.getStatus() == NoonDataGapStatus.RUNNING)) {
            return SalesHistoryBackfillStatus.BACKFILL_RUNNING;
        }
        if (gaps.stream().anyMatch((gap) -> gap.getLinkedPullTaskId() != null)) {
            return SalesHistoryBackfillStatus.BACKFILL_QUEUED;
        }
        if (gaps.stream().anyMatch((gap) -> gap.getStatus() == NoonDataGapStatus.FAILED)) {
            return SalesHistoryBackfillStatus.BACKFILL_FAILED;
        }
        return SalesHistoryBackfillStatus.BACKFILL_PENDING;
    }

    private List<Long> gapIds(List<NoonDataGapWindowRecord> gaps) {
        return gaps.stream()
                .map(NoonDataGapWindowRecord::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<Long> taskIds(List<NoonDataGapWindowRecord> gaps) {
        return gaps.stream()
                .map(NoonDataGapWindowRecord::getLinkedPullTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<String> categories(List<NoonDataGapWindowRecord> gaps) {
        Set<String> categories = new LinkedHashSet<>();
        for (NoonDataGapWindowRecord gap : gaps) {
            if (gap.getCategory() != null) {
                categories.add(gap.getCategory().name());
            }
        }
        return List.copyOf(categories);
    }
}
