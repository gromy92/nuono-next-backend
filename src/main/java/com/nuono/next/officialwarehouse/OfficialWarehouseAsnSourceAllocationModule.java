package com.nuono.next.officialwarehouse;

import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnLineInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnShippingBatchLinkInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.ShippingBatchSourceAllocationRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.StoreSiteRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

/** Allocates only the declared logistics portion of ASN lines to selected shipping batches. */
final class OfficialWarehouseAsnSourceAllocationModule {

    private final OfficialWarehouseMapper mapper;

    OfficialWarehouseAsnSourceAllocationModule(OfficialWarehouseMapper mapper) {
        this.mapper = mapper;
    }

    List<AsnShippingBatchLinkInsertRecord> buildLinks(
            Long ownerUserId,
            StoreSiteRecord site,
            Long asnId,
            List<AsnLineInsertRecord> lineRows,
            List<Long> selectedBatchIds,
            Long operatorUserId
    ) {
        if (selectedBatchIds == null || selectedBatchIds.isEmpty()) {
            return List.of();
        }
        List<AsnLineInsertRecord> batchLineRows = lineRows == null ? List.of() : lineRows.stream()
                .filter(row -> row != null && positive(row.shippingBatchQuantity) > 0)
                .collect(Collectors.toList());
        if (batchLineRows.isEmpty()) {
            throw new IllegalArgumentException("已选择物流批次时，至少选择一个物流单内商品。");
        }
        List<String> partnerSkus = batchLineRows.stream()
                .map(row -> clean(row.partnerSku))
                .filter(value -> value != null)
                .distinct()
                .collect(Collectors.toList());
        List<Long> variantIds = batchLineRows.stream()
                .filter(row -> !StringUtils.hasText(row.partnerSku))
                .map(row -> row.productVariantId)
                .filter(value -> value != null)
                .distinct()
                .collect(Collectors.toList());
        List<ShippingBatchSourceAllocationRecord> allocations = mapper.listShippingBatchSourceAllocations(
                ownerUserId, site.storeCode, site.siteCode, selectedBatchIds, variantIds, partnerSkus
        );
        if (allocations.isEmpty()) {
            throw new IllegalArgumentException("选择的物流批次没有匹配当前 ASN 商品。");
        }
        sortAllocations(allocations, selectedBatchIds);

        Map<String, List<ShippingBatchSourceAllocationRecord>> allocationsByProductKey = new LinkedHashMap<>();
        Map<Long, Integer> remainingBySourceId = new LinkedHashMap<>();
        for (ShippingBatchSourceAllocationRecord allocation : allocations) {
            Long sourceId = sourceId(allocation);
            if ((!StringUtils.hasText(allocation.partnerSku) && allocation.productVariantId == null) || sourceId == null) {
                continue;
            }
            int quantity = positive(allocation.quantity);
            if (quantity <= 0) {
                continue;
            }
            allocationsByProductKey.computeIfAbsent(
                    productKey(site, allocation.partnerSku, allocation.productVariantId),
                    ignored -> new ArrayList<>()
            ).add(allocation);
            remainingBySourceId.put(sourceId, quantity);
        }

        List<AsnShippingBatchLinkInsertRecord> links = new ArrayList<>();
        for (AsnLineInsertRecord lineRow : batchLineRows) {
            String productKey = productKey(site, lineRow.partnerSku, lineRow.productVariantId);
            int requiredQuantity = positive(lineRow.shippingBatchQuantity);
            int availableQuantity = allocationsByProductKey.getOrDefault(productKey, List.of()).stream()
                    .mapToInt(allocation -> remainingBySourceId.getOrDefault(sourceId(allocation), 0))
                    .sum();
            if (availableQuantity < requiredQuantity) {
                String label = firstText(lineRow.partnerSku, lineRow.pskuCode, lineRow.skuParent,
                        String.valueOf(lineRow.productVariantId));
                throw new IllegalArgumentException(label + " 选择的物流批次数量不足：需要 "
                        + requiredQuantity + "，批次可用 " + availableQuantity + "。");
            }
            allocateLine(links, lineRow, allocationsByProductKey.getOrDefault(productKey, List.of()),
                    remainingBySourceId, requiredQuantity, ownerUserId, site, asnId, operatorUserId);
        }
        return links;
    }

