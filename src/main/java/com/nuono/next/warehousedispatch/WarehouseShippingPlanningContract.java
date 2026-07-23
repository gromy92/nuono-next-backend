package com.nuono.next.warehousedispatch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.product.ProductImageUrlSupport;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.*;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.*;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

abstract class WarehouseShippingPlanningContract extends WarehouseDispatchProjectionContract {

    protected WarehouseShippingPlanningContract(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

protected abstract PurchaseOrderAccessRecord requireOrderAccess(BusinessAccessContext access, Long orderId);

protected abstract PurchaseOrderItemRecord requireItem(PurchaseOrderAccessRecord order, Long itemId);

protected abstract DispatchPlanRecord requireDispatchPlanAccess(BusinessAccessContext access, Long dispatchPlanId);

protected abstract ShippingBatchRecord requireShippingBatchAccess(BusinessAccessContext access, Long shippingBatchId);

protected abstract OutboundOrderRecord requireOutboundOrderAccess(BusinessAccessContext access, Long outboundOrderId);

protected abstract PackingListRecord requirePackingListAccess(BusinessAccessContext access, Long packingListId);

protected abstract DispatchPlanRecord requireHandoffAccess(BusinessAccessContext access, String handoffRequestNo);

protected abstract void requireOwnerAccess(BusinessAccessContext access, Long ownerUserId);

protected abstract boolean canUseBalance(BusinessAccessContext access, FulfillmentBalanceRecord balance);

protected abstract boolean logisticsQuoteBlocks(FulfillmentBalanceRecord balance);

protected abstract String normalizeLogisticsQuoteStatus(String value);

protected abstract String normalizeShippingSubmitStatus(String value);

protected abstract String mergedQuoteStatus(String current, String next);

protected abstract String mergedShippingSubmitStatus(String current, String next);

protected abstract boolean canAccessSourceStore(BusinessAccessContext access, String storeCode);

protected abstract Long ownerUserId(BusinessAccessContext access);

protected abstract boolean matchesKeyword(FulfillmentBalanceRecord balance, String keyword);

protected abstract boolean contains(String value, String normalizedKeyword);

protected abstract Map<String, Integer> siteSummary(List<DispatchPlanLineView> lines);

protected abstract Map<String, Integer> transportSummary(List<DispatchPlanLineView> lines);

protected abstract int shippingSkuCount(List<ShippingBatchSourceRecord> sources);

protected abstract Map<String, Integer> shippingStoreSummary(List<ShippingBatchSourceRecord> sources);

protected abstract Map<String, Integer> shippingSiteSummary(List<ShippingBatchSourceRecord> sources);

protected abstract Map<String, Integer> shippingPlannedTransportSummary(List<ShippingBatchSourceRecord> sources);

protected abstract String shippingBatchNo(Long batchId, List<ShippingBatchSourceRecord> sources);

protected abstract String shippingBatchTransportModeLabel(Collection<String> transportModes);

protected abstract Map<String, Integer> shippingOriginSummary(List<ShippingBatchSourceRecord> sources);

protected abstract Map<String, Object> shippingOptionSummary(
            ShippingOptionDefinition definition,
            List<ShippingBatchSourceRecord> sources,
            Collection<PendingShippingLine> lines
    );

protected abstract Map<String, List<ForwarderRouteQuoteRecord>> forwarderRouteQuotes(Collection<PendingShippingLine> lines);

protected abstract List<ForwarderRouteQuoteRecord> quotesForRoute(
            Map<String, List<ForwarderRouteQuoteRecord>> routeQuotes,
            String routeCode
    );

protected abstract List<String> preferredQuoteTokens(CargoCategoryEstimate cargoCategory, PendingShippingLine line);

protected abstract int quoteTokenIndex(ForwarderRouteQuoteRecord quote, List<String> tokens);

protected abstract boolean quoteMatchesToken(ForwarderRouteQuoteRecord quote, String token);

protected abstract String normalizeQuoteText(String value);

protected abstract boolean containsAny(String value, String... tokens);

protected abstract List<String> routeCodes(Collection<PendingShippingLine> lines);

protected abstract void addUnique(List<String> values, String value);

protected abstract BigDecimal zeroToNull(BigDecimal value, int scale);

protected abstract Map<String, Object> shippingLineAssignmentSnapshot(ShippingForwarderAssignment assignment);

protected abstract String shippingActualTransportMode(String optionType, ShippingBatchSourceRecord source);

protected abstract ShippingForwarderAssignment shippingForwarderAssignment(
            ShippingOptionDefinition definition,
            String actualTransportMode,
            String siteCode
    );

protected abstract ShippingOptionDefinition customShippingOptionDefinition(CreateShippingTargetOptionCommand command);

protected abstract String normalizeForwarderCode(String forwarderCode);

protected abstract boolean isSupportedAirForwarderCode(String forwarderCode);

protected abstract boolean isSupportedSeaForwarderCode(String forwarderCode);

protected abstract String forwarderName(String forwarderCode);

protected abstract ForwarderRouteSnapshot forwarderRouteSnapshot(String forwarderCode, String transportMode, String siteCode);
}
