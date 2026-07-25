package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductLogisticsCostMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CostHistoryRow;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CurrentCostRow;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WarehouseProductLogisticsPriceBridgeTest {

    @Mock
    private ProductLogisticsCostMapper mapper;

    private WarehouseProductLogisticsPriceBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new WarehouseProductLogisticsPriceBridge(
                mapper,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-07-25T03:30:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void findsCanonicalHeadhaulCurrentCostByWarehouseBusinessKey() {
        PurchaseOrderLogisticsQuoteLineRecord line = quoteLine();
        CurrentCostRow current = new CurrentCostRow();
        current.unitCostCny = new BigDecimal("67.00");
        current.feeType = "HEADHAUL";
        when(mapper.listCurrentCosts(
                307L, 301L, null, "SGGRB180", "SA", "QIKE", "AIR", 20
        )).thenReturn(java.util.List.of(current));

        CurrentCostRow resolved = bridge.findCurrentCost(line, "QIKE");

        assertThat(resolved).isSameAs(current);
    }

    @Test
    void confirmedWarehouseQuoteAppendsHistoryAndUpdatesCurrentCost() {
        PurchaseOrderLogisticsQuoteLineRecord line = quoteLine();
        CurrentCostRow existing = new CurrentCostRow();
        existing.cargoCategoryCode = "SA_GENERAL";
        existing.cargoCategoryName = "普货";
        existing.feeType = "HEADHAUL";
        when(mapper.listCurrentCosts(
                307L, 301L, null, "SGGRB180", "SA", "QIKE", "AIR", 20
        )).thenReturn(java.util.List.of(existing));
        when(mapper.nextProductLogisticsCostHistoryId()).thenReturn(370101L);
        when(mapper.nextProductLogisticsCurrentCostId()).thenReturn(380101L);

        bridge.syncConfirmedQuote(line, 308L, "SHIPPING_ORDER_INLINE_QUOTE");

        ArgumentCaptor<CostHistoryRow> historyCaptor = ArgumentCaptor.forClass(CostHistoryRow.class);
        verify(mapper).insertCostHistory(historyCaptor.capture(), eq(308L));
        CostHistoryRow history = historyCaptor.getValue();
        assertThat(history.id).isEqualTo(370101L);
        assertThat(history.partnerSku).isEqualTo("SGGRB180");
        assertThat(history.sourceShippingOrderId).isEqualTo(290002L);
        assertThat(history.sourceQuoteLineId).isEqualTo(280304L);
        assertThat(history.routeCode).isEqualTo("QIKE-SAU-AIR-FBN-RUH-20260523");
        assertThat(history.unitCostCny).isEqualByComparingTo("68.50");
        assertThat(history.cargoCategoryCode).isEqualTo("SA_GENERAL");
        assertThat(history.costOccurredAt).isEqualTo("2026-07-25T03:30:00");

        ArgumentCaptor<CurrentCostRow> currentCaptor = ArgumentCaptor.forClass(CurrentCostRow.class);
        verify(mapper).upsertCurrentCost(currentCaptor.capture(), eq(308L));
        CurrentCostRow current = currentCaptor.getValue();
        assertThat(current.id).isEqualTo(380101L);
        assertThat(current.currentHistoryId).isEqualTo(370101L);
        assertThat(current.unitCostCny).isEqualByComparingTo("68.50");
        assertThat(current.cargoCategoryCode).isEqualTo("SA_GENERAL");
        assertThat(current.sourceType).isEqualTo("SHIPPING_ORDER_INLINE_QUOTE");
    }

    @Test
    void nonCnyWarehouseQuoteDoesNotCorruptCnyCurrentCost() {
        PurchaseOrderLogisticsQuoteLineRecord line = quoteLine();
        line.currency = "USD";

        bridge.syncConfirmedQuote(line, 308L, "SHIPPING_ORDER_QUOTE_IMPORT");

        verify(mapper, never()).insertCostHistory(org.mockito.ArgumentMatchers.any(), eq(308L));
        verify(mapper, never()).upsertCurrentCost(org.mockito.ArgumentMatchers.any(), eq(308L));
    }

    private PurchaseOrderLogisticsQuoteLineRecord quoteLine() {
        PurchaseOrderLogisticsQuoteLineRecord line = new PurchaseOrderLogisticsQuoteLineRecord();
        line.id = 280304L;
        line.ownerUserId = 307L;
        line.logicalStoreId = 301L;
        line.productMasterId = 310001L;
        line.productVariantId = 54271L;
        line.partnerSku = "SGGRB180";
        line.barcode = "XGGEKSA04071";
        line.siteCode = "SA";
        line.plannedTransportMode = "AIR";
        line.forwarderCode = "QIKE";
        line.forwarderName = "启客物流";
        line.routeCode = "QIKE-SAU-AIR-FBN-RUH-20260523";
        line.routeName = "启客沙特空运";
        line.serviceCode = "QIKE-SAU-AIR-FBN-RUH-20260523";
        line.serviceName = "启客沙特空运";
        line.shippingOrderId = 290002L;
        line.shippingOrderLineId = 300106L;
        line.currency = "CNY";
        line.billingUnit = "KG";
        line.unitPrice = new BigDecimal("68.50");
        return line;
    }
}
