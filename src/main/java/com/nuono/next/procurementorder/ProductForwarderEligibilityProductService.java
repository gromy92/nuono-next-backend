package com.nuono.next.procurementorder;

import static com.nuono.next.procurementorder.WarehouseForwarderEligibilityPolicy.normalizeStatus;
import static com.nuono.next.procurementorder.WarehouseForwarderEligibilityPolicy.normalized;
import static com.nuono.next.procurementorder.WarehouseForwarderEligibilityPolicy.requiredCode;
import static com.nuono.next.procurementorder.WarehouseForwarderEligibilityPolicy.safe;
import static com.nuono.next.procurementorder.WarehouseForwarderEligibilityPolicy.trim;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateShippingOrderLineEligibilityCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ProductForwarderEligibilityProductService {

    private final ProcurementPurchaseOrderMapper mapper;
    private final WarehouseForwarderEligibilityService eligibilityService;

    public ProductForwarderEligibilityProductService(
            ProcurementPurchaseOrderMapper mapper,
            WarehouseForwarderEligibilityService eligibilityService
    ) {
        this.mapper = mapper;
        this.eligibilityService = eligibilityService;
    }

    public String currentStatus(ProductForwarderEligibilityProductScope requestedScope) {
        ProductForwarderEligibilityProductScope scope = requireProductScope(requestedScope);
        ProductForwarderEligibilityScopeAnchorRecord anchor = new ProductForwarderEligibilityScopeAnchorRecord(
                scope.ownerUserId,
                scope.logicalStoreId,
                scope.partnerSku
        );
        String status = safe(mapper.listCurrentProductForwarderTransportEligibilities(List.of(anchor))).stream()
                .filter(rule -> scope.siteCode.equals(normalized(rule.siteCode)))
                .filter(rule -> scope.forwarderCode.equals(normalized(rule.forwarderCode)))
                .filter(rule -> scope.transportMode.equals(normalized(rule.transportMode)))
                .findFirst()
                .map(rule -> normalizeStatus(rule.eligibilityStatus))
                .orElse(WarehouseForwarderEligibilityService.SUPPORTED);
        if (WarehouseForwarderEligibilityPolicy.UNKNOWN.equals(status)) {
            throw invalidData();
        }
        return status;
    }

    public Map<String, String> currentStatusesForRoute(
            Long ownerUserId,
            Long logicalStoreId,
            String siteCode,
            String forwarderCode,
            String transportMode
    ) {
        if (ownerUserId == null || ownerUserId <= 0 || logicalStoreId == null || logicalStoreId <= 0) {
            throw new IllegalArgumentException("商品缺少稳定店铺身份，不能读取承运状态。");
        }
        String normalizedSite = requiredCode(siteCode, "商品缺少站点，不能读取承运状态。");
        String normalizedForwarder = requiredCode(forwarderCode, "请选择货代。");
        String normalizedMode = normalized(transportMode);
        if (!("AIR".equals(normalizedMode) || "SEA".equals(normalizedMode))) {
            throw new IllegalArgumentException("商品缺少有效运输方式，不能读取承运状态。");
        }
        Map<String, String> statuses = new LinkedHashMap<>();
        for (ProductForwarderTransportEligibilityRecord rule : safe(
                mapper.listCurrentProductForwarderTransportEligibilitiesForRoute(
                        ownerUserId,
                        logicalStoreId,
                        normalizedSite,
                        normalizedForwarder,
                        normalizedMode
                ))) {
            String partnerSku = normalized(rule == null ? null : rule.partnerSku);
            String status = normalizeStatus(rule == null ? null : rule.eligibilityStatus);
            if (partnerSku.isEmpty() || WarehouseForwarderEligibilityPolicy.UNKNOWN.equals(status)
                    || statuses.putIfAbsent(partnerSku, status) != null) {
                throw invalidData();
            }
        }
        return statuses;
    }

    @Transactional
    public String updateProductRule(
            ProductForwarderEligibilityProductScope requestedScope,
            String requestedStatus,
            Long operatorUserId
    ) {
        ProductForwarderEligibilityProductScope scope = requireProductScope(requestedScope);
        if (operatorUserId == null || operatorUserId <= 0) {
            throw new IllegalArgumentException("承运状态操作人不能为空。");
        }
        ProductForwarderEligibilityScopeAnchorRecord anchor = new ProductForwarderEligibilityScopeAnchorRecord(
                scope.ownerUserId,
                scope.logicalStoreId,
                scope.partnerSku
        );
        mapper.ensureProductForwarderEligibilityScopeAnchors(List.of(anchor));
        List<ProductForwarderEligibilityScopeAnchorRecord> locked =
                mapper.lockProductForwarderEligibilityScopeAnchors(List.of(anchor));
        if (!List.of(anchor).equals(locked)) {
            throw concurrentUpdate();
        }

        PurchaseOrderLogisticsQuoteLineRecord line = new PurchaseOrderLogisticsQuoteLineRecord();
        line.ownerUserId = scope.ownerUserId;
        line.logicalStoreId = scope.logicalStoreId;
        line.productMasterId = scope.productMasterId;
        line.productVariantId = scope.productVariantId;
        line.sourceStoreCode = scope.sourceStoreCode;
        line.partnerSku = scope.partnerSku;
        line.siteCode = scope.siteCode;
        line.plannedTransportMode = scope.transportMode;
        UpdateShippingOrderLineEligibilityCommand command = new UpdateShippingOrderLineEligibilityCommand();
        command.forwarderCode = scope.forwarderCode;
        command.eligibilityStatus = requestedStatus;
        eligibilityService.updateRule(line, scope.forwarderCode, command, operatorUserId);
        return normalizeStatus(requestedStatus);
    }

    private static ProductForwarderEligibilityProductScope requireProductScope(
            ProductForwarderEligibilityProductScope scope
    ) {
        if (scope == null || scope.ownerUserId == null || scope.ownerUserId <= 0
                || scope.logicalStoreId == null || scope.logicalStoreId <= 0
                || normalized(scope.partnerSku).isEmpty()) {
            throw new IllegalArgumentException("商品缺少稳定店铺或 PSKU 身份，不能维护承运状态。");
        }
        String siteCode = requiredCode(scope.siteCode, "商品缺少站点，不能维护承运状态。");
        String forwarderCode = requiredCode(scope.forwarderCode, "请选择货代。");
        String transportMode = normalized(scope.transportMode);
        if (!("AIR".equals(transportMode) || "SEA".equals(transportMode))) {
            throw new IllegalArgumentException("商品缺少有效运输方式，不能维护承运状态。");
        }
        return new ProductForwarderEligibilityProductScope(
                scope.ownerUserId,
                scope.logicalStoreId,
                scope.productMasterId,
                scope.productVariantId,
                trim(scope.sourceStoreCode),
                normalized(scope.partnerSku),
                siteCode,
                forwarderCode,
                transportMode
        );
    }

    private static IllegalArgumentException invalidData() {
        return new IllegalArgumentException("承运状态数据异常，请刷新后重试。");
    }

    private static IllegalArgumentException concurrentUpdate() {
        return new IllegalArgumentException("承运状态已被其他操作更新，请刷新后重试。");
    }
}
