package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.CreateShippingOrderCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderRecord;
import com.nuono.next.productselection.LocalDbAli1688CollectionService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class LocalDbProcurementPurchaseOrderServiceShippingOrderConcurrencyTest {

    @Test
    void locksOrdersAndItemSitesInStableOrderBeforeRecheckingActiveUse() {
        ProcurementPurchaseOrderMapper mapper = mock(ProcurementPurchaseOrderMapper.class);
        LocalDbProcurementPurchaseOrderService service = ProcurementPurchaseOrderServiceTestFactory.create(
                mapper,
                mock(ProductSelectionMapper.class),
                mock(LocalDbAli1688CollectionService.class),
                new ObjectMapper(),
                mock(WarehouseLogisticsQuotePriceService.class)
        );
        PurchaseOrderRecord first = order(200001L);
        PurchaseOrderRecord second = order(200002L);
        PurchaseOrderLogisticsQuoteLineRecord firstLine = line(280001L, 220002L);
        PurchaseOrderLogisticsQuoteLineRecord secondLine = line(280002L, 220001L);
        when(mapper.selectOrderByIdForUpdate(200001L)).thenReturn(first);
        when(mapper.selectOrderByIdForUpdate(200002L)).thenReturn(second);
        when(mapper.listLogisticsQuoteCandidatesByOrder(200001L)).thenReturn(List.of(firstLine));
        when(mapper.listLogisticsQuoteCandidatesByOrder(200002L)).thenReturn(List.of(secondLine));
        when(mapper.lockPurchaseOrderItemSitesForShipping(307L, List.of(220001L, 220002L)))
                .thenReturn(List.of(220001L, 220002L));
        when(mapper.countActiveShippingOrderLinesByItemSites(List.of(220001L, 220002L)))
                .thenReturn(1);
        CreateShippingOrderCommand command = new CreateShippingOrderCommand();
        command.purchaseOrderIds = List.of("200002", "200001");

        assertThatThrownBy(() -> service.createShippingOrder(access(), command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能重复合并");

        InOrder locksBeforeCheck = inOrder(mapper);
        locksBeforeCheck.verify(mapper).selectOrderByIdForUpdate(200001L);
        locksBeforeCheck.verify(mapper).selectOrderByIdForUpdate(200002L);
        locksBeforeCheck.verify(mapper).listLogisticsQuoteCandidatesByOrder(200001L);
        locksBeforeCheck.verify(mapper).listLogisticsQuoteCandidatesByOrder(200002L);
        locksBeforeCheck.verify(mapper)
                .lockPurchaseOrderItemSitesForShipping(307L, List.of(220001L, 220002L));
        locksBeforeCheck.verify(mapper)
                .countActiveShippingOrderLinesByItemSites(List.of(220001L, 220002L));
        verify(mapper, never()).refreshLogisticsQuoteLineSnapshot(any(), anyLong());
        verify(mapper, never()).insertShippingOrder(any(), anyLong());
    }

    private BusinessAccessContext access() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .storeCodes(Set.of("STR69486-NSA"))
                .storeOwnerUserIds(Map.of("STR69486-NSA", 307L))
                .build();
    }

    private PurchaseOrderRecord order(Long id) {
        PurchaseOrderRecord order = new PurchaseOrderRecord();
        order.id = id;
        order.ownerUserId = 307L;
        order.status = "SUBMITTED";
        order.anchorStoreCodeCache = "STR69486-NSA";
        return order;
    }

    private PurchaseOrderLogisticsQuoteLineRecord line(Long id, Long itemSiteId) {
        PurchaseOrderLogisticsQuoteLineRecord line = new PurchaseOrderLogisticsQuoteLineRecord();
        line.id = id;
        line.purchaseOrderItemSiteId = itemSiteId;
        return line;
    }
}
