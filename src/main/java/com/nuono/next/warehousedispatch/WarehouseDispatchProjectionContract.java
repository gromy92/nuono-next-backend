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

abstract class WarehouseDispatchProjectionContract extends WarehouseShippingLineCostSupport {

    protected WarehouseDispatchProjectionContract(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

    public abstract ShippingBatchView createShippingBatch(
            BusinessAccessContext access,
            CreateShippingBatchCommand command
    );

    public abstract ShippingSuggestionOptionView createShippingTargetOption(
            BusinessAccessContext access,
            String shippingBatchId,
            CreateShippingTargetOptionCommand command
    );

    public abstract ShippingBatchView selectShippingOption(
            BusinessAccessContext access,
            String shippingBatchId,
            String optionId
    );

protected abstract void ensureItemBalances(PurchaseOrderItemRecord item, String fulfillmentType, Long operatorUserId);

protected abstract Map<Long, Integer> allocateByPlannedQuantity(List<FulfillmentBalanceRecord> balances, int quantity);

protected abstract ReadySourceView toReadySourceView(FulfillmentBalanceRecord balance);

protected abstract PurchaseReceiptItemView toReceiptItemView(PurchaseReceiptRow row);

protected abstract DispatchPlanView toDispatchPlanView(DispatchPlanRecord record);

protected abstract DispatchPlanLineView toDispatchLineView(DispatchPlanLineRecord line);

protected abstract DispatchPlanLineSourceView toDispatchSourceView(DispatchPlanLineSourceRecord source);

protected abstract PurchaseOrderLogisticsComparisonView toPurchaseOrderLogisticsComparisonView(List<FulfillmentBalanceRecord> balances);

protected abstract PurchaseOrderLogisticsSegmentView toPurchaseOrderLogisticsSegmentView(
            String segmentKey,
            List<FulfillmentBalanceRecord> balances
    );

protected abstract ShippingBatchSourceRecord toAnalysisShippingBatchSource(FulfillmentBalanceRecord balance);

protected abstract int purchaseComparisonQuantity(FulfillmentBalanceRecord balance);

protected abstract ShippingSuggestionOptionView evaluateShippingOptionPreview(
            String segmentKey,
            List<ShippingBatchSourceRecord> sources,
            ShippingOptionDefinition definition
    );

protected abstract ShippingDecisionEvaluation evaluateMobileShippingDecision(
            BusinessAccessContext access,
            MobileShippingDecisionRequest request,
            List<FulfillmentBalanceRecord> balances
    );

protected abstract MobileShippingDecisionRequest mobileShippingDecisionRequest(MobileShippingDecisionPreviewCommand command);

protected abstract List<ShippingBatchSourceRecord> buildMobileShippingDecisionSources(
            BusinessAccessContext access,
            MobileShippingDecisionRequest request,
            List<FulfillmentBalanceRecord> balances,
            List<String> blockers
    );

protected abstract List<ShippingOptionDefinition> mobileShippingCandidateDefinitions(MobileShippingDecisionRequest request);

protected abstract ShippingOptionDefinition mobileForwarderOption(MobileShippingDecisionRequest request, String optionKey);

protected abstract ShippingOptionDefinition mobileSingleForwarderOption(MobileShippingDecisionRequest request, String forwarderCode);

protected abstract List<String> mobileOptionForwarderCodes(String optionKey);

protected abstract String preferredMobileAirForwarderCode(List<String> forwarderCodes);

protected abstract String preferredMobileSeaForwarderCode(List<String> forwarderCodes);

protected abstract ShippingDecisionOption assessMobileShippingDecisionOption(
            ShippingSuggestionOptionView view,
            ShippingOptionDefinition definition,
            MobileShippingDecisionRequest request
    );

protected abstract List<ShippingDecisionOption> rankedMobileShippingDecisionOptions(List<ShippingDecisionOption> candidates);

protected abstract ShippingDecisionOption acceptedMobileShippingDecisionOption(
            ShippingDecisionEvaluation evaluation,
            String acceptedOptionKey
    );

protected abstract ShippingSuggestionOptionView persistedMobileDecisionOption(
            BusinessAccessContext access,
            ShippingBatchView batch,
            ShippingDecisionOption accepted
    );

protected abstract MobileShippingDecisionPreviewView toMobileShippingDecisionPreviewView(ShippingDecisionEvaluation evaluation);

protected abstract MobileShippingDecisionOptionView toMobileShippingDecisionOptionView(
            ShippingDecisionOption option,
            String persistedOptionId
    );

protected abstract MobileShippingDecisionLineView toMobileShippingDecisionLineView(ShippingSuggestionLineView line);

protected abstract List<String> mobileForwarderNames(ShippingSuggestionOptionView option);

protected abstract List<String> mobileRouteNames(ShippingSuggestionOptionView option);

protected abstract List<MobileShippingDecisionForwarderAllocationView> mobileForwarderAllocations(
            ShippingSuggestionOptionView option
    );

protected abstract String mobileOptionKey(String siteCode, String transportMode, List<String> forwarderCodes);

protected abstract void collectShippingLineDefects(List<String> blockedReasons, PendingShippingLine pendingLine);

protected abstract ShippingSuggestionOptionView recommendedShippingOption(List<ShippingSuggestionOptionView> options);

protected abstract void mergeDefects(List<String> target, List<String> source);

protected abstract List<String> missingPlanSuggestions(List<String> defects);

protected abstract BigDecimal totalSourceActualWeightKg(List<ShippingBatchSourceRecord> sources);

protected abstract BigDecimal totalSourceVolumeCbm(List<ShippingBatchSourceRecord> sources);

protected abstract List<ShippingSuggestionOptionView> createDefaultShippingSuggestionOptions(
            ShippingBatchRecord batch,
            List<ShippingBatchSourceRecord> sources,
            Long operatorUserId
    );

protected abstract ShippingSuggestionOptionView createShippingSuggestionOption(
            ShippingBatchRecord batch,
            List<ShippingBatchSourceRecord> sources,
            ShippingOptionDefinition definition,
            Long operatorUserId
    );

protected abstract ShippingBatchSourceRecord toShippingBatchSourceRecord(
            Long batchId,
            Long ownerUserId,
            Long sourceId,
            FulfillmentBalanceRecord balance,
            Integer quantity
    );

protected abstract ShippingBatchView toShippingBatchView(ShippingBatchRecord record);

protected abstract ShippingBatchView toShippingBatchDetail(ShippingBatchRecord record);

protected abstract ShippingBatchSourceView toShippingBatchSourceView(ShippingBatchSourceRecord source);

protected abstract ShippingSuggestionOptionView toShippingSuggestionOptionView(ShippingSuggestionOptionRecord option);

protected abstract ShippingSuggestionLineView toShippingSuggestionLineView(ShippingSuggestionLineRecord line);

protected abstract ShippingSuggestionLineSourceView toShippingSuggestionLineSourceView(ShippingSuggestionLineSourceRecord source);

protected abstract void validateSelectedOptionAllocation(
            List<ShippingBatchSourceRecord> batchSources,
            List<ShippingSuggestionLineRecord> optionLines,
            List<ShippingSuggestionLineSourceRecord> optionSources
    );

protected abstract OutboundOrderLineRecord toOutboundOrderLineRecord(
            Long outboundOrderId,
            Long batchId,
            Long ownerUserId,
            Long outboundOrderLineId,
            ShippingSuggestionLineRecord suggestionLine
    );

protected abstract OutboundOrderLineSourceRecord toOutboundOrderLineSourceRecord(
            Long outboundOrderId,
            Long outboundOrderLineId,
            Long outboundOrderLineSourceId,
            ShippingSuggestionLineSourceRecord suggestionSource,
            ShippingBatchSourceRecord batchSource
    );

protected abstract OutboundOrderView toOutboundOrderView(OutboundOrderRecord order);

protected abstract OutboundOrderView toOutboundOrderDetail(OutboundOrderRecord order);

protected abstract OutboundOrderLineView toOutboundOrderLineView(OutboundOrderLineRecord line);

protected abstract OutboundOrderLineSourceView toOutboundOrderLineSourceView(OutboundOrderLineSourceRecord source);

protected abstract void hydrateOutboundLineStoreScope(OutboundOrderLineView lineView);

protected abstract PendingPackingBox toPendingPackingBox(PackingBoxCommand command);

protected abstract void validatePackingConfirmation(
            List<OutboundOrderLineRecord> outboundLines,
            List<PackingBoxRecord> boxes,
            List<PackingBoxItemRecord> items
    );

protected abstract PackingListView toPackingListView(PackingListRecord packingList);

protected abstract PackingListView toPackingListDetail(PackingListRecord packingList);

protected abstract PackingBoxView toPackingBoxView(PackingBoxRecord box);

protected abstract void applyPackingBoxSealSummary(PackingBoxView boxView, PackingListRecord packingList);

protected abstract PackingBoxCommand toPackingBoxCommand(PackingBoxRecord box, List<PackingBoxItemRecord> items);

protected abstract PackingBoxItemView toPackingBoxItemView(PackingBoxItemRecord item);
}
