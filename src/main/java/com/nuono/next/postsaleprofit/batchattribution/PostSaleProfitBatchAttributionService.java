package com.nuono.next.postsaleprofit.batchattribution;

import com.nuono.next.infrastructure.mapper.PostSaleProfitBatchAttributionMapper;
import com.nuono.next.postsaleprofit.batchattribution.PostSaleProfitBatchAttributionRecords.BatchAttributionCandidateRow;
import com.nuono.next.postsaleprofit.batchattribution.PostSaleProfitBatchAttributionRecords.BatchAttributionCurrentStockRow;
import com.nuono.next.postsaleprofit.batchattribution.PostSaleProfitBatchAttributionRecords.BatchAttributionLineView;
import com.nuono.next.postsaleprofit.batchattribution.PostSaleProfitBatchAttributionRecords.BatchAttributionListView;
import com.nuono.next.postsaleprofit.batchattribution.PostSaleProfitBatchAttributionRecords.BatchAttributionSkuDetailView;
import com.nuono.next.postsaleprofit.batchattribution.PostSaleProfitBatchAttributionRecords.BatchAttributionSkuRow;
import com.nuono.next.postsaleprofit.batchattribution.PostSaleProfitBatchAttributionRecords.BatchAttributionSkuView;
import com.nuono.next.postsaleprofit.batchattribution.PostSaleProfitBatchAttributionRecords.BatchAttributionSummaryView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PostSaleProfitBatchAttributionService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PostSaleProfitBatchAttributionMapper mapper;

    public PostSaleProfitBatchAttributionService(PostSaleProfitBatchAttributionMapper mapper) {
        this.mapper = mapper;
    }

    public BatchAttributionListView listSummary(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate dateFrom,
            LocalDate dateTo,
            String keyword
    ) {
        List<BatchAttributionSkuRow> sourceRows = mapper.listSkuRows(
                ownerUserId,
                storeCode,
                siteCode,
                dateFrom,
                dateTo,
                trim(keyword)
        );
        List<BatchAttributionSkuView> rows = sourceRows.stream()
                .map(row -> BatchAttributionSkuView.from(row, status(row), blocker(row)))
                .collect(Collectors.toList());
        return new BatchAttributionListView(summary(rows), rows);
    }

    public BatchAttributionSkuDetailView getSkuDetail(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate dateFrom,
            LocalDate dateTo,
            String partnerSku
    ) {
        List<BatchAttributionLineView> lines = new ArrayList<>(mapper.listProfitBatchLines(
                        ownerUserId,
                        storeCode,
                        siteCode,
                        dateFrom,
                        dateTo,
                        partnerSku
                )
                .stream()
                .map(BatchAttributionLineView::from)
                .collect(Collectors.toList()));
        List<BatchAttributionCandidateRow> candidates = mapper.listCandidateBatchLines(
                ownerUserId,
                storeCode,
                siteCode,
                dateFrom,
                dateTo,
                partnerSku
        );
        BatchAttributionCurrentStockRow stock = mapper.selectCurrentStock(ownerUserId, storeCode, siteCode, partnerSku);
        if (stock != null && value(stock.sellableStockQuantity).compareTo(ZERO) > 0) {
            lines.addAll(currentStockTailLines(stock, candidates, lines));
        }
        BigDecimal soldQuantity = lines.stream()
                .map(BatchAttributionLineView::getSoldQuantity)
                .reduce(ZERO, BigDecimal::add);
        return new BatchAttributionSkuDetailView(
                partnerSku,
                stock == null ? ZERO : stock.stockQuantity,
                stock == null ? ZERO : stock.sellableStockQuantity,
                soldQuantity,
                lines
        );
    }

    private List<BatchAttributionLineView> currentStockTailLines(
            BatchAttributionCurrentStockRow stock,
            List<BatchAttributionCandidateRow> candidates,
            List<BatchAttributionLineView> soldLines
    ) {
        BigDecimal remainingStock = value(stock == null ? null : stock.sellableStockQuantity);
        if (!isPositive(remainingStock)) {
            return List.of();
        }
        List<BatchAttributionCandidateRow> orderedCandidates = candidates == null ? List.of() : candidates.stream()
                .filter(candidate -> candidate != null && isPositive(candidate.purchaseQuantity))
                .sorted(Comparator
                        .comparing((BatchAttributionCandidateRow candidate) -> candidate.purchaseBatchTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(candidate -> candidate.sourceId, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
        if (orderedCandidates.isEmpty()) {
            return List.of(unallocatedStockTail(stock, remainingStock));
        }

        Map<String, BigDecimal> soldBySource = soldLines.stream()
                .filter(line -> StringUtils.hasText(line.getSourceId()))
                .collect(Collectors.toMap(
                        BatchAttributionLineView::getSourceId,
                        line -> value(line.getSoldQuantity()),
                        BigDecimal::add,
                        LinkedHashMap::new
                ));
        BigDecimal genericSoldRemaining = soldLines.stream()
                .map(BatchAttributionLineView::getSoldQuantity)
                .reduce(ZERO, BigDecimal::add);
        Map<String, BigDecimal> exactSoldBySource = new LinkedHashMap<>();
        for (BatchAttributionCandidateRow candidate : orderedCandidates) {
            BigDecimal soldForSource = soldBySource.get(candidate.sourceId);
            if (!isPositive(soldForSource)) {
                continue;
            }
            BigDecimal exactSold = min(value(candidate.purchaseQuantity), soldForSource);
            exactSoldBySource.put(candidate.sourceId, exactSold);
            genericSoldRemaining = genericSoldRemaining.subtract(exactSold).max(ZERO);
        }

        List<BatchAttributionLineView> tails = new ArrayList<>();
        for (BatchAttributionCandidateRow candidate : orderedCandidates) {
            BigDecimal availableQuantity = value(candidate.purchaseQuantity)
                    .subtract(value(exactSoldBySource.get(candidate.sourceId)))
                    .max(ZERO);
            BigDecimal genericSoldQuantity = min(availableQuantity, genericSoldRemaining);
            availableQuantity = availableQuantity.subtract(genericSoldQuantity).max(ZERO);
            genericSoldRemaining = genericSoldRemaining.subtract(genericSoldQuantity).max(ZERO);
            BigDecimal stockQuantity = min(availableQuantity, remainingStock);
            if (isPositive(stockQuantity)) {
                tails.add(BatchAttributionLineView.currentStockTail(
                        candidate,
                        stockQuantity,
                        proportionalCost(candidate, stockQuantity),
                        stockTailStatus(candidate)
                ));
                remainingStock = remainingStock.subtract(stockQuantity).max(ZERO);
            }
            if (!isPositive(remainingStock)) {
                break;
            }
        }

        if (isPositive(remainingStock)) {
            tails.add(unallocatedStockTail(stock, remainingStock));
        }
        return tails;
    }

    private BatchAttributionLineView unallocatedStockTail(BatchAttributionCurrentStockRow stock, BigDecimal quantity) {
        BatchAttributionCurrentStockRow remainder = new BatchAttributionCurrentStockRow();
        remainder.partnerSku = stock == null ? null : stock.partnerSku;
        remainder.stockQuantity = quantity;
        remainder.sellableStockQuantity = quantity;
        return BatchAttributionLineView.currentStockTail(remainder);
    }

    private BigDecimal proportionalCost(BatchAttributionCandidateRow candidate, BigDecimal stockQuantity) {
        if (candidate == null || candidate.purchaseCostCny == null || !isPositive(candidate.purchaseQuantity)) {
            return null;
        }
        return candidate.purchaseCostCny
                .multiply(stockQuantity)
                .divide(candidate.purchaseQuantity, 6, RoundingMode.HALF_UP);
    }

    private String stockTailStatus(BatchAttributionCandidateRow candidate) {
        if (candidate != null
                && StringUtils.hasText(candidate.logisticsBatchNo)
                && StringUtils.hasText(candidate.asnNo)) {
            return "stock_fifo_candidate";
        }
        return "stock_fifo_candidate_missing_links";
    }

    private BatchAttributionSummaryView summary(List<BatchAttributionSkuView> rows) {
        BatchAttributionSummaryView summary = new BatchAttributionSummaryView();
        summary.setStockedSkuCount(rows.size());
        summary.setStockQuantity(rows.stream()
                .map(BatchAttributionSkuView::getStockQuantity)
                .reduce(ZERO, BigDecimal::add));
        summary.setSellableStockQuantity(rows.stream()
                .map(BatchAttributionSkuView::getSellableStockQuantity)
                .reduce(ZERO, BigDecimal::add));
        summary.setSoldQuantity(rows.stream()
                .map(BatchAttributionSkuView::getSoldQuantity)
                .reduce(ZERO, BigDecimal::add));
        summary.setClosedSkuCount(countStatus(rows, "closed"));
        summary.setFifoReadySkuCount(countStatus(rows, "fifo_ready"));
        summary.setMissingAsnSkuCount(countStatus(rows, "missing_asn"));
        summary.setMissingLogisticsSkuCount(countStatus(rows, "missing_logistics"));
        summary.setMissingPurchaseSkuCount(countStatus(rows, "missing_purchase"));
        summary.setAmbiguousSkuCount(countStatus(rows, "ambiguous"));
        summary.setLogisticsLinkedPurchaseMissingSkuCount(countStatus(rows, "logistics_linked_purchase_missing"));
        return summary;
    }

    private int countStatus(List<BatchAttributionSkuView> rows, String status) {
        return (int) rows.stream()
                .filter(row -> status.equals(row.getAttributionStatus()))
                .count();
    }

    private String status(BatchAttributionSkuRow row) {
        if (count(row.asnCount) == 0) {
            return "missing_asn";
        }
        if (count(row.bridgeLogisticsCount) > 0 && count(row.bridgePurchaseOrderCount) == 0) {
            return "logistics_linked_purchase_missing";
        }
        if (purchaseOrderCount(row) == 0) {
            return "missing_purchase";
        }
        if (count(row.inTransitBatchCount) == 0) {
            return "missing_logistics";
        }
        if (count(row.bridgeLogisticsCount) > 0 && count(row.bridgePurchaseOrderCount) > 0) {
            return "closed";
        }
        if (count(row.asnCount) == 1 && count(row.inTransitBatchCount) == 1 && purchaseOrderCount(row) == 1) {
            return "fifo_ready";
        }
        return "ambiguous";
    }

    private String blocker(BatchAttributionSkuRow row) {
        String status = status(row);
        switch (status) {
            case "missing_asn":
                return "stock_has_no_successful_asn_line";
            case "logistics_linked_purchase_missing":
                return "existing_asn_logistics_bridge_lacks_purchase_order_fields";
            case "missing_purchase":
                return "no_purchase_order_for_sku_site";
            case "missing_logistics":
                return "no_same_sku_same_site_in_transit_line";
            case "closed":
                return "existing_bridge_closed";
            case "fifo_ready":
                return "unique_fifo_candidate";
            default:
                return ambiguousBlocker(row);
        }
    }

    private String ambiguousBlocker(BatchAttributionSkuRow row) {
        int ambiguousDimensions = 0;
        if (count(row.asnCount) > 1) {
            ambiguousDimensions++;
        }
        if (count(row.inTransitBatchCount) > 1) {
            ambiguousDimensions++;
        }
        if (purchaseOrderCount(row) > 1) {
            ambiguousDimensions++;
        }
        if (ambiguousDimensions > 1) {
            return "multiple_candidate_dimensions";
        }
        if (count(row.asnCount) > 1) {
            return "multiple_asn_candidates";
        }
        if (count(row.inTransitBatchCount) > 1) {
            return "multiple_in_transit_candidates";
        }
        if (purchaseOrderCount(row) > 1) {
            return "multiple_purchase_order_candidates";
        }
        return "candidate_pool_requires_manual_review";
    }

    private int purchaseOrderCount(BatchAttributionSkuRow row) {
        return count(row.purchaseOrderNoCount != null ? row.purchaseOrderNoCount : row.purchaseOrderCount);
    }

    private int count(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal value(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private BigDecimal min(BigDecimal first, BigDecimal second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(ZERO) > 0;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
