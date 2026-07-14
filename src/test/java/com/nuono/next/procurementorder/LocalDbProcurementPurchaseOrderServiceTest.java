package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.AddItemsCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.CreateOrderCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.CreateShippingOrderCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.ItemCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.ShippingOrderSegmentScopeCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.SiteQuantityCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateItemCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateOrderCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateShippingOrderLineYiteMaterialCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateShippingOrderCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderBasePriceRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteSegmentRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderSeaRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderTransportFeeRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderWarehouseProcessingFeeRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderAli1688HistoryRow;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderAli1688PurchaseBatchRow;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ProductArchiveRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ProductForwarderChannelQuoteRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ProductOfferRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderDuplicateItemSiteRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderItemRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderItemSiteRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ProductForwarderDeclarationAttributeRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderSegmentRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.StoreScopeRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.StoreSiteRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderAli1688HistoryView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsQuoteImportView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsQuoteOptionsView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsQuoteReportExportView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsPlanView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderShippingSubmitView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.LogisticsBillView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.ShippingOrderSubmitView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.ShippingOrderView;
import com.nuono.next.productselection.Ali1688CollectionView;
import com.nuono.next.productselection.LocalDbAli1688CollectionService;
import com.nuono.next.productselection.ProductSelectionSourceCollectionRow;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.poi.hssf.usermodel.HSSFClientAnchor;
import org.apache.poi.hssf.usermodel.HSSFPicture;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

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
        lenient().when(mapper.nextProductForwarderChannelQuoteId()).thenReturn(320001L);
        lenient().when(mapper.nextLogisticsExpectedBillId()).thenReturn(330001L);
        lenient().when(mapper.nextLogisticsExpectedBillComponentId()).thenReturn(340001L);
        lenient().when(mapper.nextLogisticsBillReconciliationId()).thenReturn(360001L);
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
    void listLogisticsQuoteOptionsReturnsSupportedForwarderChannelsForUnconfirmedLines() {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        PurchaseOrderLogisticsQuoteLineRecord candidate = quoteLine(null, "PENDING_QUOTE", "NOT_SUBMITTED");
        candidate.plannedTransportMode = "SEA";

        when(mapper.selectOrderById(200001L)).thenReturn(order);
        when(mapper.listLogisticsQuoteCandidatesByOrder(200001L)).thenReturn(List.of(candidate));
        when(mapper.listRouteRecommendationCandidates(List.of("SA"), "SEA")).thenReturn(List.of(
                routeCandidate("SEA", "ZD-SAU-SEA-FBN-RUH", "ZD", "众鸫供应链",
                        "ZD-SAU-SEA-WH-RUH", "众鸫沙特海运仓到仓", "RMB", "1100", "CBM", null),
                routeCandidate("SEA", "YT-SAU-SEA-FBN-RUH", "YT", "义特物流",
                        "YT-SAU-SEA-FBN-RUH", "义特沙特海运双清包税 + FBN利雅得送仓", "RMB", "1500", "CBM", null)
        ));

        PurchaseOrderLogisticsQuoteOptionsView options =
                service.listLogisticsQuoteOptions(access(), "200001");

        assertThat(options.purchaseOrderId).isEqualTo("200001");
        assertThat(options.forwarders).hasSize(1);
        assertThat(options.unsupportedChannelCount).isEqualTo(1);
        assertThat(options.forwarders.get(0).forwarderCode).isEqualTo("YT");
        assertThat(options.forwarders.get(0).forwarderName).isEqualTo("义特物流");
        assertThat(options.forwarders.get(0).templateType).isEqualTo("YITE_B2B_SINGLE_TICKET");
        assertThat(options.forwarders.get(0).channels).hasSize(1);
        assertThat(options.forwarders.get(0).channels.get(0).routeCode).isEqualTo("YT-SAU-SEA-FBN-RUH");
        assertThat(options.forwarders.get(0).channels.get(0).siteCode).isEqualTo("SA");
        assertThat(options.forwarders.get(0).channels.get(0).transportMode).isEqualTo("SEA");
        assertThat(options.forwarders.get(0).channels.get(0).pendingLineCount).isEqualTo(1);
        assertThat(options.forwarders.get(0).channels.get(0).newProductLineCount).isEqualTo(1);
    }

    @Test
    void listLogisticsQuoteOptionsReturnsEtForwarderChannelsWhenTemplateIsConfigured() {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        PurchaseOrderLogisticsQuoteLineRecord candidate = quoteLine(null, "PENDING_QUOTE", "NOT_SUBMITTED");
        candidate.plannedTransportMode = "SEA";

        when(mapper.selectOrderById(200001L)).thenReturn(order);
        when(mapper.listLogisticsQuoteCandidatesByOrder(200001L)).thenReturn(List.of(candidate));
        when(mapper.listRouteRecommendationCandidates(List.of("SA"), "SEA")).thenReturn(List.of(
                routeCandidate("SEA", "ET-SAU-SEA-FBN-RUH-20260604", "ET", "易通物流",
                        "ET-SAU-SEA-WH-20260604", "易通沙特海运仓到仓 20260604", "RMB", "1100", "CBM", null)
        ));

        PurchaseOrderLogisticsQuoteOptionsView options =
                service.listLogisticsQuoteOptions(access(), "200001");

        assertThat(options.forwarders).hasSize(1);
        assertThat(options.unsupportedChannelCount).isZero();
        assertThat(options.forwarders.get(0).forwarderCode).isEqualTo("ET");
        assertThat(options.forwarders.get(0).forwarderName).isEqualTo("易通物流");
        assertThat(options.forwarders.get(0).templateType).isEqualTo("ET_SKU_ONE_STEP_PACKING_IMPORT");
        assertThat(options.forwarders.get(0).templateName).isEqualTo("易通SKU一步上传装箱清单导入模板");
        assertThat(options.forwarders.get(0).channels).hasSize(1);
        assertThat(options.forwarders.get(0).channels.get(0).routeCode).isEqualTo("ET-SAU-SEA-FBN-RUH-20260604");
    }

    @Test
    void exportLogisticsQuoteReportCreatesPendingRowsForCurrentOrderSites() throws Exception {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        PurchaseOrderLogisticsQuoteLineRecord candidate = quoteLine(null, "PENDING_QUOTE", "NOT_SUBMITTED");
        candidate.pskuCode = "ZPSKU-115-SA";
        candidate.barcode = "BARCODE-115";
        candidate.yiteMaterial = "塑料";
        candidate.imageUrlCache = pngDataUrl(140, 70);
        candidate.plannedTransportMode = "SEA";

        when(mapper.selectOrderById(200001L)).thenReturn(order);
        when(mapper.listLogisticsQuoteCandidatesByOrder(200001L)).thenReturn(List.of(candidate));
        when(mapper.listRouteRecommendationCandidates(List.of("SA"), "SEA")).thenReturn(List.of(
                routeCandidate("SEA", "YT-SAU-SEA-FBN-RUH", "YT", "义特物流",
                        "YT-SAU-SEA-FBN-RUH", "义特沙特海运双清包税 + FBN利雅得送仓", "RMB", "1500", "CBM", null)
        ));
        when(mapper.nextLogisticsQuoteLineId()).thenReturn(280001L);

        PurchaseOrderLogisticsQuoteReportExportView export =
                service.exportLogisticsQuoteReport(access(), "200001", "YT", "YT-SAU-SEA-FBN-RUH");

        assertThat(export.filename).isEqualTo("B2B发货审核单-PO-200001.xls");
        assertThat(export.rowCount).isEqualTo(1);
        assertThat(export.pendingCount).isEqualTo(1);
        assertThat(export.content).isNotEmpty();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(export.content))) {
            assertThat(workbook).isInstanceOf(HSSFWorkbook.class);
            assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("模板");
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("客户订单号");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue()).isEqualTo("义特沙特海运双清包税 + FBN利雅得送仓");
            Row header = workbook.getSheetAt(0).getRow(22);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("货箱编号*");
            assertThat(header.getCell(5).getStringCellValue()).isEqualTo("产品SKU*");
            Row row = workbook.getSheetAt(0).getRow(23);
            assertThat(row.getCell(5).getStringCellValue()).isEqualTo("BARCODE-115");
            assertThat(row.getCell(6).getStringCellValue()).isEqualTo("Orange Medium Marker Storage Box");
            assertThat(row.getCell(7).getStringCellValue()).isEqualTo("测试商品");
            assertThat(row.getCell(8).getNumericCellValue()).isEqualTo(20);
            assertThat(row.getCell(10).getStringCellValue()).isEqualTo("塑料");
            assertThat(row.getCell(20).getStringCellValue()).isEqualTo("280001");
            assertThat(row.getCell(23).getStringCellValue()).isEqualTo("220002");
            assertThat(row.getCell(24).getStringCellValue()).isEqualTo("YT");
            assertThat(workbook.getSheetAt(0).isColumnHidden(20)).isTrue();
            assertThat(((HSSFWorkbook) workbook).getAllPictures()).hasSize(1);
            HSSFClientAnchor imageAnchor = firstPictureAnchor((HSSFSheet) workbook.getSheetAt(0));
            assertThat((int) imageAnchor.getCol1()).isEqualTo(11);
            assertThat((int) imageAnchor.getCol2()).isEqualTo(12);
            assertThat(imageAnchor.getRow1()).isEqualTo(23);
            assertThat(imageAnchor.getRow2()).isEqualTo(24);
            assertThat(imageAnchor.getDx1()).isZero();
            assertThat(imageAnchor.getDy1()).isZero();
            assertThat(imageAnchor.getDx2()).isZero();
            assertThat(imageAnchor.getDy2()).isZero();
            assertThat(yiteImageColumnWidthPx((HSSFSheet) workbook.getSheetAt(0))).isCloseTo(70D, org.assertj.core.data.Offset.offset(1D));
            assertThat(yiteImageRowHeightPx(workbook.getSheetAt(0).getRow(23))).isCloseTo(70D, org.assertj.core.data.Offset.offset(1D));
            BufferedImage embeddedImage = ImageIO.read(new ByteArrayInputStream(
                    ((HSSFWorkbook) workbook).getAllPictures().get(0).getData()
            ));
            assertThat(embeddedImage.getWidth()).isEqualTo(400);
            assertThat(embeddedImage.getHeight()).isEqualTo(400);
            assertThat(nonTransparentWidth(embeddedImage)).isEqualTo(400);
            assertThat(nonTransparentHeight(embeddedImage)).isCloseTo(200, org.assertj.core.data.Offset.offset(1));
            assertThat((double) nonTransparentWidth(embeddedImage) / nonTransparentHeight(embeddedImage))
                    .isCloseTo(2D, org.assertj.core.data.Offset.offset(0.05D));
        }
        ArgumentCaptor<PurchaseOrderLogisticsQuoteLineRecord> rowCaptor =
                ArgumentCaptor.forClass(PurchaseOrderLogisticsQuoteLineRecord.class);
        verify(mapper).insertLogisticsQuoteLine(rowCaptor.capture(), eq(307L));
        assertThat(rowCaptor.getValue().id).isEqualTo(280001L);
        assertThat(rowCaptor.getValue().purchaseOrderItemSiteId).isEqualTo(220002L);
        assertThat(rowCaptor.getValue().quoteStatus).isEqualTo("PENDING_QUOTE");
        assertThat(rowCaptor.getValue().shippingSubmitStatus).isEqualTo("NOT_SUBMITTED");
        ArgumentCaptor<PurchaseOrderLogisticsQuoteLineRecord> assignmentCaptor =
                ArgumentCaptor.forClass(PurchaseOrderLogisticsQuoteLineRecord.class);
        verify(mapper).assignLogisticsQuoteLineChannel(assignmentCaptor.capture(), eq(307L));
        assertThat(assignmentCaptor.getValue().forwarderCode).isEqualTo("YT");
        assertThat(assignmentCaptor.getValue().routeCode).isEqualTo("YT-SAU-SEA-FBN-RUH");
        assertThat(assignmentCaptor.getValue().serviceCode).isEqualTo("YT-SAU-SEA-FBN-RUH");
        verify(mapper).markLogisticsQuoteLinesExported(200001L, List.of(280001L), 307L);
    }

    @Test
    void exportLogisticsQuoteReportUsesEtSkuOneStepPackingTemplateWithProductInfo() throws Exception {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        PurchaseOrderLogisticsQuoteLineRecord candidate = quoteLine(null, "PENDING_QUOTE", "NOT_SUBMITTED");
        candidate.plannedTransportMode = "SEA";
        candidate.imageUrlCache = pngDataUrl(140, 70);
        PurchaseOrderLogisticsQuoteLineRecord secondCandidate = quoteLine(null, "PENDING_QUOTE", "NOT_SUBMITTED");
        secondCandidate.purchaseOrderItemId = 210002L;
        secondCandidate.purchaseOrderItemSiteId = 220003L;
        secondCandidate.partnerSku = "SGGRB116";
        secondCandidate.pskuCode = "SGGRB116";
        secondCandidate.barcode = "BARCODE-116";
        secondCandidate.titleCache = "补充测试商品";
        String longEtEnglishShortName = "Replacement Water Filter Cartridge for Refrigerator Kitchen Faucet "
                + "Drinking Water Purifier System Extra Long Name";
        secondCandidate.titleEn = longEtEnglishShortName;
        secondCandidate.imageUrlCache = pngDataUrl(70, 140);
        secondCandidate.quantity = 30;
        secondCandidate.plannedTransportMode = "SEA";

        when(mapper.selectOrderById(200001L)).thenReturn(order);
        when(mapper.listLogisticsQuoteCandidatesByOrder(200001L)).thenReturn(List.of(candidate, secondCandidate));
        when(mapper.listRouteRecommendationCandidates(List.of("SA"), "SEA")).thenReturn(List.of(
                routeCandidate("SEA", "ET-SAU-SEA-FBN-RUH-20260604", "ET", "易通物流",
                        "ET-SAU-SEA-WH-20260604", "易通沙特海运仓到仓 20260604", "RMB", "1100", "CBM", null)
        ));
        when(mapper.nextLogisticsQuoteLineId()).thenReturn(280001L, 280002L);

        PurchaseOrderLogisticsQuoteReportExportView export =
                service.exportLogisticsQuoteReport(access(), "200001", "ET", "ET-SAU-SEA-FBN-RUH-20260604");

        assertThat(export.filename).isEqualTo("sku一步上传装箱清单导入模版-PO-200001.xlsx");
        assertThat(export.contentType).isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(export.rowCount).isEqualTo(2);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(export.content))) {
            assertThat(workbook).isInstanceOf(XSSFWorkbook.class);
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("装箱清单");
            assertThat(workbook.getSheetAt(1).getSheetName()).isEqualTo("填表指南");
            assertThat(workbook.getSheetAt(2).getSheetName()).isEqualTo("仓单地址及须知");
            Row instruction = workbook.getSheetAt(0).getRow(0);
            assertThat(instruction.getCell(0).getStringCellValue()).contains("产品信息和装箱清单同时导入");
            Row header = workbook.getSheetAt(0).getRow(1);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("箱号");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("*长(CM)");
            assertThat(header.getCell(5).getStringCellValue()).isEqualTo("*每箱数量");
            assertThat(header.getCell(6).getStringCellValue()).isEqualTo("*商家条码");
            assertThat(header.getCell(8).getStringCellValue()).isEqualTo("*英文简称");
            assertThat(header.getCell(9).getStringCellValue()).isEqualTo("*中文品名");
            assertThat(header.getCell(13).getStringCellValue()).isEqualTo("*图片");
            assertThat(header.getCell(14).getStringCellValue()).isEqualTo("*材质");
            Row row = workbook.getSheetAt(0).getRow(2);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("1/1");
            assertThat(row.getCell(1).getNumericCellValue()).isEqualTo(30);
            assertThat(row.getCell(2).getNumericCellValue()).isEqualTo(30);
            assertThat(row.getCell(3).getNumericCellValue()).isEqualTo(30);
            assertThat(row.getCell(4).getNumericCellValue()).isEqualTo(10);
            assertThat(row.getCell(5).getNumericCellValue()).isEqualTo(20);
            assertThat(row.getCell(6).getStringCellValue()).isEqualTo("BARCODE-115");
            assertThat(row.getCell(7).getStringCellValue()).isEqualTo("SGGRB115");
            assertThat(row.getCell(8).getStringCellValue()).isEqualTo("Orange Medium Marker Storage Box");
            assertThat(row.getCell(9).getStringCellValue()).isEqualTo("测试商品");
            assertThat(row.getCell(10).getNumericCellValue()).isEqualTo(1);
            assertThat(row.getCell(11).getStringCellValue()).isEqualTo("paper says");
            assertThat(row.getCell(12).getStringCellValue()).isEqualTo("paper says");
            assertThat(row.getCell(14).getStringCellValue()).isEmpty();
            assertThat(row.getCell(15).getStringCellValue()).isEqualTo("否");
            assertThat(row.getCell(16).getStringCellValue()).isEqualTo("否");
            assertThat(row.getCell(17).getStringCellValue()).isEqualTo("否");
            assertThat(row.getCell(18).getStringCellValue()).isEqualTo("否");
            assertThat(row.getCell(19).getStringCellValue()).isEqualTo("否");
            assertThat(row.getCell(52).getStringCellValue()).isEqualTo("280001");
            assertThat(row.getCell(55).getStringCellValue()).isEqualTo("220002");
            assertThat(row.getCell(56).getStringCellValue()).isEqualTo("ET");
            Row secondRow = workbook.getSheetAt(0).getRow(3);
            assertThat(secondRow.getCell(0).getStringCellValue()).isEqualTo("1/2");
            assertThat(secondRow.getCell(1).getNumericCellValue()).isEqualTo(30);
            assertThat(secondRow.getCell(2).getNumericCellValue()).isEqualTo(30);
            assertThat(secondRow.getCell(3).getNumericCellValue()).isEqualTo(30);
            assertThat(secondRow.getCell(4).getNumericCellValue()).isEqualTo(10);
            assertThat(secondRow.getCell(5).getNumericCellValue()).isEqualTo(30);
            assertThat(secondRow.getCell(6).getStringCellValue()).isEqualTo("BARCODE-116");
            assertThat(secondRow.getCell(7).getStringCellValue()).isEqualTo("SGGRB116");
            assertThat(secondRow.getCell(8).getStringCellValue()).hasSize(90);
            assertThat(secondRow.getCell(8).getStringCellValue()).isEqualTo(longEtEnglishShortName.substring(0, 90));
            assertThat(secondRow.getCell(9).getStringCellValue()).isEqualTo("补充测试商品");
            assertThat(secondRow.getCell(52).getStringCellValue()).isEqualTo("280002");
            assertThat(secondRow.getCell(55).getStringCellValue()).isEqualTo("220003");
            assertThat(workbook.getSheetAt(0).isColumnHidden(52)).isTrue();
            assertThat(((XSSFWorkbook) workbook).getAllPictures()).hasSize(2);
            BufferedImage embeddedImage = ImageIO.read(new ByteArrayInputStream(
                    ((XSSFWorkbook) workbook).getAllPictures().get(0).getData()
            ));
            assertThat(embeddedImage.getWidth()).isEqualTo(400);
            assertThat(embeddedImage.getHeight()).isEqualTo(400);
            assertThat(nonTransparentWidth(embeddedImage)).isEqualTo(400);
            assertThat(nonTransparentHeight(embeddedImage)).isCloseTo(200, org.assertj.core.data.Offset.offset(1));
        }
        ArgumentCaptor<PurchaseOrderLogisticsQuoteLineRecord> assignmentCaptor =
                ArgumentCaptor.forClass(PurchaseOrderLogisticsQuoteLineRecord.class);
        verify(mapper, org.mockito.Mockito.times(2)).assignLogisticsQuoteLineChannel(assignmentCaptor.capture(), eq(307L));
        assertThat(assignmentCaptor.getAllValues())
                .extracting(line -> line.forwarderCode, line -> line.routeCode, line -> line.serviceCode)
                .containsOnly(org.assertj.core.groups.Tuple.tuple(
                        "ET",
                        "ET-SAU-SEA-FBN-RUH-20260604",
                        "ET-SAU-SEA-WH-20260604"
                ));
        verify(mapper).markLogisticsQuoteLinesExported(200001L, List.of(280001L, 280002L), 307L);
    }

    @Test
    void importLogisticsQuoteReportConfirmsRowsByHiddenItemSiteId() throws Exception {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        PurchaseOrderLogisticsQuoteLineRecord line = quoteLine(280001L, "PENDING_QUOTE", "NOT_SUBMITTED");

        when(mapper.selectOrderById(200001L)).thenReturn(order);
        when(mapper.selectLogisticsQuoteLineByItemSiteForUpdate(200001L, 220002L)).thenReturn(line);

        PurchaseOrderLogisticsQuoteImportView result = service.importLogisticsQuoteReport(
                access(),
                "200001",
                new ByteArrayInputStream(quoteWorkbookBytes()),
                "物流报价.xlsx"
        );

        assertThat(result.totalRows).isEqualTo(1);
        assertThat(result.updatedRows).isEqualTo(1);
        assertThat(result.errors).isEmpty();
        ArgumentCaptor<PurchaseOrderLogisticsQuoteLineRecord> rowCaptor =
                ArgumentCaptor.forClass(PurchaseOrderLogisticsQuoteLineRecord.class);
        verify(mapper).confirmLogisticsQuoteLine(rowCaptor.capture(), eq(307L));
        assertThat(rowCaptor.getValue().id).isEqualTo(280001L);
        assertThat(rowCaptor.getValue().quoteStatus).isEqualTo("CONFIRMED");
        assertThat(rowCaptor.getValue().shippingSubmitStatus).isEqualTo("NOT_SUBMITTED");
        assertThat(rowCaptor.getValue().forwarderName).isEqualTo("易通");
        assertThat(rowCaptor.getValue().currency).isEqualTo("RMB");
        assertThat(rowCaptor.getValue().unitPrice).isEqualByComparingTo("12.50");
        assertThat(rowCaptor.getValue().estimatedAmount).isEqualByComparingTo("250.00");
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never()).markHistoricalProductForwarderChannelQuote(
                anyLong(),
                any(),
                anyLong(),
                any(),
                anyLong(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyLong()
        );
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never())
                .insertProductForwarderChannelQuote(any(), anyLong());
    }

    @Test
    void importYiteTemplateConfirmsRowsAfterLogisticsFillsBoxInfo() throws Exception {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        PurchaseOrderLogisticsQuoteLineRecord line = quoteLine(280001L, "PENDING_QUOTE", "NOT_SUBMITTED");

        when(mapper.selectOrderById(200001L)).thenReturn(order);
        when(mapper.selectLogisticsQuoteLineByItemSiteForUpdate(200001L, 220002L)).thenReturn(line);

        PurchaseOrderLogisticsQuoteImportView result = service.importLogisticsQuoteReport(
                access(),
                "200001",
                new ByteArrayInputStream(yiteTemplateWorkbookBytes()),
                "义特海外无忧-B2B单票导入模版.xls"
        );

        assertThat(result.totalRows).isEqualTo(1);
        assertThat(result.updatedRows).isEqualTo(1);
        assertThat(result.errors).isEmpty();
        ArgumentCaptor<PurchaseOrderLogisticsQuoteLineRecord> rowCaptor =
                ArgumentCaptor.forClass(PurchaseOrderLogisticsQuoteLineRecord.class);
        verify(mapper).confirmLogisticsQuoteLine(rowCaptor.capture(), eq(307L));
        assertThat(rowCaptor.getValue().id).isEqualTo(280001L);
        assertThat(rowCaptor.getValue().quoteStatus).isEqualTo("CONFIRMED");
        assertThat(rowCaptor.getValue().shippingSubmitStatus).isEqualTo("NOT_SUBMITTED");
        assertThat(rowCaptor.getValue().forwarderCode).isEqualTo("YT");
        assertThat(rowCaptor.getValue().forwarderName).isEqualTo("义特物流");
        assertThat(rowCaptor.getValue().serviceName).isEqualTo("沙特海运双清");
        assertThat(rowCaptor.getValue().unitPrice).isEqualByComparingTo("12.50");
        assertThat(rowCaptor.getValue().remark).isEqualTo("义特模板回传确认");
    }

    @Test
    void importYiteTemplateCanMatchShippingOrderRowsByProductCodeAndVisiblePriceOnly() throws Exception {
        ProcurementPurchaseOrderRecords.ShippingOrderRecord shippingOrder = shippingOrder();
        PurchaseOrderLogisticsQuoteLineRecord line = quoteLine(280001L, "PENDING_QUOTE", "SUBMITTED");
        line.shippingOrderId = 290001L;
        line.shippingOrderNo = "SO-290001";
        line.shippingOrderSegmentId = 292001L;
        line.forwarderCode = "YT";
        ShippingOrderSegmentScopeCommand command = new ShippingOrderSegmentScopeCommand();
        command.segmentIds = List.of("292001");

        when(mapper.selectShippingOrderById(290001L)).thenReturn(shippingOrder);
        when(mapper.listLogisticsQuoteCandidatesByShippingOrder(290001L)).thenReturn(List.of(line));

        PurchaseOrderLogisticsQuoteImportView result = service.importShippingOrderLogisticsQuoteReport(
                access(),
                "290001",
                new ByteArrayInputStream(yiteTemplateWorkbookWithoutHiddenIdsBytes()),
                "B2B发货审核单-SO-290001-已报价.xls",
                command
        );

        assertThat(result.totalRows).isEqualTo(1);
        assertThat(result.updatedRows).isEqualTo(1);
        assertThat(result.errors).isEmpty();
        ArgumentCaptor<PurchaseOrderLogisticsQuoteLineRecord> rowCaptor =
                ArgumentCaptor.forClass(PurchaseOrderLogisticsQuoteLineRecord.class);
        verify(mapper).confirmLogisticsQuoteLine(rowCaptor.capture(), eq(307L));
        assertThat(rowCaptor.getValue().id).isEqualTo(280001L);
        assertThat(rowCaptor.getValue().quoteStatus).isEqualTo("CONFIRMED");
        assertThat(rowCaptor.getValue().shippingSubmitStatus).isEqualTo("SUBMITTED");
        assertThat(rowCaptor.getValue().unitPrice).isEqualByComparingTo("13.50");
    }

    @Test
    void importYiteTemplateFallsBackToProductCodeWhenHiddenShippingOrderItemSiteIdIsStale() throws Exception {
        ProcurementPurchaseOrderRecords.ShippingOrderRecord shippingOrder = shippingOrder();
        PurchaseOrderLogisticsQuoteLineRecord line = quoteLine(280001L, "PENDING_QUOTE", "SUBMITTED");
        line.shippingOrderId = 290001L;
        line.shippingOrderNo = "SO-290001";
        line.shippingOrderSegmentId = 292001L;
        line.forwarderCode = "YT";
        ShippingOrderSegmentScopeCommand command = new ShippingOrderSegmentScopeCommand();
        command.segmentIds = List.of("292001");

        when(mapper.selectShippingOrderById(290001L)).thenReturn(shippingOrder);
        lenient().when(mapper.listLogisticsQuoteCandidatesByShippingOrder(290001L)).thenReturn(List.of(line));

        PurchaseOrderLogisticsQuoteImportView result = service.importShippingOrderLogisticsQuoteReport(
                access(),
                "290001",
                new ByteArrayInputStream(yiteTemplateWorkbookBytes()),
                "B2B发货审核单-SO-290006-已报价.xls",
                command
        );

        assertThat(result.totalRows).isEqualTo(1);
        assertThat(result.updatedRows).isEqualTo(1);
        assertThat(result.errors).isEmpty();
        verify(mapper).selectLogisticsQuoteLineByShippingOrderItemSiteForUpdate(290001L, 220002L);
        verify(mapper).listLogisticsQuoteCandidatesByShippingOrder(290001L);
        ArgumentCaptor<PurchaseOrderLogisticsQuoteLineRecord> rowCaptor =
                ArgumentCaptor.forClass(PurchaseOrderLogisticsQuoteLineRecord.class);
        verify(mapper).confirmLogisticsQuoteLine(rowCaptor.capture(), eq(307L));
        assertThat(rowCaptor.getValue().id).isEqualTo(280001L);
        assertThat(rowCaptor.getValue().unitPrice).isEqualByComparingTo("12.50");
    }

    @Test
    void importYiteTemplateAppliesProductCodeFallbackToDuplicateShippingOrderRows() throws Exception {
        ProcurementPurchaseOrderRecords.ShippingOrderRecord shippingOrder = shippingOrder();
        PurchaseOrderLogisticsQuoteLineRecord firstLine = quoteLine(280001L, "PENDING_QUOTE", "SUBMITTED");
        firstLine.shippingOrderId = 290001L;
        firstLine.shippingOrderNo = "SO-290001";
        firstLine.shippingOrderSegmentId = 292001L;
        firstLine.forwarderCode = "YT";
        PurchaseOrderLogisticsQuoteLineRecord secondLine = quoteLine(280002L, "PENDING_QUOTE", "SUBMITTED");
        secondLine.purchaseOrderItemSiteId = 220003L;
        secondLine.shippingOrderId = 290001L;
        secondLine.shippingOrderNo = "SO-290001";
        secondLine.shippingOrderSegmentId = 292001L;
        secondLine.forwarderCode = "YT";
        ShippingOrderSegmentScopeCommand command = new ShippingOrderSegmentScopeCommand();
        command.segmentIds = List.of("292001");

        when(mapper.selectShippingOrderById(290001L)).thenReturn(shippingOrder);
        lenient().when(mapper.listLogisticsQuoteCandidatesByShippingOrder(290001L))
                .thenReturn(List.of(firstLine, secondLine));

        PurchaseOrderLogisticsQuoteImportView result = service.importShippingOrderLogisticsQuoteReport(
                access(),
                "290001",
                new ByteArrayInputStream(yiteTemplateWorkbookBytes()),
                "B2B发货审核单-SO-290006-已报价.xls",
                command
        );

        assertThat(result.totalRows).isEqualTo(1);
        assertThat(result.updatedRows).isEqualTo(1);
        assertThat(result.errors).isEmpty();
        ArgumentCaptor<PurchaseOrderLogisticsQuoteLineRecord> rowCaptor =
                ArgumentCaptor.forClass(PurchaseOrderLogisticsQuoteLineRecord.class);
        verify(mapper, org.mockito.Mockito.times(2)).confirmLogisticsQuoteLine(rowCaptor.capture(), eq(307L));
        assertThat(rowCaptor.getAllValues())
                .extracting(line -> line.id)
                .containsExactlyInAnyOrder(280001L, 280002L);
        assertThat(rowCaptor.getAllValues())
                .extracting(line -> line.unitPrice)
                .allSatisfy(price -> assertThat(price).isEqualByComparingTo("12.50"));
    }

    @Test
    void importEtTemplateConfirmsRowsAfterLogisticsFillsBoxInfo() throws Exception {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        PurchaseOrderLogisticsQuoteLineRecord line = quoteLine(280001L, "PENDING_QUOTE", "NOT_SUBMITTED");

        when(mapper.selectOrderById(200001L)).thenReturn(order);
        when(mapper.selectLogisticsQuoteLineByItemSiteForUpdate(200001L, 220002L)).thenReturn(line);

        PurchaseOrderLogisticsQuoteImportView result = service.importLogisticsQuoteReport(
                access(),
                "200001",
                new ByteArrayInputStream(etTemplateWorkbookBytes()),
                "sku一步上传装箱清单导入模版.xlsx"
        );

        assertThat(result.totalRows).isEqualTo(1);
        assertThat(result.updatedRows).isEqualTo(1);
        assertThat(result.errors).isEmpty();
        ArgumentCaptor<PurchaseOrderLogisticsQuoteLineRecord> rowCaptor =
                ArgumentCaptor.forClass(PurchaseOrderLogisticsQuoteLineRecord.class);
        verify(mapper).confirmLogisticsQuoteLine(rowCaptor.capture(), eq(307L));
        assertThat(rowCaptor.getValue().id).isEqualTo(280001L);
        assertThat(rowCaptor.getValue().quoteStatus).isEqualTo("CONFIRMED");
        assertThat(rowCaptor.getValue().shippingSubmitStatus).isEqualTo("NOT_SUBMITTED");
        assertThat(rowCaptor.getValue().forwarderCode).isEqualTo("ET");
        assertThat(rowCaptor.getValue().forwarderName).isEqualTo("易通物流");
        assertThat(rowCaptor.getValue().routeCode).isEqualTo("ET-SAU-SEA-FBN-RUH-20260604");
        assertThat(rowCaptor.getValue().serviceName).isEqualTo("易通沙特海运仓到仓 20260604");
            assertThat(rowCaptor.getValue().remark).contains("易通模板回传确认")
                    .contains("箱号=1/1")
                    .contains("50x40x30cm")
                    .contains("箱重=10kg")
                    .contains("商家条码=SGGRB115")
                    .contains("数量=20");
    }

    @Test
    void importShippingOrderLogisticsQuoteReportSkipsRowsOutsideSelectedSegment() throws Exception {
        ProcurementPurchaseOrderRecords.ShippingOrderRecord shippingOrder = shippingOrder();
        PurchaseOrderLogisticsQuoteLineRecord line = quoteLine(280001L, "PENDING_QUOTE", "NOT_SUBMITTED");
        line.shippingOrderId = 290001L;
        line.shippingOrderSegmentId = 292002L;
        ShippingOrderSegmentScopeCommand command = new ShippingOrderSegmentScopeCommand();
        command.segmentIds = List.of("292001");

        when(mapper.selectShippingOrderById(290001L)).thenReturn(shippingOrder);
        when(mapper.selectLogisticsQuoteLineByShippingOrderItemSiteForUpdate(290001L, 220002L)).thenReturn(line);

        PurchaseOrderLogisticsQuoteImportView result = service.importShippingOrderLogisticsQuoteReport(
                access(),
                "290001",
                new ByteArrayInputStream(etTemplateWorkbookBytes()),
                "sku一步上传装箱清单导入模版.xlsx",
                command
        );

        assertThat(result.totalRows).isEqualTo(1);
        assertThat(result.updatedRows).isZero();
        assertThat(result.errors).hasSize(1);
        assertThat(result.errors.get(0).message).contains("不属于当前筛选的子发货单");
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never()).confirmLogisticsQuoteLine(any(), anyLong());
    }

    @Test
    void importShippingOrderLogisticsQuoteReportKeepsSubmittedRowsSubmitted() throws Exception {
        ProcurementPurchaseOrderRecords.ShippingOrderRecord shippingOrder = shippingOrder();
        shippingOrder.shippingSubmitStatus = "SUBMITTED";
        PurchaseOrderLogisticsQuoteLineRecord line = quoteLine(280001L, "PENDING_QUOTE", "SUBMITTED");
        line.shippingOrderId = 290001L;
        line.shippingOrderNo = "SO-290001";
        line.shippingOrderSegmentId = 292001L;
        ShippingOrderSegmentScopeCommand command = new ShippingOrderSegmentScopeCommand();
        command.segmentIds = List.of("292001");

        when(mapper.selectShippingOrderById(290001L)).thenReturn(shippingOrder);
        when(mapper.selectLogisticsQuoteLineByShippingOrderItemSiteForUpdate(290001L, 220002L)).thenReturn(line);

        PurchaseOrderLogisticsQuoteImportView result = service.importShippingOrderLogisticsQuoteReport(
                access(),
                "290001",
                new ByteArrayInputStream(etTemplateWorkbookBytes()),
                "sku一步上传装箱清单导入模版.xlsx",
                command
        );

        assertThat(result.totalRows).isEqualTo(1);
        assertThat(result.updatedRows).isEqualTo(1);
        assertThat(result.errors).isEmpty();
        ArgumentCaptor<PurchaseOrderLogisticsQuoteLineRecord> rowCaptor =
                ArgumentCaptor.forClass(PurchaseOrderLogisticsQuoteLineRecord.class);
        verify(mapper).confirmLogisticsQuoteLine(rowCaptor.capture(), eq(307L));
        assertThat(rowCaptor.getValue().quoteStatus).isEqualTo("CONFIRMED");
        assertThat(rowCaptor.getValue().shippingSubmitStatus).isEqualTo("SUBMITTED");
    }

    @Test
    void generateShippingOrderExpectedBillCreatesBillComponentsAndPendingReconciliation() {
        ProcurementPurchaseOrderRecords.ShippingOrderRecord shippingOrder = shippingOrder();
        PurchaseOrderLogisticsQuoteLineRecord line = quoteLine(280001L, "CONFIRMED", "NOT_SUBMITTED");
        line.shippingOrderId = 290001L;
        line.shippingOrderNo = "SO-290001";
        line.forwarderCode = "YT";
        line.forwarderName = "义特物流";
        line.routeCode = "YT-SAU-SEA-FBN-RUH";
        line.routeName = "义特沙特海运双清包税 + FBN利雅得送仓";
        line.serviceCode = "YT-SAU-SEA-FBN-RUH";
        line.serviceName = "义特沙特海运双清包税 + FBN利雅得送仓";
        line.currency = "RMB";
        line.billingUnit = "CBM";
        line.unitPrice = new BigDecimal("1500.00");
        line.estimatedAmount = new BigDecimal("300.00");

        when(mapper.selectShippingOrderById(290001L)).thenReturn(shippingOrder);
        when(mapper.listLogisticsQuoteCandidatesByShippingOrder(290001L)).thenReturn(List.of(line));

        LogisticsBillView view = service.generateShippingOrderExpectedBill(access(), "290001");

        assertThat(view.expectedBillNo).isEqualTo("EB-330001");
        assertThat(view.shippingOrderNo).isEqualTo("SO-290001");
        assertThat(view.expectedTotalAmount).isEqualByComparingTo("300.00");
        assertThat(view.reconciliationStatus).isEqualTo("PENDING_ACTUAL_BILL");

        ArgumentCaptor<ProcurementPurchaseOrderRecords.LogisticsExpectedBillRecord> billCaptor =
                ArgumentCaptor.forClass(ProcurementPurchaseOrderRecords.LogisticsExpectedBillRecord.class);
        ArgumentCaptor<ProcurementPurchaseOrderRecords.LogisticsExpectedBillComponentRecord> componentCaptor =
                ArgumentCaptor.forClass(ProcurementPurchaseOrderRecords.LogisticsExpectedBillComponentRecord.class);
        ArgumentCaptor<ProcurementPurchaseOrderRecords.LogisticsBillReconciliationRecord> reconciliationCaptor =
                ArgumentCaptor.forClass(ProcurementPurchaseOrderRecords.LogisticsBillReconciliationRecord.class);
        verify(mapper).cancelOpenLogisticsExpectedBills(307L, 290001L, null, 307L);
        verify(mapper).insertLogisticsExpectedBill(billCaptor.capture(), eq(307L));
        verify(mapper).insertLogisticsExpectedBillComponent(componentCaptor.capture(), eq(307L));
        verify(mapper).insertLogisticsBillReconciliation(reconciliationCaptor.capture(), eq(307L));
        assertThat(billCaptor.getValue().id).isEqualTo(330001L);
        assertThat(billCaptor.getValue().expectedTotalAmount).isEqualByComparingTo("300.00");
        assertThat(componentCaptor.getValue().quoteLineId).isEqualTo(280001L);
        assertThat(componentCaptor.getValue().barcode).isEqualTo("BARCODE-115");
        assertThat(componentCaptor.getValue().expectedAmount).isEqualByComparingTo("300.00");
        assertThat(reconciliationCaptor.getValue().reconciliationStatus).isEqualTo("PENDING_ACTUAL_BILL");
    }

    @Test
    void generateShippingOrderExpectedBillUsesSelectedSegmentScope() {
        ProcurementPurchaseOrderRecords.ShippingOrderRecord shippingOrder = shippingOrder();
        PurchaseOrderLogisticsQuoteLineRecord line = quoteLine(280001L, "CONFIRMED", "NOT_SUBMITTED");
        line.shippingOrderId = 290001L;
        line.shippingOrderNo = "SO-290001";
        line.shippingOrderSegmentId = 292001L;
        line.shippingOrderSegmentNo = "SO-290001-SA-AIR";
        line.shippingOrderLineId = 291001L;
        line.forwarderCode = "YT";
        line.forwarderName = "义特物流";
        line.routeCode = "YT-SAU-AIR-FBN-RUH";
        line.routeName = "义特沙特空运";
        line.serviceCode = "YT-SAU-AIR-FBN-RUH";
        line.serviceName = "义特沙特空运";
        line.currency = "RMB";
        line.billingUnit = "KG";
        line.unitPrice = new BigDecimal("12.00");
        line.estimatedAmount = new BigDecimal("240.00");
        ShippingOrderSegmentScopeCommand command = new ShippingOrderSegmentScopeCommand();
        command.segmentIds = List.of("292001");

        when(mapper.selectShippingOrderById(290001L)).thenReturn(shippingOrder);
        when(mapper.listLogisticsQuoteCandidatesByShippingOrderSegments(290001L, List.of(292001L))).thenReturn(List.of(line));

        LogisticsBillView view = service.generateShippingOrderExpectedBill(access(), "290001", command);

        assertThat(view.shippingOrderSegmentId).isEqualTo("292001");
        assertThat(view.shippingOrderSegmentNo).isEqualTo("SO-290001-SA-AIR");
        ArgumentCaptor<ProcurementPurchaseOrderRecords.LogisticsExpectedBillRecord> billCaptor =
                ArgumentCaptor.forClass(ProcurementPurchaseOrderRecords.LogisticsExpectedBillRecord.class);
        ArgumentCaptor<ProcurementPurchaseOrderRecords.LogisticsExpectedBillComponentRecord> componentCaptor =
                ArgumentCaptor.forClass(ProcurementPurchaseOrderRecords.LogisticsExpectedBillComponentRecord.class);
        verify(mapper).cancelOpenLogisticsExpectedBills(307L, 290001L, 292001L, 307L);
        verify(mapper).insertLogisticsExpectedBill(billCaptor.capture(), eq(307L));
        verify(mapper).insertLogisticsExpectedBillComponent(componentCaptor.capture(), eq(307L));
        assertThat(billCaptor.getValue().shippingOrderSegmentId).isEqualTo(292001L);
        assertThat(billCaptor.getValue().shippingOrderSegmentNo).isEqualTo("SO-290001-SA-AIR");
        assertThat(componentCaptor.getValue().shippingOrderSegmentId).isEqualTo(292001L);
    }

    @Test
    void generateShippingOrderExpectedBillRejectsUnconfirmedLegacyProductChannelQuoteFallback() {
        ProcurementPurchaseOrderRecords.ShippingOrderRecord shippingOrder = shippingOrder();
        PurchaseOrderLogisticsQuoteLineRecord line = quoteLine(280001L, "PENDING_QUOTE", "NOT_SUBMITTED");
        line.shippingOrderId = 290001L;
        line.shippingOrderNo = "SO-290001";
        line.plannedTransportMode = "SEA";
        line.forwarderCode = "YT";
        line.forwarderName = "义特物流";
        line.routeCode = "YT-SAU-SEA-FBN-RUH";
        line.routeName = "义特沙特海运双清包税 + FBN利雅得送仓";
        line.serviceCode = "YT-SAU-SEA-FBN-RUH";
        line.serviceName = "义特沙特海运双清包税 + FBN利雅得送仓";

        when(mapper.selectShippingOrderById(290001L)).thenReturn(shippingOrder);
        when(mapper.listLogisticsQuoteCandidatesByShippingOrder(290001L)).thenReturn(List.of(line));

        assertThatThrownBy(() -> service.generateShippingOrderExpectedBill(access(), "290001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("物流报价未确认");
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never()).confirmLogisticsQuoteLine(any(), anyLong());
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never()).insertLogisticsExpectedBill(any(), anyLong());
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never()).insertLogisticsBillReconciliation(any(), anyLong());
    }

    @Test
    void submitShippingRequiresAllLogisticsQuotesConfirmed() {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        when(mapper.selectOrderById(200001L)).thenReturn(order);
        when(mapper.countUnconfirmedLogisticsQuoteLines(200001L)).thenReturn(1);

        assertThatThrownBy(() -> service.submitShipping(access(), "200001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("物流报价未确认");
    }

    @Test
    void submitShippingMarksConfirmedQuoteRowsForWarehousePacking() {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        when(mapper.selectOrderById(200001L)).thenReturn(order);
        when(mapper.countUnconfirmedLogisticsQuoteLines(200001L)).thenReturn(0);
        when(mapper.submitLogisticsQuoteLinesForShipping(200001L, 307L)).thenReturn(1);

        PurchaseOrderShippingSubmitView view = service.submitShipping(access(), "200001");

        assertThat(view.purchaseOrderId).isEqualTo("200001");
        assertThat(view.shippingSubmitStatus).isEqualTo("SUBMITTED");
        assertThat(view.submittedLineCount).isEqualTo(1);
    }

    @Test
    void listOrdersCanReturnSubmittedOnlyForShippingOrderSelection() {
        PurchaseOrderRecord submitted = order("SGGR-0607", "人工补货");
        submitted.status = "SUBMITTED";
        when(mapper.listOrdersByOwner(307L, Set.of("STR69486-NSA"), "", true, false, 120))
                .thenReturn(List.of(submitted));
        when(mapper.listLogisticsQuoteCandidatesByOrder(200001L)).thenReturn(List.of());
        when(mapper.listItemSitesByOrder(200001L)).thenReturn(List.of());
        when(mapper.listItemsByOrder(200001L)).thenReturn(List.of());

        List<PurchaseOrderView> orders = service.listOrders(access(), null, null, true);

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).status).isEqualTo("submitted");
        verify(mapper).listOrdersByOwner(307L, Set.of("STR69486-NSA"), "", true, false, 120);
    }

    @Test
    void listShippingOrdersReturnsMissingYiteMaterialCount() {
        ProcurementPurchaseOrderRecords.ShippingOrderRecord order = shippingOrder();
        order.missingYiteMaterialCount = 75;
        when(mapper.listShippingOrders(307L, "", 50)).thenReturn(List.of(order));

        List<ShippingOrderView> orders = service.listShippingOrders(access(), null);

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).missingYiteMaterialCount).isEqualTo(75);
        verify(mapper).listShippingOrders(307L, "", 50);
    }

    @Test
    void submitOrderMarksPurchaseOrderSubmitted() {
        PurchaseOrderRecord before = order("SGGR-0607", "人工补货");
        PurchaseOrderRecord after = order("SGGR-0607", "人工补货");
        after.status = "SUBMITTED";
        PurchaseOrderItemRecord item = item();
        item.sourcingSpecText = "拉链款";
        item.totalQuantity = 30;
        ProductArchiveRecord product = sealReadyProduct(item.productVariantId, item.partnerSku);
        when(mapper.selectOrderById(200001L)).thenReturn(before, after);
        when(mapper.submitOrder(200001L, 307L)).thenReturn(1);
        when(mapper.listLogisticsQuoteCandidatesByOrder(200001L)).thenReturn(List.of());
        when(mapper.listItemSitesByOrder(200001L)).thenReturn(List.of(siteRow("SA", "AIR", 30)));
        when(mapper.listItemsByOrder(200001L)).thenReturn(List.of(item));
        when(mapper.selectProductArchiveByVariant(301L, item.productVariantId)).thenReturn(product);

        PurchaseOrderView view = service.submitOrder(access(), "200001");

        assertThat(view.status).isEqualTo("submitted");
        verify(mapper).submitOrder(200001L, 307L);
        verify(mapper).insertOperationLog(
                eq(240001L),
                eq(200001L),
                isNull(),
                eq("SUBMIT_ORDER"),
                eq(307L),
                eq("READY"),
                eq("SUBMITTED"),
                isNull()
        );
    }

    @Test
    void submitOrderRejectsEmptyPurchaseOrder() {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        when(mapper.selectOrderById(200001L)).thenReturn(order);
        when(mapper.listItemsByOrder(200001L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.submitOrder(access(), "200001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("当前采购单还没有商品，不能封存");
        verify(mapper, never()).submitOrder(anyLong(), anyLong());
    }

    @Test
    void submitOrderRejectsMissingSealRequirements() {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        PurchaseOrderItemRecord item = item();
        item.totalQuantity = 30;
        ProductArchiveRecord product = product();
        product.productVariantId = item.productVariantId;
        product.partnerSku = item.partnerSku;
        when(mapper.selectOrderById(200001L)).thenReturn(order);
        when(mapper.listItemsByOrder(200001L)).thenReturn(List.of(item));
        when(mapper.listItemSitesByOrder(200001L)).thenReturn(List.of(siteRow("SA", "UNSPECIFIED", 30)));
        when(mapper.selectProductArchiveByVariant(301L, item.productVariantId)).thenReturn(product);

        assertThatThrownBy(() -> service.submitOrder(access(), "200001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺少封存必填信息")
                .hasMessageContaining("采购规格缺失")
                .hasMessageContaining("运输方式未指定")
                .hasMessageContaining("商品尺寸缺失");
        verify(mapper, never()).submitOrder(anyLong(), anyLong());
    }

    @Test
    void updateOrderRejectsSubmittedPurchaseOrder() {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        order.status = "SUBMITTED";
        UpdateOrderCommand command = new UpdateOrderCommand();
        command.title = "SGGR-0607-改";
        when(mapper.selectOrderById(200001L)).thenReturn(order);

        assertThatThrownBy(() -> service.updateOrder(access(), "200001", command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("采购单已封存");
    }

    @Test
    void createShippingOrderRejectsUnsubmittedPurchaseOrder() {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        order.status = "READY";
        CreateShippingOrderCommand command = new CreateShippingOrderCommand();
        command.purchaseOrderIds = List.of("200001");
        when(mapper.selectOrderById(200001L)).thenReturn(order);

        assertThatThrownBy(() -> service.createShippingOrder(access(), command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已封存的采购单才可合并为仓库单");
    }

    @Test
    void createShippingOrderAcceptsSubmittedPurchaseOrder() {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        order.status = "SUBMITTED";
        PurchaseOrderLogisticsQuoteLineRecord sourceLine = quoteLine(null, "PENDING_QUOTE", "NOT_SUBMITTED");
        when(mapper.selectOrderById(200001L)).thenReturn(order, order);
        when(mapper.listLogisticsQuoteCandidatesByOrder(200001L)).thenReturn(List.of(sourceLine));
        when(mapper.nextShippingOrderId()).thenReturn(290001L);
        when(mapper.nextShippingOrderSegmentId()).thenReturn(292001L);
        when(mapper.nextShippingOrderLineId()).thenReturn(291001L);
        when(mapper.selectShippingOrderById(290001L)).thenReturn(shippingOrder());
        when(mapper.listShippingOrderSegments(290001L)).thenReturn(List.of(shippingOrderSegment(292001L, "SA", "AIR")));
        when(mapper.listShippingOrderLines(290001L)).thenReturn(List.of());
        when(mapper.listLogisticsQuoteCandidatesByShippingOrder(290001L)).thenReturn(List.of(sourceLine));

        CreateShippingOrderCommand command = new CreateShippingOrderCommand();
        command.purchaseOrderIds = List.of("200001");

        ShippingOrderView view = service.createShippingOrder(access(), command);

        assertThat(view.id).isEqualTo("290001");
        assertThat(view.shippingOrderNo).isEqualTo("SO-290001");
        verify(mapper).insertShippingOrder(any(), eq(307L));
        verify(mapper).insertShippingOrderSegment(any(), eq(307L));
        verify(mapper).insertShippingOrderLine(any(), eq(307L));
    }

    @Test
    void createShippingOrderSnapshotsSiteStoreCodeForSiteLines() {
        PurchaseOrderRecord order = order("canman-626", "人工补货");
        order.status = "SUBMITTED";
        order.anchorStoreCodeCache = "STR69486-NSA";
        order.projectNameCache = "canman";
        PurchaseOrderLogisticsQuoteLineRecord sourceLine = quoteLine(null, "PENDING_QUOTE", "NOT_SUBMITTED");
        sourceLine.sourceStoreCode = "STR69486-NAE";
        sourceLine.sourceStoreName = "canman";
        sourceLine.siteCode = "AE";

        when(mapper.selectOrderById(200001L)).thenReturn(order, order);
        when(mapper.listLogisticsQuoteCandidatesByOrder(200001L)).thenReturn(List.of(sourceLine));
        when(mapper.nextShippingOrderId()).thenReturn(290001L);
        when(mapper.nextShippingOrderSegmentId()).thenReturn(292001L);
        when(mapper.nextShippingOrderLineId()).thenReturn(291001L);
        when(mapper.selectShippingOrderById(290001L)).thenReturn(shippingOrder());
        when(mapper.listShippingOrderSegments(290001L)).thenReturn(List.of(shippingOrderSegment(292001L, "AE", "AIR")));
        when(mapper.listShippingOrderLines(290001L)).thenReturn(List.of());
        when(mapper.listLogisticsQuoteCandidatesByShippingOrder(290001L)).thenReturn(List.of(sourceLine));

        CreateShippingOrderCommand command = new CreateShippingOrderCommand();
        command.purchaseOrderIds = List.of("200001");

        service.createShippingOrder(access(), command);

        ArgumentCaptor<ShippingOrderLineRecord> lineCaptor =
                ArgumentCaptor.forClass(ShippingOrderLineRecord.class);
        verify(mapper).insertShippingOrderLine(lineCaptor.capture(), eq(307L));
        assertThat(lineCaptor.getValue().sourceStoreCode).isEqualTo("STR69486-NAE");
        assertThat(lineCaptor.getValue().siteCode).isEqualTo("AE");
    }

    @Test
    void createShippingOrderRejectsAlreadyJoinedPurchaseOrder() {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        order.status = "SUBMITTED";
        PurchaseOrderLogisticsQuoteLineRecord sourceLine = quoteLine(null, "PENDING_QUOTE", "NOT_SUBMITTED");
        when(mapper.selectOrderById(200001L)).thenReturn(order, order);
        when(mapper.listLogisticsQuoteCandidatesByOrder(200001L)).thenReturn(List.of(sourceLine));
        when(mapper.countActiveShippingOrderLinesByItemSites(List.of(220002L))).thenReturn(1);

        CreateShippingOrderCommand command = new CreateShippingOrderCommand();
        command.purchaseOrderIds = List.of("200001");

        assertThatThrownBy(() -> service.createShippingOrder(access(), command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("所选采购单已在仓库单中，不能重复合并");
        verify(mapper, never()).insertShippingOrder(any(), anyLong());
    }

    @Test
    void createShippingOrderSplitsSegmentsBySiteAndTransportMode() {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        order.status = "SUBMITTED";
        PurchaseOrderLogisticsQuoteLineRecord airLine = quoteLine(null, "PENDING_QUOTE", "NOT_SUBMITTED");
        airLine.siteCode = "SA";
        airLine.plannedTransportMode = "AIR";
        PurchaseOrderLogisticsQuoteLineRecord seaLine = quoteLine(null, "PENDING_QUOTE", "NOT_SUBMITTED");
        seaLine.purchaseOrderItemId = 210002L;
        seaLine.purchaseOrderItemSiteId = 220003L;
        seaLine.productVariantId = 320002L;
        seaLine.partnerSku = "SGGRB116";
        seaLine.siteCode = "SA";
        seaLine.plannedTransportMode = "SEA";
        seaLine.quantity = 30;
        when(mapper.selectOrderById(200001L)).thenReturn(order, order);
        when(mapper.listLogisticsQuoteCandidatesByOrder(200001L)).thenReturn(List.of(airLine, seaLine));
        when(mapper.nextShippingOrderId()).thenReturn(290001L);
        when(mapper.nextShippingOrderSegmentId()).thenReturn(292001L, 292002L);
        when(mapper.nextShippingOrderLineId()).thenReturn(291001L, 291002L);
        when(mapper.selectShippingOrderById(290001L)).thenReturn(shippingOrder());
        when(mapper.listShippingOrderSegments(290001L)).thenReturn(List.of(
                shippingOrderSegment(292001L, "SA", "AIR"),
                shippingOrderSegment(292002L, "SA", "SEA")
        ));
        when(mapper.listShippingOrderLines(290001L)).thenReturn(List.of());
        when(mapper.listLogisticsQuoteCandidatesByShippingOrder(290001L)).thenReturn(List.of(airLine, seaLine));

        CreateShippingOrderCommand command = new CreateShippingOrderCommand();
        command.purchaseOrderIds = List.of("200001");

        ShippingOrderView view = service.createShippingOrder(access(), command);

        assertThat(view.segments).extracting(segment -> segment.siteCode, segment -> segment.transportMode)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("SA", "AIR"),
                        org.assertj.core.groups.Tuple.tuple("SA", "SEA")
                );
        ArgumentCaptor<ShippingOrderSegmentRecord> segmentCaptor =
                ArgumentCaptor.forClass(ShippingOrderSegmentRecord.class);
        verify(mapper, org.mockito.Mockito.times(2)).insertShippingOrderSegment(segmentCaptor.capture(), eq(307L));
        assertThat(segmentCaptor.getAllValues())
                .extracting(segment -> segment.segmentNo, segment -> segment.siteCode, segment -> segment.transportMode, segment -> segment.totalQuantity)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("SO-290001-SA-AIR", "SA", "AIR", 20),
                        org.assertj.core.groups.Tuple.tuple("SO-290001-SA-SEA", "SA", "SEA", 30)
                );
        ArgumentCaptor<ShippingOrderLineRecord> lineCaptor =
                ArgumentCaptor.forClass(ShippingOrderLineRecord.class);
        verify(mapper, org.mockito.Mockito.times(2)).insertShippingOrderLine(lineCaptor.capture(), eq(307L));
        assertThat(lineCaptor.getAllValues())
                .extracting(line -> line.shippingOrderSegmentId, line -> line.plannedTransportMode)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(292001L, "AIR"),
                        org.assertj.core.groups.Tuple.tuple(292002L, "SEA")
                );
    }

    @Test
    void submitShippingOrderSegmentAllowsPendingQuotes() {
        ProcurementPurchaseOrderRecords.ShippingOrderRecord shippingOrder = shippingOrder();
        ProcurementPurchaseOrderRecords.ShippingOrderRecord next = shippingOrder();
        next.shippingSubmitStatus = "PARTIAL_SUBMITTED";
        ShippingOrderSegmentScopeCommand command = new ShippingOrderSegmentScopeCommand();
        command.segmentIds = List.of("292001");
        PurchaseOrderLogisticsQuoteLineRecord line = quoteLine(280001L, "PENDING_QUOTE", "NOT_SUBMITTED");
        line.shippingOrderId = 290001L;
        line.shippingOrderSegmentId = 292001L;
        line.forwarderCode = "ET";

        when(mapper.selectShippingOrderById(290001L)).thenReturn(shippingOrder, next);
        when(mapper.listLogisticsQuoteCandidatesByShippingOrderSegments(290001L, List.of(292001L))).thenReturn(List.of(line));
        when(mapper.submitLogisticsQuoteLinesForShippingOrderSegments(290001L, List.of(292001L), 307L)).thenReturn(1);

        ShippingOrderSubmitView view = service.submitShippingOrder(access(), "290001", command);

        assertThat(view.shippingOrderId).isEqualTo("290001");
        assertThat(view.submittedLineCount).isEqualTo(1);
        verify(mapper).submitLogisticsQuoteLinesForShippingOrderSegments(290001L, List.of(292001L), 307L);
    }

    @Test
    void submitShippingOrderSegmentRejectsMissingYiteMaterial() {
        ProcurementPurchaseOrderRecords.ShippingOrderRecord shippingOrder = shippingOrder();
        ShippingOrderSegmentScopeCommand command = new ShippingOrderSegmentScopeCommand();
        command.segmentIds = List.of("292001");
        PurchaseOrderLogisticsQuoteLineRecord line = quoteLine(280001L, "CONFIRMED", "NOT_SUBMITTED");
        line.shippingOrderId = 290001L;
        line.shippingOrderSegmentId = 292001L;
        line.forwarderCode = "YT";
        line.yiteMaterial = null;

        when(mapper.selectShippingOrderById(290001L)).thenReturn(shippingOrder);
        when(mapper.listLogisticsQuoteCandidatesByShippingOrderSegments(290001L, List.of(292001L))).thenReturn(List.of(line));

        assertThatThrownBy(() -> service.submitShippingOrder(access(), "290001", command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("义特材质缺失");
    }

    @Test
    void createShippingOrderReusesYiteMaterialFromProductForwarderDeclarationAttribute() {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        order.status = "SUBMITTED";
        PurchaseOrderLogisticsQuoteLineRecord sourceLine = quoteLine(null, "PENDING_QUOTE", "NOT_SUBMITTED");
        sourceLine.yiteMaterial = null;
        when(mapper.selectOrderById(200001L)).thenReturn(order, order);
        when(mapper.listLogisticsQuoteCandidatesByOrder(200001L)).thenReturn(List.of(sourceLine));
        when(mapper.listProductForwarderDeclarationAttributes(
                307L,
                "YT",
                "YITE_MATERIAL",
                List.of(320001L),
                List.of("SGGRB115")
        )).thenReturn(List.of(productForwarderDeclarationAttribute("塑料")));
        when(mapper.countActiveShippingOrderLinesByItemSites(List.of(220002L))).thenReturn(0);
        when(mapper.nextShippingOrderId()).thenReturn(290001L);
        when(mapper.nextShippingOrderLineId()).thenReturn(291001L);
        when(mapper.selectShippingOrderById(290001L)).thenReturn(shippingOrder());
        when(mapper.listShippingOrderLines(290001L)).thenReturn(List.of());
        when(mapper.listLogisticsQuoteCandidatesByShippingOrder(290001L)).thenReturn(List.of(sourceLine));

        CreateShippingOrderCommand command = new CreateShippingOrderCommand();
        command.purchaseOrderIds = List.of("200001");

        service.createShippingOrder(access(), command);

        ArgumentCaptor<ShippingOrderLineRecord> lineCaptor =
                ArgumentCaptor.forClass(ShippingOrderLineRecord.class);
        verify(mapper).insertShippingOrderLine(lineCaptor.capture(), eq(307L));
        assertThat(lineCaptor.getValue().productVariantId).isEqualTo(320001L);
        assertThat(lineCaptor.getValue().yiteMaterial).isEqualTo("塑料");
    }

    @Test
    void createShippingOrderReusesLegacyYiteMaterialWithoutSourceStore() {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        order.status = "SUBMITTED";
        PurchaseOrderLogisticsQuoteLineRecord sourceLine = quoteLine(null, "PENDING_QUOTE", "NOT_SUBMITTED");
        sourceLine.yiteMaterial = null;
        ProductForwarderDeclarationAttributeRecord legacyAttribute =
                productForwarderDeclarationAttribute("金属");
        legacyAttribute.sourceStoreCode = null;

        when(mapper.selectOrderById(200001L)).thenReturn(order, order);
        when(mapper.listLogisticsQuoteCandidatesByOrder(200001L)).thenReturn(List.of(sourceLine));
        when(mapper.listProductForwarderDeclarationAttributes(
                307L,
                "YT",
                "YITE_MATERIAL",
                List.of(320001L),
                List.of("SGGRB115")
        )).thenReturn(List.of(legacyAttribute));
        when(mapper.countActiveShippingOrderLinesByItemSites(List.of(220002L))).thenReturn(0);
        when(mapper.nextShippingOrderId()).thenReturn(290001L);
        when(mapper.nextShippingOrderLineId()).thenReturn(291001L);
        when(mapper.selectShippingOrderById(290001L)).thenReturn(shippingOrder());
        when(mapper.listShippingOrderLines(290001L)).thenReturn(List.of());
        when(mapper.listLogisticsQuoteCandidatesByShippingOrder(290001L)).thenReturn(List.of(sourceLine));

        CreateShippingOrderCommand command = new CreateShippingOrderCommand();
        command.purchaseOrderIds = List.of("200001");

        service.createShippingOrder(access(), command);

        ArgumentCaptor<ShippingOrderLineRecord> lineCaptor =
                ArgumentCaptor.forClass(ShippingOrderLineRecord.class);
        verify(mapper).insertShippingOrderLine(lineCaptor.capture(), eq(307L));
        assertThat(lineCaptor.getValue().productVariantId).isEqualTo(320001L);
        assertThat(lineCaptor.getValue().sourceStoreCode).isEqualTo("STR69486-NSA");
        assertThat(lineCaptor.getValue().yiteMaterial).isEqualTo("金属");
    }

    @Test
    void updateShippingOrderEditsTitleAndRemark() {
        ProcurementPurchaseOrderRecords.ShippingOrderRecord before = shippingOrder();
        ProcurementPurchaseOrderRecords.ShippingOrderRecord after = shippingOrder();
        after.title = "6月新品合并发货";
        after.remark = "物流确认";
        UpdateShippingOrderCommand command = new UpdateShippingOrderCommand();
        command.title = "  6月新品合并发货  ";
        command.remark = "  物流确认  ";

        when(mapper.selectShippingOrderById(290001L)).thenReturn(before, after);
        when(mapper.listShippingOrderLines(290001L)).thenReturn(List.of());

        ShippingOrderView view = service.updateShippingOrder(access(), "290001", command);

        assertThat(view.title).isEqualTo("6月新品合并发货");
        assertThat(view.remark).isEqualTo("物流确认");
        verify(mapper).updateShippingOrderHeader(290001L, 307L, "6月新品合并发货", "物流确认", 307L);
    }

    @Test
    void getShippingOrderIncludesLineQuotePrice() {
        ProcurementPurchaseOrderRecords.ShippingOrderRecord order = shippingOrder();
        ShippingOrderLineRecord line = shippingOrderLine();
        line.yiteMaterial = "塑料";
        line.unitPrice = new BigDecimal("1390.00");
        line.currency = "CNY";
        line.billingUnit = "CBM";

        when(mapper.selectShippingOrderById(290001L)).thenReturn(order);
        when(mapper.listShippingOrderSegments(290001L)).thenReturn(List.of(shippingOrderSegment(292001L, "SA", "SEA")));
        when(mapper.listShippingOrderLines(290001L)).thenReturn(List.of(line));

        ShippingOrderView view = service.getShippingOrder(access(), "290001");

        assertThat(view.lines).hasSize(1);
        assertThat(view.lines.get(0).yiteMaterial).isEqualTo("塑料");
        assertThat(view.lines.get(0).unitPrice).isEqualByComparingTo("1390.00");
        assertThat(view.lines.get(0).currency).isEqualTo("CNY");
        assertThat(view.lines.get(0).billingUnit).isEqualTo("CBM");
    }

    @Test
    void updateShippingOrderLineYiteMaterialPersistsAllowedMaterial() {
        ProcurementPurchaseOrderRecords.ShippingOrderRecord order = shippingOrder();
        ShippingOrderLineRecord line = shippingOrderLine();
        line.yiteMaterial = "陶瓷";
        UpdateShippingOrderLineYiteMaterialCommand command = new UpdateShippingOrderLineYiteMaterialCommand();
        command.yiteMaterial = "陶瓷";

        when(mapper.selectShippingOrderById(290001L)).thenReturn(order, order);
        when(mapper.selectShippingOrderLineById(290001L, 291001L, 307L)).thenReturn(line);
        when(mapper.updateShippingOrderLineYiteMaterial(290001L, 291001L, 307L, "陶瓷", 307L)).thenReturn(1);
        when(mapper.nextProductForwarderDeclarationAttributeId()).thenReturn(310001L);
        when(mapper.listShippingOrderLines(290001L)).thenReturn(List.of(line));

        ShippingOrderView view = service.updateShippingOrderLineYiteMaterial(access(), "290001", "291001", command);

        assertThat(view.lines).hasSize(1);
        assertThat(view.lines.get(0).barcode).isEqualTo("BARCODE-115");
        assertThat(view.lines.get(0).yiteMaterial).isEqualTo("陶瓷");
        verify(mapper).updateShippingOrderLineYiteMaterial(290001L, 291001L, 307L, "陶瓷", 307L);
        verify(mapper).updateShippingOrderQuoteLineYiteMaterial(290001L, 291001L, 307L, "陶瓷", 307L);
        ArgumentCaptor<ProductForwarderDeclarationAttributeRecord> attributeCaptor =
                ArgumentCaptor.forClass(ProductForwarderDeclarationAttributeRecord.class);
        verify(mapper).upsertProductForwarderDeclarationAttribute(attributeCaptor.capture(), eq(307L));
        assertThat(attributeCaptor.getValue().id).isEqualTo(310001L);
        assertThat(attributeCaptor.getValue().ownerUserId).isEqualTo(307L);
        assertThat(attributeCaptor.getValue().productMasterId).isEqualTo(310001L);
        assertThat(attributeCaptor.getValue().productVariantId).isEqualTo(320001L);
        assertThat(attributeCaptor.getValue().barcode).isEqualTo("BARCODE-115");
        assertThat(attributeCaptor.getValue().forwarderCode).isEqualTo("YT");
        assertThat(attributeCaptor.getValue().attributeCode).isEqualTo("YITE_MATERIAL");
        assertThat(attributeCaptor.getValue().attributeValue).isEqualTo("陶瓷");
        assertThat(attributeCaptor.getValue().sourceShippingOrderId).isEqualTo(290001L);
        assertThat(attributeCaptor.getValue().sourceShippingOrderLineId).isEqualTo(291001L);
    }

    @Test
    void updateShippingOrderLineYiteMaterialRejectsUnsupportedMaterial() {
        ProcurementPurchaseOrderRecords.ShippingOrderRecord order = shippingOrder();
        UpdateShippingOrderLineYiteMaterialCommand command = new UpdateShippingOrderLineYiteMaterialCommand();
        command.yiteMaterial = "玻璃";

        when(mapper.selectShippingOrderById(290001L)).thenReturn(order);

        assertThatThrownBy(() -> service.updateShippingOrderLineYiteMaterial(access(), "290001", "291001", command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("义特材质只能选择");
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
        when(mapper.selectItemByPartnerSku(200001L, "SGGRB116")).thenReturn(null);
        when(mapper.nextItemId()).thenReturn(210002L);
        when(mapper.selectProductOffer(301L, "SGGRB116", 320002L, "AE")).thenReturn(offer());
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
    void addItemsMergesSameProductSameSiteTransportAlreadyInOrder() {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        PurchaseOrderItemRecord existingItem = item();
        existingItem.productVariantId = 320002L;
        existingItem.partnerSku = "SGGRB116";
        PurchaseOrderItemSiteRecord existingSite = siteRow(210001L, "SGGRB116", "AE", "SEA", 20);
        PurchaseOrderItemSiteRecord mergedSite = siteRow(210001L, "SGGRB116", "AE", "SEA", 25);

        AddItemsCommand command = new AddItemsCommand();
        ItemCommand itemCommand = new ItemCommand();
        itemCommand.psku = "SGGRB116";
        itemCommand.site = "AE";
        itemCommand.transportMode = "SEA";
        itemCommand.quantity = 5;
        command.items = List.of(itemCommand);

        when(mapper.selectOrderById(200001L)).thenReturn(order, order, order);
        when(mapper.listItemSitesByOrder(200001L)).thenReturn(List.of(existingSite), List.of(mergedSite));
        when(mapper.listStoreSites(301L)).thenReturn(List.of(storeSite(30002L, "AE", "STR69486-NAE")));
        when(mapper.listProductArchiveMatches(301L, "SGGRB116")).thenReturn(List.of(product()));
        when(mapper.selectItemByPartnerSku(200001L, "SGGRB116")).thenReturn(existingItem);
        when(mapper.listItemsByOrder(200001L)).thenReturn(List.of(existingItem));

        PurchaseOrderView view = service.addItems(access(), "200001", command);

        assertThat(view.items).hasSize(1);
        assertThat(view.items.get(0).allocations)
                .extracting(allocation -> allocation.site + ":" + allocation.transportMode + ":" + allocation.quantity)
                .containsExactly("AE:SEA:25");
        verify(mapper).increaseItemSiteQuantity(220002L, 5, 307L);
        verify(mapper, never()).upsertItemSite(any());
        verify(mapper).recalculateItemAggregates(210001L, 307L);
        verify(mapper).recalculateOrderAggregates(200001L, 307L);
    }

    @Test
    void addItemsAllowsSameProductSameSiteTransportAlreadyInAnotherCurrentPurchaseOrder() {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");

        AddItemsCommand command = new AddItemsCommand();
        ItemCommand itemCommand = new ItemCommand();
        itemCommand.psku = "SGGRB116";
        itemCommand.site = "AE";
        itemCommand.transportMode = "SEA";
        itemCommand.quantity = 5;
        command.items = List.of(itemCommand);

        PurchaseOrderItemRecord savedItem = item();
        savedItem.id = 210002L;
        savedItem.productVariantId = 320002L;
        savedItem.partnerSku = "SGGRB116";
        PurchaseOrderItemSiteRecord savedSite = siteRow(210002L, "SGGRB116", "AE", "SEA", 5);

        when(mapper.selectOrderById(200001L)).thenReturn(order, order, order);
        when(mapper.listItemSitesByOrder(200001L)).thenReturn(List.of(), List.of(savedSite));
        when(mapper.listStoreSites(301L)).thenReturn(List.of(storeSite(30002L, "AE", "STR69486-NAE")));
        when(mapper.listProductArchiveMatches(301L, "SGGRB116")).thenReturn(List.of(product()));
        when(mapper.selectItemByPartnerSku(200001L, "SGGRB116")).thenReturn(null);
        when(mapper.nextItemId()).thenReturn(210002L);
        when(mapper.selectProductOffer(301L, "SGGRB116", 320002L, "AE")).thenReturn(offer());
        when(mapper.nextItemSiteId()).thenReturn(220002L);
        when(mapper.listItemsByOrder(200001L)).thenReturn(List.of(savedItem));

        PurchaseOrderView view = service.addItems(access(), "200001", command);

        assertThat(view.items).hasSize(1);
        assertThat(view.items.get(0).allocations)
                .extracting(allocation -> allocation.site + ":" + allocation.transportMode + ":" + allocation.quantity)
                .containsExactly("AE:SEA:5");
        verify(mapper, never()).selectCurrentOrderItemSiteDuplicate(anyLong(), anyLong(), anyString(), anyString(), anyString());
        verify(mapper).upsertItemSite(any());
        verify(mapper).recalculateItemAggregates(210002L, 307L);
        verify(mapper).recalculateOrderAggregates(200001L, 307L);
    }

    @Test
    void addItemsRejectsSubmittedPurchaseOrder() {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        order.status = "SUBMITTED";
        AddItemsCommand command = new AddItemsCommand();
        ItemCommand itemCommand = new ItemCommand();
        itemCommand.psku = "SGGRB116";
        itemCommand.site = "AE";
        itemCommand.transportMode = "SEA";
        itemCommand.quantity = 5;
        command.items = List.of(itemCommand);
        when(mapper.selectOrderById(200001L)).thenReturn(order);

        assertThatThrownBy(() -> service.addItems(access(), "200001", command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("采购单已封存");
        verify(mapper, never()).listProductArchiveMatches(anyLong(), anyString());
        verify(mapper, never()).selectProductOffer(anyLong(), anyString(), anyLong(), anyString());
        verify(mapper, never()).upsertItemSite(any());
    }

    @Test
    void addItemsAllowsSameProductSameSiteWithDifferentTransportMode() {
        PurchaseOrderRecord order = order("SGGR-0607", "人工补货");
        PurchaseOrderItemRecord existingItem = item();
        existingItem.productVariantId = 320002L;
        existingItem.partnerSku = "SGGRB116";
        PurchaseOrderItemSiteRecord airSite = siteRow(210001L, "SGGRB116", "AE", "AIR", 20);
        PurchaseOrderItemSiteRecord seaSite = siteRow(210001L, "SGGRB116", "AE", "SEA", 5);
        seaSite.id = 220099L;

        AddItemsCommand command = new AddItemsCommand();
        ItemCommand itemCommand = new ItemCommand();
        itemCommand.psku = "SGGRB116";
        itemCommand.site = "AE";
        itemCommand.transportMode = "SEA";
        itemCommand.quantity = 5;
        command.items = List.of(itemCommand);

        when(mapper.selectOrderById(200001L)).thenReturn(order, order, order);
        when(mapper.listItemSitesByOrder(200001L)).thenReturn(List.of(airSite), List.of(airSite, seaSite));
        when(mapper.listStoreSites(301L)).thenReturn(List.of(storeSite(30002L, "AE", "STR69486-NAE")));
        when(mapper.listProductArchiveMatches(301L, "SGGRB116")).thenReturn(List.of(product()));
        when(mapper.selectItemByPartnerSku(200001L, "SGGRB116")).thenReturn(existingItem);
        when(mapper.selectProductOffer(301L, "SGGRB116", 320002L, "AE")).thenReturn(offer());
        when(mapper.nextItemSiteId()).thenReturn(220099L);
        when(mapper.listItemsByOrder(200001L)).thenReturn(List.of(existingItem));

        PurchaseOrderView view = service.addItems(access(), "200001", command);

        assertThat(view.items).hasSize(1);
        assertThat(view.items.get(0).allocations)
                .extracting(allocation -> allocation.site + ":" + allocation.transportMode + ":" + allocation.quantity)
                .containsExactly("AE:AIR:20", "AE:SEA:5");
        verify(mapper).recalculateItemAggregates(210001L, 307L);
        verify(mapper).recalculateOrderAggregates(200001L, 307L);
    }

    @Test
    void createOrderPersistsAndReturnsFactoryDirectFulfillment() {
        CreateOrderCommand command = new CreateOrderCommand();
        command.storeCode = "STR69486-NSA";
        command.title = "SGGR-货代直发";
        ItemCommand itemCommand = new ItemCommand();
        itemCommand.psku = "SGGRB116";
        itemCommand.site = "AE";
        itemCommand.transportMode = "AIR";
        itemCommand.quantity = 20;
        itemCommand.fulfillmentType = "FACTORY_DIRECT";
        itemCommand.fulfillmentSourceName = "义乌厂家";
        command.items = List.of(itemCommand);

        StoreScopeRecord scope = storeScope();
        PurchaseOrderRecord savedOrder = order("SGGR-货代直发", null);
        savedOrder.id = 200002L;
        savedOrder.orderNo = "PO-200002";
        savedOrder.status = "DRAFT";
        savedOrder.siteCodesJson = "[\"AE\"]";
        PurchaseOrderItemRecord savedItem = item();
        savedItem.id = 210002L;
        savedItem.purchaseOrderId = 200002L;
        savedItem.productMasterId = 310002L;
        savedItem.productVariantId = 320002L;
        savedItem.partnerSku = "SGGRB116";
        savedItem.fulfillmentType = "FACTORY_DIRECT";
        savedItem.fulfillmentSourceName = "义乌厂家";
        PurchaseOrderItemSiteRecord savedSite = siteRow(210002L, "SGGRB116", "AE", "AIR", 20);
        savedSite.purchaseOrderId = 200002L;

        when(mapper.selectStoreScope(307L, "STR69486-NSA")).thenReturn(scope);
        when(mapper.listStoreSites(301L)).thenReturn(List.of(storeSite(30002L, "AE", "STR69486-NAE")));
        when(mapper.nextOrderId()).thenReturn(200002L);
        when(mapper.selectOrderById(200002L)).thenReturn(savedOrder, savedOrder);
        when(mapper.listProductArchiveMatches(301L, "SGGRB116")).thenReturn(List.of(product()));
        when(mapper.selectItemByPartnerSku(200002L, "SGGRB116")).thenReturn(null);
        when(mapper.nextItemId()).thenReturn(210002L);
        when(mapper.selectProductOffer(301L, "SGGRB116", 320002L, "AE")).thenReturn(offer());
        when(mapper.nextItemSiteId()).thenReturn(220002L);
        when(mapper.listItemSitesByOrder(200002L)).thenReturn(List.of(savedSite));
        when(mapper.listItemsByOrder(200002L)).thenReturn(List.of(savedItem));

        PurchaseOrderView view = service.createOrder(access(), command);

        ArgumentCaptor<PurchaseOrderItemRecord> itemCaptor = ArgumentCaptor.forClass(PurchaseOrderItemRecord.class);
        verify(mapper).insertItem(itemCaptor.capture());
        assertThat(itemCaptor.getValue().fulfillmentType).isEqualTo("FACTORY_DIRECT");
        assertThat(itemCaptor.getValue().fulfillmentSourceName).isEqualTo("义乌厂家");
        assertThat(view.items).hasSize(1);
        assertThat(view.items.get(0).fulfillmentType).isEqualTo("FACTORY_DIRECT");
        assertThat(view.items.get(0).fulfillmentTypeLabel).isEqualTo("货到货代");
        assertThat(view.items.get(0).fulfillmentSourceName).isEqualTo("义乌厂家");
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
        when(mapper.selectProductOffer(301L, "SGGRB115", 320001L, "SA")).thenReturn(offer("SA", 30001L, 330001L, "SGGRB115"));
        when(mapper.selectProductOffer(301L, "SGGRB115", 320001L, "AE")).thenReturn(offer("AE", 30002L, 330002L, "SGGRB115"));
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
        verify(mapper, never()).selectCurrentOrderItemSiteDuplicate(anyLong(), anyLong(), anyString(), anyString(), anyString());
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
    void getOrderAli1688HistoryUsesLatestProductLinkWhenBatchHistoryIsOlder() {
        PurchaseOrderRecord order = order("SGGR-0703", "人工补货");
        PurchaseOrderItemRecord item = item();
        item.skuParent = "SGGR";
        item.totalQuantity = 100;
        PurchaseOrderAli1688PurchaseBatchRow oldBatch = purchaseBatchRow();
        oldBatch.batchLabel = "批次 1";
        oldBatch.countedQuantity = 100;
        oldBatch.countedCost = new BigDecimal("218.00");
        oldBatch.orderNo = "5118264938393005337";
        oldBatch.orderTime = "2026-05-29 14:17:59";
        PurchaseOrderAli1688HistoryRow latestProductLink = historyRow();
        latestProductLink.orderId = 502L;
        latestProductLink.itemId = 602L;
        latestProductLink.assignmentId = 702L;
        latestProductLink.orderNo = "5122278795612005337";
        latestProductLink.orderTime = "2026-07-03 13:55:15";
        latestProductLink.assignedQuantity = 30;
        latestProductLink.allocatedCost = new BigDecimal("15.65");
        latestProductLink.unitPrice = new BigDecimal("0.5217");

        when(mapper.selectOrderById(200001L)).thenReturn(order);
        when(mapper.listItemsByOrder(200001L)).thenReturn(List.of(item));
        when(mapper.listItemSitesByOrder(200001L)).thenReturn(List.of(siteRow("SA", "AIR", 100)));
        when(mapper.listOrderAli1688PurchaseBatches(
                307L,
                "PRJ69486",
                List.of("SA"),
                List.of("SGGRB115"),
                List.of("SGGR")
        )).thenReturn(List.of(oldBatch));
        when(mapper.listOrderAli1688HistoryRows(
                307L,
                "PRJ69486",
                List.of("SA"),
                List.of("SGGRB115"),
                List.of("SGGR")
        )).thenReturn(List.of(latestProductLink));

        PurchaseOrderAli1688HistoryView view = service.getOrderAli1688History(access(), "200001");

        assertThat(view.items).hasSize(1);
        assertThat(view.items.get(0).purchaseCount).isEqualTo(2);
        assertThat(view.items.get(0).totalQuantity).isEqualTo(130);
        assertThat(view.items.get(0).totalCost).isEqualByComparingTo("233.65");
        assertThat(view.items.get(0).recentUnitPrice).isEqualByComparingTo("0.5217");
        assertThat(view.items.get(0).recentPurchaseTime).isEqualTo("2026-07-03 13:55:15");
        assertThat(view.items.get(0).purchaseBatches).hasSize(1);
        assertThat(view.items.get(0).history).hasSize(1);
        assertThat(view.items.get(0).history.get(0).orderNo).isEqualTo("5122278795612005337");
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

    private StoreScopeRecord storeScope() {
        StoreScopeRecord record = new StoreScopeRecord();
        record.ownerUserId = 307L;
        record.logicalStoreId = 301L;
        record.projectCode = "PRJ69486";
        record.projectName = "SGGR";
        record.anchorStoreCode = "STR69486-NSA";
        record.anchorSite = "SA";
        return record;
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

    private ProductArchiveRecord sealReadyProduct(Long variantId, String psku) {
        ProductArchiveRecord record = product();
        record.productVariantId = variantId;
        record.partnerSku = psku;
        record.productLengthCm = new BigDecimal("10");
        record.productWidthCm = new BigDecimal("8");
        record.productHeightCm = new BigDecimal("2");
        record.productWeightG = new BigDecimal("120");
        record.cartonLengthCm = new BigDecimal("50");
        record.cartonWidthCm = new BigDecimal("40");
        record.cartonHeightCm = new BigDecimal("30");
        record.cartonWeightKg = new BigDecimal("12");
        record.cartonQuantity = 100;
        record.specSourceType = "manual";
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

    private PurchaseOrderDuplicateItemSiteRecord duplicateItemSite(
            Long purchaseOrderId,
            String orderNo,
            String title,
            String psku,
            String siteCode,
            String transportMode
    ) {
        PurchaseOrderDuplicateItemSiteRecord record = new PurchaseOrderDuplicateItemSiteRecord();
        record.purchaseOrderId = purchaseOrderId;
        record.orderNo = orderNo;
        record.title = title;
        record.partnerSku = psku;
        record.siteCode = siteCode;
        record.transportMode = transportMode;
        return record;
    }

    private PurchaseOrderLogisticsQuoteLineRecord quoteLine(
            Long id,
            String quoteStatus,
            String shippingSubmitStatus
    ) {
        PurchaseOrderLogisticsQuoteLineRecord record = new PurchaseOrderLogisticsQuoteLineRecord();
        record.id = id;
        record.ownerUserId = 307L;
        record.logicalStoreId = 301L;
        record.purchaseOrderId = 200001L;
        record.purchaseOrderNo = "PO-200001";
        record.purchaseOrderTitle = "SGGR-0607";
        record.purchaseOrderItemId = 210001L;
        record.purchaseOrderItemSiteId = 220002L;
        record.productMasterId = 310001L;
        record.productVariantId = 320001L;
        record.skuParent = "SGGR";
        record.partnerSku = "SGGRB115";
        record.barcode = "BARCODE-115";
        record.sourceStoreCode = "STR69486-NSA";
        record.sourceStoreName = "SGGR";
        record.titleCache = "测试商品";
        record.titleEn = "Orange Medium Marker Storage Box";
        record.imageUrlCache = "https://example.test/SGGRB115.jpg";
        record.brandName = "paper says";
        record.siteCode = "SA";
        record.pskuCode = "SGGRB115";
        record.plannedTransportMode = "AIR";
        record.quantity = 20;
        record.fulfillmentType = "WAREHOUSE_RECEIPT";
        record.isNewProduct = Boolean.TRUE;
        record.quoteStatus = quoteStatus;
        record.shippingSubmitStatus = shippingSubmitStatus;
        return record;
    }

    private ProcurementPurchaseOrderRecords.ShippingOrderRecord shippingOrder() {
        ProcurementPurchaseOrderRecords.ShippingOrderRecord record =
                new ProcurementPurchaseOrderRecords.ShippingOrderRecord();
        record.id = 290001L;
        record.ownerUserId = 307L;
        record.shippingOrderNo = "SO-290001";
        record.title = "SGGR-0607 发货单";
        record.status = "DRAFT";
        record.purchaseOrderCount = 1;
        record.lineCount = 1;
        record.skuCount = 1;
        record.totalQuantity = 20;
        record.quoteStatus = "PENDING_QUOTE";
        record.shippingSubmitStatus = "NOT_SUBMITTED";
        return record;
    }

    private ShippingOrderLineRecord shippingOrderLine() {
        ShippingOrderLineRecord record = new ShippingOrderLineRecord();
        record.id = 291001L;
        record.shippingOrderId = 290001L;
        record.ownerUserId = 307L;
        record.logicalStoreId = 301L;
        record.sourceStoreCode = "PRJ69486";
        record.sourceStoreName = "SGGR";
        record.purchaseOrderId = 200001L;
        record.purchaseOrderNo = "PO-200001";
        record.purchaseOrderTitle = "SGGR-0607";
        record.purchaseOrderItemId = 210001L;
        record.purchaseOrderItemSiteId = 220002L;
        record.productMasterId = 310001L;
        record.productVariantId = 320001L;
        record.skuParent = "SGGR";
        record.partnerSku = "SGGRB115";
        record.barcode = "BARCODE-115";
        record.titleCache = "测试商品";
        record.imageUrlCache = "https://example.test/SGGRB115.jpg";
        record.siteCode = "SA";
        record.pskuCode = "ZPSKU-115-SA";
        record.plannedTransportMode = "SEA";
        record.quantity = 20;
        record.fulfillmentType = "WAREHOUSE_RECEIPT";
        record.quoteLineId = 280001L;
        return record;
    }

    private ShippingOrderSegmentRecord shippingOrderSegment(Long id, String siteCode, String transportMode) {
        ShippingOrderSegmentRecord record = new ShippingOrderSegmentRecord();
        record.id = id;
        record.shippingOrderId = 290001L;
        record.ownerUserId = 307L;
        record.segmentNo = "SO-290001-" + siteCode + "-" + transportMode;
        record.siteCode = siteCode;
        record.transportMode = transportMode;
        record.quoteStatus = "PENDING_QUOTE";
        record.shippingSubmitStatus = "NOT_SUBMITTED";
        record.lineCount = 1;
        record.skuCount = 1;
        record.totalQuantity = 20;
        record.missingYiteMaterialCount = 0;
        return record;
    }

    private ProductForwarderDeclarationAttributeRecord productForwarderDeclarationAttribute(String value) {
        ProductForwarderDeclarationAttributeRecord record = new ProductForwarderDeclarationAttributeRecord();
        record.id = 310001L;
        record.ownerUserId = 307L;
        record.productMasterId = 310001L;
        record.productVariantId = 320001L;
        record.logicalStoreId = 301L;
        record.sourceStoreCode = "STR69486-NSA";
        record.partnerSku = "SGGRB115";
        record.barcode = "BARCODE-115";
        record.forwarderCode = "YT";
        record.attributeCode = "YITE_MATERIAL";
        record.attributeValue = value;
        return record;
    }

    private ProductForwarderChannelQuoteRecord productForwarderChannelQuote() {
        ProductForwarderChannelQuoteRecord record = new ProductForwarderChannelQuoteRecord();
        record.id = 320001L;
        record.ownerUserId = 307L;
        record.productMasterId = 310001L;
        record.productVariantId = 320001L;
        record.logicalStoreId = 301L;
        record.sourceStoreCode = "STR69486-NSA";
        record.partnerSku = "SGGRB115";
        record.barcode = "BARCODE-115";
        record.forwarderCode = "YT";
        record.forwarderName = "义特物流";
        record.routeCode = "YT-SAU-SEA-FBN-RUH";
        record.routeName = "义特沙特海运双清包税 + FBN利雅得送仓";
        record.serviceCode = "YT-SAU-SEA-FBN-RUH";
        record.serviceName = "义特沙特海运双清包税 + FBN利雅得送仓";
        record.siteCode = "SA";
        record.transportMode = "SEA";
        record.currency = "RMB";
        record.unitPrice = new BigDecimal("14.00");
        record.billingUnit = "PCS";
        record.estimatedAmount = new BigDecimal("280.00");
        record.effectiveStatus = "CURRENT";
        return record;
    }

    private String tinyPngDataUrl() throws Exception {
        return pngDataUrl(1, 1);
    }

    private String pngDataUrl(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, 0xFF4F46E5);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
    }

    private HSSFClientAnchor firstPictureAnchor(HSSFSheet sheet) {
        return (HSSFClientAnchor) ((HSSFPicture) sheet.getDrawingPatriarch().getChildren().get(0)).getAnchor();
    }

    private double yiteImageColumnWidthPx(HSSFSheet sheet) {
        return sheet.getColumnWidth(11) / 256D * 7D;
    }

    private double yiteImageRowHeightPx(Row row) {
        return row.getHeightInPoints() * 96D / 72D;
    }

    private int nonTransparentWidth(BufferedImage image) {
        int minX = image.getWidth();
        int maxX = -1;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (((image.getRGB(x, y) >>> 24) & 0xFF) > 0) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                }
            }
        }
        return maxX < minX ? 0 : maxX - minX + 1;
    }

    private int nonTransparentHeight(BufferedImage image) {
        int minY = image.getHeight();
        int maxY = -1;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (((image.getRGB(x, y) >>> 24) & 0xFF) > 0) {
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return maxY < minY ? 0 : maxY - minY + 1;
    }

    private byte[] quoteWorkbookBytes() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("物流报价确认");
            Row header = sheet.createRow(0);
            String[] headers = {
                    "报价行ID", "采购单ID", "采购商品ID", "采购站点行ID", "采购单号", "采购单名", "站点", "运输方式",
                    "商品SKU", "PSKU", "商品名称", "数量", "履约方式", "新品", "报价状态", "提交发货状态",
                    "货代编码", "货代名称", "路线编码", "路线名称", "服务编码", "服务名称", "币种", "单价",
                    "计费单位", "确认金额", "物流备注"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("280001");
            row.createCell(1).setCellValue("200001");
            row.createCell(2).setCellValue("210001");
            row.createCell(3).setCellValue("220002");
            row.createCell(4).setCellValue("PO-200001");
            row.createCell(5).setCellValue("SGGR-0607");
            row.createCell(6).setCellValue("SA");
            row.createCell(7).setCellValue("AIR");
            row.createCell(8).setCellValue("SGGRB115");
            row.createCell(9).setCellValue("SGGRB115");
            row.createCell(10).setCellValue("测试商品");
            row.createCell(11).setCellValue(20);
            row.createCell(12).setCellValue("到仓");
            row.createCell(13).setCellValue("是");
            row.createCell(14).setCellValue("已确认");
            row.createCell(15).setCellValue("未提交");
            row.createCell(16).setCellValue("ET");
            row.createCell(17).setCellValue("易通");
            row.createCell(18).setCellValue("ET-SA-AIR");
            row.createCell(19).setCellValue("易通空运");
            row.createCell(20).setCellValue("ET-AIR-202606");
            row.createCell(21).setCellValue("易通空运普货");
            row.createCell(22).setCellValue("RMB");
            row.createCell(23).setCellValue(12.50);
            row.createCell(24).setCellValue("KG");
            row.createCell(25).setCellValue(250.00);
            row.createCell(26).setCellValue("物流已确认");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] yiteTemplateWorkbookBytes() throws Exception {
        try (HSSFWorkbook workbook = new HSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("模板");
            sheet.createRow(1).createCell(1).setCellValue("沙特海运双清");
            Row header = sheet.createRow(22);
            String[] headers = {
                    "货箱编号*", "货箱重量(KG)*", "货箱长度(CM)*", "货箱宽度(CM)*", "货箱高度(CM)*",
                    "产品SKU*", "产品英文品名*", "产品中文品名*", "产品申报数量*", "产品申报单价*",
                    "产品材质*", "产品图片", "产品品牌", "产品型号", "产品海关编码", "历史报价", "最新报价"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            Row row = sheet.createRow(23);
            row.createCell(0).setCellValue("1/1");
            row.createCell(1).setCellValue(8);
            row.createCell(2).setCellValue(40);
            row.createCell(3).setCellValue(40);
            row.createCell(4).setCellValue(40);
            row.createCell(5).setCellValue("SGGRB115");
            row.createCell(6).setCellValue("Orange Medium Marker Storage Box");
            row.createCell(7).setCellValue("测试商品");
            row.createCell(8).setCellValue(20);
            row.createCell(9).setCellValue(12.50);
            row.createCell(10).setCellValue("塑料");
            row.createCell(20).setCellValue("280001");
            row.createCell(21).setCellValue("200001");
            row.createCell(22).setCellValue("210001");
            row.createCell(23).setCellValue("220002");
            row.createCell(24).setCellValue("YT");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] yiteTemplateWorkbookWithoutHiddenIdsBytes() throws Exception {
        try (HSSFWorkbook workbook = new HSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("模板");
            sheet.createRow(1).createCell(1).setCellValue("沙特海运双清");
            Row header = sheet.createRow(22);
            String[] headers = {
                    "货箱编号*", "货箱重量(KG)*", "货箱长度(CM)*", "货箱宽度(CM)*", "货箱高度(CM)*",
                    "产品SKU*", "产品英文品名*", "产品中文品名*", "产品申报数量*", "产品申报单价*",
                    "产品材质*", "产品图片", "产品品牌", "产品型号", "产品海关编码"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            Row row = sheet.createRow(23);
            row.createCell(5).setCellValue("BARCODE-115");
            row.createCell(6).setCellValue("Orange Medium Marker Storage Box");
            row.createCell(7).setCellValue("测试商品");
            row.createCell(8).setCellValue(20);
            row.createCell(16).setCellValue(13.50);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] etTemplateWorkbookBytes() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("装箱清单");
            sheet.createRow(0).createCell(0).setCellValue("填表说明 1 1 该表格用于产品信息和装箱清单同时导入");
            Row header = sheet.createRow(1);
            String[] headers = {
                    "箱号", "*长(CM)", "*宽(CM)", "*高(CM)", "*重量(KG)", "*每箱数量",
                    "*商家条码", "款号", "*英文简称", "*中文品名", "*申报单价($)", "*实物品牌",
                    "平台品牌", "*图片", "*材质", "*是否带电", "*是否带磁", "*是否带蓝牙",
                    "*是否带液体", "*是否带粉末", "用途"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            String[] hiddenHeaders = {
                    "Nuono报价行ID", "Nuono采购单ID", "Nuono采购商品ID", "Nuono采购站点行ID",
                    "Nuono货代编码", "Nuono货代名称", "Nuono路线编码", "Nuono路线名称",
                    "Nuono服务编码", "Nuono服务名称", "货代确认单价", "计费单位", "确认金额", "物流备注"
            };
            for (int i = 0; i < hiddenHeaders.length; i++) {
                header.createCell(52 + i).setCellValue(hiddenHeaders[i]);
                sheet.setColumnHidden(52 + i, true);
            }
            Row row = sheet.createRow(2);
            row.createCell(0).setCellValue("1/1");
            row.createCell(1).setCellValue(50);
            row.createCell(2).setCellValue(40);
            row.createCell(3).setCellValue(30);
            row.createCell(4).setCellValue(10);
            row.createCell(5).setCellValue(20);
            row.createCell(6).setCellValue("SGGRB115");
            row.createCell(7).setCellValue("SGGRB115");
            row.createCell(8).setCellValue("Orange Medium Marker Storage Box");
            row.createCell(9).setCellValue("测试商品");
            row.createCell(10).setCellValue(1);
            row.createCell(11).setCellValue("paper says");
            row.createCell(12).setCellValue("paper says");
            row.createCell(14).setCellValue("塑料");
            row.createCell(15).setCellValue("否");
            row.createCell(16).setCellValue("否");
            row.createCell(17).setCellValue("否");
            row.createCell(18).setCellValue("否");
            row.createCell(19).setCellValue("否");
            row.createCell(52).setCellValue("280001");
            row.createCell(53).setCellValue("200001");
            row.createCell(54).setCellValue("210001");
            row.createCell(55).setCellValue("220002");
            row.createCell(56).setCellValue("ET");
            row.createCell(57).setCellValue("易通物流");
            row.createCell(58).setCellValue("ET-SAU-SEA-FBN-RUH-20260604");
            row.createCell(59).setCellValue("易通沙特海运仓到仓 20260604");
            row.createCell(60).setCellValue("ET-SAU-SEA-WH-20260604");
            row.createCell(61).setCellValue("易通沙特海运仓到仓 20260604");

            org.apache.poi.ss.usermodel.Sheet guide = workbook.createSheet("填表指南");
            guide.createRow(0).createCell(0).setCellValue("填表说明");
            Row guideHeader = guide.createRow(1);
            for (int i = 0; i < headers.length; i++) {
                guideHeader.createCell(i).setCellValue(headers[i]);
            }
            Row guideExample = guide.createRow(2);
            guideExample.createCell(0).setCellValue("1/20-18/20");
            guideExample.createCell(1).setCellValue(50);
            guideExample.createCell(2).setCellValue(50);
            guideExample.createCell(3).setCellValue(50);
            guideExample.createCell(4).setCellValue(10);
            guideExample.createCell(5).setCellValue(10);
            guideExample.createCell(6).setCellValue("bh088");
            guideExample.createCell(7).setCellValue("bh088");
            guideExample.createCell(8).setCellValue("T-shirt");
            guideExample.createCell(9).setCellValue("T恤");
            guideExample.createCell(10).setCellValue(1);
            guideExample.createCell(11).setCellValue("Basaa");
            guideExample.createCell(12).setCellValue("Basaa");
            guideExample.createCell(14).setCellValue("锦纶");
            guideExample.createCell(15).setCellValue("否");
            guideExample.createCell(16).setCellValue("否");
            guideExample.createCell(17).setCellValue("否");
            guideExample.createCell(18).setCellValue("否");
            guideExample.createCell(19).setCellValue("否");

            workbook.createSheet("仓单地址及须知");
            workbook.write(output);
            return output.toByteArray();
        }
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
