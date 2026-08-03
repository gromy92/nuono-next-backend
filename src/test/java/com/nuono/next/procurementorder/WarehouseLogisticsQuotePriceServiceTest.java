package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CurrentCostRow;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WarehouseLogisticsQuotePriceServiceTest {

    @Mock
    private WarehouseProductLogisticsPriceBridge productPriceBridge;

    private WarehouseLogisticsQuotePriceService service;

    @BeforeEach
    void setUp() {
        service = new WarehouseLogisticsQuotePriceService(productPriceBridge);
    }

    @Test
    void productCurrentCostIsImmediatelyUsableWithoutManualConfirmation() {
        PurchaseOrderLogisticsQuoteLineRecord line = line("NOT_SUBMITTED");
        CurrentCostRow current = current("67.00");
        when(productPriceBridge.findCurrentCost(line, "QIKE")).thenReturn(current);

        PurchaseOrderLogisticsQuoteChannelLineView view = service.resolve(line, candidate());

        assertThat(view.quoteStatus).isEqualTo("CONFIRMED");
        assertThat(view.unitPrice).isEqualByComparingTo("67.00");
        assertThat(view.currency).isEqualTo("CNY");
        assertThat(view.billingUnit).isEqualTo("KG");
        assertThat(view.priceSource).isEqualTo("PRODUCT_CURRENT");
    }

    @Test
    void zeroAndNegativeProductCurrentCostsRemainMissingWithoutStaleFacts() {
        PurchaseOrderLogisticsQuoteLineRecord line = line("NOT_SUBMITTED");
        for (String value : new String[]{"0", "-1.00"}) {
            when(productPriceBridge.findCurrentCost(line, "QIKE")).thenReturn(current(value));

            PurchaseOrderLogisticsQuoteChannelLineView view = service.resolve(line, candidate());

            assertThat(view.quoteStatus).isEqualTo("PENDING_QUOTE");
            assertThat(view.unitPrice).isNull();
            assertThat(view.currency).isNull();
            assertThat(view.billingUnit).isNull();
            assertThat(view.priceSource).isNull();
        }
    }

    @Test
    void unsubmittedOwnPriceDoesNotOverrideLatestProductCurrentCost() {
        PurchaseOrderLogisticsQuoteLineRecord line = exactSnapshot("NOT_SUBMITTED", "61.00");
        when(productPriceBridge.findCurrentCost(line, "QIKE")).thenReturn(current("67.00"));

        PurchaseOrderLogisticsQuoteChannelLineView view = service.resolve(line, candidate(), line);

        assertThat(view.unitPrice).isEqualByComparingTo("67.00");
        assertThat(view.priceSource).isEqualTo("PRODUCT_CURRENT");
    }

    @Test
    void submittedSnapshotWinsOverLaterProductCurrentCost() {
        PurchaseOrderLogisticsQuoteLineRecord line = exactSnapshot("SUBMITTED", "61.00");

        PurchaseOrderLogisticsQuoteChannelLineView view = service.resolve(line, candidate(), line);

        assertThat(view.quoteStatus).isEqualTo("CONFIRMED");
        assertThat(view.unitPrice).isEqualByComparingTo("61.00");
        assertThat(view.priceSource).isEqualTo("SHIPPING_ORDER_SNAPSHOT");
        verify(productPriceBridge, never()).findCurrentCost(line, "QIKE");
    }

    @Test
    void submittedZdMissingSnapshotDoesNotAdoptALaterCurrentPrice() {
        PurchaseOrderLogisticsQuoteLineRecord line = exactSnapshot("SUBMITTED", null);
        line.forwarderCode = "ZD";
        line.routeCode = "ZD-SA-AIR";
        line.serviceCode = "ZD-SA-AIR";
        line.currency = "CNY";
        line.billingUnit = "KG";
        ForwarderRouteRecommendationRecord candidate = candidate();
        candidate.forwarderCode = "ZD";
        candidate.routeCode = "ZD-SA-AIR";
        candidate.serviceCode = "ZD-SA-AIR";
        org.mockito.Mockito.lenient().when(productPriceBridge.findCurrentCost(line, "ZD"))
                .thenReturn(current("67.00"));

        PurchaseOrderLogisticsQuoteChannelLineView view = service.resolve(line, candidate, line);

        assertThat(view.quoteStatus).isEqualTo("PENDING_QUOTE");
        assertThat(view.unitPrice).isNull();
        assertThat(view.currency).isEqualTo("CNY");
        assertThat(view.billingUnit).isEqualTo("KG");
        assertThat(view.priceSource).isEqualTo("SHIPPING_ORDER_SNAPSHOT");
        verify(productPriceBridge, never()).findCurrentCost(line, "ZD");
    }

    @Test
    void submittedSnapshotFromAnotherChannelDoesNotOverrideCurrentCost() {
        PurchaseOrderLogisticsQuoteLineRecord line = exactSnapshot("SUBMITTED", "61.00");
        line.routeCode = "QIKE-SA-SEA";
        when(productPriceBridge.findCurrentCost(line, "QIKE")).thenReturn(current("67.00"));

        PurchaseOrderLogisticsQuoteChannelLineView view = service.resolve(line, candidate(), line);

        assertThat(view.unitPrice).isEqualByComparingTo("67.00");
        assertThat(view.priceSource).isEqualTo("PRODUCT_CURRENT");
    }

    private CurrentCostRow current(String value) {
        CurrentCostRow current = new CurrentCostRow();
        current.unitCostCny = new BigDecimal(value);
        current.currencyCode = "CNY";
        current.chargeUnit = "KG";
        return current;
    }

    private PurchaseOrderLogisticsQuoteLineRecord exactSnapshot(String submitStatus, String unitPrice) {
        PurchaseOrderLogisticsQuoteLineRecord line = line(submitStatus);
        line.forwarderCode = "QIKE";
        line.routeCode = "QIKE-SA-AIR";
        line.serviceCode = "QIKE-SA-AIR";
        line.unitPrice = unitPrice == null ? null : new BigDecimal(unitPrice);
        line.currency = "CNY";
        line.billingUnit = "KG";
        return line;
    }

    private PurchaseOrderLogisticsQuoteLineRecord line(String submitStatus) {
        PurchaseOrderLogisticsQuoteLineRecord line = new PurchaseOrderLogisticsQuoteLineRecord();
        line.id = 280304L;
        line.ownerUserId = 307L;
        line.logicalStoreId = 301L;
        line.sourceStoreCode = "STR69486-NSA";
        line.productVariantId = 54271L;
        line.partnerSku = "SGGRB180";
        line.siteCode = "SA";
        line.plannedTransportMode = "AIR";
        line.shippingOrderLineId = 300106L;
        line.shippingSubmitStatus = submitStatus;
        return line;
    }

    private ForwarderRouteRecommendationRecord candidate() {
        ForwarderRouteRecommendationRecord candidate = new ForwarderRouteRecommendationRecord();
        candidate.forwarderCode = "QIKE";
        candidate.siteCode = "SA";
        candidate.routeCode = "QIKE-SA-AIR";
        candidate.serviceCode = "QIKE-SA-AIR";
        return candidate;
    }
}
