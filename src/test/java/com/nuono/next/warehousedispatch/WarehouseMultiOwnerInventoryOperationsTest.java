package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.UpdateDispatchTargetCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentBalanceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseReceiptRow;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WarehouseMultiOwnerInventoryOperationsTest extends WarehouseDispatchServiceTestSupport {

    @Test
    void secondOwnerReadyInventoryIsVisibleThroughItsExactStorePair() {
        Map<String, Long> storeOwners = storeOwners();
        FulfillmentBalanceRecord secondOwner = balance("CONFIRMED", "SUBMITTED");
        secondOwner.ownerUserId = 409L;
        secondOwner.sourceStoreCode = "STORE-B";
        when(mapper.listReadyBalances(storeOwners, null, null)).thenReturn(List.of(secondOwner));

        var items = service.listReadyItems(multiOwnerAccess(), null, null, null);

        assertThat(items).singleElement()
                .satisfies(item -> assertThat(item.sources).singleElement()
                        .satisfies(source -> assertThat(source.fulfillmentBalanceId).isEqualTo(900001L)));
        verify(mapper).listReadyBalances(storeOwners, null, null);
    }

    @Test
    void mismatchedStoreOwnerPairIsFilteredEvenIfPersistenceReturnsIt() {
        FulfillmentBalanceRecord mismatched = balance("CONFIRMED", "SUBMITTED");
        mismatched.ownerUserId = 307L;
        mismatched.sourceStoreCode = "STORE-B";
        when(mapper.listReadyBalances(storeOwners(), null, null)).thenReturn(List.of(mismatched));

        assertThat(service.listReadyItems(multiOwnerAccess(), null, null, null)).isEmpty();
    }

    @Test
    void partiallyMappedAccessDoesNotInferAnOwnerForTheMissingStore() {
        BusinessAccessContext partialAccess = BusinessAccessContext.builder()
                .sessionUserId(901L)
                .businessOwnerUserId(307L)
                .storeCodes(Set.of("STORE-A", "STORE-B"))
                .storeOwnerUserIds(Map.of("STORE-A", 307L))
                .build();
        FulfillmentBalanceRecord missingPair = balance("CONFIRMED", "SUBMITTED");
        missingPair.sourceStoreCode = "STORE-B";
        when(mapper.listReadyBalances(Map.of("STORE-A", 307L), null, null))
                .thenReturn(List.of(missingPair));

        assertThat(service.listReadyItems(partialAccess, null, null, null)).isEmpty();
        verify(mapper).listReadyBalances(Map.of("STORE-A", 307L), null, null);
    }

    @Test
    void sameProductFromDifferentOwnersRemainsTwoSelectableItems() {
        FulfillmentBalanceRecord firstOwner = balance("CONFIRMED", "SUBMITTED");
        firstOwner.sourceStoreCode = "STORE-A";
        FulfillmentBalanceRecord secondOwner = balance("CONFIRMED", "SUBMITTED");
        secondOwner.id = 900002L;
        secondOwner.ownerUserId = 409L;
        secondOwner.sourceStoreCode = "STORE-B";
        when(mapper.listReadyBalances(storeOwners(), null, null))
                .thenReturn(List.of(firstOwner, secondOwner));

        var items = service.listReadyItems(multiOwnerAccess(), null, null, null);

        assertThat(items).hasSize(2);
        assertThat(items).extracting(item -> item.ownerUserId)
                .containsExactly(307L, 409L);
        assertThat(items).allSatisfy(item -> assertThat(item.sources).hasSize(1));
    }

    @Test
    void receiptOrderKeepsMultipleAuthorizedStoresFromTheSameOwner() {
        Map<String, Long> storeOwners = new LinkedHashMap<>();
        storeOwners.put("STORE-A", 307L);
        storeOwners.put("STORE-C", 307L);
        BusinessAccessContext access = BusinessAccessContext.builder()
                .sessionUserId(901L)
                .businessOwnerUserId(307L)
                .storeCodes(Set.copyOf(storeOwners.keySet()))
                .storeOwnerUserIds(storeOwners)
                .build();
        PurchaseReceiptRow row = new PurchaseReceiptRow();
        row.receiptSourceId = 500001L;
        row.receiptSourceStoreCode = "STORE-A,STORE-C";
        row.ownerUserId = 307L;
        row.orderId = 200001L;
        row.itemId = 210001L;
        row.transportMode = "AIR";
        row.fulfillmentType = "WAREHOUSE_RECEIPT";
        when(mapper.listReceiptRows(storeOwners, null)).thenReturn(List.of(row));

        assertThat(service.listReceiptOrders(access, null))
                .singleElement()
                .satisfies(order -> assertThat(order.items).hasSize(1));
    }

    @Test
    void secondOwnerInventoryTargetCanBeChanged() {
        FulfillmentBalanceRecord secondOwner = balance("CONFIRMED", "SUBMITTED");
        secondOwner.ownerUserId = 409L;
        secondOwner.sourceStoreCode = "STORE-B";
        when(mapper.selectAuthorizedBalancesForUpdate(List.of(900001L), storeOwners()))
                .thenReturn(List.of(secondOwner));
        when(mapper.updateBalanceDispatchTarget(900001L, 409L, "AE", "SEA", 901L)).thenReturn(1);
        UpdateDispatchTargetCommand command = new UpdateDispatchTargetCommand();
        command.targetSiteCode = "AE";
        command.targetTransportMode = "SEA";

        var source = service.updateReadyItemDispatchTarget(multiOwnerAccess(), "900001", command);

        assertThat(source.targetSiteCode).isEqualTo("AE");
        assertThat(source.targetTransportMode).isEqualTo("SEA");
        verify(mapper).updateBalanceDispatchTarget(900001L, 409L, "AE", "SEA", 901L);
    }

    private BusinessAccessContext multiOwnerAccess() {
        return BusinessAccessContext.builder()
                .sessionUserId(901L)
                .businessOwnerUserId(307L)
                .storeCodes(Set.copyOf(storeOwners().keySet()))
                .storeOwnerUserIds(storeOwners())
                .build();
    }

    private Map<String, Long> storeOwners() {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("STORE-A", 307L);
        result.put("STORE-B", 409L);
        return result;
    }
}
