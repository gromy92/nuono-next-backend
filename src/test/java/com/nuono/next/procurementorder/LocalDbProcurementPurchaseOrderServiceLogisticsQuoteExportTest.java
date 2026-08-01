package com.nuono.next.procurementorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderRecord;
import com.nuono.next.productselection.LocalDbAli1688CollectionService;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.junit.jupiter.api.Test;

class LocalDbProcurementPurchaseOrderServiceLogisticsQuoteExportTest {

    @Test
    void reportLinesIncludeAlreadyPricedLinesForSelectedRoute() {
        ForwarderRouteRecommendationRecord route = new ForwarderRouteRecommendationRecord();
        route.siteCode = "SA";
        route.transportMode = "SEA";

        PurchaseOrderLogisticsQuoteLineRecord pending = line("PENDING_QUOTE", "SA", "SEA");
        PurchaseOrderLogisticsQuoteLineRecord confirmed = line("CONFIRMED", "SA", "SEA");
        PurchaseOrderLogisticsQuoteLineRecord otherRoute = line("PENDING_QUOTE", "AE", "SEA");

        List<PurchaseOrderLogisticsQuoteLineRecord> reportLines =
                LocalDbProcurementPurchaseOrderService.logisticsQuoteReportLines(
                        List.of(pending, confirmed, otherRoute),
                        route
                );

        assertEquals(List.of(pending, confirmed), reportLines);
    }

    @Test
    void reportLinesCanExportOnlyMissingPricesForSelectedRoute() {
        ForwarderRouteRecommendationRecord route = new ForwarderRouteRecommendationRecord();
        route.siteCode = "SA";
        route.transportMode = "SEA";

        PurchaseOrderLogisticsQuoteLineRecord pending = line("PENDING_QUOTE", "SA", "SEA");
        PurchaseOrderLogisticsQuoteLineRecord confirmed = line("CONFIRMED", "SA", "SEA");
        PurchaseOrderLogisticsQuoteLineRecord otherRoute = line("PENDING_QUOTE", "AE", "SEA");

        List<PurchaseOrderLogisticsQuoteLineRecord> reportLines =
                LocalDbProcurementPurchaseOrderService.logisticsQuoteReportLines(
                        List.of(pending, confirmed, otherRoute),
                        route,
                        true
                );

        assertEquals(List.of(pending), reportLines);
    }

    @Test
    void reportLinesCanExportOnlyMissingPricesForSelectedChannelCoverage() {
        ForwarderRouteRecommendationRecord route = new ForwarderRouteRecommendationRecord();
        route.siteCode = "SA";
        route.transportMode = "SEA";

        PurchaseOrderLogisticsQuoteLineRecord missingForSelectedChannel = line("CONFIRMED", "SA", "SEA");
        missingForSelectedChannel.shippingOrderLineId = 101L;
        missingForSelectedChannel.estimatedAmount = new BigDecimal("999.00");
        PurchaseOrderLogisticsQuoteLineRecord confirmedForSelectedChannel = line("CONFIRMED", "SA", "SEA");
        confirmedForSelectedChannel.shippingOrderLineId = 102L;

        PurchaseOrderLogisticsQuoteChannelLineView pendingQuote = channelQuote("101", "PENDING_QUOTE");
        PurchaseOrderLogisticsQuoteChannelLineView confirmedQuote = channelQuote("102", "CONFIRMED");

        List<PurchaseOrderLogisticsQuoteLineRecord> reportLines =
                LocalDbProcurementPurchaseOrderService.logisticsQuoteReportLines(
                        List.of(missingForSelectedChannel, confirmedForSelectedChannel),
                        route,
                        List.of(pendingQuote, confirmedQuote),
                        true
                );

        assertEquals(List.of(missingForSelectedChannel), reportLines);
        assertEquals("PENDING_QUOTE", missingForSelectedChannel.quoteStatus);
        assertEquals(null, missingForSelectedChannel.estimatedAmount);
    }

