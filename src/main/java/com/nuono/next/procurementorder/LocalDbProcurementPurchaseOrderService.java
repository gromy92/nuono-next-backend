package com.nuono.next.procurementorder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.AddItemsCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.CreateOrderCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.ItemCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.SiteQuantityCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateItemCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateItemSourcingRequirementCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateOrderCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderBasePriceRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteSegmentRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderSeaRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderTransportFeeRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderWarehouseProcessingFeeRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.LogisticsCostComponentInsertRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.LogisticsRecommendationInsertRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderAli1688HistoryRow;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderAli1688PurchaseBatchRow;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ProductArchiveRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ProductOfferRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderItemRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderItemSiteRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.StoreScopeRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.StoreSiteRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.ProductOptionView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderAli1688HistoryItemView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderAli1688HistorySourceView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderAli1688HistoryView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderAli1688PurchaseBatchView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsCostComponentView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsPlanLineView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsRecommendationView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsPlanView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderItemView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.SiteQuantitySummaryView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.SiteAllocationView;
import com.nuono.next.productselection.Ali1688CollectionView;
import com.nuono.next.productselection.LocalDbAli1688CollectionService;
import com.nuono.next.productselection.NoonImageUrlNormalizer;
import com.nuono.next.productselection.ProductSelectionSourceCollectionRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
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
public class LocalDbProcurementPurchaseOrderService {

    private static final String HIDDEN_SOURCE_TYPE = "purchase-order-product";
    private static final String TRANSPORT_AIR = "AIR";
    private static final String TRANSPORT_EXPRESS = "EXPRESS";
    private static final String TRANSPORT_SEA = "SEA";
    private static final String TRANSPORT_UNSPECIFIED = "UNSPECIFIED";
    private static final String FULFILLMENT_WAREHOUSE_RECEIPT = "WAREHOUSE_RECEIPT";
    private static final String FULFILLMENT_FACTORY_DIRECT = "FACTORY_DIRECT";
    private static final BigDecimal CUBIC_CM_PER_CBM = new BigDecimal("1000000");
    private static final BigDecimal GRAMS_PER_KG = new BigDecimal("1000");
    private static final BigDecimal DEFAULT_AIR_VOLUME_DIVISOR = new BigDecimal("6000");
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<List<String>>() {};
    private static final DateTimeFormatter VIEW_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ProcurementPurchaseOrderMapper mapper;
    private final ProductSelectionMapper productSelectionMapper;
    private final LocalDbAli1688CollectionService ali1688CollectionService;
    private final ObjectMapper objectMapper;
    private final PurchaseOrderLogisticsCostCalculator costCalculator = new PurchaseOrderLogisticsCostCalculator();

    public LocalDbProcurementPurchaseOrderService(
            ProcurementPurchaseOrderMapper mapper,
            ProductSelectionMapper productSelectionMapper,
            LocalDbAli1688CollectionService ali1688CollectionService,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.productSelectionMapper = productSelectionMapper;
        this.ali1688CollectionService = ali1688CollectionService;
        this.objectMapper = objectMapper;
    }

    public List<PurchaseOrderView> listOrders(
            BusinessAccessContext access,
            String storeCode,
            String keyword
    ) {
        StoreScopeRecord scope = requireStoreScope(access, storeCode);
        return mapper.listOrders(scope.logicalStoreId, trim(keyword), 80).stream()
                .map(this::toOrderView)
                .collect(Collectors.toList());
    }

    public PurchaseOrderView getOrder(BusinessAccessContext access, String orderId) {
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        return toOrderView(order);
    }

    public List<ProductOptionView> listProductOptions(
            BusinessAccessContext access,
            String storeCode,
            String keyword
    ) {
        StoreScopeRecord scope = requireStoreScope(access, storeCode);
        return mapper.listProductOptions(scope.logicalStoreId, trim(keyword), 60).stream()
                .map(this::toProductOptionView)
                .collect(Collectors.toList());
    }

    @Transactional
    public PurchaseOrderView createOrder(BusinessAccessContext access, CreateOrderCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("缺少采购单参数。");
        }
        StoreScopeRecord scope = requireStoreScope(access, command.storeCode);
        List<String> siteCodes = normalizeSiteCodes(
                command.siteCodes == null || command.siteCodes.isEmpty()
                        ? siteCodesFromItems(command.items)
                        : command.siteCodes,
                scope.logicalStoreId
        );
        String title = requiredText(command.title, "请输入采购单名。");
        Long operatorUserId = access.getSessionUserId();
        Long orderId = mapper.nextOrderId();

        PurchaseOrderRecord order = new PurchaseOrderRecord();
        order.id = orderId;
        order.ownerUserId = scope.ownerUserId;
        order.logicalStoreId = scope.logicalStoreId;
        order.orderNo = "PO-" + orderId;
        order.title = title;
        order.remark = trimToNull(command.remark);
        order.status = "DRAFT";
        order.collectionStatus = "NOT_STARTED";
        order.progressPercent = 0;
        order.siteCodesJson = writeStringList(siteCodes);
        order.projectCodeCache = scope.projectCode;
        order.projectNameCache = scope.projectName;
        order.anchorStoreCodeCache = scope.anchorStoreCode;
        order.createdBy = operatorUserId;
        order.updatedBy = operatorUserId;
        mapper.insertOrder(order);
        log(orderId, null, "CREATE_ORDER", operatorUserId, null, "DRAFT", null);

