package com.nuono.next.procurementorder;

import static com.nuono.next.procurementorder.WarehouseForwarderEligibilityPolicy.normalized;
import static com.nuono.next.procurementorder.WarehouseForwarderEligibilityPolicy.safe;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

final class WarehouseForwarderEligibilityRuleReader {

    private final ProcurementPurchaseOrderMapper mapper;

    WarehouseForwarderEligibilityRuleReader(ProcurementPurchaseOrderMapper mapper) {
        this.mapper = mapper;
    }

    Map<String, ProductForwarderTransportEligibilityRecord> load(
            List<PurchaseOrderLogisticsQuoteLineRecord> lines
    ) {
        return load(lines, false);
    }

    Map<String, ProductForwarderTransportEligibilityRecord> loadForUpdate(
            List<PurchaseOrderLogisticsQuoteLineRecord> lines
    ) {
        return load(lines, true);
    }

    private Map<String, ProductForwarderTransportEligibilityRecord> load(
            List<PurchaseOrderLogisticsQuoteLineRecord> lines,
            boolean forUpdate
    ) {
        Map<String, ProductForwarderTransportEligibilityRecord> result = new LinkedHashMap<>();
        List<ProductForwarderEligibilityScopeAnchorRecord> scopes = safe(lines).stream()
                .filter(WarehouseForwarderEligibilityRuleReader::hasStableScope)
                .map(line -> new ProductForwarderEligibilityScopeAnchorRecord(
                        line.ownerUserId, line.logicalStoreId, normalized(line.partnerSku)))
                .distinct()
                .sorted(ProductForwarderEligibilityScopeAnchorRecord.LOCK_ORDER)
                .collect(Collectors.toList());
        if (scopes.isEmpty()) {
            return result;
        }
        List<ProductForwarderTransportEligibilityRecord> current = forUpdate
                ? mapper.listCurrentProductForwarderTransportEligibilitiesForUpdate(scopes)
                : mapper.listCurrentProductForwarderTransportEligibilities(scopes);
        for (ProductForwarderTransportEligibilityRecord rule : safe(current)) {
            String key = WarehouseForwarderEligibilityPolicy.key(rule);
            ProductForwarderTransportEligibilityRecord existing = result.putIfAbsent(key, rule);
            if (existing != null && !Objects.equals(existing.id, rule.id)) {
                throw new IllegalArgumentException("承运状态数据冲突，请刷新后重试。");
            }
        }
        return result;
    }

    private static boolean hasStableScope(PurchaseOrderLogisticsQuoteLineRecord line) {
        return line != null && line.ownerUserId != null && line.ownerUserId > 0
                && line.logicalStoreId != null && line.logicalStoreId > 0
                && !normalized(line.partnerSku).isEmpty();
    }
}
