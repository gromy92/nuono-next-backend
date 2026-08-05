package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseStatisticsMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider.InventoryItem;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider.InventoryPage;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.InventorySyncCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySyncScopeRecord;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OfficialWarehouseInventoryPaginationTest {

    @Test
    void inventorySyncTraversesThroughTheProvenLastPage() {
        ObjectMapper objectMapper = new ObjectMapper();
        OfficialWarehouseStatisticsMapper mapper = mock(OfficialWarehouseStatisticsMapper.class);
        OfficialWarehouseFbnInventoryProvider provider =
                mock(OfficialWarehouseFbnInventoryProvider.class);
        OfficialWarehouseInventoryReplacement replacement =
                mock(OfficialWarehouseInventoryReplacement.class);
        InventorySyncScopeRecord scope = new InventorySyncScopeRecord();
        scope.logicalStoreId = 7001L;
        scope.projectCode = "PRJ108065";
        scope.partnerId = "108065";
        when(mapper.selectInventorySyncScope(307L, "STR108065-NSA", "SA"))
                .thenReturn(scope);
        when(provider.fetchPage(any(), eq(1))).thenReturn(page(
                objectMapper,
                1,
                true,
                2,
                "FIRST"
        ));
        when(provider.fetchPage(any(), eq(2))).thenReturn(page(
                objectMapper,
                2,
                false,
                2,
                "SECOND"
        ));
        when(replacement.replace(any())).thenReturn(
                new OfficialWarehouseInventoryReplacementResult(
                        8001L,
                        "STR108065-NSA",
                        "SA",
                        2,
                        2,
                        2,
                        "2026-08-02 23:01:00"
                )
        );
        OfficialWarehouseInventorySyncService service =
                new OfficialWarehouseInventorySyncService(
                        mapper,
                        provider,
                        objectMapper,
                        replacement
                );
        InventorySyncCommand command = new InventorySyncCommand();
        command.storeCode = "STR108065-NSA";
        command.siteCode = "SA";

        OfficialWarehouseStatisticsViews.InventorySyncResultView result = service.sync(
                access(),
                command
        );

        verify(provider).fetchPage(any(), eq(1));
        verify(provider).fetchPage(any(), eq(2));
        ArgumentCaptor<OfficialWarehouseInventoryReplacementCommand> commandCaptor =
                ArgumentCaptor.forClass(OfficialWarehouseInventoryReplacementCommand.class);
        verify(replacement).replace(commandCaptor.capture());
        assertThat(commandCaptor.getValue().totalPages).isEqualTo(2);
        assertThat(commandCaptor.getValue().items).hasSize(2);
        assertThat(result.pageCount).isEqualTo(2);
        assertThat(result.insertedRows).isEqualTo(2);
    }

    private InventoryPage page(
            ObjectMapper objectMapper,
            int page,
            boolean hasNext,
            int totalPages,
            String partnerSku
    ) {
        InventoryItem item = InventoryItem.from(objectMapper.createObjectNode()
                .put("warehouse_code", "RUH01")
                .put("qty", page)
                .put("inventory_type", "saleable")
                .put("partner_sku", partnerSku));
        return new InventoryPage(
                page,
                hasNext,
                totalPages,
                false,
                List.of(item),
                objectMapper.createObjectNode()
        );
    }

    private BusinessAccessContext access() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .storeCodes(Set.of("STR108065-NSA"))
                .storeOwnerUserIds(Map.of("STR108065-NSA", 307L))
                .menuPaths(Set.of("/warehouse/official-warehouse-stock"))
                .build();
    }
}
