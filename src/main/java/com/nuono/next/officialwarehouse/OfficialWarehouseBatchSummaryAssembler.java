package com.nuono.next.officialwarehouse;

import com.nuono.next.officialwarehouse.OfficialWarehouseBatchSummaryRecords.ShippingBatchRawLineRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseBatchSummaryViews.BatchProductSummaryView;
import com.nuono.next.officialwarehouse.OfficialWarehouseBatchSummaryViews.ProductIssueView;
import com.nuono.next.officialwarehouse.OfficialWarehouseBatchSummaryViews.StoreProductSummaryView;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.ShippingBatchSourceAllocationRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.ProductCandidateView;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

final class OfficialWarehouseBatchSummaryAssembler {

    private static final String MISSING_DIMENSION = "缺尺寸";

    BatchProductSummaryView assemble(
            String currentStoreCode,
            String siteCode,
            List<ShippingBatchRawLineRecord> rawLines,
            List<ProductCandidateView> candidates,
            Map<String, List<ShippingBatchSourceAllocationRecord>> allocationsByStore
    ) {
        BatchProductSummaryView result = rawSummary(rawLines);
        result.currentStore = currentStoreSummary(
                currentStoreCode,
                siteCode,
                candidates,
                allocationsByStore.get(currentStoreCode)
        );
        allocationsByStore.forEach((storeCode, allocations) -> {
            if (!currentStoreCode.equals(storeCode)) {
                result.otherStores.add(otherStoreSummary(storeCode, siteCode, allocations));
            }
        });
        result.otherStores.sort(Comparator.comparing(item -> item.storeCode));
        applyUnassignedSummary(result, rawLines, allocationsByStore.values());
        return result;
    }

    private BatchProductSummaryView rawSummary(List<ShippingBatchRawLineRecord> rawLines) {
        BatchProductSummaryView result = new BatchProductSummaryView();
        result.totalQuantity = rawLines.stream().mapToInt(line -> positive(line.quantity)).sum();
        result.totalSkuCount = (int) rawLines.stream().map(this::rawSkuKey).distinct().count();
        result.totalLineCount = rawLines.size();
        result.unassignedQuantity = 0;
        result.unassignedSkuCount = 0;
        result.attributionWarning = false;
        return result;
    }

    private StoreProductSummaryView currentStoreSummary(
            String storeCode,
            String siteCode,
            List<ProductCandidateView> candidates,
            List<ShippingBatchSourceAllocationRecord> allocations
    ) {
        StoreProductSummaryView result = baseStoreSummary(
                storeCode,
                firstText(
                        candidates.stream().map(item -> item.storeName).filter(StringUtils::hasText)
                                .findFirst().orElse(null),
                        firstAllocationStoreName(allocations),
                        storeCode
                ),
                siteCode
        );
        Set<String> allSkus = new LinkedHashSet<>();
        Set<String> bookableSkus = new LinkedHashSet<>();
        for (ProductCandidateView candidate : candidates) {
            int quantity = positive(candidate.batchAvailableQuantity);
            String skuKey = candidateSkuKey(candidate);
            allSkus.add(skuKey);
            result.totalQuantity += quantity;
            if (candidate.missingTags == null || candidate.missingTags.isEmpty()) {
                result.bookableQuantity += quantity;
                bookableSkus.add(skuKey);
                continue;
            }
            ProductIssueView issue = issue(candidate, quantity);
            result.blockedItems.add(issue);
            result.blockedQuantity += quantity;
            if (issue.reasons.contains(MISSING_DIMENSION)) {
                result.missingDimensionItems.add(issue);
                result.missingDimensionQuantity += quantity;
            }
        }
        result.totalSkuCount = allSkus.size();
        result.bookableSkuCount = bookableSkus.size();
        result.blockedSkuCount = result.blockedItems.size();
        result.missingDimensionSkuCount = result.missingDimensionItems.size();
        return result;
    }