    @Test
    void yiteTemplateExportsHistoryQuoteColumn() throws Exception {
        LocalDbProcurementPurchaseOrderService service =
                new LocalDbProcurementPurchaseOrderService(
                        null, null, null, new ObjectMapper(), null, null, null, null
                );
        Method method = LocalDbProcurementPurchaseOrderService.class.getDeclaredMethod(
                "buildYiteLogisticsQuoteWorkbook",
                PurchaseOrderRecord.class,
                List.class
        );
        method.setAccessible(true);

        PurchaseOrderRecord order = new PurchaseOrderRecord();
        order.orderNo = "SO-TEST";
        PurchaseOrderLogisticsQuoteLineRecord line = line("CONFIRMED", "SA", "SEA");
        line.barcode = "PAPERSAYSB085";
        line.titleEn = "Sample English";
        line.titleCache = "样品";
        line.quantity = 10;
        line.forwarderCode = "YT";
        line.serviceName = "义特沙特海运";
        line.unitPrice = new BigDecimal("1390.0000");
        line.billingUnit = "CBM";

        byte[] bytes = (byte[]) method.invoke(service, order, List.of(line));

        try (HSSFWorkbook workbook = new HSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(22);
            Row data = sheet.getRow(23);
            int historyQuoteColumn = findHeaderColumn(header, "历史报价");

            assertEquals(15, historyQuoteColumn);
            assertEquals("1390/CBM", data.getCell(historyQuoteColumn).getStringCellValue());
        }
    }

