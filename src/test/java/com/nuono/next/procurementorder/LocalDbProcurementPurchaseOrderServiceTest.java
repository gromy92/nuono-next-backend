package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.AddItemsCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.ItemCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.SiteQuantityCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateItemCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateOrderCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderBasePriceRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteSegmentRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderSeaRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderTransportFeeRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderWarehouseProcessingFeeRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderAli1688HistoryRow;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderAli1688PurchaseBatchRow;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ProductArchiveRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ProductOfferRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderItemRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderItemSiteRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.StoreSiteRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderAli1688HistoryView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsPlanView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderView;
import com.nuono.next.productselection.Ali1688CollectionView;
import com.nuono.next.productselection.LocalDbAli1688CollectionService;
import com.nuono.next.productselection.ProductSelectionSourceCollectionRow;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalDbProcurementPurchaseOrderServiceTest {

    @Mock
    private ProcurementPurchaseOrderMapper mapper;

    @Mock
    private ProductSelectionMapper productSelectionMapper;

    @Mock
    private LocalDbAli1688CollectionService ali1688CollectionService;

    private LocalDbProcurementPurchaseOrderService service;

    @BeforeEach
    void setUp() {
        service = new LocalDbProcurementPurchaseOrderService(
                mapper,
                productSelectionMapper,
                ali1688CollectionService,
                new ObjectMapper()
        );
        lenient().when(mapper.nextOperationLogId()).thenReturn(240001L);
        lenient().when(mapper.listRouteSegments(any())).thenReturn(List.of());
    }

    @Test
    void updateOrderEditsTitleAndRemarkOnly() {
        PurchaseOrderRecord before = order("旧采购单", "旧备注");
        PurchaseOrderRecord after = order("SGGR-0607", null);
        UpdateOrderCommand command = new UpdateOrderCommand();
        command.title = "  SGGR-0607  ";
        command.remark = "   ";

        when(mapper.selectOrderById(200001L)).thenReturn(before, after);
        when(mapper.listItemSitesByOrder(200001L)).thenReturn(List.of());
        when(mapper.listItemsByOrder(200001L)).thenReturn(List.of());

        PurchaseOrderView view = service.updateOrder(access(), "200001", command);

        assertThat(view.title).isEqualTo("SGGR-0607");
        assertThat(view.remark).isNull();
        verify(mapper).updateOrderHeader(200001L, "SGGR-0607", null, 307L);
        verify(mapper).insertOperationLog(
                eq(240001L),
                eq(200001L),
                isNull(),
                eq("UPDATE_ORDER"),
                eq(307L),
                eq("READY"),
                eq("READY"),
                eq("{\"detail\":\"SGGR-0607\"}")
        );
    }

    @Test
    void deleteItemSoftDeletesLineAndRecalculatesOrder() {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        PurchaseOrderItemRecord item = item();

        when(mapper.selectOrderById(200001L)).thenReturn(order, order);
        when(mapper.selectItemById(210001L)).thenReturn(item);
        when(mapper.listItemSitesByOrder(200001L)).thenReturn(List.of());
        when(mapper.listItemsByOrder(200001L)).thenReturn(List.of());

        PurchaseOrderView view = service.deleteItem(access(), "200001", "210001");

        assertThat(view.items).isEmpty();
        verify(mapper).supersedeCurrentAli1688TasksByItem(210001L, 307L);
        verify(mapper).softDeleteLinksByItem(210001L, 307L);
        verify(mapper).softDeleteItemSitesByItem(210001L, 307L);
        verify(mapper).softDeleteItem(210001L, 307L);
        verify(mapper).recalculateOrderAggregates(200001L, 307L);
        verify(mapper).insertOperationLog(
                eq(240001L),
                eq(200001L),
                eq(210001L),
                eq("DELETE_ITEM"),
                eq(307L),
                eq("NOT_STARTED"),
                eq("DELETED"),
                eq("{\"detail\":\"SGGRB115\"}")
        );
    }

    @Test
    void addItemsExpandsOrderSiteRangeFromItemAllocations() {
        PurchaseOrderRecord before = order("SGGR-0607", "人工补货");
        before.siteCodesJson = "[\"SA\"]";
        PurchaseOrderRecord after = order("SGGR-0607", "人工补货");
        after.siteCodesJson = "[\"SA\",\"AE\"]";

        AddItemsCommand command = new AddItemsCommand();
        ItemCommand itemCommand = new ItemCommand();
        itemCommand.psku = "SGGRB116";
        itemCommand.site = "AE";
        itemCommand.quantity = 20;
        command.items = List.of(itemCommand);

        when(mapper.selectOrderById(200001L)).thenReturn(before, before, after);
        when(mapper.listStoreSites(301L)).thenReturn(List.of(
                storeSite(30001L, "SA", "STR69486-NSA"),
                storeSite(30002L, "AE", "STR69486-NAE")
        ));
        when(mapper.listProductArchiveMatches(301L, "SGGRB116")).thenReturn(List.of(product()));
        when(mapper.selectItemByVariant(200001L, 320002L)).thenReturn(null);
        when(mapper.nextItemId()).thenReturn(210002L);
        when(mapper.selectProductOffer(301L, 320002L, "AE")).thenReturn(offer());
        when(mapper.nextItemSiteId()).thenReturn(220002L);
        when(mapper.listItemSitesByOrder(200001L)).thenReturn(List.of(siteRow()));
        PurchaseOrderItemRecord savedItem = item();
        savedItem.id = 210002L;
        savedItem.productVariantId = 320002L;
        savedItem.partnerSku = "SGGRB116";
        when(mapper.listItemsByOrder(200001L)).thenReturn(List.of(savedItem));

        PurchaseOrderView view = service.addItems(access(), "200001", command);

        assertThat(view.siteCodes).containsExactly("SA", "AE");
        assertThat(view.items).hasSize(1);
        assertThat(view.items.get(0).allocations).hasSize(1);
        assertThat(view.items.get(0).allocations.get(0).site).isEqualTo("AE");
        verify(mapper).updateOrderSiteCodes(200001L, "[\"SA\",\"AE\"]", 307L);
        verify(mapper).recalculateItemAggregates(210002L, 307L);
        verify(mapper).recalculateOrderAggregates(200001L, 307L);
    }

    @Test
    void updateItemReplacesAllocationsAndRecalculatesOrder() {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        order.siteCodesJson = "[\"SA\"]";
        PurchaseOrderItemRecord existingItem = item();
        UpdateItemCommand command = new UpdateItemCommand();
        command.siteQuantities = List.of(
                siteQuantity("SA", "SEA", 80),
                siteQuantity("AE", "AIR", 10)
        );

        when(mapper.selectOrderById(200001L)).thenReturn(order, order);
        when(mapper.selectItemById(210001L)).thenReturn(existingItem);
        when(mapper.listStoreSites(301L)).thenReturn(List.of(
                storeSite(30001L, "SA", "STR69486-NSA"),
                storeSite(30002L, "AE", "STR69486-NAE")
        ));
        when(mapper.selectProductOffer(301L, 320001L, "SA")).thenReturn(offer("SA", 30001L, 330001L, "SGGRB115"));
        when(mapper.selectProductOffer(301L, 320001L, "AE")).thenReturn(offer("AE", 30002L, 330002L, "SGGRB115"));
        when(mapper.nextItemSiteId()).thenReturn(220101L, 220102L);
        when(mapper.listItemSitesByOrder(200001L)).thenReturn(List.of(siteRow("SA", "SEA", 80), siteRow("AE", "AIR", 10)));
        when(mapper.listItemsByOrder(200001L)).thenReturn(List.of(existingItem));

        PurchaseOrderView view = service.updateItem(access(), "200001", "210001", command);

        assertThat(view.siteCodes).containsExactly("SA", "AE");
        assertThat(view.items).hasSize(1);
        assertThat(view.items.get(0).allocations)
                .extracting(allocation -> allocation.site + ":" + allocation.transportMode + ":" + allocation.quantity)
                .containsExactly("SA:SEA:80", "AE:AIR:10");
        assertThat(view.items.get(0).productFulltype).isEqualTo("stationery-gift_wrapping_supplies");
        verify(mapper).softDeleteItemSitesByItem(210001L, 307L);
        ArgumentCaptor<PurchaseOrderItemSiteRecord> siteCaptor = ArgumentCaptor.forClass(PurchaseOrderItemSiteRecord.class);
        verify(mapper, org.mockito.Mockito.times(2)).upsertItemSite(siteCaptor.capture());
        assertThat(siteCaptor.getAllValues())
                .extracting(site -> site.siteCode + ":" + site.transportMode + ":" + site.quantity)
                .containsExactly("SA:SEA:80", "AE:AIR:10");
        verify(mapper).updateOrderSiteCodes(200001L, "[\"SA\",\"AE\"]", 307L);
        verify(mapper).recalculateItemAggregates(210001L, 307L);
        verify(mapper).recalculateOrderAggregates(200001L, 307L);
        verify(mapper).insertOperationLog(
                eq(240001L),
                eq(200001L),
                eq(210001L),
                eq("UPDATE_ITEM"),
                eq(307L),
                eq("NOT_STARTED"),
                eq("NOT_STARTED"),
                eq("{\"detail\":\"SGGRB115\"}")
        );
    }

    @Test
    void getOrderAli1688HistoryAggregatesCurrentOrderItemsByProjectCodeAndSite() {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        PurchaseOrderItemRecord item = item();
        item.skuParent = "SGGR";
        item.totalQuantity = 30;
        PurchaseOrderAli1688PurchaseBatchRow batchRow = purchaseBatchRow();
        PurchaseOrderAli1688HistoryRow historyRow = historyRow();

        when(mapper.selectOrderById(200001L)).thenReturn(order);
        when(mapper.listItemsByOrder(200001L)).thenReturn(List.of(item));
        when(mapper.listItemSitesByOrder(200001L)).thenReturn(List.of(siteRow("SA", "AIR", 30)));
        when(mapper.listOrderAli1688PurchaseBatches(
                307L,
                "PRJ69486",
                List.of("SA"),
                List.of("SGGRB115"),
                List.of("SGGR")
        )).thenReturn(List.of(batchRow));
        when(mapper.listOrderAli1688HistoryRows(
                307L,
                "PRJ69486",
                List.of("SA"),
                List.of("SGGRB115"),
                List.of("SGGR")
        )).thenReturn(List.of(historyRow));

        PurchaseOrderAli1688HistoryView view = service.getOrderAli1688History(access(), "200001");

        assertThat(view.items).hasSize(1);
        assertThat(view.items.get(0).storeCode).isEqualTo("PRJ69486");
        assertThat(view.items.get(0).siteCode).isEqualTo("SA");
        assertThat(view.items.get(0).partnerSku).isEqualTo("SGGRB115");
        assertThat(view.items.get(0).purchaseCount).isEqualTo(1);
        assertThat(view.items.get(0).totalQuantity).isEqualTo(70);
        assertThat(view.items.get(0).totalCost).isEqualByComparingTo("686.00");
        assertThat(view.items.get(0).recentUnitPrice).isEqualByComparingTo("9.80");
        assertThat(view.items.get(0).recentPurchaseTime).isEqualTo("2026-05-29 10:00:00");
        assertThat(view.items.get(0).purchaseBatches).hasSize(1);
        assertThat(view.items.get(0).purchaseBatches.get(0).unitPrice).isEqualByComparingTo("9.80");
        assertThat(view.items.get(0).purchaseBatches.get(0).sources).hasSize(1);
        assertThat(view.items.get(0).purchaseBatches.get(0).sources.get(0).orderNo).isEqualTo("5109325419624114902");
        assertThat(view.items.get(0).history).hasSize(1);
        assertThat(view.items.get(0).history.get(0).orderNo).isEqualTo("5109325419624114902");
        assertThat(view.pagination.total).isEqualTo(1);
    }

    @Test
    void getOrderAli1688HistoryMergesSame1688OrderLinesWhenNoManualBatchExists() {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        PurchaseOrderItemRecord item = item();
        item.skuParent = "SGGR";
        item.totalQuantity = 100;
        PurchaseOrderAli1688HistoryRow firstLine = historyRow();
        PurchaseOrderAli1688HistoryRow secondLine = historyRow();
        secondLine.assignmentId = 702L;
        secondLine.itemId = 602L;
        secondLine.assignedQuantity = 30;
        secondLine.allocatedCost = new BigDecimal("294.00");
        secondLine.unitPrice = new BigDecimal("9.80");

        when(mapper.selectOrderById(200001L)).thenReturn(order);
        when(mapper.listItemsByOrder(200001L)).thenReturn(List.of(item));
        when(mapper.listItemSitesByOrder(200001L)).thenReturn(List.of(siteRow("SA", "AIR", 100)));
        when(mapper.listOrderAli1688PurchaseBatches(
                307L,
                "PRJ69486",
                List.of("SA"),
                List.of("SGGRB115"),
                List.of("SGGR")
        )).thenReturn(List.of());
        when(mapper.listOrderAli1688HistoryRows(
                307L,
                "PRJ69486",
                List.of("SA"),
                List.of("SGGRB115"),
                List.of("SGGR")
        )).thenReturn(List.of(firstLine, secondLine));

        PurchaseOrderAli1688HistoryView view = service.getOrderAli1688History(access(), "200001");

        assertThat(view.items).hasSize(1);
        assertThat(view.items.get(0).purchaseCount).isEqualTo(1);
        assertThat(view.items.get(0).totalQuantity).isEqualTo(100);
        assertThat(view.items.get(0).totalCost).isEqualByComparingTo("980.00");
        assertThat(view.items.get(0).recentUnitPrice).isEqualByComparingTo("9.80");
        assertThat(view.items.get(0).history).hasSize(1);
        assertThat(view.items.get(0).history.get(0).assignedQuantity).isEqualTo(100);
        assertThat(view.items.get(0).history.get(0).allocatedCost).isEqualByComparingTo("980.00");
    }

    @Test
    void collectOrderCreatesHiddenSourceCollectionAndLinksAli1688Task() {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        PurchaseOrderItemRecord item = item();
        item.childSku = "Z320001-1";
        item.imageUrlCache = "https://example.test/SGGRB115.jpg";
        item.sourcingSpecText = "拉链款";
        item.sourcingColorText = "红色";

        PurchaseOrderItemRecord collectedItem = item();
        collectedItem.imageUrlCache = item.imageUrlCache;
        collectedItem.collectionStatus = "QUEUED";
        collectedItem.progressPercent = 5;
        collectedItem.candidateCount = 0;
        collectedItem.recommendedCount = 0;
        collectedItem.latestCollectionLinkId = 230001L;
        collectedItem.sourceCollectionId = 910001L;
        collectedItem.sourceCollectionNo = "POI-910001";
        collectedItem.aliTaskNo = "ALI1688-870001";
        collectedItem.aliStatus = "queued";
        collectedItem.aliProgressPercent = 5;
        collectedItem.aliCandidateCount = 0;
        collectedItem.aliRecommendedCount = 0;

        ProductArchiveRecord product = product();
        product.productMasterId = 310001L;
        product.productVariantId = 320001L;
        product.partnerSku = "SGGRB115";
        product.childSku = "Z320001-1";
        product.sizeEn = "20x30cm";

        Ali1688CollectionView aliView = new Ali1688CollectionView();
        aliView.taskId = "870001";
        aliView.status = "queued";
        aliView.progressPercent = 5;
        aliView.candidateCount = 0;
        aliView.recommendedCount = 0;

        when(mapper.selectOrderById(200001L)).thenReturn(order, order);
        when(mapper.listItemsByOrder(200001L)).thenReturn(List.of(item), List.of(collectedItem));
        when(mapper.selectProductArchiveByVariant(301L, 320001L)).thenReturn(product);
        when(productSelectionMapper.nextSourceCollectionId()).thenReturn(910001L);
        when(productSelectionMapper.selectSourceCollectionById(910001L)).thenReturn(null);
        when(ali1688CollectionService.ensureTaskForSourceCollection(any(ProductSelectionSourceCollectionRow.class), eq(307L)))
                .thenReturn(aliView);
        when(mapper.nextCollectionLinkId()).thenReturn(230001L);
        when(mapper.listItemSitesByOrder(200001L)).thenReturn(List.of(siteRow("SA", "AIR", 30)));

        PurchaseOrderView view = service.collectOrder(access(), "200001");

        assertThat(view.items).hasSize(1);
        assertThat(view.items.get(0).collectionStatus).isEqualTo("collecting");
        assertThat(view.items.get(0).currentTaskNo).isEqualTo("ALI1688-870001");

        ArgumentCaptor<ProductSelectionSourceCollectionRow> sourceCaptor =
                ArgumentCaptor.forClass(ProductSelectionSourceCollectionRow.class);
        verify(productSelectionMapper).insertSourceCollection(sourceCaptor.capture());
        ProductSelectionSourceCollectionRow source = sourceCaptor.getValue();
        assertThat(source.getId()).isEqualTo(910001L);
        assertThat(source.getSourceType()).isEqualTo("purchase-order-product");
        assertThat(source.getSourcePlatform()).isEqualTo("StoreArchive");
        assertThat(source.getSourceImageUrl()).isEqualTo("https://example.test/SGGRB115.jpg");
        assertThat(source.getSpecHintsJson())
                .contains("PSKU: SGGRB115")
                .contains("Noon SKU: Z320001-1")
                .contains("规格: 拉链款")
                .contains("颜色: 红色")
                .contains("Size: 20x30cm");
        assertThat(source.getSelectedText()).contains("SGGRB115", "规格: 拉链款", "颜色: 红色");
        assertThat(source.getNotes()).isEqualTo("purchaseOrder=PO-200001;itemId=210001");

        verify(mapper).supersedeCurrentAli1688TasksByItem(210001L, 307L);
        verify(mapper).supersedeCurrentLinksByItem(210001L, 307L);
        verify(mapper).insertCollectionLink(
                eq(230001L),
                eq(200001L),
                eq(210001L),
                eq(307L),
                eq(301L),
                eq(910001L),
                eq(870001L),
                eq("po-item:210001"),
                eq("QUEUED"),
                eq(5),
                eq(0),
                eq(0),
                isNull(),
                isNull(),
                org.mockito.ArgumentMatchers.contains("\"partnerSku\":\"SGGRB115\""),
                isNull(),
                eq(307L)
        );
        verify(mapper).updateItemCollection(210001L, 230001L, "QUEUED", 5, 0, 0, null, null, 0, 307L);
        verify(mapper).recalculateOrderAggregates(200001L, 307L);
    }

    @Test
    void generateLogisticsPlanPersistsDraftSnapshotAndMissingFields() {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        PurchaseOrderItemRecord item = item();
        item.id = 210002L;
        item.productVariantId = 320002L;
        item.totalQuantity = 20;
        ProductArchiveRecord product = product();
        product.productLengthCm = new BigDecimal("12.5");
        product.productWidthCm = new BigDecimal("8");
        product.productHeightCm = new BigDecimal("6");
        product.productWeightG = new BigDecimal("180");

        when(mapper.selectOrderById(200001L)).thenReturn(order);
        when(mapper.listItemsByOrder(200001L)).thenReturn(List.of(item));
        when(mapper.listItemSitesByOrder(200001L)).thenReturn(List.of(siteRow()));
        when(mapper.selectProductArchiveByVariant(301L, 320002L)).thenReturn(product);
        when(mapper.nextLogisticsPlanId()).thenReturn(250001L);

        PurchaseOrderLogisticsPlanView view = service.generateLogisticsPlan(access(), "200001");

        assertThat(view.planNo).isEqualTo("LP-250001");
        assertThat(view.itemCount).isEqualTo(1);
        assertThat(view.skuCount).isEqualTo(1);
        assertThat(view.totalQuantity).isEqualTo(20);
        assertThat(view.missingItemCount).isEqualTo(1);
        assertThat(view.lines.get(0).productDimensionsText).isEqualTo("12.5 x 8 x 6 cm");
        assertThat(view.lines.get(0).missingFields).contains("箱规尺寸缺失", "装箱数缺失");
        verify(mapper).supersedeCurrentLogisticsPlansByOrder(200001L, 307L);
        verify(mapper).softDeleteLogisticsRecommendationsByOrder(200001L, 307L);
        verify(mapper).insertLogisticsPlan(
                eq(250001L),
                eq(200001L),
                eq(307L),
                eq(301L),
                eq("LP-250001"),
                eq("DRAFT"),
                eq("PENDING"),
                eq(1),
                eq(1),
                eq(20),
                eq(1),
                eq("[{\"site\":\"AE\",\"siteName\":\"阿联酋 AE\",\"transportMode\":\"UNSPECIFIED\",\"transportModeLabel\":\"未分配\",\"quantity\":20}]"),
                org.mockito.ArgumentMatchers.contains("\"planNo\":\"LP-250001\""),
                eq(307L)
        );
        verify(mapper).insertOperationLog(
                eq(240001L),
                eq(200001L),
                isNull(),
                eq("GENERATE_LOGISTICS_PLAN"),
                eq(307L),
                eq("READY"),
                eq("READY"),
                eq("{\"detail\":\"LP-250001\"}")
        );
    }

    @Test
    void previewLogisticsPlanUsesLooseProductVolumeForSeaForwarderCandidatesWhenCartonSpecIsMissing() {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        PurchaseOrderItemRecord item = item();
        item.totalQuantity = 30;
        ProductArchiveRecord product = product();
        product.productMasterId = 310001L;
        product.productVariantId = 320001L;
        product.partnerSku = "SGGRB115";
        product.productLengthCm = new BigDecimal("12");
        product.productWidthCm = new BigDecimal("8");
        product.productHeightCm = new BigDecimal("6");
        product.productWeightG = new BigDecimal("180");

        when(mapper.selectOrderById(200001L)).thenReturn(order);
        when(mapper.listItemsByOrder(200001L)).thenReturn(List.of(item));
        when(mapper.listItemSitesByOrder(200001L)).thenReturn(List.of(siteRow("SA", "SEA", 30)));
        when(mapper.selectProductArchiveByVariant(301L, 320001L)).thenReturn(product);
        when(mapper.listRouteRecommendationCandidates(List.of("SA"), "SEA"))
                .thenReturn(List.of(
                        routeCandidate("SEA", "ZD-SAU-SEA-FBN-RUH", "ZD", "众鸫供应链", "ZD-SAU-SEA-WH-RUH", "沙特海运专线到众鸫海外仓 + FBN利雅得送仓", "RMB", "1250.0000", "CBM", null),
                        routeCandidate("SEA", "YT-SAU-SEA-FBN-RUH", "YT", "义特物流", "YT-SAU-SEA-FBN-RUH", "义特沙特海运双清包税 + FBN利雅得送仓", "CNY", "1190.0000", "CBM", null)
                ));

        PurchaseOrderLogisticsPlanView view = service.previewLogisticsPlan(access(), "200001");

        assertThat(view.recommendationStatus).isEqualTo("sea_candidate_ready");
        assertThat(view.estimatedSeaVolumeCbmText).isEqualTo("0.0173 CBM");
        assertThat(view.recommendations).hasSize(2);
        assertThat(view.recommendations.get(0).forwarderName).isEqualTo("义特物流");
        assertThat(view.recommendations.get(0).serviceCode).isEqualTo("YT-SAU-SEA-FBN-RUH");
        assertThat(view.recommendations.get(0).recommended).isTrue();
        assertThat(view.recommendations.get(0).priceSummary).isEqualTo("CNY 1190/CBM 起");
        assertThat(view.recommendations.get(0).estimatedCostText).isEqualTo("CNY 20.56");
        assertThat(view.recommendations.get(0).costComponents)
                .extracting(component -> component.componentType)
                .containsExactly("HEADHAUL");
        assertThat(view.recommendations.get(0).risks).contains("箱规缺失未阻塞本次海运估算；当前按商品散货体积计算。");
        assertThat(view.lines.get(0).seaLooseVolumeCbmText).isEqualTo("0.0173 CBM");
        assertThat(view.messages).contains("已找到 2 条海运货代服务线；当前按商品散货体积 0.0173 CBM 估算，箱规缺失不阻塞海运草案。");
    }

    @Test
    void previewLogisticsPlanRecommendsForwardersSeparatelyForSeaAndAirQuantities() {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        PurchaseOrderItemRecord item = item();
        item.totalQuantity = 50;
        ProductArchiveRecord product = product();
        product.productLengthCm = new BigDecimal("12");
        product.productWidthCm = new BigDecimal("8");
        product.productHeightCm = new BigDecimal("6");
        product.productWeightG = new BigDecimal("180");

        when(mapper.selectOrderById(200001L)).thenReturn(order);
        when(mapper.listItemsByOrder(200001L)).thenReturn(List.of(item));
        when(mapper.listItemSitesByOrder(200001L)).thenReturn(List.of(
                siteRow("SA", "SEA", 30),
                siteRow("SA", "AIR", 20)
        ));
        when(mapper.selectProductArchiveByVariant(301L, 320001L)).thenReturn(product);
        when(mapper.listRouteRecommendationCandidates(List.of("SA"), "SEA"))
                .thenReturn(List.of(routeCandidate(
                        "SEA",
                        "YT-SAU-SEA-FBN-RUH",
                        "YT",
                        "义特物流",
                        "YT-SAU-SEA-FBN-RUH",
                        "义特沙特海运双清包税 + FBN利雅得送仓",
                        "CNY",
                        "1190.0000",
                        "CBM",
                        null
                )));
        when(mapper.listRouteRecommendationCandidates(List.of("SA"), "AIR"))
                .thenReturn(List.of(routeCandidate(
                        "AIR",
                        "ET-SAU-AIR-FBN-RUH-20260604",
                        "ET",
                        "易通物流",
                        "ET-SAU-AIR-TIER1-WH-20260604",
                        "易通沙特空运一档仓到仓 20260604",
                        "RMB",
                        "67.0000",
                        "KG",
                        "5000.0000"
                )));

        PurchaseOrderLogisticsPlanView view = service.previewLogisticsPlan(access(), "200001");

        assertThat(view.recommendationStatus).isEqualTo("transport_candidate_ready");
        assertThat(view.estimatedSeaVolumeCbmText).isEqualTo("0.0173 CBM");
        assertThat(view.estimatedAirChargeableWeightKgText).isEqualTo("3.6 KG");
        assertThat(view.recommendations).hasSize(2);
        assertThat(view.recommendations.get(0).transportMode).isEqualTo("SEA");
        assertThat(view.recommendations.get(0).forwarderName).isEqualTo("义特物流");
        assertThat(view.recommendations.get(0).estimatedCostText).isEqualTo("CNY 20.56");
        assertThat(view.recommendations.get(1).transportMode).isEqualTo("AIR");
        assertThat(view.recommendations.get(1).forwarderName).isEqualTo("易通物流");
        assertThat(view.recommendations.get(1).priceSummary).isEqualTo("RMB 67/KG 起");
        assertThat(view.recommendations.get(1).estimatedCostText).isEqualTo("RMB 241.20");
        assertThat(view.messages).contains("已找到 1 条空运货代服务线；当前按各货代体积重除数和最低计费规则估算，可进入品类和计费规则复核。");
    }

    @Test
    void previewLogisticsPlanIncludesEtRouteWarehouseAndLastMileCostComponents() {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        PurchaseOrderItemRecord item = item();
        item.totalQuantity = 64;
        ProductArchiveRecord product = product();
        product.productLengthCm = new BigDecimal("20");
        product.productWidthCm = new BigDecimal("10");
        product.productHeightCm = new BigDecimal("8");
        product.productWeightG = new BigDecimal("300");

        when(mapper.selectOrderById(200001L)).thenReturn(order);
        when(mapper.listItemsByOrder(200001L)).thenReturn(List.of(item));
        when(mapper.listItemSitesByOrder(200001L)).thenReturn(List.of(siteRow("SA", "AIR", 64)));
        when(mapper.selectProductArchiveByVariant(301L, 320001L)).thenReturn(product);
        when(mapper.listRouteRecommendationCandidates(List.of("SA"), "AIR"))
                .thenReturn(List.of(routeCandidate(
                        "AIR",
                        "ET-SAU-AIR-FBN-RUH-20260604",
                        "ET",
                        "易通物流",
                        "ET-SAU-AIR-TIER1-WH-20260604",
                        "易通沙特空运一档仓到仓 20260604",
                        "RMB",
                        "67.0000",
                        "KG",
                        "5000.0000"
                )));
        when(mapper.listRouteSegments(List.of("ET-SAU-AIR-FBN-RUH-20260604")))
                .thenReturn(List.of(
                        routeSegment("ET-SAU-AIR-FBN-RUH-20260604", 1, "HEADHAUL", "ET-SAU-AIR-TIER1-WH-20260604"),
                        routeSegment("ET-SAU-AIR-FBN-RUH-20260604", 2, "WAREHOUSE_PROCESSING", "ET-WH-PROCESS-20260604"),
                        routeSegment("ET-SAU-AIR-FBN-RUH-20260604", 3, "LAST_MILE", "ET-LAST-MILE-20260604")
                ));
        when(mapper.listBasePricesByServiceCodes(List.of("ET-LAST-MILE-20260604")))
                .thenReturn(List.of(lastMileBasePrice()));
        when(mapper.listWarehouseProcessingFeesByServiceCodes(List.of("ET-WH-PROCESS-20260604")))
                .thenReturn(List.of(
                        warehouseFee(1L, "散件仓按件上架-小件", "INBOUND", "0.3", "PIECE", null),
                        warehouseFee(2L, "按件拣货-小件", "PICKING", "0.6", "PIECE", "8"),
                        warehouseFee(3L, "产品储存仓储费", "STORAGE", "8", "CBM_DAY", null)
                ));
        when(mapper.listTransportFeesByServiceCodes(List.of("ET-LAST-MILE-20260604")))
                .thenReturn(List.of(transportFee()));

        PurchaseOrderLogisticsPlanView view = service.previewLogisticsPlan(access(), "200001");

        assertThat(view.recommendations).hasSize(1);
        assertThat(view.recommendations.get(0).routeCode).isEqualTo("ET-SAU-AIR-FBN-RUH-20260604");
        assertThat(view.recommendations.get(0).estimatedCostText).contains("RMB ").contains("/天仓储");
        assertThat(view.recommendations.get(0).costComponents)
                .extracting(component -> component.componentType)
                .containsExactly(
                        "HEADHAUL",
                        "WAREHOUSE_INBOUND",
                        "WAREHOUSE_PICKING",
                        "LAST_MILE",
                        "WAREHOUSE_STORAGE_DAILY"
                );
        assertThat(view.recommendations.get(0).excludedCostNotes)
                .anyMatch(note -> note.contains("偏远"))
                .anyMatch(note -> note.contains("贴标"));
    }

    private BusinessAccessContext access() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .storeCodes(Set.of("STR69486-NSA"))
                .storeOwnerUserIds(Map.of("STR69486-NSA", 307L))
                .build();
    }

    private PurchaseOrderRecord order(String title, String remark) {
        PurchaseOrderRecord order = new PurchaseOrderRecord();
        order.id = 200001L;
        order.ownerUserId = 307L;
        order.logicalStoreId = 301L;
        order.orderNo = "PO-200001";
        order.title = title;
        order.remark = remark;
        order.status = "READY";
        order.collectionStatus = "NOT_STARTED";
        order.progressPercent = 0;
        order.siteCodesJson = "[\"SA\"]";
        order.projectCodeCache = "PRJ69486";
        order.projectNameCache = "SGGR";
        order.anchorStoreCodeCache = "STR69486-NSA";
        order.createdAt = "2026-06-07 10:00";
        order.updatedAt = "2026-06-07 10:00";
        return order;
    }

    private PurchaseOrderItemRecord item() {
        PurchaseOrderItemRecord item = new PurchaseOrderItemRecord();
        item.id = 210001L;
        item.purchaseOrderId = 200001L;
        item.ownerUserId = 307L;
        item.logicalStoreId = 301L;
        item.productMasterId = 310001L;
        item.productVariantId = 320001L;
        item.partnerSku = "SGGRB115";
        item.titleCache = "测试商品";
        item.productFulltypeCache = "stationery-gift_wrapping_supplies";
        item.collectionStatus = "NOT_STARTED";
        item.progressPercent = 0;
        return item;
    }

    private StoreSiteRecord storeSite(Long siteId, String siteCode, String storeCode) {
        StoreSiteRecord record = new StoreSiteRecord();
        record.siteId = siteId;
        record.siteCode = siteCode;
        record.storeCode = storeCode;
        return record;
    }

    private ProductArchiveRecord product() {
        ProductArchiveRecord record = new ProductArchiveRecord();
        record.productMasterId = 310002L;
        record.productVariantId = 320002L;
        record.skuParent = "Z320002";
        record.partnerSku = "SGGRB116";
        record.childSku = "Z320002-1";
        record.title = "新增测试商品";
        record.imageUrl = "https://example.test/SGGRB116.jpg";
        record.availableSiteCodesCsv = "AE";
        return record;
    }

    private ProductOfferRecord offer() {
        return offer("AE", 30002L, 330002L, "SGGRB116");
    }

    private ProductOfferRecord offer(String siteCode, Long siteId, Long offerId, String psku) {
        ProductOfferRecord record = new ProductOfferRecord();
        record.siteId = siteId;
        record.siteCode = siteCode;
        record.productSiteOfferId = offerId;
        record.pskuCode = psku;
        record.offerCode = "OFFER-" + siteCode;
        return record;
    }

    private PurchaseOrderItemSiteRecord siteRow() {
        return siteRow(210002L, "SGGRB116", "AE", "UNSPECIFIED", 20);
    }

    private PurchaseOrderItemSiteRecord siteRow(String siteCode, String transportMode, int quantity) {
        return siteRow(210001L, "SGGRB115", siteCode, transportMode, quantity);
    }

    private PurchaseOrderItemSiteRecord siteRow(Long itemId, String psku, String siteCode, String transportMode, int quantity) {
        PurchaseOrderItemSiteRecord record = new PurchaseOrderItemSiteRecord();
        record.id = 220002L;
        record.purchaseOrderId = 200001L;
        record.purchaseOrderItemId = itemId;
        record.ownerUserId = 307L;
        record.logicalStoreId = 301L;
        record.siteId = "SA".equals(siteCode) ? 30001L : 30002L;
        record.siteCode = siteCode;
        record.productSiteOfferId = "SA".equals(siteCode) ? 330001L : 330002L;
        record.pskuCode = psku;
        record.offerCode = "OFFER-" + siteCode;
        record.transportMode = transportMode;
        record.quantity = quantity;
        record.status = "ACTIVE";
        return record;
    }

    private PurchaseOrderAli1688PurchaseBatchRow purchaseBatchRow() {
        PurchaseOrderAli1688PurchaseBatchRow row = new PurchaseOrderAli1688PurchaseBatchRow();
        row.id = 102001L;
        row.storeCode = "PRJ69486";
        row.siteCode = "SA";
        row.skuParent = "SGGR";
        row.partnerSku = "SGGRB115";
        row.pskuCode = "SGGRB115";
        row.batchLabel = "历史采购-20260529";
        row.batchSequence = 1;
        row.countedQuantity = 70;
        row.countedCost = new BigDecimal("686.00");
        row.sourceOrderId = 501L;
        row.sourceItemId = 601L;
        row.sourceAssignmentId = 701L;
        row.orderNo = "5109325419624114902";
        row.orderTime = "2026-05-29 10:00:00";
        row.supplierName = "义乌测试供应商";
        return row;
    }

    private PurchaseOrderAli1688HistoryRow historyRow() {
        PurchaseOrderAli1688HistoryRow row = new PurchaseOrderAli1688HistoryRow();
        row.storeCode = "PRJ69486";
        row.siteCode = "SA";
        row.skuParent = "SGGR";
        row.partnerSku = "SGGRB115";
        row.pskuCode = "SGGRB115";
        row.productTitle = "测试商品";
        row.orderId = 501L;
        row.itemId = 601L;
        row.assignmentId = 701L;
        row.orderNo = "5109325419624114902";
        row.orderTime = "2026-05-29 10:00:00";
        row.supplierName = "义乌测试供应商";
        row.assignedQuantity = 70;
        row.allocatedCost = new BigDecimal("686.00");
        row.unitPrice = new BigDecimal("9.80");
        return row;
    }

    private SiteQuantityCommand siteQuantity(String siteCode, String transportMode, int quantity) {
        SiteQuantityCommand command = new SiteQuantityCommand();
        command.siteCode = siteCode;
        command.transportMode = transportMode;
        command.quantity = quantity;
        return command;
    }

    private ForwarderRouteRecommendationRecord routeCandidate(
            String transportMode,
            String routeCode,
            String forwarderCode,
            String forwarderName,
            String serviceCode,
            String serviceName,
            String currency,
            String minUnitPrice,
            String billingUnit,
            String volumeDivisor
    ) {
        ForwarderRouteRecommendationRecord record = new ForwarderRouteRecommendationRecord();
        record.routeCode = routeCode;
        record.routeName = serviceName;
        record.forwarderCode = forwarderCode;
        record.forwarderName = forwarderName;
        record.serviceCode = serviceCode;
        record.serviceName = serviceName;
        record.country = "沙特";
        record.siteCode = "SA";
        record.transportMode = transportMode;
        record.targetPlatform = "FBN";
        record.deliveryCity = "利雅得/RUH";
        record.destinationNode = "FBN利雅得仓";
        record.transitTimeText = "40-65天";
        record.priceRuleCount = 3;
        record.currency = currency;
        record.minUnitPrice = new BigDecimal(minUnitPrice);
        record.billingUnit = billingUnit;
        record.cargoCategoryNamesCsv = "普货,沙特海运（A类）";
        if ("CBM".equals(billingUnit)) {
            record.cbmMinUnitPrice = new BigDecimal(minUnitPrice);
        }
        if ("KG".equals(billingUnit)) {
            record.kgMinUnitPrice = new BigDecimal(minUnitPrice);
        }
        if (volumeDivisor != null) {
            record.volumeDivisor = new BigDecimal(volumeDivisor);
            record.transitTimeText = "8-15天";
            record.cargoCategoryNamesCsv = "沙特空运一档";
        }
        return record;
    }

    private ForwarderRouteSegmentRecord routeSegment(String routeCode, int segmentNo, String role, String serviceCode) {
        ForwarderRouteSegmentRecord record = new ForwarderRouteSegmentRecord();
        record.routeCode = routeCode;
        record.segmentNo = segmentNo;
        record.segmentRole = role;
        record.serviceCode = serviceCode;
        record.costPolicy = "ESTIMATE";
        record.required = true;
        return record;
    }

    private ForwarderWarehouseProcessingFeeRecord warehouseFee(
            Long id,
            String name,
            String type,
            String amount,
            String unit,
            String minCharge
    ) {
        ForwarderWarehouseProcessingFeeRecord fee = new ForwarderWarehouseProcessingFeeRecord();
        fee.id = id;
        fee.serviceCode = "ET-WH-PROCESS-20260604";
        fee.feeName = name;
        fee.feeType = type;
        fee.currency = "RMB";
        fee.amount = new BigDecimal(amount);
        fee.billingUnit = unit;
        fee.minCharge = minCharge == null ? null : new BigDecimal(minCharge);
        return fee;
    }

    private ForwarderBasePriceRecord lastMileBasePrice() {
        ForwarderBasePriceRecord price = new ForwarderBasePriceRecord();
        price.id = 4L;
        price.serviceCode = "ET-LAST-MILE-20260604";
        price.priceRuleCode = "ET-20260604-LAST-MILE-RUH-CBM";
        price.cargoCategoryName = "平台仓送仓利雅得";
        price.pricingModel = "PER_CBM";
        price.currency = "RMB";
        price.unitPrice = new BigDecimal("150");
        price.billingUnit = "CBM";
        price.deliveryCity = "利雅得/RUH";
        price.priceStatus = "NORMAL";
        return price;
    }

    private ForwarderTransportFeeRecord transportFee() {
        ForwarderTransportFeeRecord fee = new ForwarderTransportFeeRecord();
        fee.id = 5L;
        fee.serviceCode = "ET-LAST-MILE-20260604";
        fee.feeName = "沙特偏远派送费";
        fee.feeType = "REMOTE_AREA";
        fee.currency = "SAR";
        fee.amount = new BigDecimal("750");
        fee.billingUnit = "SHIPMENT";
        return fee;
    }

    private ForwarderSeaRecommendationRecord seaCandidate(
            String forwarderCode,
            String forwarderName,
            String serviceCode,
            String serviceName,
            String currency,
            String minUnitPrice,
            String billingUnit
    ) {
        ForwarderSeaRecommendationRecord record = new ForwarderSeaRecommendationRecord();
        record.forwarderCode = forwarderCode;
        record.forwarderName = forwarderName;
        record.serviceCode = serviceCode;
        record.serviceName = serviceName;
        record.country = "沙特";
        record.transportMode = "SEA";
        record.targetPlatform = "FBN";
        record.deliveryCity = "利雅得/RUH";
        record.destinationNode = "FBN利雅得仓";
        record.transitTimeText = "40-65天";
        record.priceRuleCount = 3;
        record.currency = currency;
        record.minUnitPrice = new BigDecimal(minUnitPrice);
        record.billingUnit = billingUnit;
        record.cargoCategoryNamesCsv = "普货,沙特海运（A类）";
        return record;
    }

    private ForwarderSeaRecommendationRecord airCandidate(
            String forwarderCode,
            String forwarderName,
            String serviceCode,
            String serviceName,
            String currency,
            String minUnitPrice,
            String volumeDivisor
    ) {
        ForwarderSeaRecommendationRecord record = seaCandidate(
                forwarderCode,
                forwarderName,
                serviceCode,
                serviceName,
                currency,
                minUnitPrice,
                "KG"
        );
        record.transportMode = serviceCode.contains("EXPRESS") ? "EXPRESS" : "AIR";
        record.targetPlatform = "WAREHOUSE";
        record.deliveryCity = "沙特仓";
        record.destinationNode = "沙特仓";
        record.transitTimeText = "8-15天";
        record.cargoCategoryNamesCsv = "沙特空运A类,沙特空运B类";
        record.kgMinUnitPrice = new BigDecimal(minUnitPrice);
        record.cbmMinUnitPrice = null;
        record.volumeDivisor = new BigDecimal(volumeDivisor);
        return record;
    }
}