        addItemsInternal(orderId, command.items, operatorUserId);
        mapper.recalculateOrderAggregates(orderId, operatorUserId);
        return toOrderView(requireOrder(orderId));
    }

    @Transactional
    public PurchaseOrderView updateOrder(
            BusinessAccessContext access,
            String orderId,
            UpdateOrderCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException("缺少采购单参数。");
        }
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        String title = requiredText(command.title, "请输入采购单名。");
        String remark = trimToNull(command.remark);
        Long operatorUserId = access.getSessionUserId();
        mapper.updateOrderHeader(order.id, title, remark, operatorUserId);
        log(order.id, null, "UPDATE_ORDER", operatorUserId, order.status, order.status, title);
        return toOrderView(requireOrder(order.id));
    }

    @Transactional
    public PurchaseOrderView addItems(
            BusinessAccessContext access,
            String orderId,
            AddItemsCommand command
    ) {
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        if (command == null || command.items == null || command.items.isEmpty()) {
            throw new IllegalArgumentException("请选择至少一个商品。");
        }
        addItemsInternal(order.id, command.items, access.getSessionUserId());
        mapper.recalculateOrderAggregates(order.id, access.getSessionUserId());
        return toOrderView(requireOrder(order.id));
    }

    @Transactional
    public PurchaseOrderView updateItem(
            BusinessAccessContext access,
            String orderId,
            String itemId,
            UpdateItemCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException("缺少采购单商品参数。");
        }
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        Long parsedItemId = parseLongId(itemId, "采购单商品不存在或已删除。");
        PurchaseOrderItemRecord item = mapper.selectItemById(parsedItemId);
        if (item == null || !order.id.equals(item.purchaseOrderId)) {
            throw new IllegalArgumentException("采购单商品不存在或已删除。");
        }
        String requestedPsku = trim(command.psku);
        if (StringUtils.hasText(requestedPsku) && !requestedPsku.equalsIgnoreCase(item.partnerSku)) {
            throw new IllegalArgumentException("编辑商品不支持修改 PSKU，请删除后重新添加。");
        }
        List<SiteTransportQuantity> allocations = normalizeSiteTransportQuantities(command);
        if (allocations.isEmpty()) {
            throw new IllegalArgumentException("请填写 " + item.partnerSku + " 的站点和数量。");
        }

        Map<String, StoreSiteRecord> availableStoreSites = storeSitesByCode(order.logicalStoreId);
        LinkedHashSet<String> nextOrderSiteCodes = new LinkedHashSet<>(readStringList(order.siteCodesJson));
        Long operatorUserId = access.getSessionUserId();
        String beforeStatus = dbStatus(item);
        String requestedFulfillmentType = normalizeOptionalFulfillmentType(command.fulfillmentType);
        if (requestedFulfillmentType != null || command.fulfillmentSourceName != null) {
            String nextFulfillmentType = requestedFulfillmentType == null
                    ? normalizeFulfillmentType(item.fulfillmentType)
                    : requestedFulfillmentType;
            String nextFulfillmentSourceName = command.fulfillmentSourceName == null
                    ? trimToNull(item.fulfillmentSourceName)
                    : trimToNull(command.fulfillmentSourceName);
            mapper.updateItemFulfillment(item.id, nextFulfillmentType, nextFulfillmentSourceName, operatorUserId);
            item.fulfillmentType = nextFulfillmentType;
            item.fulfillmentSourceName = nextFulfillmentSourceName;
        }
        mapper.softDeleteItemSitesByItem(parsedItemId, operatorUserId);
        for (SiteTransportQuantity allocation : allocations) {
            String siteCode = normalizeSiteCode(allocation.siteCode);
            if (!availableStoreSites.containsKey(siteCode)) {
                throw new IllegalArgumentException("站点 " + siteCode + " 不属于当前店铺。");
            }
            nextOrderSiteCodes.add(siteCode);
            ProductOfferRecord offer = mapper.selectProductOffer(order.logicalStoreId, item.productVariantId, siteCode);
            if (offer == null) {
                throw new IllegalArgumentException(item.partnerSku + " 在站点 " + siteCode + " 没有商品 Offer，不能加入采购单。");
            }
            PurchaseOrderItemSiteRecord site = new PurchaseOrderItemSiteRecord();
            site.id = mapper.nextItemSiteId();
            site.purchaseOrderId = order.id;
            site.purchaseOrderItemId = item.id;
            site.ownerUserId = order.ownerUserId;
            site.logicalStoreId = order.logicalStoreId;
            site.siteId = offer.siteId;
            site.siteCode = offer.siteCode;
            site.productSiteOfferId = offer.productSiteOfferId;
            site.pskuCode = offer.pskuCode;
            site.offerCode = offer.offerCode;
            site.transportMode = allocation.transportMode;
            site.quantity = allocation.quantity;
            site.createdBy = operatorUserId;
            site.updatedBy = operatorUserId;
            mapper.upsertItemSite(site);
        }
        mapper.recalculateItemAggregates(item.id, operatorUserId);
        mapper.recalculateOrderAggregates(order.id, operatorUserId);
        persistOrderSiteCodesIfChanged(order, nextOrderSiteCodes, operatorUserId);
        log(order.id, item.id, "UPDATE_ITEM", operatorUserId, beforeStatus, beforeStatus, item.partnerSku);
        return toOrderView(requireOrder(order.id));
    }

    @Transactional
    public PurchaseOrderView updateItemSourcingRequirement(
            BusinessAccessContext access,
            String orderId,
            String itemId,
            UpdateItemSourcingRequirementCommand command
    ) {
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        Long parsedItemId = parseLongId(itemId, "采购单商品不存在或已删除。");
        PurchaseOrderItemRecord item = mapper.selectItemById(parsedItemId);
        if (item == null || !order.id.equals(item.purchaseOrderId)) {
            throw new IllegalArgumentException("采购单商品不存在或已删除。");
        }
        ProcurementPurchaseOrderSourcingRequirement requirement = ProcurementPurchaseOrderSourcingRequirement.of(
                command == null ? null : command.sourcingSpec,
                command == null ? null : command.sourcingSize,
                command == null ? null : command.sourcingColor
        );
        mapper.updateItemSourcingRequirement(parsedItemId, requirement, access.getSessionUserId());
        log(order.id, parsedItemId, "UPDATE_ITEM_SOURCING_REQUIREMENT", access.getSessionUserId(), null, null, null);
        return toOrderView(requireOrder(order.id));
    }

    @Transactional
    public PurchaseOrderView deleteOrder(BusinessAccessContext access, String orderId) {
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        Long operatorUserId = access.getSessionUserId();
        mapper.softDeleteLinksByOrder(order.id, operatorUserId);
        mapper.softDeleteItemSitesByOrder(order.id, operatorUserId);
        mapper.softDeleteItemsByOrder(order.id, operatorUserId);
        mapper.softDeleteOrder(order.id, operatorUserId);
        log(order.id, null, "DELETE_ORDER", operatorUserId, order.status, "DELETED", null);
        PurchaseOrderView view = new PurchaseOrderView();
        view.id = String.valueOf(order.id);
        view.orderNo = order.orderNo;
        view.title = order.title;
        view.status = "deleted";
        return view;
    }

    @Transactional
    public PurchaseOrderView deleteItem(
            BusinessAccessContext access,
            String orderId,
            String itemId
    ) {
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        Long parsedItemId = parseLongId(itemId, "采购单商品不存在或已删除。");
        PurchaseOrderItemRecord item = mapper.selectItemById(parsedItemId);
        if (item == null || !order.id.equals(item.purchaseOrderId)) {
            throw new IllegalArgumentException("采购单商品不存在或已删除。");
        }
        Long operatorUserId = access.getSessionUserId();
        mapper.supersedeCurrentAli1688TasksByItem(parsedItemId, operatorUserId);
        mapper.softDeleteLinksByItem(parsedItemId, operatorUserId);
        mapper.softDeleteItemSitesByItem(parsedItemId, operatorUserId);
        mapper.softDeleteItem(parsedItemId, operatorUserId);
        mapper.recalculateOrderAggregates(order.id, operatorUserId);
        log(order.id, parsedItemId, "DELETE_ITEM", operatorUserId, dbStatus(item), "DELETED", item.partnerSku);
        return toOrderView(requireOrder(order.id));
    }

    @Transactional
    public PurchaseOrderView collectOrder(BusinessAccessContext access, String orderId) {
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        List<PurchaseOrderItemRecord> items = mapper.listItemsByOrder(order.id);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("当前采购单还没有商品，不能发起采集。");
        }
        for (PurchaseOrderItemRecord item : items) {
            collectItemInternal(order, item, access.getSessionUserId());
        }
        mapper.recalculateOrderAggregates(order.id, access.getSessionUserId());
        return toOrderView(requireOrder(order.id));
    }

    @Transactional
    public PurchaseOrderView collectItem(
            BusinessAccessContext access,
            String orderId,
            String itemId
    ) {
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        Long parsedItemId = parseLongId(itemId, "采购单商品不存在或已删除。");
        PurchaseOrderItemRecord item = mapper.selectItemById(parsedItemId);
        if (item == null || !order.id.equals(item.purchaseOrderId)) {
            throw new IllegalArgumentException("采购单商品不存在或已删除。");
        }
        collectItemInternal(order, item, access.getSessionUserId());
        mapper.recalculateOrderAggregates(order.id, access.getSessionUserId());
        return toOrderView(requireOrder(order.id));
    }

    public Ali1688CollectionView getItemAli1688(BusinessAccessContext access, String itemId) {
        PurchaseOrderItemRecord item = mapper.selectItemById(parseLongId(itemId, "采购单商品不存在或已删除。"));
        if (item == null) {
            throw new IllegalArgumentException("采购单商品不存在或已删除。");
        }
        PurchaseOrderRecord order = requireOrder(item.purchaseOrderId);
        requireOrderAccess(access, order);
        if (item.sourceCollectionId == null) {
            Ali1688CollectionView view = new Ali1688CollectionView();
            view.status = "not_started";
            view.progressPercent = 0;
            view.searchMode = "主图图搜";
            view.sourcePlatform = "店铺";
            view.sourceTitle = defaultText(item.titleCache, item.partnerSku);
            view.sourceTitleCn = defaultText(item.titleCache, item.partnerSku);
            view.sourceImageUrl = NoonImageUrlNormalizer.normalize(item.imageUrlCache);
            view.candidateCount = 0;
            view.recommendedCount = 0;
            view.message = "该采购单商品尚未发起1688采集。";
            view.canGenerateProcurementOrder = false;
            view.sourceSpecs = purchaseOrderItemSpecs(item);
            return view;
        }
        Ali1688CollectionView view = ali1688CollectionService.getCurrentView(item.sourceCollectionId);
        view.sourceSpecs = mergeSourceSpecs(purchaseOrderItemSpecs(item), view.sourceSpecs);
        return view;
    }

    @Transactional(readOnly = true)
    public PurchaseOrderAli1688HistoryView getOrderAli1688History(BusinessAccessContext access, String orderId) {
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        List<PurchaseOrderItemRecord> items = mapper.listItemsByOrder(order.id);
        List<PurchaseOrderItemSiteRecord> sites = mapper.listItemSitesByOrder(order.id);
        PurchaseOrderAli1688HistoryView view = new PurchaseOrderAli1688HistoryView();
        if (items.isEmpty() || sites.isEmpty()) {
            fillPagination(view);
            return view;
        }

        LinkedHashSet<String> siteCodes = new LinkedHashSet<>();
        LinkedHashSet<String> partnerSkus = new LinkedHashSet<>();
        LinkedHashSet<String> skuParents = new LinkedHashSet<>();
        Map<Long, PurchaseOrderItemRecord> itemsById = items.stream()
                .collect(Collectors.toMap(item -> item.id, item -> item, (left, right) -> left, LinkedHashMap::new));
        for (PurchaseOrderItemRecord item : items) {
            addTrimmed(partnerSkus, item.partnerSku);
            addTrimmed(skuParents, item.skuParent);
        }
        for (PurchaseOrderItemSiteRecord site : sites) {
            String siteCode = normalizeSiteCode(site.siteCode);
            if (StringUtils.hasText(siteCode)) {
                siteCodes.add(siteCode);
            }
            addTrimmed(partnerSkus, site.pskuCode);
            PurchaseOrderItemRecord item = itemsById.get(site.purchaseOrderItemId);
            if (item != null) {
                addTrimmed(partnerSkus, item.partnerSku);
                addTrimmed(skuParents, item.skuParent);
            }
        }
        String projectCode = firstText(order.projectCodeCache, order.anchorStoreCodeCache);
        if (!StringUtils.hasText(projectCode) || siteCodes.isEmpty() || (partnerSkus.isEmpty() && skuParents.isEmpty())) {
            fillPagination(view);
            return view;
        }

        Map<String, Ali1688HistoryAccumulator> bySku = new LinkedHashMap<>();
        for (PurchaseOrderAli1688PurchaseBatchRow row : mapper.listOrderAli1688PurchaseBatches(
                order.ownerUserId,
                projectCode,
                new ArrayList<>(siteCodes),
                new ArrayList<>(partnerSkus),
                new ArrayList<>(skuParents)
        )) {
            bySku.computeIfAbsent(ali1688HistoryGroupKey(row.siteCode, row.partnerSku, row.pskuCode, row.skuParent),
                    ignored -> new Ali1688HistoryAccumulator(row))
                    .add(row);
        }
        for (PurchaseOrderAli1688HistoryRow row : mapper.listOrderAli1688AllocationHistoryRows(
                order.ownerUserId,
                projectCode,
                new ArrayList<>(siteCodes),
                new ArrayList<>(partnerSkus),
                new ArrayList<>(skuParents)
        )) {
            bySku.computeIfAbsent(ali1688HistoryGroupKey(row.siteCode, row.partnerSku, row.pskuCode, row.skuParent),
                    ignored -> new Ali1688HistoryAccumulator(row))
                    .add(row);
        }
        for (PurchaseOrderAli1688HistoryRow row : mapper.listOrderAli1688HistoryRows(
                order.ownerUserId,
                projectCode,
                new ArrayList<>(siteCodes),
                new ArrayList<>(partnerSkus),
                new ArrayList<>(skuParents)
        )) {
            bySku.computeIfAbsent(ali1688HistoryGroupKey(row.siteCode, row.partnerSku, row.pskuCode, row.skuParent),
                    ignored -> new Ali1688HistoryAccumulator(row))
                    .add(row);
        }
        view.items = bySku.values().stream()
                .map(Ali1688HistoryAccumulator::toView)
                .collect(Collectors.toList());
        fillPagination(view);
        return view;
    }

    @Transactional(readOnly = true)
    public PurchaseOrderLogisticsPlanView previewLogisticsPlan(BusinessAccessContext access, String orderId) {
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        return buildLogisticsPlanView(order, access.getSessionUserId(), false);
    }

    @Transactional
    public PurchaseOrderLogisticsPlanView generateLogisticsPlan(BusinessAccessContext access, String orderId) {
        PurchaseOrderRecord order = requireOrderAccess(access, parseLongId(orderId, "采购单不存在或已删除。"));
        return buildLogisticsPlanView(order, access.getSessionUserId(), true);
    }

    private PurchaseOrderLogisticsPlanView buildLogisticsPlanView(
            PurchaseOrderRecord order,
            Long operatorUserId,
            boolean persist
    ) {
        List<PurchaseOrderItemRecord> items = mapper.listItemsByOrder(order.id);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("当前采购单还没有商品，不能生成物流计划。");
        }

        Map<Long, List<PurchaseOrderItemSiteRecord>> sitesByItem = mapper.listItemSitesByOrder(order.id).stream()
                .collect(Collectors.groupingBy(row -> row.purchaseOrderItemId, LinkedHashMap::new, Collectors.toList()));
        PurchaseOrderLogisticsPlanView view = new PurchaseOrderLogisticsPlanView();
        Long planId = persist ? mapper.nextLogisticsPlanId() : null;
        view.id = persist ? String.valueOf(planId) : "preview-" + order.id;
        view.planNo = persist ? "LP-" + planId : "PREVIEW-" + order.orderNo;
        view.purchaseOrderId = String.valueOf(order.id);
        view.purchaseOrderNo = order.orderNo;
        view.purchaseOrderTitle = order.title;
        view.storeName = defaultText(order.projectNameCache, order.projectCodeCache);
        view.storeCode = order.anchorStoreCodeCache;
        view.status = persist ? "draft" : "preview";
        view.transportMode = "pending";
        view.generatedAt = VIEW_DATE_TIME_FORMATTER.format(LocalDateTime.now());

        Map<String, SiteTransportQuantity> siteQuantityTotals = new LinkedHashMap<>();
        int skuCount = 0;
        int totalQuantity = 0;
        int missingItemCount = 0;
        for (PurchaseOrderItemRecord item : items) {
            List<PurchaseOrderItemSiteRecord> sites = sitesByItem.getOrDefault(item.id, Collections.emptyList());
            ProductArchiveRecord product = mapper.selectProductArchiveByVariant(order.logicalStoreId, item.productVariantId);
            PurchaseOrderLogisticsPlanLineView line = toLogisticsPlanLine(item, product, sites);
            view.lines.add(line);
            skuCount += Math.max(sites.size(), 1);
            totalQuantity += nonNull(item.totalQuantity);
            if (!line.missingFields.isEmpty()) {
                missingItemCount++;
            }
            for (PurchaseOrderItemSiteRecord site : sites) {
                String siteCode = normalizeSiteCode(site.siteCode);
                String transportMode = normalizeTransportMode(site.transportMode);
                if (StringUtils.hasText(siteCode)) {
                    addSiteTransportQuantity(siteQuantityTotals, siteCode, transportMode, site.quantity);
                }
            }
        }
        view.itemCount = items.size();
        view.skuCount = skuCount;
        view.totalQuantity = totalQuantity;
        view.missingItemCount = missingItemCount;
        view.estimatedSeaVolumeCbm = totalSeaLooseVolumeCbm(view);
        view.estimatedSeaVolumeCbmText = formatCbm(view.estimatedSeaVolumeCbm);
        view.estimatedAirChargeableWeightKg = hasMissingAirChargeableWeightInputs(view)
                ? null : totalAirChargeableWeightKg(view, DEFAULT_AIR_VOLUME_DIVISOR);
        view.estimatedAirChargeableWeightKgText = formatKg(view.estimatedAirChargeableWeightKg);
        for (SiteTransportQuantity entry : siteQuantityTotals.values()) {
            SiteQuantitySummaryView summary = new SiteQuantitySummaryView();
            summary.site = entry.siteCode;
            summary.siteName = siteName(entry.siteCode);
            summary.transportMode = entry.transportMode;
            summary.transportModeLabel = transportModeLabel(entry.transportMode);
            summary.quantity = entry.quantity;
            view.siteSummaries.add(summary);
        }
        if (missingItemCount > 0) {
            view.messages.add("有 " + missingItemCount + " 个商品缺少物流计划所需的规格或箱规信息，生成后需要人工补齐。");
        } else {
            view.messages.add("采购单商品规格和箱规信息完整，可以进入运输方式和货代推荐。");
        }
        view.messages.add("已按采购单中的空运/海运拆分数量；后续将只推荐对应货代服务线。");
        String seaRecommendationStatus = appendSeaForwarderRecommendations(view, siteQuantityTotals);
        String airRecommendationStatus = appendAirForwarderRecommendations(view, siteQuantityTotals);
        view.recommendationStatus = mergeRecommendationStatus(seaRecommendationStatus, airRecommendationStatus);

        if (persist) {
            mapper.supersedeCurrentLogisticsPlansByOrder(order.id, operatorUserId);
            mapper.softDeleteLogisticsRecommendationsByOrder(order.id, operatorUserId);
            mapper.insertLogisticsPlan(
                    planId,
                    order.id,
                    order.ownerUserId,
                    order.logicalStoreId,
                    view.planNo,
                    "DRAFT",
                    "PENDING",
                    view.itemCount,
                    view.skuCount,
                    view.totalQuantity,
                    view.missingItemCount,
                    writeJson(view.siteSummaries),
                    writeJson(view),
                    operatorUserId
            );
            persistLogisticsRecommendations(planId, order.id, view.recommendations, operatorUserId);
            log(order.id, null, "GENERATE_LOGISTICS_PLAN", operatorUserId, order.status, order.status, view.planNo);
        }
        return view;
    }

    private void persistLogisticsRecommendations(
            Long planId,
            Long purchaseOrderId,
            List<PurchaseOrderLogisticsRecommendationView> recommendations,
            Long operatorUserId
    ) {
        for (PurchaseOrderLogisticsRecommendationView recommendation : recommendations) {
            LogisticsRecommendationInsertRecord row = new LogisticsRecommendationInsertRecord();
            row.id = mapper.nextLogisticsRecommendationId();
            row.logisticsPlanId = planId;
            row.purchaseOrderId = purchaseOrderId;
            row.routeCode = recommendation.routeCode;
            row.forwarderCode = recommendation.forwarderCode;
            row.serviceCode = recommendation.serviceCode;
            row.transportMode = recommendation.transportMode;
            row.rankNo = recommendation.rank;
            row.recommended = recommendation.recommended;
            row.estimateStatus = recommendation.estimateStatus;
            row.currency = firstComponentCurrency(recommendation);
            row.estimatedTotalAmount = recommendation.estimatedTotalAmount;
            row.recurringAmountPerDay = recommendation.recurringAmountPerDay;
            row.snapshotJson = writeJson(recommendation);
            mapper.insertLogisticsRecommendation(row, operatorUserId);

            for (PurchaseOrderLogisticsCostComponentView component : recommendation.costComponents) {
                LogisticsCostComponentInsertRecord componentRow = new LogisticsCostComponentInsertRecord();
                componentRow.id = mapper.nextLogisticsCostComponentId();
                componentRow.recommendationId = row.id;
                componentRow.logisticsPlanId = planId;
                componentRow.componentType = component.componentType;
                componentRow.componentName = component.componentName;
                componentRow.sourceTable = sourceTableForComponent(component.componentType);
                componentRow.sourceId = component.sourceId;
                componentRow.currency = component.currency;
                componentRow.unitPrice = component.unitPrice;
                componentRow.billingUnit = component.billingUnit;
                componentRow.billableQuantity = component.billableQuantity;
                componentRow.amount = component.amount;
                componentRow.amountStatus = component.amountStatus;
                componentRow.includedInTotal = component.includedInTotal;
                componentRow.formulaText = component.formulaText;
                componentRow.remark = component.remark;
                mapper.insertLogisticsCostComponent(componentRow, operatorUserId);
            }
        }
    }

    private String firstComponentCurrency(PurchaseOrderLogisticsRecommendationView recommendation) {
        if (recommendation == null || recommendation.costComponents == null) {
            return null;
        }
        return recommendation.costComponents.stream()
                .map(component -> component.currency)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private String sourceTableForComponent(String componentType) {
        if ("HEADHAUL".equals(componentType) || "LAST_MILE".equals(componentType)) {
            return "forwarder_quote_base_price";
        }
        if (componentType != null && componentType.startsWith("WAREHOUSE_")) {
            return "forwarder_warehouse_processing_fee";
        }
        return null;
    }

    private String appendSeaForwarderRecommendations(
            PurchaseOrderLogisticsPlanView view,
            Map<String, SiteTransportQuantity> siteQuantityTotals
    ) {
        List<SiteTransportQuantity> seaEntries = siteQuantityTotals.values().stream()
                .filter(entry -> TRANSPORT_SEA.equals(entry.transportMode))
                .filter(entry -> entry.quantity > 0)
                .collect(Collectors.toList());
        if (seaEntries.isEmpty()) {
            return "no_sea_quantity";
        }

        List<ForwarderRouteRecommendationRecord> candidates = mapper.listRouteRecommendationCandidates(siteCodesFromEntries(seaEntries), TRANSPORT_SEA);
        if (candidates.isEmpty()) {
            view.messages.add("当前海运站点暂无可用货代报价，不能生成真实货代推荐。");
            return "no_sea_quote";
        }

        boolean blockedBySpecs = hasMissingSeaLooseVolumeInputs(view);
        boolean hasCartonWarnings = hasMissingCartonSpecs(view);
        List<ForwarderRouteRecommendationRecord> sorted = candidates.stream()
                .sorted(Comparator
                        .comparing((ForwarderRouteRecommendationRecord record) -> preferredSeaUnitPrice(record) == null ? BigDecimal.valueOf(Long.MAX_VALUE) : preferredSeaUnitPrice(record))
                        .thenComparing(record -> defaultText(record.forwarderName, record.forwarderCode))
                        .thenComparing(record -> defaultText(record.serviceCode, "")))
                .collect(Collectors.toList());
        RouteCostInputs routeCostInputs = routeCostInputs(sorted);
        int rank = 1;
        for (ForwarderRouteRecommendationRecord candidate : sorted) {
            view.recommendations.add(toSeaForwarderRecommendation(
                    candidate,
                    rank,
                    blockedBySpecs,
                    hasCartonWarnings,
                    view.estimatedSeaVolumeCbm,
                    view,
                    routeCostInputs.basePrices(candidate.routeCode),
                    routeCostInputs.warehouseFees(candidate.routeCode),
                    routeCostInputs.transportFees(candidate.routeCode)
            ));
            rank++;
        }
        if (blockedBySpecs) {
            view.messages.add("已找到 " + sorted.size() + " 条海运货代服务线；当前存在商品尺寸缺失，暂不能按散货体积估算海运费用。");
        } else if (hasCartonWarnings) {
            view.messages.add("已找到 " + sorted.size() + " 条海运货代服务线；当前按商品散货体积 "
                    + defaultText(view.estimatedSeaVolumeCbmText, "-") + " 估算，箱规缺失不阻塞海运草案。");
        } else {
            view.messages.add("已找到 " + sorted.size() + " 条海运货代服务线；当前按商品散货体积 "
                    + defaultText(view.estimatedSeaVolumeCbmText, "-") + " 估算，可进入品类和计费规则复核。");
        }
        return blockedBySpecs ? "sea_candidate_blocked_by_specs" : "sea_candidate_ready";
    }

    private String appendAirForwarderRecommendations(
            PurchaseOrderLogisticsPlanView view,
            Map<String, SiteTransportQuantity> siteQuantityTotals
    ) {
        List<SiteTransportQuantity> airEntries = siteQuantityTotals.values().stream()
                .filter(entry -> TRANSPORT_AIR.equals(entry.transportMode))
                .filter(entry -> entry.quantity > 0)
                .collect(Collectors.toList());
        if (airEntries.isEmpty()) {
            return "no_air_quantity";
        }

        List<ForwarderRouteRecommendationRecord> candidates = mapper.listRouteRecommendationCandidates(siteCodesFromEntries(airEntries), TRANSPORT_AIR);
        if (candidates.isEmpty()) {
            view.messages.add("当前空运站点暂无可用货代报价，不能生成真实货代推荐。");
            return "no_air_quote";
        }

        boolean blockedBySpecs = hasMissingAirChargeableWeightInputs(view);
        List<ForwarderRouteRecommendationRecord> sorted = candidates.stream()
                .sorted(Comparator
                        .comparing((ForwarderRouteRecommendationRecord record) -> preferredAirUnitPrice(record) == null ? BigDecimal.valueOf(Long.MAX_VALUE) : preferredAirUnitPrice(record))
                        .thenComparing(record -> defaultText(record.forwarderName, record.forwarderCode))
                        .thenComparing(record -> defaultText(record.serviceCode, "")))
                .collect(Collectors.toList());
        RouteCostInputs routeCostInputs = routeCostInputs(sorted);
        int rank = 1;
        for (ForwarderRouteRecommendationRecord candidate : sorted) {
            view.recommendations.add(toAirForwarderRecommendation(
                    candidate,
                    rank,
                    blockedBySpecs,
                    view,
                    routeCostInputs.basePrices(candidate.routeCode),
                    routeCostInputs.warehouseFees(candidate.routeCode),
                    routeCostInputs.transportFees(candidate.routeCode)
            ));
            rank++;
        }
        if (blockedBySpecs) {
            view.messages.add("已找到 " + sorted.size() + " 条空运货代服务线；当前存在空运商品尺寸或重量缺失，暂不能按计费重估算空运费用。");
        } else {
            view.messages.add("已找到 " + sorted.size() + " 条空运货代服务线；当前按各货代体积重除数和最低计费规则估算，可进入品类和计费规则复核。");
        }
        return blockedBySpecs ? "air_candidate_blocked_by_specs" : "air_candidate_ready";
    }

    private List<String> siteCodesFromEntries(List<SiteTransportQuantity> entries) {
        return entries.stream()
                .map(entry -> normalizeSiteCode(entry.siteCode))
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    private RouteCostInputs routeCostInputs(List<ForwarderRouteRecommendationRecord> candidates) {
        List<String> routeCodes = candidates.stream()
                .map(candidate -> candidate.routeCode)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (routeCodes.isEmpty()) {
            return RouteCostInputs.empty();
        }
        List<ForwarderRouteSegmentRecord> segments = mapper.listRouteSegments(routeCodes);
        Map<String, List<ForwarderRouteSegmentRecord>> segmentsByRoute = segments.stream()
                .collect(Collectors.groupingBy(segment -> segment.routeCode, LinkedHashMap::new, Collectors.toList()));
        Map<String, List<ForwarderBasePriceRecord>> basePricesByService = basePricesByService(segments);
        Map<String, List<ForwarderWarehouseProcessingFeeRecord>> warehouseFeesByService = warehouseFeesByService(segments);
        Map<String, List<ForwarderTransportFeeRecord>> transportFeesByService = transportFeesByService(segments);
        return new RouteCostInputs(segmentsByRoute, basePricesByService, warehouseFeesByService, transportFeesByService);
    }

    private Map<String, List<ForwarderBasePriceRecord>> basePricesByService(List<ForwarderRouteSegmentRecord> segments) {
        List<String> serviceCodes = segments.stream()
                .filter(segment -> "LAST_MILE".equals(segment.segmentRole))
                .map(segment -> segment.serviceCode)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (serviceCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        return mapper.listBasePricesByServiceCodes(serviceCodes).stream()
                .collect(Collectors.groupingBy(price -> price.serviceCode, LinkedHashMap::new, Collectors.toList()));
    }

    private Map<String, List<ForwarderWarehouseProcessingFeeRecord>> warehouseFeesByService(List<ForwarderRouteSegmentRecord> segments) {
        List<String> serviceCodes = segments.stream()
                .filter(segment -> "WAREHOUSE_PROCESSING".equals(segment.segmentRole))
                .map(segment -> segment.serviceCode)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (serviceCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        return mapper.listWarehouseProcessingFeesByServiceCodes(serviceCodes).stream()
                .collect(Collectors.groupingBy(fee -> fee.serviceCode, LinkedHashMap::new, Collectors.toList()));
    }

    private Map<String, List<ForwarderTransportFeeRecord>> transportFeesByService(List<ForwarderRouteSegmentRecord> segments) {
        List<String> serviceCodes = segments.stream()
                .filter(segment -> "LAST_MILE".equals(segment.segmentRole))
                .map(segment -> segment.serviceCode)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (serviceCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        return mapper.listTransportFeesByServiceCodes(serviceCodes).stream()
                .collect(Collectors.groupingBy(fee -> fee.serviceCode, LinkedHashMap::new, Collectors.toList()));
    }

    private String mergeRecommendationStatus(String seaStatus, String airStatus) {
        boolean hasSeaCandidate = isCandidateRecommendationStatus(seaStatus);
        boolean hasAirCandidate = isCandidateRecommendationStatus(airStatus);
        if (hasSeaCandidate && hasAirCandidate) {
            if (isBlockedRecommendationStatus(seaStatus) || isBlockedRecommendationStatus(airStatus)) {
                return "transport_candidate_blocked_by_specs";
            }
            return "transport_candidate_ready";
        }
        if (hasSeaCandidate) {
            return seaStatus;
        }
        if (hasAirCandidate) {
            return airStatus;
        }
        if ("no_sea_quote".equals(seaStatus) || "no_air_quote".equals(airStatus)) {
            return "no_transport_quote";
        }
        return "no_transport_quantity";
    }

    private boolean isCandidateRecommendationStatus(String status) {
        return StringUtils.hasText(status) && status.contains("candidate");
    }

    private boolean isBlockedRecommendationStatus(String status) {
        return StringUtils.hasText(status) && status.contains("blocked");
    }

    private PurchaseOrderLogisticsRecommendationView toSeaForwarderRecommendation(
            ForwarderRouteRecommendationRecord candidate,
            int rank,
            boolean blockedBySpecs,
            boolean hasCartonWarnings,
            BigDecimal estimatedSeaVolumeCbm,
            PurchaseOrderLogisticsPlanView plan,
            List<ForwarderBasePriceRecord> segmentBasePrices,
            List<ForwarderWarehouseProcessingFeeRecord> warehouseFees,
            List<ForwarderTransportFeeRecord> transportFees
    ) {
        PurchaseOrderLogisticsRecommendationView view = new PurchaseOrderLogisticsRecommendationView();
        view.rank = rank;
        view.recommended = rank == 1;
        view.routeCode = candidate.routeCode;
        view.routeName = candidate.routeName;
        view.forwarderCode = candidate.forwarderCode;
        view.forwarderName = defaultText(candidate.forwarderName, candidate.forwarderCode);
        view.serviceCode = candidate.serviceCode;
        view.serviceName = candidate.serviceName;
        view.transportMode = normalizeTransportMode(candidate.transportMode);
        view.country = candidate.country;
        view.targetPlatform = candidate.targetPlatform;
        view.deliveryCity = candidate.deliveryCity;
        view.destinationNode = candidate.destinationNode;
        view.transitTimeText = candidate.transitTimeText;
        view.priceSummary = seaPriceSummary(candidate);
        view.cargoCategorySummary = summarizeCsv(candidate.cargoCategoryNamesCsv, 3);
        view.estimateStatus = blockedBySpecs ? "blocked_by_missing_specs" : "loose_volume_estimated";
        if (!blockedBySpecs) {
            costCalculator.enrich(view, candidate, plan, segmentBasePrices, warehouseFees, transportFees);
            view.estimatedCostText = defaultText(costSummaryText(view), estimatedSeaCostText(candidate, estimatedSeaVolumeCbm));
        }
        view.reasons.add("匹配采购单海运数量和 " + defaultText(candidate.country, "目标站点") + " 服务线。");
        if (nonNull(candidate.priceRuleCount) > 0) {
            view.reasons.add("已读取 " + candidate.priceRuleCount + " 条基础报价规则。");
        }
        if (blockedBySpecs) {
            view.risks.add("采购单存在商品尺寸缺失，暂不能按散货体积估算海运费用。");
        } else if (hasCartonWarnings) {
            view.risks.add("箱规缺失未阻塞本次海运估算；当前按商品散货体积计算。");
        }
        if (candidate.minUnitPrice == null) {
            view.risks.add("服务线未提供可直接计价的正常基础单价，需要人工询价。");
        }
        return view;
    }

    private PurchaseOrderLogisticsRecommendationView toAirForwarderRecommendation(
            ForwarderRouteRecommendationRecord candidate,
            int rank,
            boolean blockedBySpecs,
            PurchaseOrderLogisticsPlanView plan,
            List<ForwarderBasePriceRecord> segmentBasePrices,
            List<ForwarderWarehouseProcessingFeeRecord> warehouseFees,
            List<ForwarderTransportFeeRecord> transportFees
    ) {
        PurchaseOrderLogisticsRecommendationView view = new PurchaseOrderLogisticsRecommendationView();
        view.rank = rank;
        view.recommended = rank == 1;
        view.routeCode = candidate.routeCode;
        view.routeName = candidate.routeName;
        view.forwarderCode = candidate.forwarderCode;
        view.forwarderName = defaultText(candidate.forwarderName, candidate.forwarderCode);
        view.serviceCode = candidate.serviceCode;
        view.serviceName = candidate.serviceName;
        view.transportMode = normalizeTransportMode(candidate.transportMode);
        view.country = candidate.country;
        view.targetPlatform = candidate.targetPlatform;
        view.deliveryCity = candidate.deliveryCity;
        view.destinationNode = candidate.destinationNode;
        view.transitTimeText = candidate.transitTimeText;
        view.priceSummary = seaPriceSummary(candidate);
        view.cargoCategorySummary = summarizeCsv(candidate.cargoCategoryNamesCsv, 3);
        view.estimateStatus = blockedBySpecs ? "air_blocked_by_missing_specs" : "air_chargeable_weight_estimated";
        if (!blockedBySpecs) {
            costCalculator.enrich(view, candidate, plan, segmentBasePrices, warehouseFees, transportFees);
            view.estimatedCostText = defaultText(costSummaryText(view), estimatedAirCostText(candidate, plan));
        }
        view.reasons.add("匹配采购单空运数量和 " + defaultText(candidate.country, "目标站点") + " 服务线。");
        if (nonNull(candidate.priceRuleCount) > 0) {
            view.reasons.add("已读取 " + candidate.priceRuleCount + " 条基础报价规则。");
        }
        if (candidate.volumeDivisor != null) {
            view.reasons.add("体积重除数 " + formatDecimal(candidate.volumeDivisor) + "。");
        }
        if (blockedBySpecs) {
            view.risks.add("采购单存在空运商品尺寸或重量缺失，暂不能按计费重估算空运费用。");
        }
        if (preferredAirKgUnitPrice(candidate) == null) {
            view.risks.add("服务线未提供可直接计价的 KG 正常基础单价，需要人工询价。");
        }
        return view;
    }

    private void addItemsInternal(
            Long orderId,
            List<ItemCommand> itemCommands,
            Long operatorUserId
    ) {
        PurchaseOrderRecord order = requireOrder(orderId);
        if (itemCommands == null || itemCommands.isEmpty()) {
            return;
        }
        Map<String, StoreSiteRecord> availableStoreSites = storeSitesByCode(order.logicalStoreId);
        Map<Long, Set<String>> existingSitesByItemId = existingSitesByItemId(order.id);
        Map<Long, Set<String>> pendingSitesByVariantId = new LinkedHashMap<>();
        LinkedHashSet<String> nextOrderSiteCodes = new LinkedHashSet<>(readStringList(order.siteCodesJson));
        for (ItemCommand itemCommand : itemCommands) {
            String psku = requiredText(itemCommand == null ? null : itemCommand.psku, "请选择 PSKU。");
            String requestedFulfillmentType = normalizeOptionalFulfillmentType(itemCommand == null ? null : itemCommand.fulfillmentType);
            String fulfillmentSourceName = trimToNull(itemCommand == null ? null : itemCommand.fulfillmentSourceName);
            ProductArchiveRecord product = resolveProduct(order.logicalStoreId, psku);
            List<SiteTransportQuantity> allocations = normalizeSiteTransportQuantities(itemCommand);
            if (allocations.isEmpty()) {
                throw new IllegalArgumentException("请填写 " + psku + " 的站点和数量。");
            }
            ensureNoDuplicateSitesInAllocations(psku, allocations);

            PurchaseOrderItemRecord item = mapper.selectItemByVariant(order.id, product.productVariantId);
            boolean itemAlreadyExisted = item != null;
            if (item == null) {
                item = new PurchaseOrderItemRecord();
                item.id = mapper.nextItemId();
                item.purchaseOrderId = order.id;
                item.ownerUserId = order.ownerUserId;
                item.logicalStoreId = order.logicalStoreId;
                item.productMasterId = product.productMasterId;
                item.productVariantId = product.productVariantId;
                item.skuParent = product.skuParent;
                item.partnerSku = product.partnerSku;
                item.childSku = product.childSku;
                item.titleCache = defaultText(product.title, product.partnerSku);
                item.imageUrlCache = NoonImageUrlNormalizer.normalize(product.imageUrl);
                item.sourceType = "STORE_ARCHIVE";
                item.fulfillmentType = requestedFulfillmentType == null
                        ? FULFILLMENT_WAREHOUSE_RECEIPT
                        : requestedFulfillmentType;
                item.fulfillmentSourceName = fulfillmentSourceName;
                item.createdBy = operatorUserId;
                item.updatedBy = operatorUserId;
                mapper.insertItem(item);
            } else if (
                    requestedFulfillmentType != null
                            && !normalizeFulfillmentType(item.fulfillmentType).equals(requestedFulfillmentType)
            ) {
                throw new IllegalArgumentException(psku + " 已在采购单中选择 "
                        + fulfillmentTypeLabel(item.fulfillmentType)
                        + "，同一采购单商品只能选择一种到货方式。");
            }

            Set<String> existingSites = itemAlreadyExisted
                    ? existingSitesByItemId.getOrDefault(item.id, Collections.emptySet())
                    : Collections.emptySet();
            Set<String> pendingSites = pendingSitesByVariantId.computeIfAbsent(
                    product.productVariantId,
                    ignored -> new LinkedHashSet<>()
            );
            for (SiteTransportQuantity allocation : allocations) {
                String siteCode = normalizeSiteCode(allocation.siteCode);
                if (!availableStoreSites.containsKey(siteCode)) {
                    throw new IllegalArgumentException("站点 " + siteCode + " 不属于当前店铺。");
                }
                if (existingSites.contains(siteCode) || pendingSites.contains(siteCode)) {
                    throw new IllegalArgumentException(psku + " 已在站点 " + siteCode
                            + " 加入采购单，不能重复添加相同商品相同站点。");
                }
                nextOrderSiteCodes.add(siteCode);
                ProductOfferRecord offer = mapper.selectProductOffer(order.logicalStoreId, product.productVariantId, siteCode);
                if (offer == null) {
                    throw new IllegalArgumentException(psku + " 在站点 " + siteCode + " 没有商品 Offer，不能加入采购单。");
                }
                PurchaseOrderItemSiteRecord site = new PurchaseOrderItemSiteRecord();
                site.id = mapper.nextItemSiteId();
                site.purchaseOrderId = order.id;
                site.purchaseOrderItemId = item.id;
                site.ownerUserId = order.ownerUserId;
                site.logicalStoreId = order.logicalStoreId;
                site.siteId = offer.siteId;
                site.siteCode = offer.siteCode;
                site.productSiteOfferId = offer.productSiteOfferId;
                site.pskuCode = offer.pskuCode;
                site.offerCode = offer.offerCode;
                site.transportMode = allocation.transportMode;
                site.quantity = allocation.quantity;
                site.createdBy = operatorUserId;
                site.updatedBy = operatorUserId;
                mapper.upsertItemSite(site);
                pendingSites.add(siteCode);
            }
            mapper.recalculateItemAggregates(item.id, operatorUserId);
            log(order.id, item.id, "UPSERT_ITEM", operatorUserId, null, null, psku);
        }
        persistOrderSiteCodesIfChanged(order, nextOrderSiteCodes, operatorUserId);
    }

    private Map<Long, Set<String>> existingSitesByItemId(Long orderId) {
        List<PurchaseOrderItemSiteRecord> sites = mapper.listItemSitesByOrder(orderId);
        if (sites == null || sites.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Set<String>> result = new LinkedHashMap<>();
        for (PurchaseOrderItemSiteRecord site : sites) {
            if (site == null || site.purchaseOrderItemId == null) {
                continue;
            }
            String siteCode = normalizeSiteCode(site.siteCode);
            if (!StringUtils.hasText(siteCode)) {
                continue;
            }
            result.computeIfAbsent(site.purchaseOrderItemId, ignored -> new LinkedHashSet<>()).add(siteCode);
        }
        return result;
    }

    private void ensureNoDuplicateSitesInAllocations(String psku, List<SiteTransportQuantity> allocations) {
        Set<String> seenSites = new LinkedHashSet<>();
        for (SiteTransportQuantity allocation : allocations) {
            String siteCode = normalizeSiteCode(allocation == null ? null : allocation.siteCode);
            if (!StringUtils.hasText(siteCode)) {
                continue;
            }
            if (!seenSites.add(siteCode)) {
                throw new IllegalArgumentException(psku + " 在站点 " + siteCode
                        + " 重复填写，不能重复添加相同商品相同站点。");
            }
        }
    }

    private void collectItemInternal(
            PurchaseOrderRecord order,
            PurchaseOrderItemRecord item,
            Long operatorUserId
    ) {
        String status = dbStatus(item);
        if (item.sourceCollectionId != null && ("QUEUED".equals(status) || "RUNNING".equals(status))) {
            return;
        }
        if (!StringUtils.hasText(item.imageUrlCache)) {
            mapper.markItemCollectionFailed(
                    item.id,
                    "missing_product_image",
                    "该商品缺少主图，不能发起 1688 图搜采集。",
                    operatorUserId
            );
            log(order.id, item.id, "COLLECT_SKIPPED", operatorUserId, status, "FAILED", "missing_product_image");
            return;
        }

        mapper.supersedeCurrentAli1688TasksByItem(item.id, operatorUserId);
        mapper.supersedeCurrentLinksByItem(item.id, operatorUserId);
        ProductSelectionSourceCollectionRow source = createHiddenSourceCollection(order, item, operatorUserId);
        Ali1688CollectionView aliView = ali1688CollectionService.ensureTaskForSourceCollection(source, operatorUserId);
        Long linkId = mapper.nextCollectionLinkId();
        String dbStatus = toDbCollectionStatus(aliView.status);
        mapper.insertCollectionLink(
                linkId,
                order.id,
                item.id,
                order.ownerUserId,
                order.logicalStoreId,
                source.getId(),
                parseNullableLong(aliView.taskId),
                "po-item:" + item.id,
                dbStatus,
                nonNull(aliView.progressPercent),
                nonNull(aliView.candidateCount),
                nonNull(aliView.recommendedCount),
                aliView.failureCode,
                aliView.failureMessage,
                writeJson(Map.of(
                        "purchaseOrderId", order.id,
                        "purchaseOrderItemId", item.id,
                        "partnerSku", item.partnerSku
                )),
                null,
                operatorUserId
        );
        mapper.updateItemCollection(
                item.id,
                linkId,
                dbStatus,
                nonNull(aliView.progressPercent),
                nonNull(aliView.candidateCount),
                nonNull(aliView.recommendedCount),
                aliView.failureCode,
                aliView.failureMessage,
                isFinished(dbStatus) ? 1 : 0,
                operatorUserId
        );
        log(order.id, item.id, "START_1688_COLLECTION", operatorUserId, status, dbStatus, aliView.taskId);
    }

    private ProductSelectionSourceCollectionRow createHiddenSourceCollection(
            PurchaseOrderRecord order,
            PurchaseOrderItemRecord item,
            Long operatorUserId
    ) {
        Long sourceId = productSelectionMapper.nextSourceCollectionId();
        ProductSelectionSourceCollectionRow row = new ProductSelectionSourceCollectionRow();
        row.setId(sourceId);
        row.setOwnerUserId(order.ownerUserId);
        row.setLogicalStoreId(order.logicalStoreId);
        row.setCollectionNo("POI-" + sourceId);
        row.setSourceType(HIDDEN_SOURCE_TYPE);
        row.setSourcePlatform("StoreArchive");
        row.setSourceTitle(defaultText(item.titleCache, item.partnerSku));
        row.setSourceTitleCn(defaultText(item.titleCache, item.partnerSku));
        String imageUrl = NoonImageUrlNormalizer.normalize(item.imageUrlCache);
        row.setSourceImageUrl(imageUrl);
        row.setImageUrlsJson(writeStringList(Collections.singletonList(imageUrl)));
        ProductArchiveRecord product = mapper.selectProductArchiveByVariant(item.logicalStoreId, item.productVariantId);
        List<String> specHints = buildSpecHints(product, item);
        row.setSpecHintsJson(writeStringList(specHints));
        row.setSpecAttributeCount(specHints.size());
        row.setSelectedText(buildSelectedText(item, specHints));
        row.setNotes("purchaseOrder=" + order.orderNo + ";itemId=" + item.id);
        row.setStatus("success");
        row.setCreatedBy(operatorUserId);
        row.setUpdatedBy(operatorUserId);
        productSelectionMapper.insertSourceCollection(row);
        ProductSelectionSourceCollectionRow inserted = productSelectionMapper.selectSourceCollectionById(sourceId);
        return inserted == null ? row : inserted;
    }

    private PurchaseOrderView toOrderView(PurchaseOrderRecord order) {
        PurchaseOrderView view = new PurchaseOrderView();
        view.id = String.valueOf(order.id);
        view.orderNo = order.orderNo;
        view.title = order.title;
        view.storeName = defaultText(order.projectNameCache, order.projectCodeCache);
        view.storeCode = order.anchorStoreCodeCache;
        view.ownerName = "";
        view.status = toViewOrderStatus(order.status);
        view.createdAt = order.createdAt;
        view.updatedAt = order.updatedAt;
        view.remark = order.remark;
        view.siteCodes = readStringList(order.siteCodesJson);

        Map<Long, List<PurchaseOrderItemSiteRecord>> sitesByItem = mapper.listItemSitesByOrder(order.id).stream()
                .collect(Collectors.groupingBy(row -> row.purchaseOrderItemId, LinkedHashMap::new, Collectors.toList()));
        for (PurchaseOrderItemRecord item : mapper.listItemsByOrder(order.id)) {
            view.items.add(toItemView(item, sitesByItem.getOrDefault(item.id, Collections.emptyList())));
        }
        return view;
    }

    private PurchaseOrderItemView toItemView(
            PurchaseOrderItemRecord item,
            List<PurchaseOrderItemSiteRecord> sites
    ) {
        PurchaseOrderItemView view = new PurchaseOrderItemView();
        view.id = String.valueOf(item.id);
        view.sourceCollectionId = item.sourceCollectionId == null ? null : String.valueOf(item.sourceCollectionId);
        view.sourceCollectionNo = item.sourceCollectionNo;
        view.sourcePlatform = "店铺";
        view.sourceTitle = defaultText(item.titleCache, item.partnerSku);
        view.sourceTitleCn = defaultText(item.titleCache, item.partnerSku);
        view.sourceImageUrl = NoonImageUrlNormalizer.normalize(item.imageUrlCache);
        view.variantId = String.valueOf(item.productVariantId);
        view.skuParent = item.skuParent;
        view.partnerSku = item.partnerSku;
        view.productFulltype = item.productFulltypeCache;
        view.productTitle = defaultText(item.titleCache, item.partnerSku);
        view.productImageUrl = NoonImageUrlNormalizer.normalize(item.imageUrlCache);
        view.sourcingSpec = item.sourcingSpecText;
        view.sourcingSize = item.sourcingSizeText;
        view.sourcingColor = item.sourcingColorText;
        view.fulfillmentType = normalizeFulfillmentType(item.fulfillmentType);
        view.fulfillmentTypeLabel = fulfillmentTypeLabel(view.fulfillmentType);
        view.fulfillmentSourceName = trimToNull(item.fulfillmentSourceName);
        view.totalQuantity = nonNull(item.totalQuantity);
        view.collectionStatus = toViewItemStatus(dbStatus(item));
        view.progress = nonNull(firstNonNull(item.aliProgressPercent, item.progressPercent));
        view.currentTaskNo = item.aliTaskNo;
        view.candidateCount = nonNull(firstNonNull(item.aliCandidateCount, item.candidateCount));
        view.top5Count = nonNull(firstNonNull(item.aliRecommendedCount, item.recommendedCount));
        view.failureMessage = firstText(item.aliFailureMessage, item.failureMessage);
        view.lastCollectedAt = firstText(item.aliFinishedAt, item.lastCollectedAt);
        for (PurchaseOrderItemSiteRecord site : sites) {
            view.allocations.add(toSiteAllocationView(site));
        }
        return view;
    }

    private SiteAllocationView toSiteAllocationView(PurchaseOrderItemSiteRecord site) {
        SiteAllocationView view = new SiteAllocationView();
        view.site = site.siteCode;
        view.siteName = siteName(site.siteCode);
        view.siteId = site.siteId;
        view.pskuCode = site.pskuCode;
        view.transportMode = normalizeTransportMode(site.transportMode);
        view.transportModeLabel = transportModeLabel(view.transportMode);
        view.quantity = nonNull(site.quantity);
        view.enabled = "ACTIVE".equals(site.status);
        return view;
    }

    private PurchaseOrderLogisticsPlanLineView toLogisticsPlanLine(
            PurchaseOrderItemRecord item,
            ProductArchiveRecord product,
            List<PurchaseOrderItemSiteRecord> sites
    ) {
        PurchaseOrderLogisticsPlanLineView view = new PurchaseOrderLogisticsPlanLineView();
        view.itemId = String.valueOf(item.id);
        view.partnerSku = item.partnerSku;
        view.productTitle = defaultText(firstText(product == null ? null : product.title, item.titleCache), item.partnerSku);
        view.productImageUrl = NoonImageUrlNormalizer.normalize(firstText(product == null ? null : product.imageUrl, item.imageUrlCache));
        int totalQuantity = nonNull(item.totalQuantity);
        if (totalQuantity <= 0) {
            totalQuantity = sites.stream().mapToInt(site -> nonNull(site.quantity)).sum();
        }
        view.totalQuantity = totalQuantity;
        view.productDimensionsText = dimensionsText(
                product == null ? null : product.productLengthCm,
                product == null ? null : product.productWidthCm,
                product == null ? null : product.productHeightCm,
                "cm"
        );
        view.productWeightText = formatMeasure(product == null ? null : product.productWeightG, "g");
        view.cartonDimensionsText = dimensionsText(
                product == null ? null : product.cartonLengthCm,
                product == null ? null : product.cartonWidthCm,
                product == null ? null : product.cartonHeightCm,
                "cm"
        );
        view.cartonWeightText = formatMeasure(product == null ? null : product.cartonWeightKg, "kg");
        view.cartonQuantity = product == null ? null : product.cartonQuantity;
        view.looseVolumeCbm = looseVolumeCbm(product, totalQuantity);
        view.looseVolumeCbmText = formatCbm(view.looseVolumeCbm);
        view.seaQuantity = sites.stream()
                .filter(site -> TRANSPORT_SEA.equals(normalizeTransportMode(site.transportMode)))
                .mapToInt(site -> nonNull(site.quantity))
                .sum();
        view.seaLooseVolumeCbm = looseVolumeCbm(product, view.seaQuantity);
        view.seaLooseVolumeCbmText = formatCbm(view.seaLooseVolumeCbm);
        view.airQuantity = sites.stream()
                .filter(site -> TRANSPORT_AIR.equals(normalizeTransportMode(site.transportMode)))
                .mapToInt(site -> nonNull(site.quantity))
                .sum();
        view.airActualWeightKg = actualWeightKg(product, view.airQuantity);
        view.airActualWeightKgText = formatKg(view.airActualWeightKg);
        view.airLooseVolumeCbm = looseVolumeCbm(product, view.airQuantity);
        view.airLooseVolumeCbmText = formatCbm(view.airLooseVolumeCbm);
        view.specSourceType = product == null ? null : product.specSourceType;
        for (PurchaseOrderItemSiteRecord site : sites) {
            view.allocations.add(toSiteAllocationView(site));
        }
        appendLogisticsMissingFields(view.missingFields, item, product);
        return view;
    }

    private void appendLogisticsMissingFields(
            List<String> target,
            PurchaseOrderItemRecord item,
            ProductArchiveRecord product
    ) {
        if (product == null) {
            target.add("商品规格快照缺失");
            return;
        }
        if (!allPresent(product.productLengthCm, product.productWidthCm, product.productHeightCm)) {
            target.add("商品尺寸缺失");
        }
        if (product.productWeightG == null) {
            target.add("商品重量缺失");
        }
        if (!allPresent(product.cartonLengthCm, product.cartonWidthCm, product.cartonHeightCm)) {
            target.add("箱规尺寸缺失");
        }
        if (product.cartonWeightKg == null) {
            target.add("箱重缺失");
        }
        if (product.cartonQuantity == null || product.cartonQuantity <= 0) {
            target.add("装箱数缺失");
        }
    }

    private boolean hasMissingLogisticsSpecs(PurchaseOrderLogisticsPlanView view) {
        if (view == null || view.lines == null) {
            return false;
        }
        return view.lines.stream().anyMatch(line -> line.missingFields != null && !line.missingFields.isEmpty());
    }

    private boolean hasMissingSeaLooseVolumeInputs(PurchaseOrderLogisticsPlanView view) {
        if (view == null || view.lines == null) {
            return false;
        }
        return view.lines.stream()
                .filter(line -> nonNull(line.seaQuantity) > 0)
                .anyMatch(line -> line.seaLooseVolumeCbm == null);
    }

    private boolean hasMissingAirChargeableWeightInputs(PurchaseOrderLogisticsPlanView view) {
        if (view == null || view.lines == null) {
            return false;
        }
        return view.lines.stream()
                .filter(line -> nonNull(line.airQuantity) > 0)
                .anyMatch(line -> line.airActualWeightKg == null || line.airLooseVolumeCbm == null);
    }

    private boolean hasMissingCartonSpecs(PurchaseOrderLogisticsPlanView view) {
        if (view == null || view.lines == null) {
            return false;
        }
        return view.lines.stream()
                .filter(line -> nonNull(line.seaQuantity) > 0)
                .anyMatch(line -> line.missingFields != null && line.missingFields.stream()
                        .anyMatch(field -> field != null && field.contains("箱")));
    }

    private BigDecimal totalSeaLooseVolumeCbm(PurchaseOrderLogisticsPlanView view) {
        if (view == null || view.lines == null) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        boolean hasVolume = false;
        for (PurchaseOrderLogisticsPlanLineView line : view.lines) {
            if (line == null || line.seaLooseVolumeCbm == null) {
                continue;
            }
            total = total.add(line.seaLooseVolumeCbm);
            hasVolume = true;
        }
        return hasVolume ? total : null;
    }

    private BigDecimal totalAirChargeableWeightKg(PurchaseOrderLogisticsPlanView view, BigDecimal volumeDivisor) {
        BigDecimal actualWeightKg = totalAirActualWeightKg(view);
        BigDecimal volumeWeightKg = totalAirVolumeWeightKg(view, volumeDivisor);
        if (actualWeightKg == null || volumeWeightKg == null) {
            return null;
        }
        return actualWeightKg.max(volumeWeightKg);
    }

    private BigDecimal totalAirActualWeightKg(PurchaseOrderLogisticsPlanView view) {
        if (view == null || view.lines == null) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        boolean hasWeight = false;
        for (PurchaseOrderLogisticsPlanLineView line : view.lines) {
            if (line == null || nonNull(line.airQuantity) <= 0 || line.airActualWeightKg == null) {
                continue;
            }
            total = total.add(line.airActualWeightKg);
            hasWeight = true;
        }
        return hasWeight ? total : null;
    }

    private BigDecimal totalAirVolumeWeightKg(PurchaseOrderLogisticsPlanView view, BigDecimal volumeDivisor) {
        BigDecimal totalVolumeCbm = totalAirLooseVolumeCbm(view);
        BigDecimal divisor = validVolumeDivisor(volumeDivisor);
        if (totalVolumeCbm == null || divisor == null) {
            return null;
        }
        return totalVolumeCbm
                .multiply(CUBIC_CM_PER_CBM)
                .divide(divisor, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal totalAirLooseVolumeCbm(PurchaseOrderLogisticsPlanView view) {
        if (view == null || view.lines == null) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        boolean hasVolume = false;
        for (PurchaseOrderLogisticsPlanLineView line : view.lines) {
            if (line == null || nonNull(line.airQuantity) <= 0 || line.airLooseVolumeCbm == null) {
                continue;
            }
            total = total.add(line.airLooseVolumeCbm);
            hasVolume = true;
        }
        return hasVolume ? total : null;
    }

    private BigDecimal looseVolumeCbm(ProductArchiveRecord product, int quantity) {
        if (product == null || quantity <= 0) {
            return null;
        }
        if (!allPresent(product.productLengthCm, product.productWidthCm, product.productHeightCm)) {
            return null;
        }
        return product.productLengthCm
                .multiply(product.productWidthCm)
                .multiply(product.productHeightCm)
                .multiply(BigDecimal.valueOf(quantity))
                .divide(CUBIC_CM_PER_CBM, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal actualWeightKg(ProductArchiveRecord product, int quantity) {
        if (product == null || quantity <= 0 || product.productWeightG == null) {
            return null;
        }
        return product.productWeightG
                .multiply(BigDecimal.valueOf(quantity))
                .divide(GRAMS_PER_KG, 8, RoundingMode.HALF_UP);
    }

    private void appendQuoteCountryNames(Set<String> target, String siteCode) {
        String normalized = normalizeSiteCode(siteCode);
        switch (normalized) {
            case "SA":
                target.add("沙特");
                target.add("SA");
                target.add("Saudi Arabia");
                target.add("Saudi");
                return;
            case "AE":
                target.add("阿联酋");
                target.add("UAE");
                target.add("AE");
                target.add("United Arab Emirates");
                return;
            default:
                if (StringUtils.hasText(normalized)) {
                    target.add(normalized);
                }
        }
    }

    private String seaPriceSummary(ForwarderSeaRecommendationRecord candidate) {
        if (candidate == null) {
            return "需询价";
        }
        List<String> parts = new ArrayList<>();
        String currency = defaultText(candidate.currency, "");
        if (candidate.cbmMinUnitPrice != null) {
            parts.add(formatPricePart(currency, candidate.cbmMinUnitPrice, "CBM"));
        }
        if (candidate.kgMinUnitPrice != null) {
            parts.add(formatPricePart(currency, candidate.kgMinUnitPrice, "KG"));
        }
        if (!parts.isEmpty()) {
            return String.join("；", parts);
        }
        if (candidate.minUnitPrice == null) {
            return "需询价";
        }
        String billingUnit = defaultText(candidate.billingUnit, "单位");
        return formatPricePart(currency, candidate.minUnitPrice, billingUnit);
    }

    private String seaPriceSummary(ForwarderRouteRecommendationRecord candidate) {
        if (candidate == null) {
            return "需询价";
        }
        List<String> parts = new ArrayList<>();
        String currency = defaultText(candidate.currency, "");
        if (candidate.cbmMinUnitPrice != null) {
            parts.add(formatPricePart(currency, candidate.cbmMinUnitPrice, "CBM"));
        }
        if (candidate.kgMinUnitPrice != null) {
            parts.add(formatPricePart(currency, candidate.kgMinUnitPrice, "KG"));
        }
        if (!parts.isEmpty()) {
            return String.join("；", parts);
        }
        if (candidate.minUnitPrice == null) {
            return "需询价";
        }
        String billingUnit = defaultText(candidate.billingUnit, "单位");
        return formatPricePart(currency, candidate.minUnitPrice, billingUnit);
    }

    private BigDecimal preferredSeaUnitPrice(ForwarderSeaRecommendationRecord candidate) {
        if (candidate == null) {
            return null;
        }
        if (candidate.cbmMinUnitPrice != null) {
            return candidate.cbmMinUnitPrice;
        }
        return candidate.minUnitPrice;
    }

    private BigDecimal preferredSeaUnitPrice(ForwarderRouteRecommendationRecord candidate) {
        if (candidate == null) {
            return null;
        }
        if (candidate.cbmMinUnitPrice != null) {
            return candidate.cbmMinUnitPrice;
        }
        return candidate.minUnitPrice;
    }

    private BigDecimal preferredSeaCbmUnitPrice(ForwarderSeaRecommendationRecord candidate) {
        if (candidate == null) {
            return null;
        }
        if (candidate.cbmMinUnitPrice != null) {
            return candidate.cbmMinUnitPrice;
        }
        if ("CBM".equalsIgnoreCase(defaultText(candidate.billingUnit, ""))) {
            return candidate.minUnitPrice;
        }
        return null;
    }

    private BigDecimal preferredSeaCbmUnitPrice(ForwarderRouteRecommendationRecord candidate) {
        if (candidate == null) {
            return null;
        }
        if (candidate.cbmMinUnitPrice != null) {
            return candidate.cbmMinUnitPrice;
        }
        if ("CBM".equalsIgnoreCase(defaultText(candidate.billingUnit, ""))) {
            return candidate.minUnitPrice;
        }
        return null;
    }

    private String estimatedSeaCostText(ForwarderSeaRecommendationRecord candidate, BigDecimal estimatedSeaVolumeCbm) {
        BigDecimal unitPrice = preferredSeaCbmUnitPrice(candidate);
        if (unitPrice == null || estimatedSeaVolumeCbm == null || estimatedSeaVolumeCbm.signum() <= 0) {
            return null;
        }
        BigDecimal amount = unitPrice.multiply(estimatedSeaVolumeCbm).setScale(2, RoundingMode.HALF_UP);
        String currency = defaultText(candidate.currency, "");
        String prefix = StringUtils.hasText(currency) ? currency + " " : "";
        return prefix + formatMoney(amount) + "（按散货 " + formatCbm(estimatedSeaVolumeCbm) + " 估算）";
    }

    private String estimatedSeaCostText(ForwarderRouteRecommendationRecord candidate, BigDecimal estimatedSeaVolumeCbm) {
        BigDecimal unitPrice = preferredSeaCbmUnitPrice(candidate);
        if (unitPrice == null || estimatedSeaVolumeCbm == null || estimatedSeaVolumeCbm.signum() <= 0) {
            return null;
        }
        BigDecimal amount = unitPrice.multiply(estimatedSeaVolumeCbm).setScale(2, RoundingMode.HALF_UP);
        String currency = defaultText(candidate.currency, "");
        String prefix = StringUtils.hasText(currency) ? currency + " " : "";
        return prefix + formatMoney(amount) + "（按散货 " + formatCbm(estimatedSeaVolumeCbm) + " 估算）";
    }

    private BigDecimal preferredAirUnitPrice(ForwarderSeaRecommendationRecord candidate) {
        if (candidate == null) {
            return null;
        }
        if (candidate.kgMinUnitPrice != null) {
            return candidate.kgMinUnitPrice;
        }
        return candidate.minUnitPrice;
    }

    private BigDecimal preferredAirUnitPrice(ForwarderRouteRecommendationRecord candidate) {
        if (candidate == null) {
            return null;
        }
        if (candidate.kgMinUnitPrice != null) {
            return candidate.kgMinUnitPrice;
        }
        return candidate.minUnitPrice;
    }

    private BigDecimal preferredAirKgUnitPrice(ForwarderSeaRecommendationRecord candidate) {
        if (candidate == null) {
            return null;
        }
        if (candidate.kgMinUnitPrice != null) {
            return candidate.kgMinUnitPrice;
        }
        if ("KG".equalsIgnoreCase(defaultText(candidate.billingUnit, ""))) {
            return candidate.minUnitPrice;
        }
        return null;
    }

    private BigDecimal preferredAirKgUnitPrice(ForwarderRouteRecommendationRecord candidate) {
        if (candidate == null) {
            return null;
        }
        if (candidate.kgMinUnitPrice != null) {
            return candidate.kgMinUnitPrice;
        }
        if ("KG".equalsIgnoreCase(defaultText(candidate.billingUnit, ""))) {
            return candidate.minUnitPrice;
        }
        return null;
    }

    private String estimatedAirCostText(
            ForwarderSeaRecommendationRecord candidate,
            PurchaseOrderLogisticsPlanView plan
    ) {
        BigDecimal unitPrice = preferredAirKgUnitPrice(candidate);
        BigDecimal chargeableWeightKg = totalAirChargeableWeightKg(plan, candidate == null ? null : candidate.volumeDivisor);
        if (unitPrice == null || chargeableWeightKg == null || chargeableWeightKg.signum() <= 0) {
            return null;
        }
        BigDecimal amount = unitPrice.multiply(chargeableWeightKg).setScale(2, RoundingMode.HALF_UP);
        String currency = defaultText(candidate.currency, "");
        String prefix = StringUtils.hasText(currency) ? currency + " " : "";
        return prefix + formatMoney(amount) + "（按空运计费重 " + formatKg(chargeableWeightKg) + " 估算）";
    }

    private String estimatedAirCostText(
            ForwarderRouteRecommendationRecord candidate,
            PurchaseOrderLogisticsPlanView plan
    ) {
        BigDecimal unitPrice = preferredAirKgUnitPrice(candidate);
        BigDecimal chargeableWeightKg = totalAirChargeableWeightKg(plan, candidate == null ? null : candidate.volumeDivisor);
        if (unitPrice == null || chargeableWeightKg == null || chargeableWeightKg.signum() <= 0) {
            return null;
        }
        BigDecimal amount = unitPrice.multiply(chargeableWeightKg).setScale(2, RoundingMode.HALF_UP);
        String currency = defaultText(candidate.currency, "");
        String prefix = StringUtils.hasText(currency) ? currency + " " : "";
        return prefix + formatMoney(amount) + "（按空运计费重 " + formatKg(chargeableWeightKg) + " 估算）";
    }

    private String costSummaryText(PurchaseOrderLogisticsRecommendationView recommendation) {
        if (!StringUtils.hasText(recommendation.estimatedTotalCostText)) {
            return null;
        }
        if (StringUtils.hasText(recommendation.recurringCostText)) {
            return recommendation.estimatedTotalCostText + " + " + recommendation.recurringCostText + "仓储";
        }
        return recommendation.estimatedTotalCostText;
    }

    private BigDecimal validVolumeDivisor(BigDecimal volumeDivisor) {
        if (volumeDivisor != null && volumeDivisor.signum() > 0) {
            return volumeDivisor;
        }
        return DEFAULT_AIR_VOLUME_DIVISOR;
    }

    private String formatPricePart(String currency, BigDecimal value, String billingUnit) {
        String prefix = StringUtils.hasText(currency) ? currency + " " : "";
        return prefix + formatDecimal(value) + "/" + defaultText(billingUnit, "单位") + " 起";
    }

    private String summarizeCsv(String csv, int limit) {
        if (!StringUtils.hasText(csv)) {
            return null;
        }
        List<String> values = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String raw : csv.split(",")) {
            String value = trim(raw);
            if (StringUtils.hasText(value) && seen.add(value)) {
                values.add(value);
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        int safeLimit = Math.max(limit, 1);
        if (values.size() <= safeLimit) {
            return String.join("、", values);
        }
        return String.join("、", values.subList(0, safeLimit)) + " 等 " + values.size() + " 类";
    }

    private List<Ali1688CollectionView.SpecValue> purchaseOrderItemSpecs(PurchaseOrderItemRecord item) {
        List<Ali1688CollectionView.SpecValue> specs = new ArrayList<>();
        addSpec(specs, "规格", item.sourcingSpecText);
        addSpec(specs, "尺寸", item.sourcingSizeText);
        addSpec(specs, "颜色", item.sourcingColorText);
        return specs;
    }

    private List<Ali1688CollectionView.SpecValue> mergeSourceSpecs(
            List<Ali1688CollectionView.SpecValue> preferred,
            List<Ali1688CollectionView.SpecValue> fallback
    ) {
        List<Ali1688CollectionView.SpecValue> merged = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        appendSpecs(merged, seen, preferred);
        appendSpecs(merged, seen, fallback);
        return merged;
    }

    private void appendSpecs(
            List<Ali1688CollectionView.SpecValue> target,
            Set<String> seen,
            List<Ali1688CollectionView.SpecValue> specs
    ) {
        if (specs == null) {
            return;
        }
        for (Ali1688CollectionView.SpecValue spec : specs) {
            if (spec == null || !StringUtils.hasText(spec.name) || !StringUtils.hasText(spec.value)) {
                continue;
            }
            String key = spec.name.trim() + "\n" + spec.value.trim();
            if (seen.add(key)) {
                target.add(new Ali1688CollectionView.SpecValue(spec.name.trim(), spec.value.trim()));
            }
        }
    }

    private void addSpec(List<Ali1688CollectionView.SpecValue> specs, String name, String value) {
        String trimmed = trim(value);
        if (StringUtils.hasText(trimmed)) {
            specs.add(new Ali1688CollectionView.SpecValue(name, trimmed));
        }
    }

    private ProductOptionView toProductOptionView(ProductArchiveRecord record) {
        ProductOptionView view = new ProductOptionView();
        view.variantId = record.productVariantId == null ? null : String.valueOf(record.productVariantId);
        view.skuParent = record.skuParent;
        view.partnerSku = record.partnerSku;
        view.productTitle = defaultText(record.title, record.partnerSku);
        view.productImageUrl = NoonImageUrlNormalizer.normalize(record.imageUrl);
        view.availableSiteCodes = splitCsv(record.availableSiteCodesCsv);
        return view;
    }

    private List<String> buildSpecHints(ProductArchiveRecord product, PurchaseOrderItemRecord item) {
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        String partnerSku = firstText(product == null ? null : product.partnerSku, item.partnerSku);
        String childSku = firstText(product == null ? null : product.childSku, item.childSku);
        addHint(hints, "PSKU", partnerSku);
        addHint(hints, "Noon SKU", childSku);
        ProcurementPurchaseOrderSourcingRequirement.of(
                item.sourcingSpecText,
                item.sourcingSizeText,
                item.sourcingColorText
        ).toSpecHints().forEach(hints::add);
        addHint(hints, "Size", product == null ? null : product.sizeEn);
        addHint(hints, "Size AR", product == null ? null : product.sizeAr);
        addDimensionHint(
                hints,
                "Product dimensions",
                product == null ? null : product.productLengthCm,
                product == null ? null : product.productWidthCm,
                product == null ? null : product.productHeightCm,
                "cm"
        );
        addHint(hints, "Product weight", formatMeasure(product == null ? null : product.productWeightG, "g"));
        addDimensionHint(
                hints,
                "Carton dimensions",
                product == null ? null : product.cartonLengthCm,
                product == null ? null : product.cartonWidthCm,
                product == null ? null : product.cartonHeightCm,
                "cm"
        );
        addHint(hints, "Carton weight", formatMeasure(product == null ? null : product.cartonWeightKg, "kg"));
        addHint(hints, "Carton quantity", product == null || product.cartonQuantity == null ? null : String.valueOf(product.cartonQuantity));
        addHint(hints, "Spec source", product == null ? null : product.specSourceType);
        return new ArrayList<>(hints);
    }

    private String buildSelectedText(PurchaseOrderItemRecord item, List<String> specHints) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(item.partnerSku)) {
            parts.add(item.partnerSku.trim());
        }
        specHints.stream()
                .filter(text -> text.startsWith("规格:")
                        || text.startsWith("尺寸:")
                        || text.startsWith("颜色:")
                        || text.startsWith("Size:")
                        || text.startsWith("Product dimensions:")
                        || text.startsWith("Carton dimensions:"))
                .forEach(parts::add);
        return String.join("; ", parts);
    }

    private void addDimensionHint(
            LinkedHashSet<String> hints,
            String label,
            BigDecimal length,
            BigDecimal width,
            BigDecimal height,
            String unit
    ) {
        if (length == null && width == null && height == null) {
            return;
        }
        String value = formatDecimal(length) + " x " + formatDecimal(width) + " x " + formatDecimal(height) + " " + unit;
        addHint(hints, label, value);
    }

    private String dimensionsText(BigDecimal length, BigDecimal width, BigDecimal height, String unit) {
        if (length == null && width == null && height == null) {
            return null;
        }
        return formatDecimal(length) + " x " + formatDecimal(width) + " x " + formatDecimal(height) + " " + unit;
    }

    private boolean allPresent(BigDecimal... values) {
        if (values == null || values.length == 0) {
            return false;
        }
        for (BigDecimal value : values) {
            if (value == null) {
                return false;
            }
        }
        return true;
    }

    private void addHint(LinkedHashSet<String> hints, String label, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        String normalized = value.trim();
        if (!StringUtils.hasText(normalized) || "null".equalsIgnoreCase(normalized)) {
            return;
        }
        hints.add(label + ": " + normalized);
    }

    private String formatMeasure(BigDecimal value, String unit) {
        return value == null ? null : formatDecimal(value) + " " + unit;
    }

    private String formatCbm(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " CBM";
    }

    private String formatKg(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " KG";
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatDecimal(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private ProductArchiveRecord resolveProduct(Long logicalStoreId, String psku) {
        List<ProductArchiveRecord> matches = mapper.listProductArchiveMatches(logicalStoreId, psku);
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("PSKU 不属于当前店铺商品档案，不能加入采购单。");
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("PSKU 命中多个商品档案，请用更精确的 PSKU。");
        }
        return matches.get(0);
    }

    private PurchaseOrderRecord requireOrder(Long orderId) {
        PurchaseOrderRecord order = mapper.selectOrderById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("采购单不存在或已删除。");
        }
        return order;
    }

    private PurchaseOrderRecord requireOrderAccess(BusinessAccessContext access, Long orderId) {
        return requireOrderAccess(access, requireOrder(orderId));
    }

    private PurchaseOrderRecord requireOrderAccess(BusinessAccessContext access, PurchaseOrderRecord order) {
        if (access == null || !access.canAccessStore(order.anchorStoreCodeCache)) {
            throw new IllegalArgumentException("当前账号不能操作该采购单。");
        }
        return order;
    }

    private StoreScopeRecord requireStoreScope(BusinessAccessContext access, String requestedStoreCode) {
        if (access == null) {
            throw new IllegalArgumentException("缺少业务访问上下文。");
        }
        String storeCode = StringUtils.hasText(requestedStoreCode)
                ? requestedStoreCode.trim()
                : access.getStoreCodes().stream().findFirst().orElse(null);
        if (!StringUtils.hasText(storeCode)) {
            throw new IllegalArgumentException("请选择店铺。");
        }
        if (!access.canAccessStore(storeCode)) {
            throw new IllegalArgumentException("当前账号不能操作该店铺。");
        }
        Long ownerUserId = access.resolveOwnerUserIdForStore(storeCode);
        if (ownerUserId == null) {
            ownerUserId = access.getBusinessOwnerUserId();
        }
        StoreScopeRecord scope = mapper.selectStoreScope(ownerUserId, storeCode);
        if (scope == null || scope.logicalStoreId == null) {
            throw new IllegalArgumentException("该店铺尚未初始化商品档案，不能创建采购单。");
        }
        return scope;
    }

    private List<String> normalizeSiteCodes(List<String> requestedSiteCodes, Long logicalStoreId) {
        Map<String, StoreSiteRecord> sites = storeSitesByCode(logicalStoreId);
        if (sites.isEmpty()) {
            throw new IllegalArgumentException("当前店铺没有可用站点。");
        }
        List<String> source = requestedSiteCodes == null || requestedSiteCodes.isEmpty()
                ? new ArrayList<>(sites.keySet())
                : requestedSiteCodes;
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String siteCode : source) {
            String normalizedSite = normalizeSiteCode(siteCode);
            if (!sites.containsKey(normalizedSite)) {
                throw new IllegalArgumentException("站点 " + normalizedSite + " 不属于当前店铺。");
            }
            normalized.add(normalizedSite);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("请选择至少一个站点。");
        }
        return new ArrayList<>(normalized);
    }

    private Map<String, StoreSiteRecord> storeSitesByCode(Long logicalStoreId) {
        return mapper.listStoreSites(logicalStoreId).stream()
                .collect(Collectors.toMap(
                        site -> normalizeSiteCode(site.siteCode),
                        site -> site,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private List<String> siteCodesFromItems(List<ItemCommand> itemCommands) {
        LinkedHashSet<String> siteCodes = new LinkedHashSet<>();
        if (itemCommands == null) {
            return new ArrayList<>();
        }
        for (ItemCommand command : itemCommands) {
            if (command == null) {
                continue;
            }
            if (command.siteQuantities != null) {
                for (SiteQuantityCommand siteQuantity : command.siteQuantities) {
                    String siteCode = normalizeSiteCode(siteQuantity == null ? null : siteQuantity.siteCode);
                    if (StringUtils.hasText(siteCode)) {
                        siteCodes.add(siteCode);
                    }
                }
            }
            String siteCode = normalizeSiteCode(command.site);
            if (StringUtils.hasText(siteCode)) {
                siteCodes.add(siteCode);
            }
        }
        return new ArrayList<>(siteCodes);
    }

    private void persistOrderSiteCodesIfChanged(
            PurchaseOrderRecord order,
            LinkedHashSet<String> nextOrderSiteCodes,
            Long operatorUserId
    ) {
        List<String> current = readStringList(order.siteCodesJson);
        List<String> next = new ArrayList<>(nextOrderSiteCodes);
        if (!current.equals(next)) {
            mapper.updateOrderSiteCodes(order.id, writeStringList(next), operatorUserId);
            order.siteCodesJson = writeStringList(next);
        }
    }

    private List<SiteTransportQuantity> normalizeSiteTransportQuantities(ItemCommand command) {
        Map<String, SiteTransportQuantity> result = new LinkedHashMap<>();
        if (command != null && command.siteQuantities != null) {
            for (SiteQuantityCommand siteQuantity : command.siteQuantities) {
                addSiteTransportQuantity(
                        result,
                        siteQuantity == null ? null : siteQuantity.siteCode,
                        siteQuantity == null ? null : siteQuantity.transportMode,
                        siteQuantity == null ? null : siteQuantity.quantity
                );
            }
        }
        if (result.isEmpty() && command != null) {
            addSiteTransportQuantity(result, command.site, command.transportMode, command.quantity);
        }
        return new ArrayList<>(result.values());
    }

    private void addSiteTransportQuantity(
            Map<String, SiteTransportQuantity> target,
            String rawSiteCode,
            String rawTransportMode,
            Integer quantity
    ) {
        String siteCode = normalizeSiteCode(rawSiteCode);
        if (!StringUtils.hasText(siteCode) || quantity == null || quantity <= 0) {
            return;
        }
        String transportMode = normalizeTransportMode(rawTransportMode);
        String key = siteCode + "|" + transportMode;
        SiteTransportQuantity current = target.get(key);
        if (current == null) {
            target.put(key, new SiteTransportQuantity(siteCode, transportMode, quantity));
            return;
        }
        current.quantity += quantity;
    }

    private String normalizeTransportMode(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
        switch (normalized) {
            case "AIR":
            case "空":
            case "空运":
                return TRANSPORT_AIR;
            case "EXPRESS":
            case "快递":
                return TRANSPORT_EXPRESS;
            case "SEA":
            case "海":
            case "海运":
                return TRANSPORT_SEA;
            case "UNSPECIFIED":
            case "未分配":
            case "未指定":
                return TRANSPORT_UNSPECIFIED;
            default:
                return TRANSPORT_UNSPECIFIED;
        }
    }

    private String transportModeLabel(String transportMode) {
        switch (normalizeTransportMode(transportMode)) {
            case TRANSPORT_AIR:
                return "空";
            case TRANSPORT_EXPRESS:
                return "快递";
            case TRANSPORT_SEA:
                return "海";
            default:
                return "未分配";
        }
    }

    private void log(
            Long orderId,
            Long itemId,
            String operationType,
            Long operatorUserId,
            String beforeStatus,
            String afterStatus,
            String detail
    ) {
        mapper.insertOperationLog(
                mapper.nextOperationLogId(),
                orderId,
                itemId,
                operationType,
                operatorUserId,
                beforeStatus,
                afterStatus,
                detail == null ? null : writeJson(Map.of("detail", detail))
        );
    }

    private String dbStatus(PurchaseOrderItemRecord item) {
        return toDbCollectionStatus(firstText(item.aliStatus, item.collectionStatus));
    }

    private String toDbCollectionStatus(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
        switch (normalized) {
            case "WAITING_SOURCE":
            case "NOT_STARTED":
                return "NOT_STARTED";
            case "QUEUED":
                return "QUEUED";
            case "RUNNING":
                return "RUNNING";
            case "SUCCESS":
            case "SUCCEEDED":
                return "SUCCEEDED";
            case "PARTIAL_SUCCESS":
            case "PARTIAL_SUCCEEDED":
                return "PARTIAL_SUCCEEDED";
            case "FAILED":
                return "FAILED";
            default:
                return "NOT_STARTED";
        }
    }

    private String toViewItemStatus(String value) {
        String dbStatus = toDbCollectionStatus(value);
        if ("QUEUED".equals(dbStatus) || "RUNNING".equals(dbStatus)) {
            return "collecting";
        }
        if ("SUCCEEDED".equals(dbStatus) || "PARTIAL_SUCCEEDED".equals(dbStatus)) {
            return "succeeded";
        }
        if ("FAILED".equals(dbStatus)) {
            return "failed";
        }
        return "not_started";
    }

    private String toViewOrderStatus(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
        switch (normalized) {
            case "READY":
                return "pending_collection";
            case "COLLECTING":
                return "collecting";
            case "PARTIAL_DONE":
                return "partial_done";
            case "COMPLETED":
                return "done";
            case "ABNORMAL":
                return "exception";
            default:
                return "draft";
        }
    }

    private boolean isFinished(String dbStatus) {
        return "SUCCEEDED".equals(dbStatus) || "PARTIAL_SUCCEEDED".equals(dbStatus) || "FAILED".equals(dbStatus);
    }

    private Long parseNullableLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException error) {
            return null;
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

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (Exception error) {
            return new ArrayList<>();
        }
    }

    private String writeStringList(List<String> values) {
        return writeJson(values == null ? Collections.emptyList() : values);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("采购单 JSON 序列化失败。", error);
        }
    }

    private List<String> splitCsv(String value) {
        if (!StringUtils.hasText(value)) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String item : value.split(",")) {
            String normalized = normalizeSiteCode(item);
            if (StringUtils.hasText(normalized) && !result.contains(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    private String normalizeSiteCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String normalizeOptionalFulfillmentType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return normalizeFulfillmentType(value);
    }

    private String normalizeFulfillmentType(String value) {
        String text = trim(value);
        if (!StringUtils.hasText(text)) {
            return FULFILLMENT_WAREHOUSE_RECEIPT;
        }
        String upper = text.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (FULFILLMENT_WAREHOUSE_RECEIPT.equals(upper)
                || "WAREHOUSE".equals(upper)
                || "WAREHOUSE_RECEIVING".equals(upper)
                || "货到仓库".equals(text)
                || "到仓".equals(text)
                || "仓库".equals(text)
                || "入仓".equals(text)) {
            return FULFILLMENT_WAREHOUSE_RECEIPT;
        }
        if (FULFILLMENT_FACTORY_DIRECT.equals(upper)
                || "FACTORY".equals(upper)
                || "FORWARDER".equals(upper)
                || "FORWARDER_RECEIPT".equals(upper)
                || "货到货代".equals(text)
                || "货代".equals(text)
                || "厂家".equals(text)
                || "厂家直发".equals(text)
                || "直发货代".equals(text)) {
            return FULFILLMENT_FACTORY_DIRECT;
        }
        throw new IllegalArgumentException("不支持的到货方式：" + text);
    }

    private String fulfillmentTypeLabel(String fulfillmentType) {
        return FULFILLMENT_FACTORY_DIRECT.equals(normalizeFulfillmentType(fulfillmentType))
                ? "货到货代"
                : "货到仓库";
    }

    private String requiredText(String value, String message) {
        String text = trim(value);
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String trimToNull(String value) {
        String text = trim(value);
        return StringUtils.hasText(text) ? text : null;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private Integer firstNonNull(Integer first, Integer second) {
        return first == null ? second : first;
    }

    private Integer nonNull(Integer value) {
        return value == null ? 0 : value;
    }

    private void addTrimmed(Set<String> target, String value) {
        String text = trim(value);
        if (StringUtils.hasText(text)) {
            target.add(text);
        }
    }

    private String ali1688HistoryGroupKey(
            String siteCode,
            String partnerSku,
            String pskuCode,
            String skuParent
    ) {
        return normalizeSiteCode(siteCode) + ":"
                + defaultText(firstText(firstText(partnerSku, pskuCode), skuParent), "").toUpperCase(Locale.ROOT);
    }

    private void fillPagination(PurchaseOrderAli1688HistoryView view) {
        view.pagination.page = 1;
        view.pagination.pageSize = view.items.size();
        view.pagination.total = view.items.size();
        view.unlinkedAssignedLineCount = 0;
    }

    private String siteName(String siteCode) {
        if ("SA".equalsIgnoreCase(siteCode)) {
            return "沙特 SA";
        }
        if ("AE".equalsIgnoreCase(siteCode)) {
            return "阿联酋 AE";
        }
        return siteCode;
    }

    private static final class Ali1688HistoryAccumulator {
        private String storeCode;
        private String siteCode;
        private String skuParent;
        private String partnerSku;
        private String pskuCode;
        private String productTitle;
        private final LinkedHashMap<Long, PurchaseOrderAli1688PurchaseBatchView> purchaseBatches = new LinkedHashMap<>();
        private final LinkedHashSet<String> batchSourceKeys = new LinkedHashSet<>();
        private final LinkedHashMap<String, PurchaseOrderAli1688HistorySourceView> historyByOrder = new LinkedHashMap<>();

        private Ali1688HistoryAccumulator(PurchaseOrderAli1688PurchaseBatchRow row) {
            fillIdentity(row.storeCode, row.siteCode, row.skuParent, row.partnerSku, row.pskuCode, null);
        }

        private Ali1688HistoryAccumulator(PurchaseOrderAli1688HistoryRow row) {
            fillIdentity(row.storeCode, row.siteCode, row.skuParent, row.partnerSku, row.pskuCode, row.productTitle);
        }

        private void add(PurchaseOrderAli1688PurchaseBatchRow row) {
            fillIdentity(row.storeCode, row.siteCode, row.skuParent, row.partnerSku, row.pskuCode, null);
            PurchaseOrderAli1688PurchaseBatchView batch = purchaseBatches.computeIfAbsent(row.id, ignored -> toBatchView(row));
            PurchaseOrderAli1688HistorySourceView source = toBatchSourceView(row);
            if (source == null) {
                return;
            }
            String key = sourceKey(source);
            if (batchSourceKeys.add(row.id + ":" + key)) {
                batch.sources.add(source);
            }
        }

        private void add(PurchaseOrderAli1688HistoryRow row) {
            fillIdentity(row.storeCode, row.siteCode, row.skuParent, row.partnerSku, row.pskuCode, row.productTitle);
            PurchaseOrderAli1688HistorySourceView source = toHistorySourceView(row);
            String key = historyOrderKey(source);
            PurchaseOrderAli1688HistorySourceView existing = historyByOrder.get(key);
            if (existing == null) {
                historyByOrder.put(key, source);
            } else {
                mergeHistorySource(existing, source);
            }
        }

        private PurchaseOrderAli1688HistoryItemView toView() {
            PurchaseOrderAli1688HistoryItemView view = new PurchaseOrderAli1688HistoryItemView();
            view.storeCode = storeCode;
            view.siteCode = siteCode;
            view.skuParent = skuParent;
            view.partnerSku = partnerSku;
            view.pskuCode = pskuCode;
            view.productTitle = productTitle;
            view.purchaseBatches = new ArrayList<>(purchaseBatches.values());
            view.history = new ArrayList<>(historyByOrder.values());

            if (!view.purchaseBatches.isEmpty()) {
                view.purchaseCount = view.purchaseBatches.size();
                view.totalQuantity = view.purchaseBatches.stream()
                        .map(batch -> batch.countedQuantity)
                        .filter(quantity -> quantity != null)
                        .mapToInt(Integer::intValue)
                        .sum();
                view.totalCost = view.purchaseBatches.stream()
                        .map(batch -> batch.countedCost)
                        .filter(cost -> cost != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                view.averageUnitPrice = unitPrice(view.totalCost, view.totalQuantity);
                PurchaseOrderAli1688PurchaseBatchView latestBatch = latestBatch(view.purchaseBatches);
                view.recentPurchaseTime = latestBatchSourceTime(latestBatch);
                view.recentUnitPrice = latestBatch == null ? null : firstNonNull(latestBatch.unitPrice, view.averageUnitPrice);
                return view;
            }

            view.purchaseCount = view.history.size();
            view.totalQuantity = view.history.stream()
                    .map(source -> source.assignedQuantity)
                    .filter(quantity -> quantity != null)
                    .mapToInt(Integer::intValue)
                    .sum();
            view.totalCost = view.history.stream()
                    .map(source -> source.allocatedCost)
                    .filter(cost -> cost != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            view.averageUnitPrice = unitPrice(view.totalCost, view.totalQuantity);
            PurchaseOrderAli1688HistorySourceView latestHistory = latestHistory(view.history);
            view.recentPurchaseTime = latestHistory == null ? null : latestHistory.orderTime;
            view.recentUnitPrice = latestHistory == null ? view.averageUnitPrice : firstNonNull(latestHistory.unitPrice, view.averageUnitPrice);
            return view;
        }

        private void fillIdentity(
                String storeCode,
                String siteCode,
                String skuParent,
                String partnerSku,
                String pskuCode,
                String productTitle
        ) {
            this.storeCode = firstNonBlank(this.storeCode, storeCode);
            this.siteCode = firstNonBlank(this.siteCode, siteCode);
            this.skuParent = firstNonBlank(this.skuParent, skuParent);
            this.partnerSku = firstNonBlank(this.partnerSku, partnerSku);
            this.pskuCode = firstNonBlank(this.pskuCode, pskuCode);
            this.productTitle = firstNonBlank(this.productTitle, productTitle);
        }

        private static PurchaseOrderAli1688PurchaseBatchView toBatchView(PurchaseOrderAli1688PurchaseBatchRow row) {
            PurchaseOrderAli1688PurchaseBatchView view = new PurchaseOrderAli1688PurchaseBatchView();
            view.id = row.id;
            view.label = row.batchLabel;
            view.batchSequence = row.batchSequence;
            view.countedQuantity = row.countedQuantity;
            view.countedCost = row.countedCost;
            view.unitPrice = unitPrice(row.countedCost, row.countedQuantity);
            view.note = row.note;
            return view;
        }

        private static PurchaseOrderAli1688HistorySourceView toBatchSourceView(
                PurchaseOrderAli1688PurchaseBatchRow row
        ) {
            if (row.sourceOrderId == null
                    && row.sourceItemId == null
                    && row.sourceAssignmentId == null
                    && !StringUtils.hasText(row.orderNo)) {
                return null;
            }
            PurchaseOrderAli1688HistorySourceView view = new PurchaseOrderAli1688HistorySourceView();
            view.orderId = row.sourceOrderId;
            view.itemId = row.sourceItemId;
            view.assignmentId = row.sourceAssignmentId;
            view.orderNo = row.orderNo;
            view.orderTime = row.orderTime;
            view.supplierName = row.supplierName;
            return view;
        }

        private static PurchaseOrderAli1688HistorySourceView toHistorySourceView(PurchaseOrderAli1688HistoryRow row) {
            PurchaseOrderAli1688HistorySourceView view = new PurchaseOrderAli1688HistorySourceView();
            view.allocationId = row.allocationId;
            view.orderId = row.orderId;
            view.itemId = row.itemId;
            view.assignmentId = row.assignmentId;
            view.orderNo = row.orderNo;
            view.orderTime = row.orderTime;
            view.supplierName = row.supplierName;
            view.assignedQuantity = row.assignedQuantity;
            view.allocatedCost = row.allocatedCost;
            view.unitPrice = row.unitPrice;
            view.sourceLineLabel = row.sourceLineLabel;
            view.allocationBasis = row.allocationBasis;
            view.evidenceText = row.evidenceText;
            return view;
        }

        private static String sourceKey(PurchaseOrderAli1688HistorySourceView source) {
            if (source.allocationId != null) {
                return "allocation:" + source.allocationId;
            }
            if (source.assignmentId != null) {
                return "assignment:" + source.assignmentId;
            }
            return defaultString(source.orderNo) + ":" + defaultString(source.itemId) + ":" + defaultString(source.orderTime);
        }

        private static String historyOrderKey(PurchaseOrderAli1688HistorySourceView source) {
            if (source.allocationId != null) {
                return "allocation:" + source.allocationId;
            }
            if (StringUtils.hasText(source.orderNo)) {
                return "orderNo:" + source.orderNo;
            }
            if (source.orderId != null) {
                return "orderId:" + source.orderId;
            }
            return sourceKey(source);
        }

        private static void mergeHistorySource(
                PurchaseOrderAli1688HistorySourceView target,
                PurchaseOrderAli1688HistorySourceView source
        ) {
            target.assignedQuantity = addNullable(target.assignedQuantity, source.assignedQuantity);
            target.allocatedCost = addNullable(target.allocatedCost, source.allocatedCost);
            target.unitPrice = unitPrice(target.allocatedCost, target.assignedQuantity);
            if (!StringUtils.hasText(target.orderTime) || compareNullableText(source.orderTime, target.orderTime) > 0) {
                target.orderTime = source.orderTime;
            }
            target.supplierName = firstNonBlank(target.supplierName, source.supplierName);
            target.sourceLineLabel = firstNonBlank(target.sourceLineLabel, source.sourceLineLabel);
            target.allocationBasis = firstNonBlank(target.allocationBasis, source.allocationBasis);
            target.evidenceText = firstNonBlank(target.evidenceText, source.evidenceText);
        }

        private static PurchaseOrderAli1688PurchaseBatchView latestBatch(
                List<PurchaseOrderAli1688PurchaseBatchView> batches
        ) {
            PurchaseOrderAli1688PurchaseBatchView latest = null;
            String latestTime = null;
            for (PurchaseOrderAli1688PurchaseBatchView batch : batches) {
                String sourceTime = latestBatchSourceTime(batch);
                if (latest == null || compareNullableText(sourceTime, latestTime) > 0) {
                    latest = batch;
                    latestTime = sourceTime;
                }
            }
            return latest;
        }

        private static String latestBatchSourceTime(PurchaseOrderAli1688PurchaseBatchView batch) {
            if (batch == null || batch.sources == null) {
                return null;
            }
            String latest = null;
            for (PurchaseOrderAli1688HistorySourceView source : batch.sources) {
                if (source != null && compareNullableText(source.orderTime, latest) > 0) {
                    latest = source.orderTime;
                }
            }
            return latest;
        }

        private static PurchaseOrderAli1688HistorySourceView latestHistory(
                List<PurchaseOrderAli1688HistorySourceView> sources
        ) {
            PurchaseOrderAli1688HistorySourceView latest = null;
            for (PurchaseOrderAli1688HistorySourceView source : sources) {
                if (source != null && (latest == null || compareNullableText(source.orderTime, latest.orderTime) > 0)) {
                    latest = source;
                }
            }
            return latest;
        }

        private static BigDecimal unitPrice(BigDecimal cost, Integer quantity) {
            if (cost == null || quantity == null || quantity <= 0) {
                return null;
            }
            return cost.divide(BigDecimal.valueOf(quantity), 4, RoundingMode.HALF_UP).stripTrailingZeros();
        }

        private static Integer addNullable(Integer left, Integer right) {
            if (left == null) {
                return right;
            }
            if (right == null) {
                return left;
            }
            return left + right;
        }

        private static BigDecimal addNullable(BigDecimal left, BigDecimal right) {
            if (left == null) {
                return right;
            }
            if (right == null) {
                return left;
            }
            return left.add(right);
        }

        private static BigDecimal firstNonNull(BigDecimal first, BigDecimal second) {
            return first == null ? second : first;
        }

        private static int compareNullableText(String left, String right) {
            if (!StringUtils.hasText(left) && !StringUtils.hasText(right)) {
                return 0;
            }
            if (!StringUtils.hasText(left)) {
                return -1;
            }
            if (!StringUtils.hasText(right)) {
                return 1;
            }
            return left.compareTo(right);
        }

        private static String firstNonBlank(String first, String second) {
            return StringUtils.hasText(first) ? first : second;
        }

        private static String defaultString(Object value) {
            return value == null ? "" : String.valueOf(value);
        }
    }

    private static final class RouteCostInputs {
        private final Map<String, List<ForwarderRouteSegmentRecord>> segmentsByRoute;
        private final Map<String, List<ForwarderBasePriceRecord>> basePricesByService;
        private final Map<String, List<ForwarderWarehouseProcessingFeeRecord>> warehouseFeesByService;
        private final Map<String, List<ForwarderTransportFeeRecord>> transportFeesByService;

        private RouteCostInputs(
                Map<String, List<ForwarderRouteSegmentRecord>> segmentsByRoute,
                Map<String, List<ForwarderBasePriceRecord>> basePricesByService,
                Map<String, List<ForwarderWarehouseProcessingFeeRecord>> warehouseFeesByService,
                Map<String, List<ForwarderTransportFeeRecord>> transportFeesByService
        ) {
            this.segmentsByRoute = segmentsByRoute;
            this.basePricesByService = basePricesByService;
            this.warehouseFeesByService = warehouseFeesByService;
            this.transportFeesByService = transportFeesByService;
        }

        private static RouteCostInputs empty() {
            return new RouteCostInputs(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }

        private List<ForwarderBasePriceRecord> basePrices(String routeCode) {
            List<ForwarderBasePriceRecord> values = new ArrayList<>();
            for (ForwarderRouteSegmentRecord segment : segmentsByRoute.getOrDefault(routeCode, Collections.emptyList())) {
                if ("LAST_MILE".equals(segment.segmentRole)) {
                    values.addAll(basePricesByService.getOrDefault(segment.serviceCode, Collections.emptyList()));
                }
            }
            return values;
        }

        private List<ForwarderWarehouseProcessingFeeRecord> warehouseFees(String routeCode) {
            List<ForwarderWarehouseProcessingFeeRecord> values = new ArrayList<>();
            for (ForwarderRouteSegmentRecord segment : segmentsByRoute.getOrDefault(routeCode, Collections.emptyList())) {
                if ("WAREHOUSE_PROCESSING".equals(segment.segmentRole)) {
                    values.addAll(warehouseFeesByService.getOrDefault(segment.serviceCode, Collections.emptyList()));
                }
            }
            return values;
        }

        private List<ForwarderTransportFeeRecord> transportFees(String routeCode) {
            List<ForwarderTransportFeeRecord> values = new ArrayList<>();
            for (ForwarderRouteSegmentRecord segment : segmentsByRoute.getOrDefault(routeCode, Collections.emptyList())) {
                if ("LAST_MILE".equals(segment.segmentRole)) {
                    values.addAll(transportFeesByService.getOrDefault(segment.serviceCode, Collections.emptyList()));
                }
            }
            return values;
        }
    }

    private static final class SiteTransportQuantity {
        private final String siteCode;
        private final String transportMode;
        private int quantity;

        private SiteTransportQuantity(String siteCode, String transportMode, int quantity) {
            this.siteCode = siteCode;
            this.transportMode = transportMode;
            this.quantity = quantity;
        }
    }
}