    @Test
    void unsupportedPurchaseExportDoesNotMaterializeOrAssignQuoteRows() {
        ProcurementPurchaseOrderMapper mapper = mock(ProcurementPurchaseOrderMapper.class);
        WarehouseLogisticsQuotePriceService priceService = mock(WarehouseLogisticsQuotePriceService.class);
        LocalDbProcurementPurchaseOrderService service = service(mapper, priceService);
        PurchaseOrderRecord order = order();
        PurchaseOrderLogisticsQuoteLineRecord line = eligibilityLine("PENDING_QUOTE");
        ForwarderRouteRecommendationRecord candidate = candidate();
        when(mapper.selectOrderByIdForUpdate(200001L)).thenReturn(order);
        when(mapper.listLogisticsQuoteCandidatesByOrder(200001L)).thenReturn(List.of(line));
        when(mapper.lockProductVariantsForForwarderEligibility(307L, List.of(9001L)))
                .thenReturn(List.of(9001L));
        when(mapper.listRouteRecommendationCandidates(List.of("SA"), "SEA"))
                .thenReturn(List.of(candidate));
        when(mapper.listCurrentProductForwarderTransportEligibilities(307L, List.of(9001L)))
                .thenReturn(List.of(rule("UNSUPPORTED")));
        when(priceService.resolve(any(), any(), any()))
                .thenReturn(new PurchaseOrderLogisticsQuoteChannelLineView());

        assertThatThrownBy(() -> service.exportLogisticsQuoteReport(
                access(), "200001", "ET", "ET-SAU-SEA-FBN-RUH"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("当前不接");

        verify(mapper, never()).insertLogisticsQuoteLine(any(), anyLong());
        verify(mapper, never()).refreshLogisticsQuoteLineSnapshot(any(), anyLong());
        verify(mapper, never()).persistLogisticsQuoteLineSelection(any(), anyLong());
        verify(mapper, never()).markLogisticsQuoteLinesExported(anyLong(), anyList(), anyLong());
    }

    @Test
    void purchaseSubmitRechecksEligibilityForPositivePriceWithLegacyPendingStatus() {
        ProcurementPurchaseOrderMapper mapper = mock(ProcurementPurchaseOrderMapper.class);
        WarehouseLogisticsQuotePriceService priceService = mock(WarehouseLogisticsQuotePriceService.class);
        LocalDbProcurementPurchaseOrderService service = service(mapper, priceService);
        PurchaseOrderLogisticsQuoteLineRecord line = eligibilityLine("PENDING_QUOTE");
        line.forwarderCode = "ET";
        line.routeCode = "ET-SAU-SEA-FBN-RUH";
        line.serviceCode = "ET-SAU-SEA-WH";
        line.unitPrice = new BigDecimal("1540.0000");
        when(mapper.selectOrderByIdForUpdate(200001L)).thenReturn(order());
        when(mapper.listLogisticsQuoteCandidatesByOrder(200001L)).thenReturn(List.of(line));
        when(mapper.lockProductVariantsForForwarderEligibility(307L, List.of(9001L)))
                .thenReturn(List.of(9001L));
        when(mapper.listCurrentProductForwarderTransportEligibilities(307L, List.of(9001L)))
                .thenReturn(List.of(rule("INQUIRY_REQUIRED")));
        when(mapper.listRouteRecommendationCandidates(List.of("SA"), "SEA"))
                .thenReturn(List.of(candidate()));
        PurchaseOrderLogisticsQuoteChannelLineView current = new PurchaseOrderLogisticsQuoteChannelLineView();
        current.unitPrice = new BigDecimal("1540.0000");
        current.currency = "CNY";
        current.billingUnit = "CBM";
        when(priceService.resolve(any(), any(), any())).thenReturn(current);
        when(mapper.confirmLogisticsQuoteLine(any(), anyLong())).thenReturn(1);

        assertThatThrownBy(() -> service.submitShipping(access(), "200001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("询价确认");

        verify(mapper).lockProductVariantsForForwarderEligibility(307L, List.of(9001L));
        verify(mapper, never()).countMissingLogisticsQuotePrices(anyLong());
        verify(mapper, never()).submitLogisticsQuoteLinesForShipping(anyLong(), anyLong());
    }

    private LocalDbProcurementPurchaseOrderService service(
            ProcurementPurchaseOrderMapper mapper,
            WarehouseLogisticsQuotePriceService priceService
    ) {
        return ProcurementPurchaseOrderServiceTestFactory.create(
                mapper,
                mock(ProductSelectionMapper.class),
                mock(LocalDbAli1688CollectionService.class),
                new ObjectMapper(),
                priceService
        );
    }

    private BusinessAccessContext access() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .storeCodes(Set.of("STR69486-NSA"))
                .storeOwnerUserIds(Map.of("STR69486-NSA", 307L))
                .build();
    }

    private PurchaseOrderRecord order() {
        PurchaseOrderRecord order = new PurchaseOrderRecord();
        order.id = 200001L;
        order.ownerUserId = 307L;
        order.orderNo = "PO-200001";
        order.status = "READY";
        order.anchorStoreCodeCache = "STR69486-NSA";
        return order;
    }

    private PurchaseOrderLogisticsQuoteLineRecord eligibilityLine(String quoteStatus) {
        PurchaseOrderLogisticsQuoteLineRecord line = new PurchaseOrderLogisticsQuoteLineRecord();
        line.id = 280001L;
        line.ownerUserId = 307L;
        line.productVariantId = 9001L;
        line.partnerSku = "PSKU-1";
        line.siteCode = "SA";
        line.plannedTransportMode = "SEA";
        line.quoteStatus = quoteStatus;
        line.shippingSubmitStatus = "NOT_SUBMITTED";
        return line;
    }

    private ForwarderRouteRecommendationRecord candidate() {
        ForwarderRouteRecommendationRecord candidate = new ForwarderRouteRecommendationRecord();
        candidate.forwarderCode = "ET";
        candidate.routeCode = "ET-SAU-SEA-FBN-RUH";
        candidate.serviceCode = "ET-SAU-SEA-WH";
        candidate.siteCode = "SA";
        candidate.transportMode = "SEA";
        return candidate;
    }

    private ProductForwarderTransportEligibilityRecord rule(String status) {
        ProductForwarderTransportEligibilityRecord rule =
                new ProductForwarderTransportEligibilityRecord();
        rule.ownerUserId = 307L;
        rule.productVariantId = 9001L;
        rule.siteCode = "SA";
        rule.forwarderCode = "ET";
        rule.transportMode = "SEA";
        rule.eligibilityStatus = status;
        return rule;
    }

    private int findHeaderColumn(Row header, String title) {
        for (int index = 0; index < header.getLastCellNum(); index++) {
            Cell cell = header.getCell(index);
            if (cell != null && title.equals(cell.getStringCellValue())) {
                return index;
            }
        }
        return -1;
    }

    private PurchaseOrderLogisticsQuoteLineRecord line(String quoteStatus, String siteCode, String transportMode) {
        PurchaseOrderLogisticsQuoteLineRecord line = new PurchaseOrderLogisticsQuoteLineRecord();
        line.quoteStatus = quoteStatus;
        line.unitPrice = "CONFIRMED".equals(quoteStatus) ? BigDecimal.ONE : null;
        line.siteCode = siteCode;
        line.plannedTransportMode = transportMode;
        return line;
    }

    private PurchaseOrderLogisticsQuoteChannelLineView channelQuote(String shippingOrderLineId, String quoteStatus) {
        PurchaseOrderLogisticsQuoteChannelLineView quote = new PurchaseOrderLogisticsQuoteChannelLineView();
        quote.shippingOrderLineId = shippingOrderLineId;
        quote.quoteStatus = quoteStatus;
        quote.unitPrice = "CONFIRMED".equals(quoteStatus) ? BigDecimal.ONE : null;
        return quote;
    }
}
