package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderSegmentRecord;
import java.util.List;
import org.junit.jupiter.api.Test;

class WarehouseShippingSubmissionCompletionTest {

    @Test
    void partialQuoteSubmissionDoesNotMarkSegmentsOrOrderSubmitted() {
        ProcurementPurchaseOrderMapper mapper = mock(ProcurementPurchaseOrderMapper.class);
        ShippingOrderRecord order = order();
        ShippingOrderSegmentRecord segment = new ShippingOrderSegmentRecord();
        segment.id = 292001L;
        PurchaseOrderLogisticsQuoteLineRecord first = new PurchaseOrderLogisticsQuoteLineRecord();
        PurchaseOrderLogisticsQuoteLineRecord second = new PurchaseOrderLogisticsQuoteLineRecord();
        when(mapper.submitLogisticsQuoteLinesForShippingOrder(290001L, 307L)).thenReturn(1);

        assertThatThrownBy(() -> WarehouseShippingSubmissionCompletion.markSubmitted(
                mapper, order, List.of(segment), List.of(first, second), 307L))
                .hasMessageContaining("状态已变化");

        verify(mapper, never()).markShippingOrderSegmentsSubmitted(290001L, 307L, 307L);
        verify(mapper, never()).markShippingOrderSubmitted(290001L, 307L, 307L);
    }

    @Test
    void partialSegmentSubmissionDoesNotMarkOrderSubmitted() {
        ProcurementPurchaseOrderMapper mapper = mock(ProcurementPurchaseOrderMapper.class);
        ShippingOrderRecord order = order();
        ShippingOrderSegmentRecord first = new ShippingOrderSegmentRecord();
        ShippingOrderSegmentRecord second = new ShippingOrderSegmentRecord();
        when(mapper.submitLogisticsQuoteLinesForShippingOrder(290001L, 307L)).thenReturn(1);
        when(mapper.markShippingOrderSegmentsSubmitted(290001L, 307L, 307L)).thenReturn(1);

        assertThatThrownBy(() -> WarehouseShippingSubmissionCompletion.markSubmitted(
                mapper, order, List.of(first, second),
                List.of(new PurchaseOrderLogisticsQuoteLineRecord()), 307L))
                .hasMessageContaining("状态已变化");

        verify(mapper, never()).markShippingOrderSubmitted(290001L, 307L, 307L);
    }

    private ShippingOrderRecord order() {
        ShippingOrderRecord order = new ShippingOrderRecord();
        order.id = 290001L;
        order.ownerUserId = 307L;
        return order;
    }
}
