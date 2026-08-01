package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderSegmentRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.ShippingOrderView;
import com.nuono.next.productselection.LocalDbAli1688CollectionService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocalDbProcurementPurchaseOrderServiceEligibilityProjectionTest {

    @Test
    void shippingOrderDetailHidesHistoricalPriceForCurrentlyUnsupportedProduct() {
        ProcurementPurchaseOrderMapper mapper = mock(ProcurementPurchaseOrderMapper.class);
        LocalDbProcurementPurchaseOrderService service = ProcurementPurchaseOrderServiceTestFactory.create(
                mapper,
                mock(ProductSelectionMapper.class),
                mock(LocalDbAli1688CollectionService.class),
                new ObjectMapper(),
                mock(WarehouseLogisticsQuotePriceService.class)
        );
        ShippingOrderRecord order = new ShippingOrderRecord();
        order.id = 290001L;
        order.ownerUserId = 307L;
        ShippingOrderSegmentRecord segment = new ShippingOrderSegmentRecord();
        segment.id = 292001L;
        segment.siteCode = "SA";
        segment.transportMode = "SEA";
        segment.forwarderCode = "YT";
        ShippingOrderLineRecord line = new ShippingOrderLineRecord();
        line.id = 291001L;
        line.shippingOrderSegmentId = 292001L;
        line.ownerUserId = 307L;
        line.logicalStoreId = 108065L;
        line.productVariantId = 9001L;
        line.partnerSku = "PSKU-1";
        line.siteCode = "SA";
        line.plannedTransportMode = "SEA";
        line.quoteStatus = "CONFIRMED";
        line.shippingSubmitStatus = "NOT_SUBMITTED";
        line.unitPrice = new BigDecimal("1540.0000");
        line.currency = "RMB";
        line.billingUnit = "CBM";
        ProductForwarderTransportEligibilityRecord rule =
                new ProductForwarderTransportEligibilityRecord();
        rule.ownerUserId = 307L;
        rule.logicalStoreId = 108065L;
        rule.partnerSku = "PSKU-1";
        rule.productVariantId = 9001L;
        rule.siteCode = "SA";
        rule.forwarderCode = "YT";
        rule.transportMode = "SEA";
        rule.eligibilityStatus = "UNSUPPORTED";
        when(mapper.selectShippingOrderById(290001L)).thenReturn(order);
        when(mapper.listShippingOrderSegments(290001L)).thenReturn(List.of(segment));
        when(mapper.listShippingOrderLines(290001L)).thenReturn(List.of(line));
        when(mapper.listCurrentProductForwarderTransportEligibilities(List.of(
                new ProductForwarderEligibilityScopeAnchorRecord(307L, 108065L, "PSKU-1"))))
                .thenReturn(List.of(rule));

        ShippingOrderView view = service.getShippingOrder(
                BusinessAccessContext.builder()
                        .sessionUserId(307L)
                        .businessOwnerUserId(307L)
                        .build(),
                "290001"
        );

        assertThat(view.lines).singleElement().satisfies(projected -> {
            assertThat(projected.eligibilityStatus).isEqualTo("UNSUPPORTED");
            assertThat(projected.quoteStatus).isEqualTo("PENDING_QUOTE");
            assertThat(projected.unitPrice).isNull();
            assertThat(projected.currency).isNull();
            assertThat(projected.billingUnit).isNull();
        });
    }
}
