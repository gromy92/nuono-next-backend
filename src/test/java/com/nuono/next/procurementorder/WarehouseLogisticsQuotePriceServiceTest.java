package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ProductForwarderChannelQuoteRecord;
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
    private ProcurementPurchaseOrderMapper mapper;

    @Mock
    private WarehouseProductLogisticsPriceBridge productPriceBridge;

    private WarehouseLogisticsQuotePriceService service;

    @BeforeEach
    void setUp() {
        service = new WarehouseLogisticsQuotePriceService(mapper, productPriceBridge);
    }

    @Test
    void productCurrentCostIsAPendingWarehouseSuggestion() {
        PurchaseOrderLogisticsQuoteLineRecord line = line("PENDING_QUOTE");
        ForwarderRouteRecommendationRecord candidate = candidate();
        CurrentCostRow current = new CurrentCostRow();
        current.unitCostCny = new BigDecimal("67.00");
        current.currencyCode = "CNY";
        current.chargeUnit = "KG";
        when(productPriceBridge.findCurrentCost(line, "QIKE")).thenReturn(current);

        var view = service.resolve(line, candidate);

        assertThat(view.quoteStatus).isEqualTo("PENDING_QUOTE");
        assertThat(view.unitPrice).isEqualByComparingTo("67.00");
        assertThat(view.priceSource).isEqualTo("PRODUCT_CURRENT");
    }

    @Test
    void ownConfirmedSnapshotWinsOverLaterProductCurrentCost() {
        PurchaseOrderLogisticsQuoteLineRecord line = line("CONFIRMED");
        line.forwarderCode = "QIKE";
        line.routeCode = "QIKE-SA-AIR";
        line.serviceCode = "QIKE-SA-AIR";
        line.unitPrice = new BigDecimal("67.00");
        line.currency = "CNY";
        line.billingUnit = "KG";

        var view = service.resolve(line, candidate());

        assertThat(view.quoteStatus).isEqualTo("CONFIRMED");
        assertThat(view.unitPrice).isEqualByComparingTo("67.00");
        assertThat(view.priceSource).isEqualTo("SHIPPING_ORDER_SNAPSHOT");
        verify(productPriceBridge, never()).findCurrentCost(line, "QIKE");
    }

    @Test
    void legacyExactChannelQuoteRemainsPendingUntilSaved() {
        PurchaseOrderLogisticsQuoteLineRecord line = line("PENDING_QUOTE");
        ForwarderRouteRecommendationRecord candidate = candidate();
        ProductForwarderChannelQuoteRecord legacy = new ProductForwarderChannelQuoteRecord();
        legacy.unitPrice = new BigDecimal("61.00");
        legacy.currency = "CNY";
        legacy.billingUnit = "KG";
        when(mapper.selectCurrentProductForwarderChannelQuote(
                307L, "STR69486-NSA", 301L, "SGGRB180", 54271L,
                "QIKE", "SA", "QIKE-SA-AIR", "QIKE-SA-AIR"
        )).thenReturn(legacy);

        var view = service.resolve(line, candidate);

        assertThat(view.quoteStatus).isEqualTo("PENDING_QUOTE");
        assertThat(view.unitPrice).isEqualByComparingTo("61.00");
        assertThat(view.priceSource).isEqualTo("LEGACY_CHANNEL_QUOTE");
    }

    private PurchaseOrderLogisticsQuoteLineRecord line(String status) {
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
        line.quoteStatus = status;
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
