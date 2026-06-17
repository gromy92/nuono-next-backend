package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseReceiptRow;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PurchaseReceiptItemView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PurchaseReceiptOrderView;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalDbWarehouseDispatchServiceTest {

    @Mock
    private WarehouseDispatchMapper mapper;

    @Test
    void listReceiptOrdersReturnsVariantAndStoreIdentityForMobileSpecPersistence() {
        LocalDbWarehouseDispatchService service =
                new LocalDbWarehouseDispatchService(mapper, new ObjectMapper());
        BusinessAccessContext access = BusinessAccessContext.builder()
                .sessionUserId(90004L)
                .businessOwnerUserId(307L)
                .storeCodes(Set.of("STR69486-NSA"))
                .build();

        PurchaseReceiptRow row = new PurchaseReceiptRow();
        row.orderId = 501L;
        row.orderNo = "PO-0617";
        row.orderTitle = "SGGR 0617";
        row.storeName = "SGGR";
        row.sourceStoreCode = "STR69486-NSA";
        row.createdAt = "2026-06-17";
        row.itemId = 9001L;
        row.productVariantId = 70001L;
        row.partnerSku = "PAPERSAYSB024";
        row.skuParent = "PAPERSAYS024";
        row.titleCache = "白卡标签贴";
        row.siteCode = "SA";
        row.transportMode = "AIR";
        row.expectedQuantity = 10;
        row.receivedQuantity = 0;
        row.plannedQuantity = 0;
        row.specStatus = "SPEC_MISSING";
        row.fulfillmentType = "WAREHOUSE_RECEIPT";

        when(mapper.listReceiptRows(eq(307L), eq(Set.of("STR69486-NSA")), eq(null)))
                .thenReturn(List.of(row));

        List<PurchaseReceiptOrderView> orders = service.listReceiptOrders(access, null);

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).storeCode).isEqualTo("STR69486-NSA");
        PurchaseReceiptItemView item = orders.get(0).items.get(0);
        assertThat(item.storeCode).isEqualTo("STR69486-NSA");
        assertThat(item.productVariantId).isEqualTo("70001");
    }
}