    private void allocateLine(
            List<AsnShippingBatchLinkInsertRecord> links,
            AsnLineInsertRecord lineRow,
            List<ShippingBatchSourceAllocationRecord> allocations,
            Map<Long, Integer> remainingBySourceId,
            int requiredQuantity,
            Long ownerUserId,
            StoreSiteRecord site,
            Long asnId,
            Long operatorUserId
    ) {
        int remainingQuantity = requiredQuantity;
        for (ShippingBatchSourceAllocationRecord allocation : allocations) {
            Long sourceId = sourceId(allocation);
            int sourceRemaining = sourceId == null ? 0 : remainingBySourceId.getOrDefault(sourceId, 0);
            if (remainingQuantity <= 0) break;
            if (sourceRemaining <= 0) continue;
            int linkedQuantity = Math.min(remainingQuantity, sourceRemaining);
            links.add(toLink(allocation, lineRow, ownerUserId, site, asnId, operatorUserId, linkedQuantity));
            OfficialWarehouseAsnProductPreflightModule.addSourceBarcode(lineRow, allocation.sourceBarcode);
            remainingQuantity -= linkedQuantity;
            remainingBySourceId.put(sourceId, sourceRemaining - linkedQuantity);
        }
    }

    private AsnShippingBatchLinkInsertRecord toLink(
            ShippingBatchSourceAllocationRecord allocation,
            AsnLineInsertRecord line,
            Long ownerUserId,
            StoreSiteRecord site,
            Long asnId,
            Long operatorUserId,
            int quantity
    ) {
        AsnShippingBatchLinkInsertRecord link = new AsnShippingBatchLinkInsertRecord();
        link.id = mapper.nextAsnShippingBatchLinkId();
        link.asnId = asnId;
        link.asnLineId = line.id;
        link.ownerUserId = ownerUserId;
        link.storeCode = site.storeCode;
        link.siteCode = site.siteCode;
        link.shippingBatchId = allocation.shippingBatchId;
        link.shippingBatchNo = allocation.shippingBatchNo;
        link.shippingBatchSourceId = allocation.shippingBatchSourceId;
        link.inTransitBatchId = allocation.inTransitBatchId;
        link.batchReferenceNo = allocation.batchReferenceNo;
        link.trackingNo = allocation.trackingNo;
        link.externalShipmentNo = allocation.externalShipmentNo;
        link.forwarderName = allocation.forwarderName;
        link.transportMode = allocation.transportMode;
        link.latestNodeStatus = allocation.latestNodeStatus;
        link.inTransitGoodsLineId = allocation.inTransitGoodsLineId;
        link.sourceBarcode = allocation.sourceBarcode;
        link.fulfillmentBalanceId = allocation.fulfillmentBalanceId;
        link.purchaseOrderId = allocation.purchaseOrderId;
        link.purchaseOrderNo = allocation.purchaseOrderNo;
        link.purchaseOrderItemId = allocation.purchaseOrderItemId;
        link.purchaseOrderItemSiteId = allocation.purchaseOrderItemSiteId;
        link.productMasterId = line.productMasterId;
        link.productVariantId = line.productVariantId;
        link.partnerSku = line.partnerSku;
        link.pskuCode = line.pskuCode;
        link.quantity = quantity;
        link.relationStatus = "LINKED";
        link.relationBasis = allocation.inTransitBatchId == null
                ? "ASN_CREATE_SELECTED_BATCH" : "ASN_CREATE_SELECTED_IN_TRANSIT_BATCH";
        link.operatorUserId = operatorUserId;
        return link;
    }

    private void sortAllocations(List<ShippingBatchSourceAllocationRecord> rows, List<Long> selectedBatchIds) {
        Map<Long, Integer> order = new LinkedHashMap<>();
        for (int index = 0; index < selectedBatchIds.size(); index++) order.put(selectedBatchIds.get(index), index);
        rows.sort((left, right) -> {
            int comparison = Integer.compare(order.getOrDefault(batchId(left), Integer.MAX_VALUE),
                    order.getOrDefault(batchId(right), Integer.MAX_VALUE));
            if (comparison != 0) return comparison;
            return Long.compare(sourceId(left) == null ? Long.MAX_VALUE : sourceId(left),
                    sourceId(right) == null ? Long.MAX_VALUE : sourceId(right));
        });
    }

    private Long batchId(ShippingBatchSourceAllocationRecord row) {
        return row.inTransitBatchId == null ? row.shippingBatchId : row.inTransitBatchId;
    }

    private Long sourceId(ShippingBatchSourceAllocationRecord row) {
        return row.inTransitGoodsLineId == null ? row.shippingBatchSourceId : row.inTransitGoodsLineId;
    }

    private String productKey(StoreSiteRecord site, String partnerSku, Long variantId) {
        String psku = clean(partnerSku);
        if (clean(site.storeCode) != null && clean(site.siteCode) != null && psku != null) {
            return site.storeCode.toUpperCase(Locale.ROOT) + "|" + site.siteCode.toUpperCase(Locale.ROOT) + "|psku:" + psku;
        }
        return "variant:" + variantId;
    }

    private String firstText(String... values) {
        for (String value : values) if (clean(value) != null) return clean(value);
        return "未知商品";
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int positive(Integer value) {
        return Math.max(0, value == null ? 0 : value);
    }
}