    private StoreProductSummaryView otherStoreSummary(
            String storeCode,
            String siteCode,
            List<ShippingBatchSourceAllocationRecord> allocations
    ) {
        StoreProductSummaryView result = baseStoreSummary(
                storeCode,
                firstText(firstAllocationStoreName(allocations), storeCode),
                siteCode
        );
        result.totalQuantity = allocations.stream().mapToInt(item -> positive(item.quantity)).sum();
        result.totalSkuCount = (int) allocations.stream().map(this::allocationSkuKey).distinct().count();
        result.bookableQuantity = null;
        result.bookableSkuCount = null;
        result.blockedQuantity = null;
        result.blockedSkuCount = null;
        result.missingDimensionQuantity = null;
        result.missingDimensionSkuCount = null;
        return result;
    }

    private StoreProductSummaryView baseStoreSummary(String storeCode, String storeName, String siteCode) {
        StoreProductSummaryView result = new StoreProductSummaryView();
        result.storeCode = storeCode;
        result.storeName = storeName;
        result.siteCode = siteCode;
        result.totalQuantity = 0;
        result.totalSkuCount = 0;
        result.bookableQuantity = 0;
        result.bookableSkuCount = 0;
        result.blockedQuantity = 0;
        result.blockedSkuCount = 0;
        result.missingDimensionQuantity = 0;
        result.missingDimensionSkuCount = 0;
        return result;
    }

    private void applyUnassignedSummary(
            BatchProductSummaryView result,
            List<ShippingBatchRawLineRecord> rawLines,
            Collection<List<ShippingBatchSourceAllocationRecord>> allocationsByStore
    ) {
        Map<Long, Integer> attributedByLine = new LinkedHashMap<>();
        allocationsByStore.stream().flatMap(Collection::stream).forEach(allocation -> {
            if (allocation.inTransitGoodsLineId != null) {
                attributedByLine.merge(
                        allocation.inTransitGoodsLineId,
                        positive(allocation.quantity),
                        Integer::sum
                );
            }
        });
        Set<String> unassignedSkus = new LinkedHashSet<>();
        for (ShippingBatchRawLineRecord rawLine : rawLines) {
            int rawQuantity = positive(rawLine.quantity);
            int attributedQuantity = attributedByLine.getOrDefault(rawLine.goodsLineId, 0);
            if (attributedQuantity > rawQuantity) {
                result.attributionWarning = true;
            }
            int unassigned = Math.max(0, rawQuantity - attributedQuantity);
            result.unassignedQuantity += unassigned;
            if (unassigned > 0) {
                unassignedSkus.add(rawSkuKey(rawLine));
            }
        }
        result.unassignedSkuCount = unassignedSkus.size();
    }

    private ProductIssueView issue(ProductCandidateView candidate, int quantity) {
        ProductIssueView issue = new ProductIssueView();
        issue.partnerSku = firstText(
                candidate.partnerSku,
                candidate.pskuCode,
                candidate.childSku,
                candidate.skuParent
        );
        issue.title = candidate.title;
        issue.quantity = quantity;
        issue.reasons = new ArrayList<>(candidate.missingTags);
        return issue;
    }

    private String rawSkuKey(ShippingBatchRawLineRecord line) {
        return firstText(line.sku, "LINE-" + line.goodsLineId);
    }

    private String candidateSkuKey(ProductCandidateView candidate) {
        return upper(firstText(
                candidate.partnerSku,
                candidate.pskuCode,
                candidate.childSku,
                candidate.skuParent,
                candidate.productVariantId
        ));
    }

    private String allocationSkuKey(ShippingBatchSourceAllocationRecord allocation) {
        return upper(firstText(
                allocation.partnerSku,
                allocation.skuParent,
                allocation.productVariantId == null ? null : String.valueOf(allocation.productVariantId),
                "LINE-" + allocation.inTransitGoodsLineId
        ));
    }

    private String firstAllocationStoreName(List<ShippingBatchSourceAllocationRecord> allocations) {
        return allocations == null ? null : allocations.stream()
                .map(item -> item.sourceStoreName)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private static int positive(Integer value) {
        return Math.max(0, value == null ? 0 : value);
    }

    private static String upper(String value) {
        return value.toUpperCase(Locale.ROOT);
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
