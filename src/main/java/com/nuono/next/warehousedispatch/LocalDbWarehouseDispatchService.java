package com.nuono.next.warehousedispatch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ConfirmationCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ConfirmationLineCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateDispatchPlanCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreatePackingListCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateShippingBatchCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateShippingTargetOptionCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.DispatchPlanSourceCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.HandoffFailureCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.PackingBoxCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.PackingBoxItemCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ReplacePackingBoxesCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ShippingBatchSourceCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.UpdateFulfillmentCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.BalanceQuantityDelta;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ForwarderRouteQuoteRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentBalanceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentConfirmationInsertRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentConfirmationLineInsertRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingBoxItemRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingBoxRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingListRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseOrderAccessRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseOrderItemRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseOrderItemSiteRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseReceiptRow;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionOptionRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.ConfirmationLineView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.ConfirmationView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.DispatchPlanLineSourceView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.DispatchPlanLineView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.DispatchPlanView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.FulfillmentItemView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.LogisticsHandoffView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.OutboundOrderLineSourceView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.OutboundOrderLineView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.OutboundOrderView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PackingBoxItemView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PackingBoxView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PackingListView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PurchaseReceiptItemView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PurchaseReceiptOrderView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PurchaseOrderLogisticsComparisonView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PurchaseOrderLogisticsSegmentView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.ReadyItemView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.ReadySourceView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.ShippingBatchSourceView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.ShippingBatchView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.ShippingSuggestionLineSourceView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.ShippingSuggestionLineView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.ShippingSuggestionOptionView;
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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbWarehouseDispatchService {

    private static final String FULFILLMENT_WAREHOUSE = "WAREHOUSE_RECEIPT";
    private static final String FULFILLMENT_FACTORY = "FACTORY_DIRECT";
    private static final String TRANSPORT_AIR = "AIR";
    private static final String TRANSPORT_SEA = "SEA";
    private static final String TRANSPORT_UNSPECIFIED = "UNSPECIFIED";
    private static final BigDecimal CUBIC_CM_PER_CBM = BigDecimal.valueOf(1_000_000L);
    private static final BigDecimal GRAMS_PER_KG = BigDecimal.valueOf(1_000L);
    private static final BigDecimal DEFAULT_AIR_VOLUME_DIVISOR = BigDecimal.valueOf(5000L);
    private static final BigDecimal DEFAULT_YT_SEA_MIN_CBM = BigDecimal.valueOf(0.2);
    private static final List<ShippingOptionDefinition> DEFAULT_SHIPPING_OPTIONS = List.of(
            new ShippingOptionDefinition("AUTO_RECOMMEND", "自动推荐组合", 95, "AUTO",
                    List.of("ZD", "YT"), List.of("众鸫", "义特"), true, "ZD", "YT"),
            new ShippingOptionDefinition("FORWARDER_ET", "易通单货代", 88, "SINGLE",
                    List.of("ET"), List.of("易通"), false, "ET", "ET"),
            new ShippingOptionDefinition("FORWARDER_ZD", "众鸫单货代", 84, "SINGLE",
                    List.of("ZD"), List.of("众鸫"), false, "ZD", "ZD"),
            new ShippingOptionDefinition("COMBINATION_ZD_YT", "众鸫空运 + 义特海运", 86, "COMBINATION",
                    List.of("ZD", "YT"), List.of("众鸫", "义特"), false, "ZD", "YT"),
            new ShippingOptionDefinition("COMBINATION_ET_ZD", "易通 + 众鸫组合", 82, "COMBINATION",
                    List.of("ET", "ZD"), List.of("易通", "众鸫"), false, "ZD", "ET")
    );

    private final WarehouseDispatchMapper mapper;
    private final ObjectMapper objectMapper;

    public LocalDbWarehouseDispatchService(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FulfillmentItemView updateItemFulfillment(
            BusinessAccessContext access,
            String purchaseOrderId,
            String purchaseOrderItemId,
            UpdateFulfillmentCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException("缺少履约方式参数。");
        }
        Long parsedOrderId = parseLongId(purchaseOrderId, "采购单不存在或已删除。");
        Long parsedItemId = parseLongId(purchaseOrderItemId, "采购单商品不存在或已删除。");
        PurchaseOrderAccessRecord order = requireOrderAccess(access, parsedOrderId);
        PurchaseOrderItemRecord item = requireItem(order, parsedItemId);
        String fulfillmentType = normalizeFulfillmentType(command.fulfillmentType);
        String sourceName = trimToNull(command.sourceName);

        ensureItemBalances(item, fulfillmentType, access.getSessionUserId());
        if (mapper.countItemFulfillmentActivity(item.id) > 0) {
            throw new IllegalArgumentException("该商品已经收货、预留或交接物流，不能修改履约方式。");
        }

        mapper.updatePurchaseOrderItemFulfillment(item.id, fulfillmentType, sourceName, access.getSessionUserId());
        mapper.updateActiveBalancesFulfillment(item.id, fulfillmentType, access.getSessionUserId());
        log(null, "UPDATE_ITEM_FULFILLMENT", access.getSessionUserId(), null, null, item.partnerSku);

        FulfillmentItemView view = new FulfillmentItemView();
        view.purchaseOrderId = String.valueOf(order.id);
        view.purchaseOrderItemId = String.valueOf(item.id);
        view.fulfillmentType = fulfillmentType;
        view.sourceName = sourceName;
        return view;
    }

    @Transactional
    public ConfirmationView createConfirmation(BusinessAccessContext access, ConfirmationCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("缺少收货确认参数。");
        }
        Long purchaseOrderId = parseLongId(command.purchaseOrderId, "采购单不存在或已删除。");
        PurchaseOrderAccessRecord order = requireOrderAccess(access, purchaseOrderId);
        String confirmationType = normalizeConfirmationType(command.confirmationType);
        List<ConfirmationLineCommand> lines = command.lines == null ? List.of() : command.lines;
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("请选择至少一个确认商品。");
        }

        Long confirmationId = mapper.nextConfirmationId();
        List<FulfillmentConfirmationLineInsertRecord> lineRows = new ArrayList<>();
        List<BalanceQuantityDelta> balanceDeltas = new ArrayList<>();
        ConfirmationView view = new ConfirmationView();
        view.id = String.valueOf(confirmationId);
        view.confirmationNo = "FC-" + confirmationId;
        view.confirmationType = confirmationType;
        view.status = "CONFIRMED";

        int expectedTotal = 0;
        int confirmedTotal = 0;
        int abnormalTotal = 0;
        for (ConfirmationLineCommand lineCommand : lines) {
            Long itemId = parseLongId(lineCommand == null ? null : lineCommand.purchaseOrderItemId, "采购单商品不存在或已删除。");
            PurchaseOrderItemRecord item = requireItem(order, itemId);
            int confirmedDelta = nonNull(lineCommand.confirmedQuantity);
            int abnormalDelta = nonNull(lineCommand.abnormalQuantity);
            if (confirmedDelta < 0 || abnormalDelta < 0) {
                throw new IllegalArgumentException("收货数量不能为负数。");
            }
            if (confirmedDelta == 0 && abnormalDelta == 0) {
                throw new IllegalArgumentException("收货确认数量必须大于 0。");
            }

            ensureItemBalances(item, normalizeFulfillmentType(item.fulfillmentType), access.getSessionUserId());
            List<FulfillmentBalanceRecord> balances = mapper.listBalancesForItemForUpdate(item.id);
            if (balances.isEmpty()) {
                throw new IllegalArgumentException("采购单商品缺少站点计划，不能确认收货。");
            }
            int plannedTotal = balances.stream().mapToInt(row -> nonNull(row.plannedQuantity)).sum();
            int currentUsable = balances.stream()
                    .mapToInt(row -> nonNull(row.confirmedQuantity) - nonNull(row.abnormalQuantity))
                    .sum();
            int nextUsable = currentUsable + confirmedDelta - abnormalDelta;
            if (nextUsable > plannedTotal) {
                throw new IllegalArgumentException(item.partnerSku + " 可用收货数量超过采购计划。");
            }

            Map<Long, Integer> confirmedAllocations = allocateByPlannedQuantity(balances, confirmedDelta);
            Map<Long, Integer> abnormalAllocations = allocateByPlannedQuantity(balances, abnormalDelta);
            for (FulfillmentBalanceRecord balance : balances) {
                int allocatedConfirmed = confirmedAllocations.getOrDefault(balance.id, 0);
                int allocatedAbnormal = abnormalAllocations.getOrDefault(balance.id, 0);
                int nextAvailable = nonNull(balance.confirmedQuantity) + allocatedConfirmed
                        - nonNull(balance.abnormalQuantity) - allocatedAbnormal
                        - nonNull(balance.reservedQuantity)
                        - nonNull(balance.logisticsHandoffQuantity);
                if (nextAvailable < 0) {
                    throw new IllegalArgumentException(item.partnerSku + " 异常数量超过可用收货数量。");
                }
                if (allocatedConfirmed != 0 || allocatedAbnormal != 0) {
                    balanceDeltas.add(new BalanceQuantityDelta(
                            balance.id,
                            allocatedConfirmed,
                            allocatedAbnormal,
                            access.getSessionUserId()
                    ));
                }
            }

            Long lineId = mapper.nextConfirmationLineId();
            FulfillmentConfirmationLineInsertRecord row = new FulfillmentConfirmationLineInsertRecord();
            row.id = lineId;
            row.confirmationId = confirmationId;
            row.ownerUserId = order.ownerUserId;
            row.logicalStoreId = order.logicalStoreId;
            row.purchaseOrderId = order.id;
            row.purchaseOrderItemId = item.id;
            row.productMasterId = item.productMasterId;
            row.productVariantId = item.productVariantId;
            row.partnerSku = item.partnerSku;
            row.skuParent = item.skuParent;
            row.titleCache = item.titleCache;
            row.imageUrlCache = item.imageUrlCache;
            row.fulfillmentType = normalizeFulfillmentType(item.fulfillmentType);
            row.expectedQuantity = plannedTotal;
            row.confirmedQuantityDelta = confirmedDelta;
            row.abnormalQuantityDelta = abnormalDelta;
            row.exceptionReason = trimToNull(lineCommand.exceptionReason);
            row.snapshotJson = writeJson(Map.of(
                    "allocation", balanceDeltas.stream()
                            .filter(delta -> confirmedAllocations.containsKey(delta.balanceId)
                                    || abnormalAllocations.containsKey(delta.balanceId))
                            .collect(Collectors.toList())
            ));
            row.operatorUserId = access.getSessionUserId();
            lineRows.add(row);

            ConfirmationLineView lineView = new ConfirmationLineView();
            lineView.purchaseOrderItemId = String.valueOf(item.id);
            lineView.partnerSku = item.partnerSku;
            lineView.expectedQuantity = plannedTotal;
            lineView.confirmedQuantity = confirmedDelta;
            lineView.abnormalQuantity = abnormalDelta;
            view.lines.add(lineView);

            expectedTotal += plannedTotal;
            confirmedTotal += confirmedDelta;
            abnormalTotal += abnormalDelta;
        }

        FulfillmentConfirmationInsertRecord header = new FulfillmentConfirmationInsertRecord();
        header.id = confirmationId;
        header.ownerUserId = order.ownerUserId;
        header.logicalStoreId = order.logicalStoreId;
        header.purchaseOrderId = order.id;
        header.confirmationNo = view.confirmationNo;
        header.confirmationType = confirmationType;
        header.status = "CONFIRMED";
        header.sourcePartyName = trimToNull(command.sourcePartyName);
        header.operatorUserId = access.getSessionUserId();
        header.expectedQuantity = expectedTotal;
        header.confirmedQuantityDelta = confirmedTotal;
        header.abnormalQuantityDelta = abnormalTotal;
        header.remark = trimToNull(command.remark);
        mapper.insertConfirmation(header);
        for (FulfillmentConfirmationLineInsertRecord row : lineRows) {
            mapper.insertConfirmationLine(row);
        }
        for (BalanceQuantityDelta delta : balanceDeltas) {
            mapper.updateBalanceQuantities(delta);
        }
        log(null, "CREATE_FULFILLMENT_CONFIRMATION", access.getSessionUserId(), null, "CONFIRMED", view.confirmationNo);
        view.expectedQuantity = expectedTotal;
        view.confirmedQuantity = confirmedTotal;
        view.abnormalQuantity = abnormalTotal;
        return view;
    }

    @Transactional(readOnly = true)
    public List<PurchaseReceiptOrderView> listReceiptOrders(BusinessAccessContext access, String keyword) {
        Long ownerUserId = ownerUserId(access);
        Collection<String> storeCodes = access == null ? Set.of() : access.getStoreCodes();
        List<PurchaseReceiptRow> rows = mapper.listReceiptRows(ownerUserId, storeCodes, trim(keyword));
        Map<Long, PurchaseReceiptOrderView> byOrder = new LinkedHashMap<>();
        for (PurchaseReceiptRow row : rows) {
            if (!canAccessSourceStore(access, row.sourceStoreCode)) {
                continue;
            }
            PurchaseReceiptOrderView order = byOrder.computeIfAbsent(row.orderId, ignored -> {
                PurchaseReceiptOrderView view = new PurchaseReceiptOrderView();
                view.id = String.valueOf(row.orderId);
                view.orderNo = row.orderNo;
                view.title = row.orderTitle;
                view.storeName = row.storeName;
                view.storeCode = row.sourceStoreCode;
                view.createdAt = row.createdAt;
                return view;
            });
            order.items.add(toReceiptItemView(row));
        }
        return new ArrayList<>(byOrder.values());
    }

    @Transactional(readOnly = true)
    public List<ReadyItemView> listReadyItems(
            BusinessAccessContext access,
            String siteCode,
            String fulfillmentType,
            String keyword
    ) {
        Long ownerUserId = ownerUserId(access);
        Collection<String> storeCodes = access == null ? Set.of() : access.getStoreCodes();
        String normalizedSiteCode = normalizeSiteCode(siteCode);
        String normalizedFulfillmentType = StringUtils.hasText(fulfillmentType)
                ? normalizeFulfillmentType(fulfillmentType)
                : null;
        String normalizedKeyword = trim(keyword);
        List<FulfillmentBalanceRecord> balances = mapper.listReadyBalances(
                ownerUserId,
                storeCodes,
                normalizedSiteCode,
                normalizedFulfillmentType
        );
        Map<String, ReadyItemView> grouped = new LinkedHashMap<>();
        for (FulfillmentBalanceRecord balance : balances) {
            if (!matchesKeyword(balance, normalizedKeyword)) {
                continue;
            }
            if (!canAccessSourceStore(access, balance.sourceStoreCode)) {
                continue;
            }
            String key = balance.productVariantId + "|" + balance.siteCode + "|"
                    + normalizeFulfillmentType(balance.fulfillmentType) + "|"
                    + defaultText(balance.specStatus, "READY");
            ReadyItemView view = grouped.computeIfAbsent(key, ignored -> {
                ReadyItemView item = new ReadyItemView();
                item.productVariantId = String.valueOf(balance.productVariantId);
                item.partnerSku = balance.partnerSku;
                item.skuParent = balance.skuParent;
                item.productTitle = defaultText(balance.titleCache, balance.partnerSku);
                item.productImageUrl = balance.imageUrlCache;
                item.siteCode = balance.siteCode;
                item.isNewProduct = Boolean.TRUE.equals(balance.isNewProduct);
                item.manualConfirmRequired = requiresManualConfirm(balance);
                item.fulfillmentType = normalizeFulfillmentType(balance.fulfillmentType);
                item.specStatus = defaultText(balance.specStatus, "READY");
                item.availableQuantity = 0;
                return item;
            });
            view.isNewProduct = Boolean.TRUE.equals(view.isNewProduct) || Boolean.TRUE.equals(balance.isNewProduct);
            view.manualConfirmRequired = Boolean.TRUE.equals(view.manualConfirmRequired) || requiresManualConfirm(balance);
            view.availableQuantity += nonNull(balance.availableQuantity);
            view.sources.add(toReadySourceView(balance));
        }
        return new ArrayList<>(grouped.values());
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderLogisticsComparisonView> listPurchaseOrderLogisticsComparisons(
            BusinessAccessContext access,
            Integer limit
    ) {
        int normalizedLimit = Math.max(1, Math.min(limit == null ? 10 : limit, 50));
        Long ownerUserId = ownerUserId(access);
        Collection<String> storeCodes = access == null ? Set.of() : access.getStoreCodes();
        List<FulfillmentBalanceRecord> balances = mapper.listPurchasePlanBalances(ownerUserId, storeCodes).stream()
                .filter(balance -> balance.purchaseOrderId != null)
                .filter(balance -> canAccessSourceStore(access, balance.sourceStoreCode))
                .filter(balance -> purchaseComparisonQuantity(balance) > 0)
                .sorted(Comparator
                        .comparing((FulfillmentBalanceRecord balance) -> balance.purchaseOrderId, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(balance -> defaultText(balance.siteCode, ""))
                        .thenComparing(balance -> defaultText(balance.plannedTransportMode, "")))
                .collect(Collectors.toList());
        Set<Long> includedOrderIds = new LinkedHashSet<>();
        List<FulfillmentBalanceRecord> limitedBalances = new ArrayList<>();
        for (FulfillmentBalanceRecord balance : balances) {
            if (!includedOrderIds.contains(balance.purchaseOrderId) && includedOrderIds.size() >= normalizedLimit) {
                continue;
            }
            includedOrderIds.add(balance.purchaseOrderId);
            limitedBalances.add(balance);
        }

        Map<Long, List<FulfillmentBalanceRecord>> byOrder = limitedBalances.stream()
                .collect(Collectors.groupingBy(
                        balance -> balance.purchaseOrderId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<PurchaseOrderLogisticsComparisonView> result = new ArrayList<>();
        for (List<FulfillmentBalanceRecord> orderBalances : byOrder.values()) {
            result.add(toPurchaseOrderLogisticsComparisonView(orderBalances));
        }
        return result;
    }

    @Transactional
    public DispatchPlanView createDispatchPlan(BusinessAccessContext access, CreateDispatchPlanCommand command) {
        if (command == null || command.sources == null || command.sources.isEmpty()) {
            throw new IllegalArgumentException("请选择可发运商品。");
        }
        LinkedHashMap<Long, List<DispatchPlanSourceCommand>> requested = new LinkedHashMap<>();
        for (DispatchPlanSourceCommand source : command.sources) {
            if (source == null || source.fulfillmentBalanceId == null || nonNull(source.quantity) <= 0) {
                continue;
            }
            requested.computeIfAbsent(source.fulfillmentBalanceId, ignored -> new ArrayList<>()).add(source);
        }
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("请选择可发运商品。");
        }

        List<FulfillmentBalanceRecord> balances = mapper.selectBalancesForUpdate(new ArrayList<>(requested.keySet()));
        if (balances.size() != requested.size()) {
            throw new IllegalArgumentException("可发运来源不存在或已被占用。");
        }
        Long operatorUserId = access.getSessionUserId();
        Long ownerUserId = ownerUserId(access);
        Long planId = mapper.nextDispatchPlanId();
        String planNo = "DP-" + planId;

        Map<String, PendingDispatchLine> lineGroups = new LinkedHashMap<>();
        for (FulfillmentBalanceRecord balance : balances) {
            List<DispatchPlanSourceCommand> sourceCommands = requested.get(balance.id);
            if (!canUseBalance(access, balance)) {
                throw new IllegalArgumentException("当前账号不能发运所选来源。");
            }
            int totalQuantity = sourceCommands.stream().mapToInt(source -> nonNull(source.quantity)).sum();
            if (totalQuantity > nonNull(balance.availableQuantity)) {
                throw new IllegalArgumentException(balance.partnerSku + " 可发运数量不足。");
            }
            int reserved = mapper.reserveBalance(balance.id, totalQuantity, operatorUserId);
            if (reserved != 1) {
                throw new IllegalArgumentException(balance.partnerSku + " 可发运数量不足或已被占用。");
            }
            String fulfillmentType = normalizeFulfillmentType(balance.fulfillmentType);
            String specStatus = defaultText(balance.specStatus, "READY");
            for (DispatchPlanSourceCommand source : sourceCommands) {
                int quantity = nonNull(source.quantity);
                String actualTransportMode = normalizeTransportMode(
                        StringUtils.hasText(source.actualTransportMode)
                                ? source.actualTransportMode
                                : balance.plannedTransportMode
                );
                String key = dispatchLineKey(balance, actualTransportMode, fulfillmentType, specStatus);
                lineGroups.computeIfAbsent(key, ignored -> new PendingDispatchLine(
                        balance,
                        actualTransportMode,
                        fulfillmentType,
                        specStatus
                )).sources.add(new PendingDispatchSource(balance, quantity));
            }
        }

        DispatchPlanView view = new DispatchPlanView();
        view.id = String.valueOf(planId);
        view.ownerUserId = ownerUserId;
        view.planNo = planNo;
        view.status = "DRAFT";
        view.totalQuantity = 0;

        LinkedHashSet<String> skuKeys = new LinkedHashSet<>();
        for (PendingDispatchLine pendingLine : lineGroups.values()) {
            DispatchPlanLineRecord line = pendingLine.toRecord(planId, ownerUserId, mapper.nextDispatchLineId());
            mapper.insertDispatchPlanLine(line, operatorUserId);
            DispatchPlanLineView lineView = toDispatchLineView(line);
            for (PendingDispatchSource pendingSource : pendingLine.sources) {
                DispatchPlanLineSourceRecord sourceRow = pendingSource.toRecord(
                        planId,
                        line.id,
                        ownerUserId,
                        mapper.nextDispatchSourceId(),
                        pendingLine.fulfillmentType
                );
                mapper.insertDispatchPlanLineSource(sourceRow, operatorUserId);
                lineView.sources.add(toDispatchSourceView(sourceRow));
            }
            view.lines.add(lineView);
            view.totalQuantity += nonNull(line.quantity);
            skuKeys.add(String.valueOf(line.productVariantId));
        }

        view.itemCount = view.lines.size();
        view.skuCount = skuKeys.size();
        DispatchPlanRecord plan = new DispatchPlanRecord();
        plan.id = planId;
        plan.ownerUserId = ownerUserId;
        plan.planNo = planNo;
        plan.status = "DRAFT";
        plan.remark = trimToNull(command.remark);
        plan.handoffGenerationNo = 0;
        plan.itemCount = view.itemCount;
        plan.skuCount = view.skuCount;
        plan.totalQuantity = view.totalQuantity;
        plan.siteSummaryJson = writeJson(siteSummary(view.lines));
        plan.transportSummaryJson = writeJson(transportSummary(view.lines));
        mapper.insertDispatchPlan(plan, operatorUserId);
        log(planId, "CREATE_DISPATCH_PLAN", operatorUserId, null, "DRAFT", planNo);
        return view;
    }

    @Transactional
    public ShippingBatchView createShippingBatch(BusinessAccessContext access, CreateShippingBatchCommand command) {
        if (command == null || command.sources == null || command.sources.isEmpty()) {
            throw new IllegalArgumentException("请选择可发运商品。");
        }
        LinkedHashMap<Long, Integer> requested = new LinkedHashMap<>();
        for (ShippingBatchSourceCommand source : command.sources) {
            if (source == null || source.fulfillmentBalanceId == null || nonNull(source.quantity) <= 0) {
                continue;
            }
            requested.merge(source.fulfillmentBalanceId, nonNull(source.quantity), Integer::sum);
        }
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("请选择可发运商品。");
        }

        List<FulfillmentBalanceRecord> balances = mapper.selectBalancesForUpdate(new ArrayList<>(requested.keySet()));
        if (balances.size() != requested.size()) {
            throw new IllegalArgumentException("可发运来源不存在或已被占用。");
        }

        Long operatorUserId = access.getSessionUserId();
        Long ownerUserId = ownerUserId(access);
        Long batchId = mapper.nextShippingBatchId();

        List<ShippingBatchSourceRecord> sourceRows = new ArrayList<>();
        for (FulfillmentBalanceRecord balance : balances) {
            if (!canUseBalance(access, balance)) {
                throw new IllegalArgumentException("当前账号不能发运所选来源。");
            }
            int quantity = requested.getOrDefault(balance.id, 0);
            if (quantity <= 0 || quantity > nonNull(balance.availableQuantity)) {
                throw new IllegalArgumentException(balance.partnerSku + " 可发运数量不足。");
            }
            int reserved = mapper.reserveBalance(balance.id, quantity, operatorUserId);
            if (reserved != 1) {
                throw new IllegalArgumentException(balance.partnerSku + " 可发运数量不足或已被占用。");
            }
            sourceRows.add(toShippingBatchSourceRecord(
                    batchId,
                    ownerUserId,
                    mapper.nextShippingBatchSourceId(),
                    balance,
                    quantity
            ));
        }
        String batchNo = shippingBatchNo(batchId, sourceRows);

        ShippingBatchRecord batch = new ShippingBatchRecord();
        batch.id = batchId;
        batch.ownerUserId = ownerUserId;
        batch.batchNo = batchNo;
        batch.status = "DRAFT";
        batch.sourceCount = sourceRows.size();
        batch.skuCount = shippingSkuCount(sourceRows);
        batch.totalQuantity = sourceRows.stream().mapToInt(source -> nonNull(source.reservedQuantity)).sum();
        batch.storeSummaryJson = writeJson(shippingStoreSummary(sourceRows));
        batch.siteSummaryJson = writeJson(shippingSiteSummary(sourceRows));
        batch.transportSummaryJson = writeJson(shippingPlannedTransportSummary(sourceRows));
        batch.originSummaryJson = writeJson(shippingOriginSummary(sourceRows));
        batch.remark = trimToNull(command.remark);
        mapper.insertShippingBatch(batch, operatorUserId);
        for (ShippingBatchSourceRecord sourceRow : sourceRows) {
            mapper.insertShippingBatchSource(sourceRow, operatorUserId);
        }

        ShippingBatchView view = toShippingBatchView(batch);
        for (ShippingBatchSourceRecord sourceRow : sourceRows) {
            view.sources.add(toShippingBatchSourceView(sourceRow));
        }
        view.options.addAll(createDefaultShippingSuggestionOptions(batch, sourceRows, operatorUserId));
        log(null, "CREATE_SHIPPING_BATCH", operatorUserId, null, "DRAFT", batchNo);
        return view;
    }

    @Transactional(readOnly = true)
    public List<ShippingBatchView> listShippingBatches(BusinessAccessContext access) {
        Long ownerUserId = ownerUserId(access);
        return mapper.listShippingBatches(ownerUserId, 50).stream()
                .map(this::toShippingBatchView)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ShippingBatchView getShippingBatch(BusinessAccessContext access, String shippingBatchId) {
        return toShippingBatchDetail(requireShippingBatchAccess(
                access,
                parseLongId(shippingBatchId, "发货批次不存在或已删除。")
        ));
    }

    @Transactional
    public ShippingSuggestionOptionView createShippingTargetOption(
            BusinessAccessContext access,
            String shippingBatchId,
            CreateShippingTargetOptionCommand command
    ) {
        Long parsedBatchId = parseLongId(shippingBatchId, "发货批次不存在或已删除。");
        ShippingBatchRecord batch = requireShippingBatchAccess(access, parsedBatchId);
        if (!"DRAFT".equals(batch.status) && !"OPTION_SELECTED".equals(batch.status)) {
            throw new IllegalArgumentException("只有草稿状态的发货批次可以新增目标货代方案。");
        }

        List<ShippingBatchSourceRecord> sources = emptyIfNull(mapper.listShippingBatchSources(batch.id));
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("发货批次没有可评估商品。");
        }

        Long operatorUserId = access.getSessionUserId();
        ShippingOptionDefinition definition = customShippingOptionDefinition(command);
        ShippingSuggestionOptionView optionView = createShippingSuggestionOption(
                batch,
                sources,
                definition,
                operatorUserId
        );
        log(null, "CREATE_SHIPPING_TARGET_OPTION", operatorUserId, batch.status, batch.status, definition.optionName);
        return optionView;
    }

    @Transactional
    public ShippingBatchView selectShippingOption(
            BusinessAccessContext access,
            String shippingBatchId,
            String optionId
    ) {
        Long parsedBatchId = parseLongId(shippingBatchId, "发货批次不存在或已删除。");
        Long parsedOptionId = parseLongId(optionId, "货运计划方案不存在或已删除。");
        ShippingBatchRecord batch = requireShippingBatchAccess(access, parsedBatchId);
        if (!"DRAFT".equals(batch.status) && !"OPTION_SELECTED".equals(batch.status)) {
            throw new IllegalArgumentException("只有草稿状态的发货批次可以选择建议方案。");
        }
        ShippingSuggestionOptionRecord option = mapper.selectShippingSuggestionOptionById(parsedOptionId);
        if (option == null || !parsedBatchId.equals(option.batchId)) {
            throw new IllegalArgumentException("货运计划方案不存在或不属于该批次。");
        }

        Long operatorUserId = access.getSessionUserId();
        mapper.clearSelectedShippingOptions(batch.id, operatorUserId);
        if (mapper.selectShippingSuggestionOption(batch.id, option.id, operatorUserId) != 1) {
            throw new IllegalArgumentException("货运计划方案不存在或已失效。");
        }
        if (mapper.updateShippingBatchSelectedOption(batch.id, batch.ownerUserId, option.id, operatorUserId) != 1) {
            throw new IllegalArgumentException("发货批次状态已变化，请刷新后重试。");
        }
        log(null, "SELECT_SHIPPING_OPTION", operatorUserId, batch.status, "OPTION_SELECTED", option.optionType);

        ShippingBatchRecord updated = mapper.selectShippingBatchById(batch.id);
        return toShippingBatchView(updated == null ? batch : updated);
    }

    @Transactional
    public List<OutboundOrderView> createOutboundOrders(BusinessAccessContext access, String shippingBatchId) {
        Long parsedBatchId = parseLongId(shippingBatchId, "发货批次不存在或已删除。");
        ShippingBatchRecord batch = requireShippingBatchAccess(access, parsedBatchId);
        if (!"OPTION_SELECTED".equals(batch.status)) {
            throw new IllegalArgumentException("请先选择货运计划方案。");
        }
        if (batch.selectedOptionId == null) {
            throw new IllegalArgumentException("请先选择货运计划方案。");
        }
        ShippingSuggestionOptionRecord option = mapper.selectShippingSuggestionOptionById(batch.selectedOptionId);
        if (option == null || !batch.id.equals(option.batchId)) {
            throw new IllegalArgumentException("选中的货运计划方案不存在或已失效。");
        }

        List<ShippingBatchSourceRecord> sources = emptyIfNull(mapper.listShippingBatchSources(batch.id));
        List<ShippingSuggestionLineRecord> optionLines = emptyIfNull(mapper.listShippingSuggestionLines(batch.id)).stream()
                .filter(line -> batch.selectedOptionId.equals(line.optionId))
                .collect(Collectors.toList());
        List<ShippingSuggestionLineSourceRecord> optionSources = emptyIfNull(mapper.listShippingSuggestionLineSources(batch.id)).stream()
                .filter(source -> batch.selectedOptionId.equals(source.optionId))
                .collect(Collectors.toList());
        if (optionLines.isEmpty()) {
            throw new IllegalArgumentException("选中的货运计划方案没有可出库明细。");
        }
        validateSelectedOptionAllocation(sources, optionLines, optionSources);

        Long operatorUserId = access.getSessionUserId();
        Map<Long, ShippingBatchSourceRecord> batchSourceById = sources.stream()
                .collect(Collectors.toMap(source -> source.id, source -> source, (left, right) -> left, LinkedHashMap::new));
        Map<Long, List<ShippingSuggestionLineSourceRecord>> suggestionSourcesByLine = optionSources.stream()
                .collect(Collectors.groupingBy(source -> source.lineId, LinkedHashMap::new, Collectors.toList()));
        Map<String, List<ShippingSuggestionLineRecord>> linesByOrigin = optionLines.stream()
                .collect(Collectors.groupingBy(this::shippingOriginKey, LinkedHashMap::new, Collectors.toList()));

        List<OutboundOrderView> result = new ArrayList<>();
        for (List<ShippingSuggestionLineRecord> originLines : linesByOrigin.values()) {
            ShippingSuggestionLineRecord firstLine = originLines.get(0);
            Long outboundOrderId = mapper.nextOutboundOrderId();
            OutboundOrderRecord order = new OutboundOrderRecord();
            order.id = outboundOrderId;
            order.batchId = batch.id;
            order.optionId = batch.selectedOptionId;
            order.ownerUserId = batch.ownerUserId;
            order.outboundNo = "WO-" + outboundOrderId;
            order.status = "DRAFT";
            order.originType = normalizeFulfillmentType(firstLine.fulfillmentType);
            order.originName = firstLine.sourcePartyName;
            order.skuCount = outboundSkuCount(originLines);
            order.totalQuantity = originLines.stream().mapToInt(line -> nonNull(line.quantity)).sum();
            order.siteSummaryJson = writeJson(outboundSiteSummary(originLines));
            order.transportSummaryJson = writeJson(outboundTransportSummary(originLines));
            mapper.insertOutboundOrder(order, operatorUserId);

            OutboundOrderView orderView = toOutboundOrderView(order);
            for (ShippingSuggestionLineRecord suggestionLine : originLines) {
                OutboundOrderLineRecord outboundLine = toOutboundOrderLineRecord(
                        outboundOrderId,
                        batch.id,
                        batch.ownerUserId,
                        mapper.nextOutboundOrderLineId(),
                        suggestionLine
                );
                mapper.insertOutboundOrderLine(outboundLine, operatorUserId);
                OutboundOrderLineView lineView = toOutboundOrderLineView(outboundLine);
                for (ShippingSuggestionLineSourceRecord suggestionSource
                        : suggestionSourcesByLine.getOrDefault(suggestionLine.id, List.of())) {
                    ShippingBatchSourceRecord batchSource = batchSourceById.get(suggestionSource.batchSourceId);
                    OutboundOrderLineSourceRecord outboundSource = toOutboundOrderLineSourceRecord(
                            outboundOrderId,
                            outboundLine.id,
                            mapper.nextOutboundOrderLineSourceId(),
                            suggestionSource,
                            batchSource
                    );
                    mapper.insertOutboundOrderLineSource(outboundSource, operatorUserId);
                    lineView.sources.add(toOutboundOrderLineSourceView(outboundSource));
                }
                orderView.lines.add(lineView);
            }
            result.add(orderView);
        }

        if (mapper.updateShippingBatchOutboundCreated(batch.id, batch.ownerUserId, operatorUserId) != 1) {
            throw new IllegalArgumentException("发货批次状态已变化，请刷新后重试。");
        }
        log(null, "CREATE_OUTBOUND_ORDER", operatorUserId, batch.status, "OUTBOUND_CREATED", batch.batchNo);
        return result;
    }

    @Transactional(readOnly = true)
    public List<OutboundOrderView> listOutboundOrders(BusinessAccessContext access, String shippingBatchId) {
        ShippingBatchRecord batch = requireShippingBatchAccess(
                access,
                parseLongId(shippingBatchId, "发货批次不存在或已删除。")
        );
        return mapper.listOutboundOrdersByBatch(batch.id).stream()
                .map(this::toOutboundOrderDetail)
                .collect(Collectors.toList());
    }

    @Transactional
    public PackingListView createPackingList(
            BusinessAccessContext access,
            String outboundOrderId,
            CreatePackingListCommand command
    ) {
        OutboundOrderRecord outboundOrder = requireOutboundOrderAccess(
                access,
                parseLongId(outboundOrderId, "出库单不存在或已删除。")
        );
        if (!"DRAFT".equals(outboundOrder.status) && !"PACKING".equals(outboundOrder.status)) {
            throw new IllegalArgumentException("只有草稿或装箱中的出库单可以创建装箱单。");
        }

        Long operatorUserId = access.getSessionUserId();
        Long packingListId = mapper.nextPackingListId();
        PackingListRecord packingList = new PackingListRecord();
        packingList.id = packingListId;
        packingList.outboundOrderId = outboundOrder.id;
        packingList.ownerUserId = outboundOrder.ownerUserId;
        packingList.packingNo = "PK-" + packingListId;
        packingList.status = "DRAFT";
        packingList.boxCount = 0;
        packingList.packedQuantity = 0;
        packingList.remark = command == null ? null : trimToNull(command.remark);
        mapper.insertPackingList(packingList, operatorUserId);
        if (mapper.markOutboundOrderPacking(outboundOrder.id, outboundOrder.ownerUserId, operatorUserId) != 1) {
            throw new IllegalArgumentException("出库单状态已变化，请刷新后重试。");
        }
        log(null, "CREATE_PACKING_LIST", operatorUserId, outboundOrder.status, "PACKING", packingList.packingNo);
        return toPackingListView(packingList);
    }

    @Transactional(readOnly = true)
    public List<PackingListView> listPackingLists(BusinessAccessContext access, String outboundOrderId) {
        OutboundOrderRecord outboundOrder = requireOutboundOrderAccess(
                access,
                parseLongId(outboundOrderId, "出库单不存在或已删除。")
        );
        return mapper.listPackingListsByOutboundOrder(outboundOrder.id).stream()
                .map(this::toPackingListDetail)
                .collect(Collectors.toList());
    }

    @Transactional
    public PackingListView replacePackingBoxes(
            BusinessAccessContext access,
            String packingListId,
            ReplacePackingBoxesCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException("缺少装箱参数。");
        }
        PackingListRecord packingList = requirePackingListAccess(
                access,
                parseLongId(packingListId, "装箱单不存在或已删除。")
        );
        if (!"DRAFT".equals(packingList.status)) {
            throw new IllegalArgumentException("只有草稿装箱单可以修改箱明细。");
        }
        OutboundOrderRecord outboundOrder = requireOutboundOrderAccess(access, packingList.outboundOrderId);
        Map<Long, OutboundOrderLineRecord> outboundLineById = mapper.listOutboundOrderLines(outboundOrder.id).stream()
                .collect(Collectors.toMap(line -> line.id, line -> line, (left, right) -> left, LinkedHashMap::new));
        List<PackingBoxCommand> boxCommands = command.boxes == null ? List.of() : command.boxes;
        if (boxCommands.isEmpty()) {
            throw new IllegalArgumentException("请至少填写一个箱。");
        }

        List<PendingPackingBox> pendingBoxes = new ArrayList<>();
        Map<Long, Integer> packedByLine = new LinkedHashMap<>();
        BigDecimal grossWeightKg = BigDecimal.ZERO;
        BigDecimal volumeCbm = BigDecimal.ZERO;
        int packedQuantity = 0;
        for (PackingBoxCommand boxCommand : boxCommands) {
            PendingPackingBox pendingBox = toPendingPackingBox(boxCommand);
            if (pendingBox.items.isEmpty()) {
                throw new IllegalArgumentException("每个箱至少需要一个商品。");
            }
            for (PendingPackingItem pendingItem : pendingBox.items) {
                OutboundOrderLineRecord outboundLine = outboundLineById.get(pendingItem.outboundOrderLineId);
                if (outboundLine == null) {
                    throw new IllegalArgumentException("装箱商品不属于该出库单。");
                }
                packedByLine.merge(outboundLine.id, pendingItem.quantity, Integer::sum);
                if (packedByLine.get(outboundLine.id) > nonNull(outboundLine.quantity)) {
                    throw new IllegalArgumentException(outboundLine.partnerSku + " 装箱数量超过出库数量。");
                }
                packedQuantity += pendingItem.quantity;
            }
            grossWeightKg = grossWeightKg.add(pendingBox.grossWeightKg);
            volumeCbm = volumeCbm.add(pendingBox.volumeCbm());
            pendingBoxes.add(pendingBox);
        }

        Long operatorUserId = access.getSessionUserId();
        mapper.deletePackingBoxItems(packingList.id, operatorUserId);
        mapper.deletePackingBoxes(packingList.id, operatorUserId);

        PackingListView view = toPackingListView(packingList);
        view.boxes.clear();
        for (PendingPackingBox pendingBox : pendingBoxes) {
            Long boxId = mapper.nextPackingBoxId();
            PackingBoxRecord box = pendingBox.toRecord(
                    boxId,
                    packingList.id,
                    outboundOrder.id,
                    packingList.ownerUserId
            );
            mapper.insertPackingBox(box, operatorUserId);
            PackingBoxView boxView = toPackingBoxView(box);
            for (PendingPackingItem pendingItem : pendingBox.items) {
                OutboundOrderLineRecord outboundLine = outboundLineById.get(pendingItem.outboundOrderLineId);
                PackingBoxItemRecord item = pendingItem.toRecord(
                        mapper.nextPackingBoxItemId(),
                        packingList.id,
                        box.id,
                        outboundOrder.id,
                        packingList.ownerUserId,
                        outboundLine
                );
                mapper.insertPackingBoxItem(item, operatorUserId);
                boxView.items.add(toPackingBoxItemView(item));
            }
            view.boxes.add(boxView);
        }
        mapper.updatePackingListTotals(
                packingList.id,
                packingList.ownerUserId,
                pendingBoxes.size(),
                packedQuantity,
                grossWeightKg,
                volumeCbm.setScale(4, RoundingMode.HALF_UP),
                trimToNull(command.remark),
                operatorUserId
        );
        view.boxCount = pendingBoxes.size();
        view.packedQuantity = packedQuantity;
        view.grossWeightKg = grossWeightKg.toPlainString();
        view.volumeCbm = volumeCbm.setScale(4, RoundingMode.HALF_UP).toPlainString();
        view.remark = trimToNull(command.remark);
        return view;
    }

    @Transactional
    public PackingListView confirmPackingList(BusinessAccessContext access, String packingListId) {
        PackingListRecord packingList = requirePackingListAccess(
                access,
                parseLongId(packingListId, "装箱单不存在或已删除。")
        );
        if (!"DRAFT".equals(packingList.status)) {
            throw new IllegalArgumentException("只有草稿装箱单可以确认。");
        }
        OutboundOrderRecord outboundOrder = requireOutboundOrderAccess(access, packingList.outboundOrderId);
        List<OutboundOrderLineRecord> outboundLines = mapper.listOutboundOrderLines(outboundOrder.id);
        List<PackingBoxRecord> boxes = mapper.listPackingBoxes(packingList.id);
        List<PackingBoxItemRecord> items = mapper.listPackingBoxItems(packingList.id);
        validatePackingConfirmation(outboundLines, boxes, items);

        Long operatorUserId = access.getSessionUserId();
        if (mapper.confirmPackingList(packingList.id, packingList.ownerUserId, operatorUserId) != 1) {
            throw new IllegalArgumentException("装箱单状态已变化，请刷新后重试。");
        }
        if (mapper.markOutboundOrderPacked(outboundOrder.id, outboundOrder.ownerUserId, operatorUserId) != 1) {
            throw new IllegalArgumentException("出库单状态已变化，请刷新后重试。");
        }
        log(null, "CONFIRM_PACKING_LIST", operatorUserId, "DRAFT", "CONFIRMED", packingList.packingNo);

        PackingListView view = toPackingListView(packingList);
        view.status = "CONFIRMED";
        view.boxes.clear();
        Map<Long, List<PackingBoxItemRecord>> itemsByBox = items.stream()
                .collect(Collectors.groupingBy(item -> item.packingBoxId, LinkedHashMap::new, Collectors.toList()));
        for (PackingBoxRecord box : boxes) {
            PackingBoxView boxView = toPackingBoxView(box);
            for (PackingBoxItemRecord item : itemsByBox.getOrDefault(box.id, List.of())) {
                boxView.items.add(toPackingBoxItemView(item));
            }
            view.boxes.add(boxView);
        }
        view.boxCount = boxes.size();
        view.packedQuantity = items.stream().mapToInt(item -> nonNull(item.quantity)).sum();
        return view;
    }

    @Transactional(readOnly = true)
    public List<DispatchPlanView> listDispatchPlans(BusinessAccessContext access) {
        Long ownerUserId = ownerUserId(access);
        return mapper.listDispatchPlans(ownerUserId, 50).stream()
                .map(this::toDispatchPlanView)
                .collect(Collectors.toList());
    }

    @Transactional
    public DispatchPlanView readyForLogistics(BusinessAccessContext access, String dispatchPlanId) {
        Long parsedPlanId = parseLongId(dispatchPlanId, "发运计划不存在或已删除。");
        DispatchPlanRecord plan = requireDispatchPlanAccess(access, parsedPlanId);
        if (!"DRAFT".equals(plan.status) && !"HANDOFF_FAILED".equals(plan.status)) {
            throw new IllegalArgumentException("只有草稿或物流交接失败的发运计划可以提交物流。");
        }
        int nextGeneration = nonNull(plan.handoffGenerationNo) + 1;
        String handoffRequestNo = "WDH-" + plan.id + "-" + nextGeneration;
        mapper.updateDispatchPlanReady(
                plan.id,
                plan.ownerUserId,
                nextGeneration,
                handoffRequestNo,
                access.getSessionUserId()
        );
        log(plan.id, "READY_FOR_LOGISTICS", access.getSessionUserId(), plan.status, "READY_FOR_LOGISTICS", handoffRequestNo);
        DispatchPlanRecord updated = mapper.selectDispatchPlanById(plan.id);
        if (updated == null) {
            updated = plan;
            updated.status = "READY_FOR_LOGISTICS";
            updated.handoffGenerationNo = nextGeneration;
            updated.handoffRequestNo = handoffRequestNo;
        }
        return toDispatchPlanView(updated);
    }

    @Transactional(readOnly = true)
    public LogisticsHandoffView getLogisticsHandoff(BusinessAccessContext access, String dispatchPlanId) {
        DispatchPlanRecord plan = requireDispatchPlanAccess(access, parseLongId(dispatchPlanId, "发运计划不存在或已删除。"));
        LogisticsHandoffView view = new LogisticsHandoffView();
        view.dispatchPlanId = String.valueOf(plan.id);
        view.dispatchPlanNo = plan.planNo;
        view.status = plan.status;
        view.handoffGenerationNo = nonNull(plan.handoffGenerationNo);
        view.handoffRequestNo = plan.handoffRequestNo;
        view.lines = toDispatchPlanView(plan).lines;
        return view;
    }

    @Transactional
    public DispatchPlanView reopenDraft(BusinessAccessContext access, String dispatchPlanId) {
        DispatchPlanRecord plan = requireDispatchPlanAccess(access, parseLongId(dispatchPlanId, "发运计划不存在或已删除。"));
        mapper.reopenDispatchPlanDraft(plan.id, plan.ownerUserId, access.getSessionUserId());
        log(plan.id, "REOPEN_DRAFT", access.getSessionUserId(), plan.status, "DRAFT", plan.planNo);
        DispatchPlanRecord updated = mapper.selectDispatchPlanById(plan.id);
        return toDispatchPlanView(updated == null ? plan : updated);
    }

    @Transactional
    public DispatchPlanView markLogisticsHandoffSuccess(BusinessAccessContext access, String handoffRequestNo) {
        String requestNo = requiredText(handoffRequestNo, "缺少物流交接编号。");
        DispatchPlanRecord plan = requireHandoffAccess(access, requestNo);
        int changed = mapper.markDispatchPlanHandoffSuccess(requestNo, access.getSessionUserId());
        if (changed > 0) {
            for (DispatchPlanLineSourceRecord source : mapper.listDispatchLineSources(plan.id)) {
                mapper.moveReservedToLogisticsHandoff(
                        source.fulfillmentBalanceId,
                        nonNull(source.quantity),
                        access.getSessionUserId()
                );
            }
            log(plan.id, "HANDOFF_SUCCESS", access.getSessionUserId(), plan.status, "LOGISTICS_REQUESTED", requestNo);
        }
        DispatchPlanRecord updated = mapper.selectDispatchPlanByHandoffRequest(requestNo);
        return toDispatchPlanView(updated == null ? plan : updated);
    }

    @Transactional
    public DispatchPlanView markLogisticsHandoffFailure(BusinessAccessContext access, HandoffFailureCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("缺少物流交接失败参数。");
        }
        String requestNo = requiredText(command.handoffRequestNo, "缺少物流交接编号。");
        DispatchPlanRecord plan = requireHandoffAccess(access, requestNo);
        mapper.markDispatchPlanHandoffFailed(requestNo, trimToNull(command.errorMessage), access.getSessionUserId());
        log(plan.id, "HANDOFF_FAILED", access.getSessionUserId(), plan.status, "HANDOFF_FAILED", command.errorMessage);
        DispatchPlanRecord updated = mapper.selectDispatchPlanByHandoffRequest(requestNo);
        return toDispatchPlanView(updated == null ? plan : updated);
    }

    private void ensureItemBalances(PurchaseOrderItemRecord item, String fulfillmentType, Long operatorUserId) {
        List<PurchaseOrderItemSiteRecord> sites = mapper.listItemSitesForBalance(item.id);
        if (sites.isEmpty()) {
            throw new IllegalArgumentException("采购单商品缺少站点计划，不能进入仓库发运。");
        }
        for (PurchaseOrderItemSiteRecord site : sites) {
            mapper.upsertBalanceFromItemSite(site.id, fulfillmentType, operatorUserId);
        }
    }

    private Map<Long, Integer> allocateByPlannedQuantity(List<FulfillmentBalanceRecord> balances, int quantity) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        if (quantity <= 0) {
            for (FulfillmentBalanceRecord balance : balances) {
                result.put(balance.id, 0);
            }
            return result;
        }
        int plannedTotal = balances.stream().mapToInt(balance -> nonNull(balance.plannedQuantity)).sum();
        if (plannedTotal <= 0) {
            throw new IllegalArgumentException("采购计划数量为空，不能分摊收货。");
        }
        List<AllocationRemainder> remainders = new ArrayList<>();
        int allocated = 0;
        int index = 0;
        for (FulfillmentBalanceRecord balance : balances) {
            BigDecimal exact = BigDecimal.valueOf(quantity)
                    .multiply(BigDecimal.valueOf(nonNull(balance.plannedQuantity)))
                    .divide(BigDecimal.valueOf(plannedTotal), 8, RoundingMode.HALF_UP);
            int floor = exact.setScale(0, RoundingMode.DOWN).intValue();
            result.put(balance.id, floor);
            allocated += floor;
            remainders.add(new AllocationRemainder(
                    balance.id,
                    exact.subtract(BigDecimal.valueOf(floor)),
                    nonNull(balance.plannedQuantity),
                    index
            ));
            index++;
        }
        int remaining = quantity - allocated;
        remainders.sort(Comparator
                .comparing((AllocationRemainder item) -> item.remainder).reversed()
                .thenComparing((AllocationRemainder item) -> item.plannedQuantity, Comparator.reverseOrder())
                .thenComparing(item -> item.index));
        for (int i = 0; i < remaining && i < remainders.size(); i++) {
            AllocationRemainder remainder = remainders.get(i);
            result.put(remainder.balanceId, result.get(remainder.balanceId) + 1);
        }
        return result;
    }

    private ReadySourceView toReadySourceView(FulfillmentBalanceRecord balance) {
        ReadySourceView source = new ReadySourceView();
        source.fulfillmentBalanceId = balance.id;
        source.sourceStoreCode = balance.sourceStoreCode;
        source.sourceStoreName = balance.sourceStoreName;
        source.purchaseOrderId = balance.purchaseOrderId;
        source.purchaseOrderNo = balance.purchaseOrderNo;
        source.purchaseOrderTitle = balance.purchaseOrderTitle;
        source.purchaseOrderItemId = balance.purchaseOrderItemId;
        source.purchaseOrderItemSiteId = balance.purchaseOrderItemSiteId;
        source.plannedTransportMode = normalizeTransportMode(balance.plannedTransportMode);
        source.availableQuantity = nonNull(balance.availableQuantity);
        return source;
    }

    private PurchaseReceiptItemView toReceiptItemView(PurchaseReceiptRow row) {
        PurchaseReceiptItemView view = new PurchaseReceiptItemView();
        view.id = String.valueOf(row.itemId);
        view.orderId = String.valueOf(row.orderId);
        view.orderNo = row.orderNo;
        view.storeName = row.storeName;
        view.storeCode = row.sourceStoreCode;
        view.productVariantId = row.productVariantId == null ? null : String.valueOf(row.productVariantId);
        view.psku = row.partnerSku;
        view.title = defaultText(row.titleCache, row.partnerSku);
        view.titleCn = row.titleCn;
        view.imageUrl = row.imageUrlCache;
        view.siteCode = defaultText(row.siteCode, "SA");
        view.transportMode = normalizeTransportMode(row.transportMode);
        view.expectedQty = nonNull(row.expectedQuantity);
        view.receivedQty = nonNull(row.receivedQuantity);
        view.plannedQty = nonNull(row.plannedQuantity);
        view.specStatus = "SPEC_MISSING".equals(row.specStatus) ? "missing" : "complete";
        view.fulfillmentType = normalizeFulfillmentType(row.fulfillmentType);
        view.fulfillmentSourceName = row.fulfillmentSourceName;
        if (view.receivedQty < view.expectedQty && view.receivedQty > 0) {
            view.exceptionText = "少货 " + (view.expectedQty - view.receivedQty);
        }
        return view;
    }

    private DispatchPlanView toDispatchPlanView(DispatchPlanRecord record) {
        DispatchPlanView view = new DispatchPlanView();
        if (record == null) {
            return view;
        }
        view.id = String.valueOf(record.id);
        view.ownerUserId = record.ownerUserId;
        view.planNo = record.planNo;
        view.status = record.status;
        view.itemCount = nonNull(record.itemCount);
        view.skuCount = nonNull(record.skuCount);
        view.totalQuantity = nonNull(record.totalQuantity);
        view.handoffGenerationNo = nonNull(record.handoffGenerationNo);
        view.handoffRequestNo = record.handoffRequestNo;
        view.handoffErrorMessage = record.handoffErrorMessage;
        view.createdAt = record.createdAt;
        view.updatedAt = record.updatedAt;
        List<DispatchPlanLineRecord> lines = emptyIfNull(mapper.listDispatchPlanLines(record.id));
        Map<Long, List<DispatchPlanLineSourceRecord>> sourcesByLine = emptyIfNull(mapper.listDispatchLineSources(record.id)).stream()
                .collect(Collectors.groupingBy(source -> source.dispatchPlanLineId, LinkedHashMap::new, Collectors.toList()));
        for (DispatchPlanLineRecord line : lines) {
            DispatchPlanLineView lineView = toDispatchLineView(line);
            for (DispatchPlanLineSourceRecord source : sourcesByLine.getOrDefault(line.id, List.of())) {
                lineView.sources.add(toDispatchSourceView(source));
            }
            view.lines.add(lineView);
        }
        return view;
    }

    private DispatchPlanLineView toDispatchLineView(DispatchPlanLineRecord line) {
        DispatchPlanLineView view = new DispatchPlanLineView();
        view.id = String.valueOf(line.id);
        view.partnerSku = line.partnerSku;
        view.skuParent = line.skuParent;
        view.productTitle = defaultText(line.titleCache, line.partnerSku);
        view.productImageUrl = line.imageUrlCache;
        view.siteCode = line.siteCode;
        view.actualTransportMode = normalizeTransportMode(line.actualTransportMode);
        view.fulfillmentType = normalizeFulfillmentType(line.fulfillmentType);
        view.specStatus = defaultText(line.specStatus, "READY");
        view.quantity = nonNull(line.quantity);
        return view;
    }

    private DispatchPlanLineSourceView toDispatchSourceView(DispatchPlanLineSourceRecord source) {
        DispatchPlanLineSourceView view = new DispatchPlanLineSourceView();
        view.id = String.valueOf(source.id);
        view.dispatchPlanId = source.dispatchPlanId;
        view.dispatchPlanLineId = source.dispatchPlanLineId;
        view.fulfillmentBalanceId = source.fulfillmentBalanceId;
        view.sourceStoreCode = source.sourceStoreCode;
        view.sourceStoreName = source.sourceStoreName;
        view.purchaseOrderId = source.purchaseOrderId;
        view.purchaseOrderNo = source.purchaseOrderNo;
        view.purchaseOrderItemId = source.purchaseOrderItemId;
        view.purchaseOrderItemSiteId = source.purchaseOrderItemSiteId;
        view.plannedTransportMode = normalizeTransportMode(source.plannedTransportMode);
        view.fulfillmentType = normalizeFulfillmentType(source.fulfillmentType);
        view.quantity = nonNull(source.quantity);
        return view;
    }

    private List<ShippingSuggestionOptionView> createDefaultShippingSuggestionOptions(
            ShippingBatchRecord batch,
            List<ShippingBatchSourceRecord> sources,
            Long operatorUserId
    ) {
        List<ShippingSuggestionOptionView> result = new ArrayList<>();
        for (ShippingOptionDefinition definition : DEFAULT_SHIPPING_OPTIONS) {
            result.add(createShippingSuggestionOption(batch, sources, definition, operatorUserId));
        }
        return result;
    }

    private PurchaseOrderLogisticsComparisonView toPurchaseOrderLogisticsComparisonView(List<FulfillmentBalanceRecord> balances) {
        FulfillmentBalanceRecord first = balances.get(0);
        PurchaseOrderLogisticsComparisonView view = new PurchaseOrderLogisticsComparisonView();
        view.purchaseOrderId = String.valueOf(first.purchaseOrderId);
        view.purchaseOrderNo = first.purchaseOrderNo;
        view.purchaseOrderTitle = first.purchaseOrderTitle;
        view.sourceStoreCode = first.sourceStoreCode;
        view.sourceStoreName = first.sourceStoreName;
        view.skuCount = (int) balances.stream()
                .map(balance -> String.valueOf(balance.productVariantId))
                .distinct()
                .count();
        view.totalQuantity = balances.stream().mapToInt(this::purchaseComparisonQuantity).sum();
        view.quantityBasis = "PLANNED_PURCHASE_QUANTITY";
        view.quantityBasisLabel = "按采购计划数量预估";
        view.fulfillmentReadinessNote = "这是采购阶段物流成本预估，不代表已可发货；需仓库验收产生可用数量后再生成货运计划。";

        Map<String, List<FulfillmentBalanceRecord>> bySegment = balances.stream()
                .collect(Collectors.groupingBy(
                        balance -> defaultText(balance.siteCode, "UNKNOWN") + "|"
                                + normalizeTransportMode(balance.plannedTransportMode),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        for (Map.Entry<String, List<FulfillmentBalanceRecord>> entry : bySegment.entrySet()) {
            PurchaseOrderLogisticsSegmentView segment = toPurchaseOrderLogisticsSegmentView(entry.getKey(), entry.getValue());
            view.segments.add(segment);
            mergeDefects(view.defects, segment.defects);
            mergeDefects(view.missingPlanSuggestions, segment.missingPlanSuggestions);
            if (segment.actualWeightKg != null) {
                view.actualWeightKg = view.actualWeightKg == null ? segment.actualWeightKg : view.actualWeightKg.add(segment.actualWeightKg);
            }
            if (segment.volumeCbm != null) {
                view.volumeCbm = view.volumeCbm == null ? segment.volumeCbm : view.volumeCbm.add(segment.volumeCbm);
            }
            if (segment.recommendedEstimatedAmount != null) {
                view.recommendedEstimatedAmount = view.recommendedEstimatedAmount == null
                        ? segment.recommendedEstimatedAmount
                        : view.recommendedEstimatedAmount.add(segment.recommendedEstimatedAmount);
                view.currency = defaultText(view.currency, segment.currency);
            }
        }
        view.actualWeightKg = view.actualWeightKg == null ? null : view.actualWeightKg.setScale(3, RoundingMode.HALF_UP);
        view.volumeCbm = view.volumeCbm == null ? null : view.volumeCbm.setScale(4, RoundingMode.HALF_UP);
        if (view.recommendedEstimatedAmount != null) {
            view.recommendedOptionId = "SEGMENT_RECOMMENDED";
            view.recommendedOptionName = "按目的地/运输方式分段推荐";
            view.recommendedEstimatedAmount = view.recommendedEstimatedAmount.setScale(4, RoundingMode.HALF_UP);
        } else {
            addUnique(view.defects, "没有可计价的物流方案");
            addUnique(view.missingPlanSuggestions, "补充至少一个可覆盖该采购单目的地和运输方式的货代报价。");
        }
        return view;
    }

    private PurchaseOrderLogisticsSegmentView toPurchaseOrderLogisticsSegmentView(
            String segmentKey,
            List<FulfillmentBalanceRecord> balances
    ) {
        List<ShippingBatchSourceRecord> sources = balances.stream()
                .map(this::toAnalysisShippingBatchSource)
                .collect(Collectors.toList());
        ShippingBatchSourceRecord first = sources.get(0);
        PurchaseOrderLogisticsSegmentView view = new PurchaseOrderLogisticsSegmentView();
        view.segmentKey = segmentKey;
        view.siteCode = first.siteCode;
        view.plannedTransportMode = normalizeTransportMode(first.plannedTransportMode);
        view.skuCount = shippingSkuCount(sources);
        view.totalQuantity = sources.stream().mapToInt(source -> nonNull(source.reservedQuantity)).sum();
        view.quantityBasis = "PLANNED_PURCHASE_QUANTITY";
        view.quantityBasisLabel = "按采购计划数量预估";
        view.actualWeightKg = totalSourceActualWeightKg(sources);
        view.volumeCbm = totalSourceVolumeCbm(sources);

        for (ShippingOptionDefinition definition : DEFAULT_SHIPPING_OPTIONS) {
            ShippingSuggestionOptionView option = evaluateShippingOptionPreview(segmentKey, sources, definition);
            view.options.add(option);
            mergeDefects(view.defects, option.blockedReasons);
        }
        ShippingSuggestionOptionView recommended = recommendedShippingOption(view.options);
        if (recommended != null) {
            view.recommendedOptionId = recommended.id;
            view.recommendedOptionName = recommended.optionName;
            view.recommendedEstimatedAmount = recommended.estimatedTotalAmount;
            view.currency = recommended.currency;
        } else {
            addUnique(view.defects, "该目的地/运输方式没有可计价方案");
        }
        view.missingPlanSuggestions.addAll(missingPlanSuggestions(view.defects));
        return view;
    }

    private ShippingBatchSourceRecord toAnalysisShippingBatchSource(FulfillmentBalanceRecord balance) {
        ShippingBatchSourceRecord record = new ShippingBatchSourceRecord();
        record.id = balance.id;
        record.ownerUserId = balance.ownerUserId;
        record.fulfillmentBalanceId = balance.id;
        record.sourceStoreCode = balance.sourceStoreCode;
        record.sourceStoreName = balance.sourceStoreName;
        record.purchaseOrderId = balance.purchaseOrderId;
        record.purchaseOrderNo = balance.purchaseOrderNo;
        record.purchaseOrderTitle = balance.purchaseOrderTitle;
        record.purchaseOrderItemId = balance.purchaseOrderItemId;
        record.purchaseOrderItemSiteId = balance.purchaseOrderItemSiteId;
        record.productMasterId = balance.productMasterId;
        record.productVariantId = balance.productVariantId;
        record.partnerSku = balance.partnerSku;
        record.skuParent = balance.skuParent;
        record.titleCache = balance.titleCache;
        record.imageUrlCache = balance.imageUrlCache;
        record.siteCode = balance.siteCode;
        record.plannedTransportMode = normalizeTransportMode(balance.plannedTransportMode);
        record.fulfillmentType = normalizeFulfillmentType(balance.fulfillmentType);
        record.sourcePartyName = fulfillmentSourcePartyName(record.fulfillmentType, balance);
        record.specStatus = defaultText(balance.specStatus, "READY");
        record.productLengthCm = balance.productLengthCm;
        record.productWidthCm = balance.productWidthCm;
        record.productHeightCm = balance.productHeightCm;
        record.productWeightG = balance.productWeightG;
        record.logisticsProfileStatus = balance.logisticsProfileStatus;
        List<String> sensitiveReasons = sensitiveReasons(balance);
        record.sensitiveFlag = !sensitiveReasons.isEmpty();
        record.sensitiveReasonJson = sensitiveReasons.isEmpty() ? null : writeJson(sensitiveReasons);
        record.reservedQuantity = purchaseComparisonQuantity(balance);
        return record;
    }

    private int purchaseComparisonQuantity(FulfillmentBalanceRecord balance) {
        return nonNull(balance.plannedQuantity);
    }

    private ShippingSuggestionOptionView evaluateShippingOptionPreview(
            String segmentKey,
            List<ShippingBatchSourceRecord> sources,
            ShippingOptionDefinition definition
    ) {
        Map<String, PendingShippingLine> lineGroups = new LinkedHashMap<>();
        for (ShippingBatchSourceRecord source : sources) {
            String actualTransportMode = shippingActualTransportMode(definition.optionType, source);
            ShippingForwarderAssignment assignment = shippingForwarderAssignment(definition, actualTransportMode, source.siteCode);
            String key = shippingLineKey(source, actualTransportMode, assignment);
            lineGroups.computeIfAbsent(
                            key,
                            ignored -> new PendingShippingLine(
                                    source,
                                    actualTransportMode,
                                    assignment,
                                    writeJson(shippingLineAssignmentSnapshot(assignment))
                            )
                    )
                    .sources.add(new PendingShippingSource(
                            source,
                            nonNull(source.reservedQuantity),
                            readJsonStringList(source.sensitiveReasonJson)
                    ));
        }
        Map<String, List<ForwarderRouteQuoteRecord>> routeQuotes = forwarderRouteQuotes(lineGroups.values());
        for (PendingShippingLine pendingLine : lineGroups.values()) {
            pendingLine.evaluate(quotesForRoute(routeQuotes, pendingLine.routeCode));
        }

        ShippingSuggestionOptionView view = new ShippingSuggestionOptionView();
        view.id = "preview:" + segmentKey + ":" + definition.optionType;
        view.optionType = definition.optionType;
        view.optionName = definition.optionName;
        view.status = "CANDIDATE";
        view.selectedFlag = false;
        view.score = definition.score;
        view.skuCount = shippingSkuCount(sources);
        view.totalQuantity = 0;
        view.airQuantity = 0;
        view.seaQuantity = 0;
        view.specMissingCount = 0;
        view.warningCount = 0;
        view.forwarderPlanType = definition.forwarderPlanType;
        view.autoRecommended = definition.autoRecommended;
        view.targetForwarderCodes.addAll(definition.targetForwarderCodes);
        view.targetForwarderNames.addAll(definition.targetForwarderNames);
        view.routeCodes.addAll(routeCodes(lineGroups.values()));

        BigDecimal actualWeightKg = BigDecimal.ZERO;
        BigDecimal volumeCbm = BigDecimal.ZERO;
        BigDecimal chargeableWeightKg = BigDecimal.ZERO;
        BigDecimal estimatedTotalAmount = BigDecimal.ZERO;
        long lineId = -1L;
        for (PendingShippingLine pendingLine : lineGroups.values()) {
            int lineQuantity = pendingLine.quantity();
            view.totalQuantity += lineQuantity;
            if (TRANSPORT_AIR.equals(pendingLine.actualTransportMode)) {
                view.airQuantity += lineQuantity;
            }
            if (TRANSPORT_SEA.equals(pendingLine.actualTransportMode)) {
                view.seaQuantity += lineQuantity;
            }
            collectShippingLineDefects(view.blockedReasons, pendingLine);
            view.warningCount = view.blockedReasons.size();
            if (pendingLine.actualWeightKg != null) {
                actualWeightKg = actualWeightKg.add(pendingLine.actualWeightKg);
            }
            if (pendingLine.volumeCbm != null) {
                volumeCbm = volumeCbm.add(pendingLine.volumeCbm);
            }
            if (pendingLine.chargeableWeightKg != null) {
                chargeableWeightKg = chargeableWeightKg.add(pendingLine.chargeableWeightKg);
            }
            if (pendingLine.estimatedAmount != null) {
                estimatedTotalAmount = estimatedTotalAmount.add(pendingLine.estimatedAmount);
                view.currency = defaultText(view.currency, pendingLine.currency);
            }
            ShippingSuggestionLineRecord line = pendingLine.toRecord(null, null, null, lineId--);
            view.lines.add(toShippingSuggestionLineView(line));
        }
        view.actualWeightKg = zeroToNull(actualWeightKg, 3);
        view.volumeCbm = zeroToNull(volumeCbm, 4);
        view.chargeableWeightKg = zeroToNull(chargeableWeightKg, 3);
        view.estimatedTotalAmount = zeroToNull(estimatedTotalAmount, 4);
        view.avgUnitAmount = view.estimatedTotalAmount == null || view.totalQuantity == null || view.totalQuantity <= 0
                ? null
                : view.estimatedTotalAmount.divide(BigDecimal.valueOf(view.totalQuantity), 4, RoundingMode.HALF_UP);
        view.evaluationStatus = view.blockedReasons.isEmpty() ? "READY" : "NEEDS_REVIEW";
        return view;
    }

    private void collectShippingLineDefects(List<String> blockedReasons, PendingShippingLine pendingLine) {
        if (isSpecMissing(pendingLine.firstSource.specStatus)) {
            addUnique(blockedReasons, "规格缺失");
        }
        if (!StringUtils.hasText(pendingLine.routeCode)) {
            addUnique(blockedReasons, "目标货代缺少可用线路");
        }
        boolean hasEstimatedAmount = pendingLine.estimatedAmount != null;
        if (!pendingLine.sensitiveReasons().isEmpty() && !hasEstimatedAmount) {
            addUnique(blockedReasons, "存在敏货，需确认目标货代是否接收");
        }
        if (Boolean.TRUE.equals(pendingLine.cargoCategoryReviewRequired) && !hasEstimatedAmount) {
            addUnique(blockedReasons, "货物类别需人工复核");
        }
        if (Boolean.TRUE.equals(pendingLine.quoteMissingForCargoCategory)) {
            addUnique(blockedReasons, "目标货代缺少匹配货物类别报价");
        }
        if (pendingLine.estimatedAmount == null) {
            addUnique(blockedReasons, "报价规则不足，需人工复核");
        }
    }

    private ShippingSuggestionOptionView recommendedShippingOption(List<ShippingSuggestionOptionView> options) {
        return options.stream()
                .filter(option -> option.estimatedTotalAmount != null)
                .sorted(Comparator
                        .comparing((ShippingSuggestionOptionView option) -> !"READY".equalsIgnoreCase(defaultText(option.evaluationStatus, "")))
                        .thenComparing(option -> option.estimatedTotalAmount)
                        .thenComparing(option -> option.score == null ? 0 : -option.score))
                .findFirst()
                .orElse(null);
    }

    private void mergeDefects(List<String> target, List<String> source) {
        for (String value : emptyIfNull(source)) {
            addUnique(target, value);
        }
    }

    private List<String> missingPlanSuggestions(List<String> defects) {
        List<String> suggestions = new ArrayList<>();
        if (defects.contains("规格缺失")) {
            suggestions.add("补齐商品长宽高重量规格，否则无法评估真实物流成本。");
        }
        if (defects.contains("目标货代缺少可用线路")) {
            suggestions.add("补充目标站点和运输方式的货代线路模板。");
        }
        if (defects.contains("目标货代缺少匹配货物类别报价") || defects.contains("报价规则不足，需人工复核")) {
            suggestions.add("补充货代对应货物类别的有效报价档，避免用普货价低估敏货成本。");
        }
        if (defects.contains("货物类别需人工复核") || defects.contains("存在敏货，需确认目标货代是否接收")) {
            suggestions.add("补充商品物流属性到货物类别的明确映射，并确认目标货代敏货接收规则。");
        }
        if (defects.contains("未达到目标货代最低计费单位")) {
            suggestions.add("小批量触发最低计费，可考虑合单或补充小包/小票专线方案。");
        }
        return suggestions;
    }

    private BigDecimal totalSourceActualWeightKg(List<ShippingBatchSourceRecord> sources) {
        BigDecimal total = BigDecimal.ZERO;
        for (ShippingBatchSourceRecord source : sources) {
            if (source.productWeightG == null) {
                return null;
            }
            total = total.add(source.productWeightG.multiply(BigDecimal.valueOf(nonNull(source.reservedQuantity))));
        }
        return total.divide(GRAMS_PER_KG, 3, RoundingMode.HALF_UP);
    }

    private BigDecimal totalSourceVolumeCbm(List<ShippingBatchSourceRecord> sources) {
        BigDecimal total = BigDecimal.ZERO;
        for (ShippingBatchSourceRecord source : sources) {
            if (source.productLengthCm == null || source.productWidthCm == null || source.productHeightCm == null) {
                return null;
            }
            total = total.add(source.productLengthCm
                    .multiply(source.productWidthCm)
                    .multiply(source.productHeightCm)
                    .multiply(BigDecimal.valueOf(nonNull(source.reservedQuantity))));
        }
        return total.divide(CUBIC_CM_PER_CBM, 4, RoundingMode.HALF_UP);
    }

    private ShippingSuggestionOptionView createShippingSuggestionOption(
            ShippingBatchRecord batch,
            List<ShippingBatchSourceRecord> sources,
            ShippingOptionDefinition definition,
            Long operatorUserId
    ) {
        Map<String, PendingShippingLine> lineGroups = new LinkedHashMap<>();
        for (ShippingBatchSourceRecord source : sources) {
            String actualTransportMode = shippingActualTransportMode(definition.optionType, source);
            ShippingForwarderAssignment assignment = shippingForwarderAssignment(definition, actualTransportMode, source.siteCode);
            String key = shippingLineKey(source, actualTransportMode, assignment);
            lineGroups.computeIfAbsent(
                            key,
                            ignored -> new PendingShippingLine(
                                    source,
                                    actualTransportMode,
                                    assignment,
                                    writeJson(shippingLineAssignmentSnapshot(assignment))
                            )
                    )
                    .sources.add(new PendingShippingSource(
                            source,
                            nonNull(source.reservedQuantity),
                            readJsonStringList(source.sensitiveReasonJson)
                    ));
        }
        Map<String, List<ForwarderRouteQuoteRecord>> routeQuotes = forwarderRouteQuotes(lineGroups.values());
        for (PendingShippingLine pendingLine : lineGroups.values()) {
            pendingLine.evaluate(quotesForRoute(routeQuotes, pendingLine.routeCode));
        }

        ShippingSuggestionOptionRecord option = new ShippingSuggestionOptionRecord();
        option.id = mapper.nextShippingSuggestionOptionId();
        option.batchId = batch.id;
        option.ownerUserId = batch.ownerUserId;
        option.optionType = definition.optionType;
        option.optionName = definition.optionName;
        option.status = "CANDIDATE";
        option.selectedFlag = false;
        option.score = definition.score;
        option.skuCount = shippingSkuCount(sources);
        option.totalQuantity = 0;
        option.airQuantity = 0;
        option.seaQuantity = 0;
        option.specMissingCount = 0;
        option.warningCount = 0;
        option.forwarderPlanType = definition.forwarderPlanType;
        option.autoRecommended = definition.autoRecommended;
        option.targetForwarderCodesJson = writeJson(definition.targetForwarderCodes);
        option.targetForwarderNamesJson = writeJson(definition.targetForwarderNames);
        option.routeCodesJson = writeJson(routeCodes(lineGroups.values()));
        option.actualWeightKg = BigDecimal.ZERO;
        option.volumeCbm = BigDecimal.ZERO;
        option.chargeableWeightKg = BigDecimal.ZERO;
        option.estimatedTotalAmount = BigDecimal.ZERO;
        option.currency = null;
        List<String> blockedReasons = new ArrayList<>();
        List<Map<String, Object>> costSnapshots = new ArrayList<>();
        for (PendingShippingLine pendingLine : lineGroups.values()) {
            int lineQuantity = pendingLine.quantity();
            option.totalQuantity += lineQuantity;
            if (TRANSPORT_AIR.equals(pendingLine.actualTransportMode)) {
                option.airQuantity += lineQuantity;
            }
            if (TRANSPORT_SEA.equals(pendingLine.actualTransportMode)) {
                option.seaQuantity += lineQuantity;
            }
            if (isSpecMissing(pendingLine.firstSource.specStatus)) {
                option.specMissingCount += 1;
                option.warningCount += 1;
                addUnique(blockedReasons, "规格缺失");
            }
            if (!StringUtils.hasText(pendingLine.routeCode)) {
                option.warningCount += 1;
                addUnique(blockedReasons, "目标货代缺少可用线路");
            }
            if (!pendingLine.sensitiveReasons().isEmpty()) {
                option.warningCount += 1;
                addUnique(blockedReasons, "存在敏货，需确认目标货代是否接收");
            }
            if (Boolean.TRUE.equals(pendingLine.cargoCategoryReviewRequired)) {
                option.warningCount += 1;
                addUnique(blockedReasons, "货物类别需人工复核");
            }
            if (Boolean.TRUE.equals(pendingLine.quoteMissingForCargoCategory)) {
                option.warningCount += 1;
                addUnique(blockedReasons, "目标货代缺少匹配货物类别报价");
            }
            if (pendingLine.actualWeightKg != null) {
                option.actualWeightKg = option.actualWeightKg.add(pendingLine.actualWeightKg);
            }
            if (pendingLine.volumeCbm != null) {
                option.volumeCbm = option.volumeCbm.add(pendingLine.volumeCbm);
            }
            if (pendingLine.chargeableWeightKg != null) {
                option.chargeableWeightKg = option.chargeableWeightKg.add(pendingLine.chargeableWeightKg);
            }
            if (pendingLine.estimatedAmount != null) {
                option.estimatedTotalAmount = option.estimatedTotalAmount.add(pendingLine.estimatedAmount);
                option.currency = defaultText(option.currency, pendingLine.currency);
            } else {
                option.warningCount += 1;
                addUnique(blockedReasons, "报价规则不足，需人工复核");
            }
            if (pendingLine.minimumNotMet) {
                option.warningCount += 1;
                addUnique(blockedReasons, "未达到目标货代最低计费单位");
            }
            costSnapshots.add(pendingLine.costSnapshot());
        }
        option.actualWeightKg = zeroToNull(option.actualWeightKg, 3);
        option.volumeCbm = zeroToNull(option.volumeCbm, 4);
        option.chargeableWeightKg = zeroToNull(option.chargeableWeightKg, 3);
        option.estimatedTotalAmount = zeroToNull(option.estimatedTotalAmount, 4);
        option.avgUnitAmount = option.estimatedTotalAmount == null || option.totalQuantity == null || option.totalQuantity <= 0
                ? null
                : option.estimatedTotalAmount.divide(BigDecimal.valueOf(option.totalQuantity), 4, RoundingMode.HALF_UP);
        option.blockedReasonsJson = writeJson(blockedReasons);
        option.costSnapshotJson = writeJson(costSnapshots);
        option.evaluationStatus = blockedReasons.isEmpty() ? "READY" : "NEEDS_REVIEW";
        option.summaryJson = writeJson(shippingOptionSummary(definition, sources, lineGroups.values()));
        mapper.insertShippingSuggestionOption(option, operatorUserId);

        ShippingSuggestionOptionView optionView = toShippingSuggestionOptionView(option);
        for (PendingShippingLine pendingLine : lineGroups.values()) {
            ShippingSuggestionLineRecord line = pendingLine.toRecord(
                    option.id,
                    batch.id,
                    batch.ownerUserId,
                    mapper.nextShippingSuggestionLineId()
            );
            mapper.insertShippingSuggestionLine(line, operatorUserId);
            ShippingSuggestionLineView lineView = toShippingSuggestionLineView(line);
            for (PendingShippingSource pendingSource : pendingLine.sources) {
                ShippingSuggestionLineSourceRecord source = pendingSource.toRecord(
                        option.id,
                        line.id,
                        batch.id,
                        mapper.nextShippingSuggestionLineSourceId()
                );
                mapper.insertShippingSuggestionLineSource(source, operatorUserId);
                lineView.sources.add(toShippingSuggestionLineSourceView(source));
            }
            optionView.lines.add(lineView);
        }
        return optionView;
    }

    private ShippingBatchSourceRecord toShippingBatchSourceRecord(
            Long batchId,
            Long ownerUserId,
            Long sourceId,
            FulfillmentBalanceRecord balance,
            Integer quantity
    ) {
        String fulfillmentType = normalizeFulfillmentType(balance.fulfillmentType);
        ShippingBatchSourceRecord record = new ShippingBatchSourceRecord();
        record.id = sourceId;
        record.batchId = batchId;
        record.ownerUserId = ownerUserId;
        record.fulfillmentBalanceId = balance.id;
        record.sourceStoreCode = balance.sourceStoreCode;
        record.sourceStoreName = balance.sourceStoreName;
        record.purchaseOrderId = balance.purchaseOrderId;
        record.purchaseOrderNo = balance.purchaseOrderNo;
        record.purchaseOrderTitle = balance.purchaseOrderTitle;
        record.purchaseOrderItemId = balance.purchaseOrderItemId;
        record.purchaseOrderItemSiteId = balance.purchaseOrderItemSiteId;
        record.productMasterId = balance.productMasterId;
        record.productVariantId = balance.productVariantId;
        record.partnerSku = balance.partnerSku;
        record.skuParent = balance.skuParent;
        record.titleCache = balance.titleCache;
        record.imageUrlCache = balance.imageUrlCache;
        record.siteCode = balance.siteCode;
        record.plannedTransportMode = normalizeTransportMode(balance.plannedTransportMode);
        record.fulfillmentType = fulfillmentType;
        record.sourcePartyName = fulfillmentSourcePartyName(fulfillmentType, balance);
        record.specStatus = defaultText(balance.specStatus, "READY");
        record.productLengthCm = balance.productLengthCm;
        record.productWidthCm = balance.productWidthCm;
        record.productHeightCm = balance.productHeightCm;
        record.productWeightG = balance.productWeightG;
        record.logisticsProfileStatus = balance.logisticsProfileStatus;
        List<String> sensitiveReasons = sensitiveReasons(balance);
        record.sensitiveFlag = !sensitiveReasons.isEmpty();
        record.sensitiveReasonJson = sensitiveReasons.isEmpty() ? null : writeJson(sensitiveReasons);
        record.reservedQuantity = nonNull(quantity);
        return record;
    }

    private ShippingBatchView toShippingBatchView(ShippingBatchRecord record) {
        ShippingBatchView view = new ShippingBatchView();
        if (record == null) {
            return view;
        }
        view.id = String.valueOf(record.id);
        view.ownerUserId = record.ownerUserId;
        view.batchNo = record.batchNo;
        view.status = record.status;
        view.selectedOptionId = record.selectedOptionId == null ? null : String.valueOf(record.selectedOptionId);
        view.sourceCount = nonNull(record.sourceCount);
        view.skuCount = nonNull(record.skuCount);
        view.totalQuantity = nonNull(record.totalQuantity);
        view.remark = record.remark;
        view.createdAt = record.createdAt;
        view.updatedAt = record.updatedAt;
        return view;
    }

    private ShippingBatchView toShippingBatchDetail(ShippingBatchRecord record) {
        ShippingBatchView view = toShippingBatchView(record);
        for (ShippingBatchSourceRecord source : emptyIfNull(mapper.listShippingBatchSources(record.id))) {
            view.sources.add(toShippingBatchSourceView(source));
        }
        Map<Long, List<ShippingSuggestionLineRecord>> linesByOption = emptyIfNull(mapper.listShippingSuggestionLines(record.id)).stream()
                .collect(Collectors.groupingBy(line -> line.optionId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<ShippingSuggestionLineSourceRecord>> sourcesByLine = emptyIfNull(mapper.listShippingSuggestionLineSources(record.id)).stream()
                .collect(Collectors.groupingBy(source -> source.lineId, LinkedHashMap::new, Collectors.toList()));
        for (ShippingSuggestionOptionRecord option : emptyIfNull(mapper.listShippingSuggestionOptions(record.id))) {
            ShippingSuggestionOptionView optionView = toShippingSuggestionOptionView(option);
            for (ShippingSuggestionLineRecord line : linesByOption.getOrDefault(option.id, List.of())) {
                ShippingSuggestionLineView lineView = toShippingSuggestionLineView(line);
                for (ShippingSuggestionLineSourceRecord source : sourcesByLine.getOrDefault(line.id, List.of())) {
                    lineView.sources.add(toShippingSuggestionLineSourceView(source));
                }
                optionView.lines.add(lineView);
            }
            view.options.add(optionView);
        }
        return view;
    }

    private ShippingBatchSourceView toShippingBatchSourceView(ShippingBatchSourceRecord source) {
        ShippingBatchSourceView view = new ShippingBatchSourceView();
        view.id = String.valueOf(source.id);
        view.batchId = source.batchId;
        view.fulfillmentBalanceId = source.fulfillmentBalanceId;
        view.sourceStoreCode = source.sourceStoreCode;
        view.sourceStoreName = source.sourceStoreName;
        view.purchaseOrderId = source.purchaseOrderId;
        view.purchaseOrderNo = source.purchaseOrderNo;
        view.purchaseOrderTitle = source.purchaseOrderTitle;
        view.purchaseOrderItemId = source.purchaseOrderItemId;
        view.purchaseOrderItemSiteId = source.purchaseOrderItemSiteId;
        view.productVariantId = source.productVariantId;
        view.partnerSku = source.partnerSku;
        view.skuParent = source.skuParent;
        view.productTitle = defaultText(source.titleCache, source.partnerSku);
        view.productImageUrl = source.imageUrlCache;
        view.siteCode = source.siteCode;
        view.plannedTransportMode = normalizeTransportMode(source.plannedTransportMode);
        view.fulfillmentType = normalizeFulfillmentType(source.fulfillmentType);
        view.sourcePartyName = source.sourcePartyName;
        view.specStatus = defaultText(source.specStatus, "READY");
        view.productLengthCm = source.productLengthCm == null ? null : source.productLengthCm.toPlainString();
        view.productWidthCm = source.productWidthCm == null ? null : source.productWidthCm.toPlainString();
        view.productHeightCm = source.productHeightCm == null ? null : source.productHeightCm.toPlainString();
        view.productWeightG = source.productWeightG == null ? null : source.productWeightG.toPlainString();
        view.logisticsProfileStatus = source.logisticsProfileStatus;
        view.sensitiveFlag = Boolean.TRUE.equals(source.sensitiveFlag);
        view.sensitiveReasons.addAll(readJsonStringList(source.sensitiveReasonJson));
        view.reservedQuantity = nonNull(source.reservedQuantity);
        return view;
    }

    private ShippingSuggestionOptionView toShippingSuggestionOptionView(ShippingSuggestionOptionRecord option) {
        ShippingSuggestionOptionView view = new ShippingSuggestionOptionView();
        view.id = String.valueOf(option.id);
        view.batchId = option.batchId;
        view.optionType = option.optionType;
        view.optionName = option.optionName;
        view.status = option.status;
        view.selectedFlag = Boolean.TRUE.equals(option.selectedFlag);
        view.score = nonNull(option.score);
        view.skuCount = nonNull(option.skuCount);
        view.totalQuantity = nonNull(option.totalQuantity);
        view.airQuantity = nonNull(option.airQuantity);
        view.seaQuantity = nonNull(option.seaQuantity);
        view.specMissingCount = nonNull(option.specMissingCount);
        view.warningCount = nonNull(option.warningCount);
        Map<String, Object> summary = readJsonObject(option.summaryJson);
        view.forwarderPlanType = defaultText(option.forwarderPlanType, textFromObject(summary.get("forwarderPlanType"), "SINGLE"));
        view.autoRecommended = Boolean.TRUE.equals(option.autoRecommended) || booleanFromObject(summary.get("autoRecommended"));
        view.targetForwarderCodes.addAll(orElse(
                readJsonStringList(option.targetForwarderCodesJson),
                stringListFromObject(summary.get("targetForwarderCodes"))
        ));
        view.targetForwarderNames.addAll(orElse(
                readJsonStringList(option.targetForwarderNamesJson),
                stringListFromObject(summary.get("targetForwarderNames"))
        ));
        view.routeCodes.addAll(orElse(
                readJsonStringList(option.routeCodesJson),
                stringListFromObject(summary.get("routeCodes"))
        ));
        view.evaluationStatus = defaultText(option.evaluationStatus, "PENDING");
        view.blockedReasons.addAll(readJsonStringList(option.blockedReasonsJson));
        view.actualWeightKg = option.actualWeightKg;
        view.volumeCbm = option.volumeCbm;
        view.chargeableWeightKg = option.chargeableWeightKg;
        view.estimatedTotalAmount = option.estimatedTotalAmount;
        view.avgUnitAmount = option.avgUnitAmount;
        view.currency = option.currency;
        return view;
    }

    private ShippingSuggestionLineView toShippingSuggestionLineView(ShippingSuggestionLineRecord line) {
        ShippingSuggestionLineView view = new ShippingSuggestionLineView();
        view.id = String.valueOf(line.id);
        view.optionId = line.optionId;
        view.batchId = line.batchId;
        view.productVariantId = line.productVariantId;
        view.partnerSku = line.partnerSku;
        view.skuParent = line.skuParent;
        view.productTitle = defaultText(line.titleCache, line.partnerSku);
        view.productImageUrl = line.imageUrlCache;
        view.siteCode = line.siteCode;
        view.actualTransportMode = normalizeTransportMode(line.actualTransportMode);
        view.fulfillmentType = normalizeFulfillmentType(line.fulfillmentType);
        view.sourcePartyName = line.sourcePartyName;
        view.specStatus = defaultText(line.specStatus, "READY");
        Map<String, Object> assignment = readJsonObject(line.warningJson);
        view.targetForwarderCode = defaultText(line.targetForwarderCode, textFromObject(assignment.get("targetForwarderCode"), null));
        view.targetForwarderName = defaultText(line.targetForwarderName, textFromObject(assignment.get("targetForwarderName"), null));
        view.routeCode = defaultText(line.routeCode, textFromObject(assignment.get("routeCode"), null));
        view.routeName = defaultText(line.routeName, textFromObject(assignment.get("routeName"), null));
        view.cargoCategoryCode = textFromObject(assignment.get("cargoCategoryCode"), null);
        view.cargoCategoryName = textFromObject(assignment.get("cargoCategoryName"), null);
        view.quoteCargoCategoryCode = textFromObject(assignment.get("quoteCargoCategoryCode"), null);
        view.quoteCargoCategoryName = textFromObject(assignment.get("quoteCargoCategoryName"), null);
        view.cargoCategoryReviewRequired = booleanFromObject(assignment.get("cargoCategoryReviewRequired"));
        view.actualWeightKg = line.actualWeightKg;
        view.volumeCbm = line.volumeCbm;
        view.chargeableWeightKg = line.chargeableWeightKg;
        view.estimatedAmount = line.estimatedAmount;
        view.currency = line.currency;
        view.quantity = nonNull(line.quantity);
        return view;
    }

    private ShippingSuggestionLineSourceView toShippingSuggestionLineSourceView(ShippingSuggestionLineSourceRecord source) {
        ShippingSuggestionLineSourceView view = new ShippingSuggestionLineSourceView();
        view.id = String.valueOf(source.id);
        view.optionId = source.optionId;
        view.lineId = source.lineId;
        view.batchId = source.batchId;
        view.batchSourceId = source.batchSourceId;
        view.fulfillmentBalanceId = source.fulfillmentBalanceId;
        view.plannedTransportMode = normalizeTransportMode(source.plannedTransportMode);
        view.quantity = nonNull(source.quantity);
        return view;
    }

    private void validateSelectedOptionAllocation(
            List<ShippingBatchSourceRecord> batchSources,
            List<ShippingSuggestionLineRecord> optionLines,
            List<ShippingSuggestionLineSourceRecord> optionSources
    ) {
        Map<Long, ShippingBatchSourceRecord> batchSourceById = batchSources.stream()
                .collect(Collectors.toMap(source -> source.id, source -> source, (left, right) -> left, LinkedHashMap::new));
        Map<Long, ShippingSuggestionLineRecord> lineById = optionLines.stream()
                .collect(Collectors.toMap(line -> line.id, line -> line, (left, right) -> left, LinkedHashMap::new));
        Map<Long, Integer> allocatedBySource = new LinkedHashMap<>();
        Map<Long, Integer> allocatedByLine = new LinkedHashMap<>();
        for (ShippingSuggestionLineSourceRecord source : optionSources) {
            if (!batchSourceById.containsKey(source.batchSourceId)) {
                throw new IllegalArgumentException("货运计划方案包含无效来源。");
            }
            if (!lineById.containsKey(source.lineId)) {
                throw new IllegalArgumentException("货运计划方案包含无效明细。");
            }
            int quantity = nonNull(source.quantity);
            if (quantity <= 0) {
                throw new IllegalArgumentException("货运计划方案来源数量必须大于 0。");
            }
            allocatedBySource.merge(source.batchSourceId, quantity, Integer::sum);
            allocatedByLine.merge(source.lineId, quantity, Integer::sum);
        }
        for (ShippingBatchSourceRecord source : batchSources) {
            int reservedQuantity = nonNull(source.reservedQuantity);
            int allocatedQuantity = allocatedBySource.getOrDefault(source.id, 0);
            if (reservedQuantity != allocatedQuantity) {
                throw new IllegalArgumentException(source.partnerSku + " 货运计划方案未完整分配已预留数量。");
            }
        }
        for (ShippingSuggestionLineRecord line : optionLines) {
            int lineQuantity = nonNull(line.quantity);
            int allocatedQuantity = allocatedByLine.getOrDefault(line.id, 0);
            if (lineQuantity != allocatedQuantity) {
                throw new IllegalArgumentException(line.partnerSku + " 货运计划方案明细数量与来源数量不一致。");
            }
        }
    }

    private OutboundOrderLineRecord toOutboundOrderLineRecord(
            Long outboundOrderId,
            Long batchId,
            Long ownerUserId,
            Long outboundOrderLineId,
            ShippingSuggestionLineRecord suggestionLine
    ) {
        OutboundOrderLineRecord record = new OutboundOrderLineRecord();
        record.id = outboundOrderLineId;
        record.outboundOrderId = outboundOrderId;
        record.batchId = batchId;
        record.optionLineId = suggestionLine.id;
        record.ownerUserId = ownerUserId;
        record.productMasterId = suggestionLine.productMasterId;
        record.productVariantId = suggestionLine.productVariantId;
        record.partnerSku = suggestionLine.partnerSku;
        record.skuParent = suggestionLine.skuParent;
        record.titleCache = suggestionLine.titleCache;
        record.imageUrlCache = suggestionLine.imageUrlCache;
        record.siteCode = suggestionLine.siteCode;
        record.actualTransportMode = normalizeTransportMode(suggestionLine.actualTransportMode);
        record.fulfillmentType = normalizeFulfillmentType(suggestionLine.fulfillmentType);
        record.sourcePartyName = suggestionLine.sourcePartyName;
        record.specStatus = defaultText(suggestionLine.specStatus, "READY");
        record.quantity = nonNull(suggestionLine.quantity);
        record.packedQuantity = 0;
        return record;
    }

    private OutboundOrderLineSourceRecord toOutboundOrderLineSourceRecord(
            Long outboundOrderId,
            Long outboundOrderLineId,
            Long outboundOrderLineSourceId,
            ShippingSuggestionLineSourceRecord suggestionSource,
            ShippingBatchSourceRecord batchSource
    ) {
        if (batchSource == null) {
            throw new IllegalArgumentException("货运计划方案包含无效来源。");
        }
        OutboundOrderLineSourceRecord record = new OutboundOrderLineSourceRecord();
        record.id = outboundOrderLineSourceId;
        record.outboundOrderId = outboundOrderId;
        record.outboundOrderLineId = outboundOrderLineId;
        record.batchSourceId = suggestionSource.batchSourceId;
        record.fulfillmentBalanceId = suggestionSource.fulfillmentBalanceId;
        record.purchaseOrderId = batchSource.purchaseOrderId;
        record.purchaseOrderNo = batchSource.purchaseOrderNo;
        record.purchaseOrderTitle = batchSource.purchaseOrderTitle;
        record.purchaseOrderItemId = batchSource.purchaseOrderItemId;
        record.purchaseOrderItemSiteId = batchSource.purchaseOrderItemSiteId;
        record.plannedTransportMode = normalizeTransportMode(suggestionSource.plannedTransportMode);
        record.quantity = nonNull(suggestionSource.quantity);
        return record;
    }

    private OutboundOrderView toOutboundOrderView(OutboundOrderRecord order) {
        OutboundOrderView view = new OutboundOrderView();
        view.id = String.valueOf(order.id);
        view.batchId = order.batchId;
        view.optionId = order.optionId;
        view.ownerUserId = order.ownerUserId;
        view.outboundNo = order.outboundNo;
        view.status = order.status;
        view.originType = normalizeFulfillmentType(order.originType);
        view.originName = order.originName;
        view.skuCount = nonNull(order.skuCount);
        view.totalQuantity = nonNull(order.totalQuantity);
        view.remark = order.remark;
        view.createdAt = order.createdAt;
        view.updatedAt = order.updatedAt;
        return view;
    }

    private OutboundOrderView toOutboundOrderDetail(OutboundOrderRecord order) {
        OutboundOrderView view = toOutboundOrderView(order);
        Map<Long, List<OutboundOrderLineSourceRecord>> sourcesByLine = emptyIfNull(mapper.listOutboundOrderLineSources(order.id)).stream()
                .collect(Collectors.groupingBy(source -> source.outboundOrderLineId, LinkedHashMap::new, Collectors.toList()));
        for (OutboundOrderLineRecord line : emptyIfNull(mapper.listOutboundOrderLines(order.id))) {
            OutboundOrderLineView lineView = toOutboundOrderLineView(line);
            for (OutboundOrderLineSourceRecord source : sourcesByLine.getOrDefault(line.id, List.of())) {
                lineView.sources.add(toOutboundOrderLineSourceView(source));
            }
            view.lines.add(lineView);
        }
        return view;
    }

    private OutboundOrderLineView toOutboundOrderLineView(OutboundOrderLineRecord line) {
        OutboundOrderLineView view = new OutboundOrderLineView();
        view.id = String.valueOf(line.id);
        view.outboundOrderId = line.outboundOrderId;
        view.batchId = line.batchId;
        view.optionLineId = line.optionLineId;
        view.productVariantId = line.productVariantId;
        view.partnerSku = line.partnerSku;
        view.skuParent = line.skuParent;
        view.productTitle = defaultText(line.titleCache, line.partnerSku);
        view.productImageUrl = line.imageUrlCache;
        view.siteCode = line.siteCode;
        view.actualTransportMode = normalizeTransportMode(line.actualTransportMode);
        view.fulfillmentType = normalizeFulfillmentType(line.fulfillmentType);
        view.sourcePartyName = line.sourcePartyName;
        view.specStatus = defaultText(line.specStatus, "READY");
        view.quantity = nonNull(line.quantity);
        view.packedQuantity = nonNull(line.packedQuantity);
        return view;
    }

    private OutboundOrderLineSourceView toOutboundOrderLineSourceView(OutboundOrderLineSourceRecord source) {
        OutboundOrderLineSourceView view = new OutboundOrderLineSourceView();
        view.id = String.valueOf(source.id);
        view.outboundOrderId = source.outboundOrderId;
        view.outboundOrderLineId = source.outboundOrderLineId;
        view.batchSourceId = source.batchSourceId;
        view.fulfillmentBalanceId = source.fulfillmentBalanceId;
        view.purchaseOrderId = source.purchaseOrderId;
        view.purchaseOrderNo = source.purchaseOrderNo;
        view.purchaseOrderTitle = source.purchaseOrderTitle;
        view.purchaseOrderItemId = source.purchaseOrderItemId;
        view.purchaseOrderItemSiteId = source.purchaseOrderItemSiteId;
        view.plannedTransportMode = normalizeTransportMode(source.plannedTransportMode);
        view.quantity = nonNull(source.quantity);
        return view;
    }

    private PendingPackingBox toPendingPackingBox(PackingBoxCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("装箱参数不能为空。");
        }
        PendingPackingBox box = new PendingPackingBox(
                requiredText(command.boxNo, "箱号不能为空。"),
                positiveDecimal(command.lengthCm, "箱长必须大于 0。"),
                positiveDecimal(command.widthCm, "箱宽必须大于 0。"),
                positiveDecimal(command.heightCm, "箱高必须大于 0。"),
                positiveDecimal(command.grossWeightKg, "箱毛重必须大于 0。")
        );
        List<PackingBoxItemCommand> items = command.items == null ? List.of() : command.items;
        for (PackingBoxItemCommand item : items) {
            if (item == null || item.outboundOrderLineId == null || nonNull(item.quantity) <= 0) {
                continue;
            }
            box.items.add(new PendingPackingItem(item.outboundOrderLineId, nonNull(item.quantity)));
        }
        return box;
    }

    private void validatePackingConfirmation(
            List<OutboundOrderLineRecord> outboundLines,
            List<PackingBoxRecord> boxes,
            List<PackingBoxItemRecord> items
    ) {
        if (boxes == null || boxes.isEmpty()) {
            throw new IllegalArgumentException("请先填写装箱箱明细。");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("请先填写装箱商品明细。");
        }
        for (PackingBoxRecord box : boxes) {
            if (box.lengthCm == null || box.lengthCm.compareTo(BigDecimal.ZERO) <= 0
                    || box.widthCm == null || box.widthCm.compareTo(BigDecimal.ZERO) <= 0
                    || box.heightCm == null || box.heightCm.compareTo(BigDecimal.ZERO) <= 0
                    || box.grossWeightKg == null || box.grossWeightKg.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("确认装箱前必须填写箱规和毛重。");
            }
        }

        Map<Long, Integer> packedByLine = new LinkedHashMap<>();
        for (PackingBoxItemRecord item : items) {
            int quantity = nonNull(item.quantity);
            if (quantity <= 0) {
                throw new IllegalArgumentException("装箱商品数量必须大于 0。");
            }
            packedByLine.merge(item.outboundOrderLineId, quantity, Integer::sum);
        }
        for (OutboundOrderLineRecord line : outboundLines) {
            int expected = nonNull(line.quantity);
            int packed = packedByLine.getOrDefault(line.id, 0);
            if (expected != packed) {
                throw new IllegalArgumentException(line.partnerSku + " 装箱数量必须等于出库数量。");
            }
        }
    }

    private PackingListView toPackingListView(PackingListRecord packingList) {
        PackingListView view = new PackingListView();
        view.id = String.valueOf(packingList.id);
        view.outboundOrderId = packingList.outboundOrderId;
        view.ownerUserId = packingList.ownerUserId;
        view.packingNo = packingList.packingNo;
        view.status = packingList.status;
        view.boxCount = nonNull(packingList.boxCount);
        view.packedQuantity = nonNull(packingList.packedQuantity);
        view.grossWeightKg = packingList.grossWeightKg == null ? null : packingList.grossWeightKg.toPlainString();
        view.volumeCbm = packingList.volumeCbm == null ? null : packingList.volumeCbm.toPlainString();
        view.remark = packingList.remark;
        view.createdAt = packingList.createdAt;
        view.updatedAt = packingList.updatedAt;
        return view;
    }

    private PackingListView toPackingListDetail(PackingListRecord packingList) {
        PackingListView view = toPackingListView(packingList);
        Map<Long, List<PackingBoxItemRecord>> itemsByBox = emptyIfNull(mapper.listPackingBoxItems(packingList.id)).stream()
                .collect(Collectors.groupingBy(item -> item.packingBoxId, LinkedHashMap::new, Collectors.toList()));
        for (PackingBoxRecord box : emptyIfNull(mapper.listPackingBoxes(packingList.id))) {
            PackingBoxView boxView = toPackingBoxView(box);
            for (PackingBoxItemRecord item : itemsByBox.getOrDefault(box.id, List.of())) {
                boxView.items.add(toPackingBoxItemView(item));
            }
            view.boxes.add(boxView);
        }
        return view;
    }

    private PackingBoxView toPackingBoxView(PackingBoxRecord box) {
        PackingBoxView view = new PackingBoxView();
        view.id = String.valueOf(box.id);
        view.packingListId = box.packingListId;
        view.outboundOrderId = box.outboundOrderId;
        view.boxNo = box.boxNo;
        view.lengthCm = box.lengthCm.toPlainString();
        view.widthCm = box.widthCm.toPlainString();
        view.heightCm = box.heightCm.toPlainString();
        view.grossWeightKg = box.grossWeightKg.toPlainString();
        view.quantity = nonNull(box.quantity);
        return view;
    }

    private PackingBoxItemView toPackingBoxItemView(PackingBoxItemRecord item) {
        PackingBoxItemView view = new PackingBoxItemView();
        view.id = String.valueOf(item.id);
        view.packingListId = item.packingListId;
        view.packingBoxId = item.packingBoxId;
        view.outboundOrderId = item.outboundOrderId;
        view.outboundOrderLineId = item.outboundOrderLineId;
        view.productVariantId = item.productVariantId;
        view.partnerSku = item.partnerSku;
        view.siteCode = item.siteCode;
        view.actualTransportMode = normalizeTransportMode(item.actualTransportMode);
        view.quantity = nonNull(item.quantity);
        return view;
    }

    private PurchaseOrderAccessRecord requireOrderAccess(BusinessAccessContext access, Long orderId) {
        PurchaseOrderAccessRecord order = mapper.selectOrderAccess(orderId);
        if (order == null) {
            throw new IllegalArgumentException("采购单不存在或已删除。");
        }
        if (access == null || !canAccessSourceStore(access, order.anchorStoreCodeCache)) {
            throw new IllegalArgumentException("当前账号不能操作该采购单。");
        }
        return order;
    }

    private PurchaseOrderItemRecord requireItem(PurchaseOrderAccessRecord order, Long itemId) {
        PurchaseOrderItemRecord item = mapper.selectPurchaseOrderItem(itemId);
        if (item == null || !order.id.equals(item.purchaseOrderId)) {
            throw new IllegalArgumentException("采购单商品不存在或已删除。");
        }
        return item;
    }

    private DispatchPlanRecord requireDispatchPlanAccess(BusinessAccessContext access, Long dispatchPlanId) {
        DispatchPlanRecord plan = mapper.selectDispatchPlanById(dispatchPlanId);
        if (plan == null) {
            throw new IllegalArgumentException("发运计划不存在或已删除。");
        }
        requireOwnerAccess(access, plan.ownerUserId);
        return plan;
    }

    private ShippingBatchRecord requireShippingBatchAccess(BusinessAccessContext access, Long shippingBatchId) {
        ShippingBatchRecord batch = mapper.selectShippingBatchById(shippingBatchId);
        if (batch == null) {
            throw new IllegalArgumentException("发货批次不存在或已删除。");
        }
        requireOwnerAccess(access, batch.ownerUserId);
        return batch;
    }

    private OutboundOrderRecord requireOutboundOrderAccess(BusinessAccessContext access, Long outboundOrderId) {
        OutboundOrderRecord outboundOrder = mapper.selectOutboundOrderById(outboundOrderId);
        if (outboundOrder == null) {
            throw new IllegalArgumentException("出库单不存在或已删除。");
        }
        requireOwnerAccess(access, outboundOrder.ownerUserId);
        return outboundOrder;
    }

    private PackingListRecord requirePackingListAccess(BusinessAccessContext access, Long packingListId) {
        PackingListRecord packingList = mapper.selectPackingListById(packingListId);
        if (packingList == null) {
            throw new IllegalArgumentException("装箱单不存在或已删除。");
        }
        requireOwnerAccess(access, packingList.ownerUserId);
        return packingList;
    }

    private DispatchPlanRecord requireHandoffAccess(BusinessAccessContext access, String handoffRequestNo) {
        DispatchPlanRecord plan = mapper.selectDispatchPlanByHandoffRequest(handoffRequestNo);
        if (plan == null) {
            throw new IllegalArgumentException("物流交接不存在或已失效。");
        }
        requireOwnerAccess(access, plan.ownerUserId);
        return plan;
    }

    private void requireOwnerAccess(BusinessAccessContext access, Long ownerUserId) {
        if (access == null || ownerUserId == null || !ownerUserId.equals(ownerUserId(access))) {
            throw new IllegalArgumentException("当前账号不能操作该发运计划。");
        }
    }

    private boolean canUseBalance(BusinessAccessContext access, FulfillmentBalanceRecord balance) {
        return balance != null
                && ownerUserId(access).equals(balance.ownerUserId)
                && canAccessSourceStore(access, balance.sourceStoreCode);
    }

    private boolean canAccessSourceStore(BusinessAccessContext access, String storeCode) {
        return access != null && (access.getStoreCodes().isEmpty() || access.canAccessStore(storeCode));
    }

    private Long ownerUserId(BusinessAccessContext access) {
        if (access == null) {
            throw new IllegalArgumentException("缺少业务访问上下文。");
        }
        if (access.getBusinessOwnerUserId() != null) {
            return access.getBusinessOwnerUserId();
        }
        return access.getSessionUserId();
    }

    private boolean matchesKeyword(FulfillmentBalanceRecord balance, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String normalized = keyword.toLowerCase(Locale.ROOT);
        return contains(balance.partnerSku, normalized)
                || contains(balance.skuParent, normalized)
                || contains(balance.titleCache, normalized)
                || contains(balance.purchaseOrderNo, normalized)
                || contains(balance.purchaseOrderTitle, normalized);
    }

    private boolean contains(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    private Map<String, Integer> siteSummary(List<DispatchPlanLineView> lines) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (DispatchPlanLineView line : lines) {
            result.merge(defaultText(line.siteCode, "UNKNOWN"), nonNull(line.quantity), Integer::sum);
        }
        return result;
    }

    private Map<String, Integer> transportSummary(List<DispatchPlanLineView> lines) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (DispatchPlanLineView line : lines) {
            result.merge(normalizeTransportMode(line.actualTransportMode), nonNull(line.quantity), Integer::sum);
        }
        return result;
    }

    private int shippingSkuCount(List<ShippingBatchSourceRecord> sources) {
        return (int) sources.stream()
                .map(source -> String.valueOf(source.productVariantId))
                .distinct()
                .count();
    }

    private Map<String, Integer> shippingStoreSummary(List<ShippingBatchSourceRecord> sources) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (ShippingBatchSourceRecord source : sources) {
            result.merge(defaultText(source.sourceStoreCode, "UNKNOWN"), nonNull(source.reservedQuantity), Integer::sum);
        }
        return result;
    }

    private Map<String, Integer> shippingSiteSummary(List<ShippingBatchSourceRecord> sources) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (ShippingBatchSourceRecord source : sources) {
            result.merge(defaultText(source.siteCode, "UNKNOWN"), nonNull(source.reservedQuantity), Integer::sum);
        }
        return result;
    }

    private Map<String, Integer> shippingPlannedTransportSummary(List<ShippingBatchSourceRecord> sources) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (ShippingBatchSourceRecord source : sources) {
            result.merge(normalizeTransportMode(source.plannedTransportMode), nonNull(source.reservedQuantity), Integer::sum);
        }
        return result;
    }

    private String shippingBatchNo(Long batchId, List<ShippingBatchSourceRecord> sources) {
        String date = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))
                .format(java.time.format.DateTimeFormatter.ofPattern("MMdd"));
        String transportMode = shippingBatchTransportModeLabel(shippingPlannedTransportSummary(sources).keySet());
        int quantity = sources.stream().mapToInt(source -> nonNull(source.reservedQuantity)).sum();
        long codeNumber = batchId == null ? 0L : Math.floorMod(batchId, 100L);
        String code = String.format(Locale.ROOT, "%02d", codeNumber);
        return date + "-" + transportMode + "-" + quantity + "件-" + code;
    }

    private String shippingBatchTransportModeLabel(Collection<String> transportModes) {
        Set<String> normalized = transportModes == null ? Set.of() : transportModes.stream()
                .map(this::normalizeTransportMode)
                .filter(mode -> !TRANSPORT_UNSPECIFIED.equals(mode))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalized.contains(TRANSPORT_AIR) && normalized.contains(TRANSPORT_SEA)) {
            return "空海运";
        }
        if (normalized.contains(TRANSPORT_AIR)) {
            return "空运";
        }
        if (normalized.contains(TRANSPORT_SEA)) {
            return "海运";
        }
        return "货运";
    }

    private Map<String, Integer> shippingOriginSummary(List<ShippingBatchSourceRecord> sources) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (ShippingBatchSourceRecord source : sources) {
            String key = normalizeFulfillmentType(source.fulfillmentType) + "|"
                    + defaultText(source.sourcePartyName, defaultText(source.sourceStoreName, "UNKNOWN"));
            result.merge(key, nonNull(source.reservedQuantity), Integer::sum);
        }
        return result;
    }

    private Map<String, Object> shippingOptionSummary(
            ShippingOptionDefinition definition,
            List<ShippingBatchSourceRecord> sources,
            Collection<PendingShippingLine> lines
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceCount", sources.size());
        result.put("lineCount", lines.size());
        result.put("forwarderPlanType", definition.forwarderPlanType);
        result.put("targetForwarderCodes", definition.targetForwarderCodes);
        result.put("targetForwarderNames", definition.targetForwarderNames);
        result.put("autoRecommended", definition.autoRecommended);
        result.put("routeCodes", routeCodes(lines));
        return result;
    }

    private Map<String, List<ForwarderRouteQuoteRecord>> forwarderRouteQuotes(Collection<PendingShippingLine> lines) {
        List<String> routeCodes = routeCodes(lines);
        if (routeCodes.isEmpty()) {
            return Map.of();
        }
        return emptyIfNull(mapper.listForwarderRouteQuotes(routeCodes)).stream()
                .collect(Collectors.groupingBy(
                        quote -> quote.routeCode,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private List<ForwarderRouteQuoteRecord> quotesForRoute(
            Map<String, List<ForwarderRouteQuoteRecord>> routeQuotes,
            String routeCode
    ) {
        if (!StringUtils.hasText(routeCode) || routeQuotes == null || routeQuotes.isEmpty()) {
            return List.of();
        }
        return routeQuotes.getOrDefault(routeCode, List.of());
    }

    private CargoCategoryEstimate inferCargoCategory(List<String> sensitiveReasons) {
        if (sensitiveReasons == null || sensitiveReasons.isEmpty()) {
            return new CargoCategoryEstimate("A", "普货", false);
        }
        String joined = sensitiveReasons.stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" "))
                .toLowerCase(Locale.ROOT);
        boolean reviewRequired = containsAny(joined, "人工确认", "manual", "敏货");
        if (containsAny(joined, "食品", "food", "化妆", "美妆", "cosmetic", "液体", "粉末", "膏体", "酒精", "医疗")) {
            return new CargoCategoryEstimate("D", "特殊敏货", reviewRequired);
        }
        if (containsAny(joined, "刀具", "blade", "品牌", "侵权", "按摩", "摄像", "太阳能", "灯具", "卫浴", "眼镜")) {
            return new CargoCategoryEstimate("C", "需确认敏感货", reviewRequired);
        }
        if (containsAny(joined, "带电", "电器", "电池", "插电", "磁", "battery", "electric", "magnetic")) {
            return new CargoCategoryEstimate("B", "带电带磁类", reviewRequired);
        }
        return new CargoCategoryEstimate("B", "一般敏感货", true);
    }

    private ForwarderRouteQuoteRecord selectRouteQuote(
            List<ForwarderRouteQuoteRecord> quotes,
            CargoCategoryEstimate cargoCategory,
            PendingShippingLine line
    ) {
        if (quotes == null || quotes.isEmpty()) {
            return null;
        }
        List<String> tokens = preferredQuoteTokens(cargoCategory, line);
        return quotes.stream()
                .filter(quote -> quote.minUnitPrice != null && StringUtils.hasText(quote.billingUnit))
                .map(quote -> Map.entry(quote, quoteTokenIndex(quote, tokens)))
                .filter(entry -> entry.getValue() >= 0)
                .min(Comparator
                        .comparingInt((Map.Entry<ForwarderRouteQuoteRecord, Integer> entry) -> entry.getValue())
                        .thenComparing(entry -> entry.getKey().minUnitPrice))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private List<String> preferredQuoteTokens(CargoCategoryEstimate cargoCategory, PendingShippingLine line) {
        String cargoCode = cargoCategory == null ? "A" : cargoCategory.code;
        String reasonText = normalizeQuoteText(line.sensitiveReasons().stream().collect(Collectors.joining(" ")));
        if ("ET-SAU-AIR-FBN-RUH-20260604".equals(line.routeCode)) {
            if (containsAny(reasonText, "食品", "food", "大牌", "奢侈", "品牌侵权", "侵权")) {
                return List.of("CAT-GF", "GF类", "CAT-D", "D类", "CAT-STD", "一档");
            }
            if (containsAny(reasonText, "医疗", "眼镜")) {
                return List.of("CAT-E", "E类", "CAT-BC", "BC类", "CAT-STD", "一档");
            }
            if (containsAny(reasonText, "液体", "粉末", "膏体", "凝胶", "化妆", "美妆", "cosmetic")) {
                return List.of("CAT-D", "D类", "CAT-GF", "GF类", "CAT-STD", "一档");
            }
            if (containsAny(reasonText, "电池", "battery", "扫码")) {
                return List.of("CAT-H", "H类", "CAT-BC", "BC类", "CAT-STD", "一档");
            }
            if ("C".equals(cargoCode) || "B".equals(cargoCode)) {
                return List.of("CAT-BC", "BC类", "CAT-B", "B类", "CAT-STD", "一档");
            }
            if ("D".equals(cargoCode)) {
                return List.of("CAT-D", "D类", "CAT-GF", "GF类", "CAT-STD", "一档");
            }
            return List.of("CAT-A", "A类", "CAT-STD", "一档");
        }
        if ("ET-AE-SEA-WH-20260604".equals(line.routeCode)) {
            if (containsAny(reasonText, "特殊敏货", "粉末", "食品", "food", "大牌", "香水", "充电宝", "纯电池", "液体", "膏体", "化妆", "美妆", "cosmetic")
                    || "D".equals(cargoCode)) {
                return List.of("CAT-C", "C类", "1880起");
            }
            if ("B".equals(cargoCode) || "C".equals(cargoCode)) {
                return List.of("CAT-B", "B类", "CAT-C", "C类");
            }
            return List.of("CAT-A", "A类", "普货");
        }
        if ("ZD".equals(line.targetForwarderCode) && TRANSPORT_AIR.equals(line.actualTransportMode)) {
            return "A".equals(cargoCode) ? List.of("CAT-001", "普货") : List.of("CAT-002", "敏货");
        }
        if ("YT-SAU-SEA-FBN-RUH".equals(line.routeCode)) {
            if ("A".equals(cargoCode)) {
                return List.of("CAT-020", "普货");
            }
            if (containsAny(reasonText, "灯", "卫浴", "太阳能")) {
                return List.of("CAT-022", "灯具", "卫浴");
            }
            if (containsAny(reasonText, "带电", "插电", "电器", "电池", "磁")) {
                return List.of("CAT-021", "带电带插带磁");
            }
            if ("D".equals(cargoCode)) {
                return List.of("CAT-028", "敏感货", "CAT-025", "特殊类");
            }
            return List.of("CAT-023", "一般敏感货", "CAT-021");
        }
        if ("ZD-SAU-SEA-FBN-RUH".equals(line.routeCode)) {
            if ("A".equals(cargoCode)) {
                return List.of("CAT-003", "A类");
            }
            if (containsAny(reasonText, "食品", "化妆", "美妆", "液体", "粉末", "膏体")) {
                return List.of("CAT-014", "G类", "CAT-011", "D类");
            }
            if (containsAny(reasonText, "按摩", "医疗")) {
                return List.of("CAT-017", "J类");
            }
            if (containsAny(reasonText, "电器", "插电")) {
                return List.of("CAT-012", "E类");
            }
            if (containsAny(reasonText, "带电", "磁", "电池")) {
                return List.of("CAT-006", "B2类", "CAT-004", "B类");
            }
            if ("C".equals(cargoCode)) {
                return List.of("CAT-009", "C类");
            }
            if ("D".equals(cargoCode)) {
                return List.of("CAT-014", "G类", "CAT-011", "D类");
            }
            return List.of("CAT-004", "B类");
        }
        if ("D".equals(cargoCode) && containsAny(reasonText, "食品", "food", "化妆", "美妆", "cosmetic")) {
            return List.of("CAT-G", "G类", "CAT-D", "D类");
        }
        if ("D".equals(cargoCode)) {
            return List.of("CAT-D", "D类", "CAT-G", "G类");
        }
        if ("C".equals(cargoCode)) {
            return List.of("CAT-C", "C类", "CAT-BC", "BC类");
        }
        if ("B".equals(cargoCode)) {
            return List.of("CAT-B", "B类", "CAT-BC", "BC类");
        }
        return List.of("CAT-A", "A类", "普货", "CAT-STD");
    }

    private int quoteTokenIndex(ForwarderRouteQuoteRecord quote, List<String> tokens) {
        for (int index = 0; index < tokens.size(); index += 1) {
            if (quoteMatchesToken(quote, tokens.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private boolean quoteMatchesToken(ForwarderRouteQuoteRecord quote, String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        String normalizedToken = normalizeQuoteText(token);
        String normalizedCode = normalizeQuoteText(quote == null ? null : quote.cargoCategoryCode);
        if (normalizedToken.startsWith("CAT-")) {
            return normalizedCode.endsWith("-" + normalizedToken);
        }
        String normalizedName = normalizeQuoteText(quote == null ? null : quote.cargoCategoryName);
        return normalizedName.contains(normalizedToken) || normalizedCode.contains(normalizedToken);
    }

    private String normalizeQuoteText(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean containsAny(String value, String... tokens) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalizedValue = value.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (StringUtils.hasText(token) && normalizedValue.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private List<String> routeCodes(Collection<PendingShippingLine> lines) {
        return lines.stream()
                .map(line -> line.routeCode)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    private void addUnique(List<String> values, String value) {
        if (StringUtils.hasText(value) && !values.contains(value)) {
            values.add(value);
        }
    }

    private BigDecimal zeroToNull(BigDecimal value, int scale) {
        if (value == null || value.signum() == 0) {
            return null;
        }
        return value.setScale(scale, RoundingMode.HALF_UP);
    }

    private Map<String, Object> shippingLineAssignmentSnapshot(ShippingForwarderAssignment assignment) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetForwarderCode", assignment.targetForwarderCode);
        result.put("targetForwarderName", assignment.targetForwarderName);
        if (StringUtils.hasText(assignment.routeCode)) {
            result.put("routeCode", assignment.routeCode);
        }
        if (StringUtils.hasText(assignment.routeName)) {
            result.put("routeName", assignment.routeName);
        }
        if (!StringUtils.hasText(assignment.routeCode)) {
            result.put("warning", "no_route_template");
        }
        return result;
    }

    private String shippingActualTransportMode(String optionType, ShippingBatchSourceRecord source) {
        return normalizeTransportMode(source.plannedTransportMode);
    }

    private ShippingForwarderAssignment shippingForwarderAssignment(
            ShippingOptionDefinition definition,
            String actualTransportMode,
            String siteCode
    ) {
        String forwarderCode = TRANSPORT_AIR.equals(normalizeTransportMode(actualTransportMode))
                ? definition.airForwarderCode
                : definition.seaForwarderCode;
        if ("AUTO_RECOMMEND".equals(definition.optionType)
                && "AE".equalsIgnoreCase(defaultText(siteCode, ""))) {
            forwarderCode = "ET";
        }
        forwarderCode = normalizeForwarderCode(forwarderCode);
        String forwarderName = forwarderName(forwarderCode);
        ForwarderRouteSnapshot route = forwarderRouteSnapshot(forwarderCode, actualTransportMode, siteCode);
        return new ShippingForwarderAssignment(forwarderCode, forwarderName, route.routeCode, route.routeName);
    }

    private ShippingOptionDefinition customShippingOptionDefinition(CreateShippingTargetOptionCommand command) {
        String airForwarderCode = normalizeForwarderCode(command == null ? null : command.airForwarderCode);
        String seaForwarderCode = normalizeForwarderCode(command == null ? null : command.seaForwarderCode);
        if (!StringUtils.hasText(airForwarderCode) || !StringUtils.hasText(seaForwarderCode)) {
            throw new IllegalArgumentException("请选择空运和海运目标货代。");
        }
        if (!isSupportedAirForwarderCode(airForwarderCode)) {
            throw new IllegalArgumentException("空运目标货代暂只支持易通和众鸫。");
        }
        if (!isSupportedSeaForwarderCode(seaForwarderCode)) {
            throw new IllegalArgumentException("海运目标货代暂只支持易通、众鸫和义特。");
        }

        List<String> targetForwarderCodes = new ArrayList<>();
        addUnique(targetForwarderCodes, airForwarderCode);
        addUnique(targetForwarderCodes, seaForwarderCode);
        List<String> targetForwarderNames = targetForwarderCodes.stream()
                .map(this::forwarderName)
                .collect(Collectors.toList());
        String forwarderPlanType = airForwarderCode.equals(seaForwarderCode) ? "SINGLE" : "COMBINATION";
        String optionName = trimToNull(command == null ? null : command.optionName);
        if (!StringUtils.hasText(optionName)) {
            if ("SINGLE".equals(forwarderPlanType)) {
                optionName = "自定义：" + forwarderName(airForwarderCode) + "单货代";
            } else {
                optionName = "自定义：" + forwarderName(airForwarderCode) + "空运 + "
                        + forwarderName(seaForwarderCode) + "海运";
            }
        }
        return new ShippingOptionDefinition(
                "CUSTOM",
                optionName,
                80,
                forwarderPlanType,
                targetForwarderCodes,
                targetForwarderNames,
                false,
                airForwarderCode,
                seaForwarderCode
        );
    }

    private String normalizeForwarderCode(String forwarderCode) {
        String normalized = trimToNull(forwarderCode);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private boolean isSupportedAirForwarderCode(String forwarderCode) {
        return "ET".equals(forwarderCode) || "ZD".equals(forwarderCode);
    }

    private boolean isSupportedSeaForwarderCode(String forwarderCode) {
        return "ET".equals(forwarderCode) || "ZD".equals(forwarderCode) || "YT".equals(forwarderCode);
    }

    private String forwarderName(String forwarderCode) {
        if ("ZD".equals(forwarderCode)) {
            return "众鸫";
        }
        if ("ET".equals(forwarderCode)) {
            return "易通";
        }
        if ("YT".equals(forwarderCode)) {
            return "义特";
        }
        return forwarderCode;
    }

    private ForwarderRouteSnapshot forwarderRouteSnapshot(String forwarderCode, String transportMode, String siteCode) {
        String normalizedSiteCode = defaultText(siteCode, "").toUpperCase(Locale.ROOT);
        String normalizedTransportMode = normalizeTransportMode(transportMode);
        if ("ET".equals(forwarderCode)) {
            if ("AE".equals(normalizedSiteCode)) {
                if (TRANSPORT_AIR.equals(normalizedTransportMode)) {
                    return new ForwarderRouteSnapshot(
                            "ET-AE-AIR-WH-20260604",
                            "易通阿联酋空运仓到仓 20260604"
                    );
                }
                if (TRANSPORT_SEA.equals(normalizedTransportMode)) {
                    return new ForwarderRouteSnapshot(
                            "ET-AE-SEA-WH-20260604",
                            "易通阿联酋海运仓到仓 20260604"
                    );
                }
                return new ForwarderRouteSnapshot(null, null);
            }
            if (!"SA".equals(normalizedSiteCode)) {
                return new ForwarderRouteSnapshot(null, null);
            }
            if (TRANSPORT_AIR.equals(normalizedTransportMode)) {
                return new ForwarderRouteSnapshot(
                        "ET-SAU-AIR-FBN-RUH-20260604",
                        "易通沙特空运一档 + 海外仓 + FBN利雅得送仓 20260604"
                );
            }
            return new ForwarderRouteSnapshot(
                    "ET-SAU-SEA-FBN-RUH-20260604",
                    "易通沙特海运 + 海外仓 + FBN利雅得送仓 20260604"
            );
        }
        if (!"SA".equals(normalizedSiteCode)) {
            return new ForwarderRouteSnapshot(null, null);
        }
        if ("ZD".equals(forwarderCode)) {
            if (TRANSPORT_AIR.equals(normalizedTransportMode)) {
                return new ForwarderRouteSnapshot(
                        "ZD-SAU-AIR-FBN-RUH",
                        "众鸫沙特空运专线 FBN利雅得（含送仓报价）"
                );
            }
            return new ForwarderRouteSnapshot(
                    "ZD-SAU-SEA-FBN-RUH",
                    "众鸫沙特海运专线到海外仓 + FBN利雅得送仓"
            );
        }
        if ("YT".equals(forwarderCode) && TRANSPORT_SEA.equals(normalizedTransportMode)) {
            return new ForwarderRouteSnapshot(
                    "YT-SAU-SEA-FBN-RUH",
                    "义特沙特海运双清包税 + FBN利雅得送仓"
            );
        }
        return new ForwarderRouteSnapshot(null, null);
    }

    private String shippingLineKey(
            ShippingBatchSourceRecord source,
            String actualTransportMode,
            ShippingForwarderAssignment assignment
    ) {
        return source.productVariantId + "|" + source.siteCode + "|" + normalizeTransportMode(actualTransportMode) + "|"
                + normalizeFulfillmentType(source.fulfillmentType) + "|"
                + defaultText(source.sourcePartyName, "") + "|"
                + defaultText(source.specStatus, "READY") + "|"
                + defaultText(assignment.targetForwarderCode, "") + "|"
                + defaultText(assignment.routeCode, "");
    }

    private String shippingOriginKey(ShippingSuggestionLineRecord line) {
        return normalizeFulfillmentType(line.fulfillmentType) + "|"
                + defaultText(line.sourcePartyName, "");
    }

    private int outboundSkuCount(List<ShippingSuggestionLineRecord> lines) {
        return (int) lines.stream()
                .map(line -> String.valueOf(line.productVariantId))
                .distinct()
                .count();
    }

    private Map<String, Integer> outboundSiteSummary(List<ShippingSuggestionLineRecord> lines) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (ShippingSuggestionLineRecord line : lines) {
            result.merge(defaultText(line.siteCode, "UNKNOWN"), nonNull(line.quantity), Integer::sum);
        }
        return result;
    }

    private Map<String, Integer> outboundTransportSummary(List<ShippingSuggestionLineRecord> lines) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (ShippingSuggestionLineRecord line : lines) {
            result.merge(normalizeTransportMode(line.actualTransportMode), nonNull(line.quantity), Integer::sum);
        }
        return result;
    }

    private String fulfillmentSourcePartyName(String fulfillmentType, FulfillmentBalanceRecord balance) {
        String sourceName = defaultText(balance.sourceStoreName, balance.sourceStoreCode);
        if (FULFILLMENT_FACTORY.equals(fulfillmentType)) {
            return sourceName;
        }
        return defaultText(sourceName, "WAREHOUSE");
    }

    private boolean isSpecMissing(String specStatus) {
        return "SPEC_MISSING".equalsIgnoreCase(defaultText(specStatus, "READY"));
    }

    private List<String> sensitiveReasons(FulfillmentBalanceRecord balance) {
        List<String> reasons = new ArrayList<>();
        addSensitiveReason(reasons, balance.batteryType, "带电");
        addSensitiveReason(reasons, balance.magneticType, "带磁");
        addSensitiveReason(reasons, balance.liquidPowderType, "液体/粉末");
        addSensitiveReason(reasons, balance.electricType, "电器");
        addSensitiveReason(reasons, balance.bladeWeaponType, "刀具");
        if (requiresManualConfirm(balance)) {
            addUnique(reasons, "新品物流属性需人工确认");
        }
        for (String tag : readJsonStringList(balance.sensitiveTagsJson)) {
            addUnique(reasons, tag);
        }
        return reasons;
    }

    private void addSensitiveReason(List<String> reasons, String value, String label) {
        String normalized = defaultText(value, "unknown").toLowerCase(Locale.ROOT);
        if (!"unknown".equals(normalized) && !"none".equals(normalized) && !"no".equals(normalized)
                && !"not_applicable".equals(normalized) && !"normal".equals(normalized)) {
            addUnique(reasons, label + ":" + value);
        }
    }

    private boolean requiresManualConfirm(FulfillmentBalanceRecord balance) {
        return balance != null
                && Boolean.TRUE.equals(balance.isNewProduct)
                && Boolean.TRUE.equals(balance.manualConfirmRequired);
    }

    private String dispatchLineKey(
            FulfillmentBalanceRecord balance,
            String actualTransportMode,
            String fulfillmentType,
            String specStatus
    ) {
        return balance.productVariantId + "|" + balance.siteCode + "|" + actualTransportMode + "|"
                + fulfillmentType + "|" + specStatus;
    }

    private <T> List<T> emptyIfNull(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String normalizeFulfillmentType(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
        switch (normalized) {
            case "FACTORY":
            case "FACTORY_DIRECT":
            case "厂家":
            case "厂家直发":
                return FULFILLMENT_FACTORY;
            case "WAREHOUSE":
            case "WAREHOUSE_RECEIPT":
            case "到仓":
            case "仓库":
            case "":
                return FULFILLMENT_WAREHOUSE;
            default:
                throw new IllegalArgumentException("不支持的履约方式：" + value);
        }
    }

    private String normalizeConfirmationType(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
        switch (normalized) {
            case "FACTORY_DIRECT":
            case "WAREHOUSE_RECEIPT":
            case "ADJUSTMENT":
            case "CANCELLATION":
                return normalized;
            case "":
                return FULFILLMENT_WAREHOUSE;
            default:
                throw new IllegalArgumentException("不支持的履约确认类型：" + value);
        }
    }

    private String normalizeTransportMode(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
        switch (normalized) {
            case "AIR":
            case "空":
            case "空运":
                return TRANSPORT_AIR;
            case "SEA":
            case "海":
            case "海运":
                return TRANSPORT_SEA;
            default:
                return TRANSPORT_UNSPECIFIED;
        }
    }

    private String normalizeSiteCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String requiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String trimToNull(String value) {
        return trim(value);
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private int nonNull(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal positiveDecimal(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        try {
            BigDecimal decimal = new BigDecimal(value.trim());
            if (decimal.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(message);
            }
            return decimal;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(message);
        }
    }

    private Long parseLongId(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(message);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("仓库发运 JSON 序列化失败。", error);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonObject(String value) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        try {
            Object parsed = objectMapper.readValue(value, Map.class);
            return parsed instanceof Map ? (Map<String, Object>) parsed : Map.of();
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String textFromObject(Object value, String fallback) {
        return value == null ? fallback : defaultText(String.valueOf(value), fallback);
    }

    private boolean booleanFromObject(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value != null && "true".equalsIgnoreCase(String.valueOf(value));
    }

    private List<String> stringListFromObject(Object value) {
        if (!(value instanceof Collection<?>)) {
            return List.of();
        }
        return ((Collection<?>) value).stream()
                .map(item -> item == null ? null : String.valueOf(item))
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    private List<String> readJsonStringList(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            Object parsed = objectMapper.readValue(value, List.class);
            return stringListFromObject(parsed);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<String> orElse(List<String> primary, List<String> fallback) {
        return primary == null || primary.isEmpty() ? fallback : primary;
    }

    private void log(
            Long dispatchPlanId,
            String operationType,
            Long operatorUserId,
            String beforeStatus,
            String afterStatus,
            String detail
    ) {
        mapper.insertOperationLog(
                mapper.nextOperationLogId(),
                dispatchPlanId,
                operationType,
                operatorUserId,
                beforeStatus,
                afterStatus,
                detail == null ? null : writeJson(Map.of("detail", detail))
        );
    }

    private static final class AllocationRemainder {
        private final Long balanceId;
        private final BigDecimal remainder;
        private final Integer plannedQuantity;
        private final Integer index;

        private AllocationRemainder(Long balanceId, BigDecimal remainder, Integer plannedQuantity, Integer index) {
            this.balanceId = balanceId;
            this.remainder = remainder;
            this.plannedQuantity = plannedQuantity;
            this.index = index;
        }
    }

    private static final class PendingPackingBox {
        private final String boxNo;
        private final BigDecimal lengthCm;
        private final BigDecimal widthCm;
        private final BigDecimal heightCm;
        private final BigDecimal grossWeightKg;
        private final List<PendingPackingItem> items = new ArrayList<>();

        private PendingPackingBox(
                String boxNo,
                BigDecimal lengthCm,
                BigDecimal widthCm,
                BigDecimal heightCm,
                BigDecimal grossWeightKg
        ) {
            this.boxNo = boxNo;
            this.lengthCm = lengthCm;
            this.widthCm = widthCm;
            this.heightCm = heightCm;
            this.grossWeightKg = grossWeightKg;
        }

        private int quantity() {
            return items.stream().mapToInt(item -> item.quantity).sum();
        }

        private BigDecimal volumeCbm() {
            return lengthCm.multiply(widthCm)
                    .multiply(heightCm)
                    .divide(BigDecimal.valueOf(1_000_000L), 8, RoundingMode.HALF_UP);
        }

        private PackingBoxRecord toRecord(
                Long packingBoxId,
                Long packingListId,
                Long outboundOrderId,
                Long ownerUserId
        ) {
            PackingBoxRecord record = new PackingBoxRecord();
            record.id = packingBoxId;
            record.packingListId = packingListId;
            record.outboundOrderId = outboundOrderId;
            record.ownerUserId = ownerUserId;
            record.boxNo = boxNo;
            record.lengthCm = lengthCm;
            record.widthCm = widthCm;
            record.heightCm = heightCm;
            record.grossWeightKg = grossWeightKg;
            record.quantity = quantity();
            return record;
        }
    }

    private static final class PendingPackingItem {
        private final Long outboundOrderLineId;
        private final Integer quantity;

        private PendingPackingItem(Long outboundOrderLineId, Integer quantity) {
            this.outboundOrderLineId = outboundOrderLineId;
            this.quantity = quantity;
        }

        private PackingBoxItemRecord toRecord(
                Long packingBoxItemId,
                Long packingListId,
                Long packingBoxId,
                Long outboundOrderId,
                Long ownerUserId,
                OutboundOrderLineRecord outboundLine
        ) {
            PackingBoxItemRecord record = new PackingBoxItemRecord();
            record.id = packingBoxItemId;
            record.packingListId = packingListId;
            record.packingBoxId = packingBoxId;
            record.outboundOrderId = outboundOrderId;
            record.outboundOrderLineId = outboundOrderLineId;
            record.ownerUserId = ownerUserId;
            record.productVariantId = outboundLine.productVariantId;
            record.partnerSku = outboundLine.partnerSku;
            record.siteCode = outboundLine.siteCode;
            record.actualTransportMode = outboundLine.actualTransportMode;
            record.quantity = quantity;
            return record;
        }
    }

    private static final class ShippingOptionDefinition {
        private final String optionType;
        private final String optionName;
        private final Integer score;
        private final String forwarderPlanType;
        private final List<String> targetForwarderCodes;
        private final List<String> targetForwarderNames;
        private final boolean autoRecommended;
        private final String airForwarderCode;
        private final String seaForwarderCode;

        private ShippingOptionDefinition(
                String optionType,
                String optionName,
                Integer score,
                String forwarderPlanType,
                List<String> targetForwarderCodes,
                List<String> targetForwarderNames,
                boolean autoRecommended,
                String airForwarderCode,
                String seaForwarderCode
        ) {
            this.optionType = optionType;
            this.optionName = optionName;
            this.score = score;
            this.forwarderPlanType = forwarderPlanType;
            this.targetForwarderCodes = targetForwarderCodes;
            this.targetForwarderNames = targetForwarderNames;
            this.autoRecommended = autoRecommended;
            this.airForwarderCode = airForwarderCode;
            this.seaForwarderCode = seaForwarderCode;
        }
    }

    private static final class ShippingForwarderAssignment {
        private final String targetForwarderCode;
        private final String targetForwarderName;
        private final String routeCode;
        private final String routeName;

        private ShippingForwarderAssignment(
                String targetForwarderCode,
                String targetForwarderName,
                String routeCode,
                String routeName
        ) {
            this.targetForwarderCode = targetForwarderCode;
            this.targetForwarderName = targetForwarderName;
            this.routeCode = routeCode;
            this.routeName = routeName;
        }
    }

    private static final class ForwarderRouteSnapshot {
        private final String routeCode;
        private final String routeName;

        private ForwarderRouteSnapshot(String routeCode, String routeName) {
            this.routeCode = routeCode;
            this.routeName = routeName;
        }
    }

    private final class PendingShippingLine {
        private final ShippingBatchSourceRecord firstSource;
        private final String actualTransportMode;
        private final String targetForwarderCode;
        private final String targetForwarderName;
        private final String routeCode;
        private final String routeName;
        private final String warningJson;
        private final List<PendingShippingSource> sources = new ArrayList<>();
        private BigDecimal actualWeightKg;
        private BigDecimal volumeCbm;
        private BigDecimal chargeableWeightKg;
        private BigDecimal estimatedAmount;
        private String currency;
        private Boolean minimumNotMet = false;
        private String cargoCategoryCode;
        private String cargoCategoryName;
        private String quoteCargoCategoryCode;
        private String quoteCargoCategoryName;
        private Boolean cargoCategoryReviewRequired = false;
        private Boolean quoteMissingForCargoCategory = false;

        private PendingShippingLine(
                ShippingBatchSourceRecord firstSource,
                String actualTransportMode,
                ShippingForwarderAssignment assignment,
                String warningJson
        ) {
            this.firstSource = firstSource;
            this.actualTransportMode = actualTransportMode;
            this.targetForwarderCode = assignment.targetForwarderCode;
            this.targetForwarderName = assignment.targetForwarderName;
            this.routeCode = assignment.routeCode;
            this.routeName = assignment.routeName;
            this.warningJson = warningJson;
        }

        private int quantity() {
            return sources.stream().mapToInt(source -> source.quantity).sum();
        }

        private void evaluate(List<ForwarderRouteQuoteRecord> quotes) {
            actualWeightKg = totalActualWeightKg();
            volumeCbm = totalVolumeCbm();
            CargoCategoryEstimate cargoCategory = inferCargoCategory(sensitiveReasons());
            cargoCategoryCode = cargoCategory.code;
            cargoCategoryName = cargoCategory.name;
            cargoCategoryReviewRequired = cargoCategory.reviewRequired;
            ForwarderRouteQuoteRecord quote = selectRouteQuote(quotes, cargoCategory, this);
            if (quote == null) {
                quoteMissingForCargoCategory = true;
            } else {
                quoteCargoCategoryCode = quote.cargoCategoryCode;
                quoteCargoCategoryName = quote.cargoCategoryName;
            }
            if (TRANSPORT_AIR.equals(actualTransportMode) && actualWeightKg != null && volumeCbm != null) {
                BigDecimal divisor = quote == null || quote.volumeDivisor == null || quote.volumeDivisor.signum() <= 0
                        ? DEFAULT_AIR_VOLUME_DIVISOR
                        : quote.volumeDivisor;
                BigDecimal volumeWeightKg = volumeCbm.multiply(CUBIC_CM_PER_CBM).divide(divisor, 6, RoundingMode.HALF_UP);
                chargeableWeightKg = actualWeightKg.max(volumeWeightKg).setScale(3, RoundingMode.HALF_UP);
            } else if (TRANSPORT_SEA.equals(actualTransportMode) && actualWeightKg != null) {
                chargeableWeightKg = actualWeightKg.setScale(3, RoundingMode.HALF_UP);
            }
            if (quote == null || quote.minUnitPrice == null || !StringUtils.hasText(quote.billingUnit)) {
                return;
            }
            currency = quote.currency;
            BigDecimal billableQuantity = billableQuantity(quote);
            if (billableQuantity == null) {
                return;
            }
            BigDecimal minBillableUnit = effectiveMinBillableUnit(quote);
            if (minBillableUnit != null && minBillableUnit.signum() > 0
                    && billableQuantity.compareTo(minBillableUnit) < 0) {
                minimumNotMet = true;
                billableQuantity = minBillableUnit;
            }
            BigDecimal amount = billableQuantity.multiply(quote.minUnitPrice);
            if (quote.minCharge != null && quote.minCharge.signum() > 0 && amount.compareTo(quote.minCharge) < 0) {
                amount = quote.minCharge;
            }
            estimatedAmount = amount.setScale(4, RoundingMode.HALF_UP);
        }

        private BigDecimal effectiveMinBillableUnit(ForwarderRouteQuoteRecord quote) {
            if (quote.minBillableUnit != null && quote.minBillableUnit.signum() > 0) {
                return quote.minBillableUnit;
            }
            String billingUnit = quote.billingUnit == null ? "" : quote.billingUnit.trim().toUpperCase(Locale.ROOT);
            if ("YT-SAU-SEA-FBN-RUH".equals(routeCode) && "CBM".equals(billingUnit)) {
                return DEFAULT_YT_SEA_MIN_CBM;
            }
            return null;
        }

        private BigDecimal billableQuantity(ForwarderRouteQuoteRecord quote) {
            String billingUnit = quote.billingUnit == null ? "" : quote.billingUnit.trim().toUpperCase(Locale.ROOT);
            if ("CBM".equals(billingUnit)) {
                return volumeCbm == null ? null : volumeCbm.setScale(4, RoundingMode.HALF_UP);
            }
            if ("KG".equals(billingUnit)) {
                return chargeableWeightKg == null ? null : chargeableWeightKg.setScale(3, RoundingMode.HALF_UP);
            }
            return null;
        }

        private BigDecimal totalActualWeightKg() {
            BigDecimal total = BigDecimal.ZERO;
            for (PendingShippingSource source : sources) {
                if (source.source.productWeightG == null) {
                    return null;
                }
                total = total.add(source.source.productWeightG.multiply(BigDecimal.valueOf(source.quantity)));
            }
            return total.divide(GRAMS_PER_KG, 3, RoundingMode.HALF_UP);
        }

        private BigDecimal totalVolumeCbm() {
            BigDecimal total = BigDecimal.ZERO;
            for (PendingShippingSource source : sources) {
                if (source.source.productLengthCm == null || source.source.productWidthCm == null || source.source.productHeightCm == null) {
                    return null;
                }
                total = total.add(source.source.productLengthCm
                        .multiply(source.source.productWidthCm)
                        .multiply(source.source.productHeightCm)
                        .multiply(BigDecimal.valueOf(source.quantity)));
            }
            return total.divide(CUBIC_CM_PER_CBM, 4, RoundingMode.HALF_UP);
        }

        private List<String> sensitiveReasons() {
            return sources.stream()
                    .flatMap(source -> source.sensitiveReasons().stream())
                    .distinct()
                    .collect(Collectors.toList());
        }

        private Map<String, Object> costSnapshot() {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("partnerSku", firstSource.partnerSku);
            snapshot.put("targetForwarderCode", targetForwarderCode);
            snapshot.put("targetForwarderName", targetForwarderName);
            snapshot.put("routeCode", routeCode);
            snapshot.put("routeName", routeName);
            snapshot.put("transportMode", actualTransportMode);
            snapshot.put("quantity", quantity());
            snapshot.put("actualWeightKg", actualWeightKg);
            snapshot.put("volumeCbm", volumeCbm);
            snapshot.put("chargeableWeightKg", chargeableWeightKg);
            snapshot.put("estimatedAmount", estimatedAmount);
            snapshot.put("currency", currency);
            snapshot.put("minimumNotMet", minimumNotMet);
            snapshot.put("cargoCategoryCode", cargoCategoryCode);
            snapshot.put("cargoCategoryName", cargoCategoryName);
            snapshot.put("quoteCargoCategoryCode", quoteCargoCategoryCode);
            snapshot.put("quoteCargoCategoryName", quoteCargoCategoryName);
            snapshot.put("cargoCategoryReviewRequired", cargoCategoryReviewRequired);
            snapshot.put("quoteMissingForCargoCategory", quoteMissingForCargoCategory);
            snapshot.put("sensitiveReasons", sensitiveReasons());
            return snapshot;
        }

        private String augmentedWarningJson() {
            Map<String, Object> snapshot = new LinkedHashMap<>(readJsonObject(warningJson));
            snapshot.put("cargoCategoryCode", cargoCategoryCode);
            snapshot.put("cargoCategoryName", cargoCategoryName);
            snapshot.put("quoteCargoCategoryCode", quoteCargoCategoryCode);
            snapshot.put("quoteCargoCategoryName", quoteCargoCategoryName);
            snapshot.put("cargoCategoryReviewRequired", cargoCategoryReviewRequired);
            snapshot.put("quoteMissingForCargoCategory", quoteMissingForCargoCategory);
            return writeJson(snapshot);
        }

        private ShippingSuggestionLineRecord toRecord(
                Long optionId,
                Long batchId,
                Long ownerUserId,
                Long lineId
        ) {
            ShippingSuggestionLineRecord record = new ShippingSuggestionLineRecord();
            record.id = lineId;
            record.optionId = optionId;
            record.batchId = batchId;
            record.ownerUserId = ownerUserId;
            record.productMasterId = firstSource.productMasterId;
            record.productVariantId = firstSource.productVariantId;
            record.partnerSku = firstSource.partnerSku;
            record.skuParent = firstSource.skuParent;
            record.titleCache = firstSource.titleCache;
            record.imageUrlCache = firstSource.imageUrlCache;
            record.siteCode = firstSource.siteCode;
            record.actualTransportMode = actualTransportMode;
            record.fulfillmentType = firstSource.fulfillmentType;
            record.sourcePartyName = firstSource.sourcePartyName;
            record.specStatus = firstSource.specStatus;
            record.targetForwarderCode = targetForwarderCode;
            record.targetForwarderName = targetForwarderName;
            record.routeCode = routeCode;
            record.routeName = routeName;
            record.actualWeightKg = actualWeightKg;
            record.volumeCbm = volumeCbm;
            record.chargeableWeightKg = chargeableWeightKg;
            record.estimatedAmount = estimatedAmount;
            record.currency = currency;
            record.quantity = quantity();
            record.sourceCount = sources.size();
            record.warningJson = augmentedWarningJson();
            return record;
        }
    }

    private static final class CargoCategoryEstimate {
        private final String code;
        private final String name;
        private final Boolean reviewRequired;

        private CargoCategoryEstimate(String code, String name, Boolean reviewRequired) {
            this.code = code;
            this.name = name;
            this.reviewRequired = reviewRequired;
        }
    }

    private static final class PendingShippingSource {
        private final ShippingBatchSourceRecord source;
        private final Integer quantity;
        private final List<String> sensitiveReasons;

        private PendingShippingSource(ShippingBatchSourceRecord source, Integer quantity, List<String> sensitiveReasons) {
            this.source = source;
            this.quantity = quantity;
            this.sensitiveReasons = sensitiveReasons;
        }

        private List<String> sensitiveReasons() {
            if (!Boolean.TRUE.equals(source.sensitiveFlag)) {
                return List.of();
            }
            return sensitiveReasons == null || sensitiveReasons.isEmpty() ? List.of("敏货") : sensitiveReasons;
        }

        private ShippingSuggestionLineSourceRecord toRecord(
                Long optionId,
                Long lineId,
                Long batchId,
                Long lineSourceId
        ) {
            ShippingSuggestionLineSourceRecord record = new ShippingSuggestionLineSourceRecord();
            record.id = lineSourceId;
            record.optionId = optionId;
            record.lineId = lineId;
            record.batchId = batchId;
            record.batchSourceId = source.id;
            record.fulfillmentBalanceId = source.fulfillmentBalanceId;
            record.plannedTransportMode = source.plannedTransportMode;
            record.quantity = quantity;
            return record;
        }
    }

    private static final class PendingDispatchLine {
        private final FulfillmentBalanceRecord firstBalance;
        private final String actualTransportMode;
        private final String fulfillmentType;
        private final String specStatus;
        private final List<PendingDispatchSource> sources = new ArrayList<>();

        private PendingDispatchLine(
                FulfillmentBalanceRecord firstBalance,
                String actualTransportMode,
                String fulfillmentType,
                String specStatus
        ) {
            this.firstBalance = firstBalance;
            this.actualTransportMode = actualTransportMode;
            this.fulfillmentType = fulfillmentType;
            this.specStatus = specStatus;
        }

        private DispatchPlanLineRecord toRecord(Long dispatchPlanId, Long ownerUserId, Long lineId) {
            DispatchPlanLineRecord record = new DispatchPlanLineRecord();
            record.id = lineId;
            record.dispatchPlanId = dispatchPlanId;
            record.ownerUserId = ownerUserId;
            record.productMasterId = firstBalance.productMasterId;
            record.productVariantId = firstBalance.productVariantId;
            record.partnerSku = firstBalance.partnerSku;
            record.skuParent = firstBalance.skuParent;
            record.titleCache = firstBalance.titleCache;
            record.imageUrlCache = firstBalance.imageUrlCache;
            record.siteCode = firstBalance.siteCode;
            record.actualTransportMode = actualTransportMode;
            record.fulfillmentType = fulfillmentType;
            record.specStatus = specStatus;
            record.quantity = sources.stream().mapToInt(source -> source.quantity).sum();
            record.sourceCount = sources.size();
            return record;
        }
    }

    private static final class PendingDispatchSource {
        private final FulfillmentBalanceRecord balance;
        private final Integer quantity;

        private PendingDispatchSource(FulfillmentBalanceRecord balance, Integer quantity) {
            this.balance = balance;
            this.quantity = quantity;
        }

        private DispatchPlanLineSourceRecord toRecord(
                Long dispatchPlanId,
                Long dispatchPlanLineId,
                Long ownerUserId,
                Long sourceId,
                String fulfillmentType
        ) {
            DispatchPlanLineSourceRecord record = new DispatchPlanLineSourceRecord();
            record.id = sourceId;
            record.dispatchPlanId = dispatchPlanId;
            record.dispatchPlanLineId = dispatchPlanLineId;
            record.ownerUserId = ownerUserId;
            record.fulfillmentBalanceId = balance.id;
            record.sourceStoreCode = balance.sourceStoreCode;
            record.sourceStoreName = balance.sourceStoreName;
            record.purchaseOrderId = balance.purchaseOrderId;
            record.purchaseOrderNo = balance.purchaseOrderNo;
            record.purchaseOrderItemId = balance.purchaseOrderItemId;
            record.purchaseOrderItemSiteId = balance.purchaseOrderItemSiteId;
            record.plannedTransportMode = balance.plannedTransportMode;
            record.fulfillmentType = fulfillmentType;
            record.quantity = quantity;
            return record;
        }
    }
}
