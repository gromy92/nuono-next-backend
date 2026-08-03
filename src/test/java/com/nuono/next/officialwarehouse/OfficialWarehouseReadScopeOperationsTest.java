package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseStatisticsMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.InboundStatisticsView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.StockStatisticsQuery;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.StockStatisticsView;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfficialWarehouseReadScopeOperationsTest {

    @Mock
    private OfficialWarehouseMapper warehouseMapper;

    @Mock
    private OfficialWarehouseStatisticsMapper statisticsMapper;

    private LocalDbOfficialWarehouseService warehouseService;
    private LocalDbOfficialWarehouseStatisticsService statisticsService;

    @BeforeEach
    void setUp() {
        warehouseService = new LocalDbOfficialWarehouseService(
                warehouseMapper, null, null, null, null, new ObjectMapper(), null, null, null
        );
        statisticsService = new LocalDbOfficialWarehouseStatisticsService(statisticsMapper);
    }

    @Test
    void legacyStoreSetFallsBackToCanonicalOwner() {
        BusinessAccessContext access = access(307L, Set.of("STORE-A"), Map.of());

        OfficialWarehouseBusinessScope scope =
                OfficialWarehouseBusinessScope.resolve(access, "STORE-A");

        assertThat(scope.ownerUserId()).isEqualTo(307L);
        assertThat(scope.storeOwnerUserIds()).containsExactlyEntriesOf(Map.of("STORE-A", 307L));
    }

    @Test
    void explicitStoreWithoutExactMappingFailsClosedOnceMappingsExist() {
        BusinessAccessContext access = access(
                307L,
                Set.of("STORE-A", "STORE-B"),
                Map.of("STORE-B", 808L)
        );

        assertThatThrownBy(() -> OfficialWarehouseBusinessScope.resolve(access, "STORE-A"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能访问该店铺");
    }

    @Test
    void emptyAuthorizedStoreScopeReturnsEmptyWithoutOwnerWideQueries() {
        BusinessAccessContext access = access(307L, Set.of(), Map.of());

        assertThat(warehouseService.listAsns(access, null, null, null)).isEmpty();
        assertThat(warehouseService.listAppointments(access, null, null, null, null)).isEmpty();
        StockStatisticsView stock = statisticsService.stockStatistics(access, new StockStatisticsQuery());
        InboundStatisticsView inbound = statisticsService.inboundStatistics(
                access, null, null, null, null, null, null
        );

        assertThat(stock.rows).isEmpty();
        assertThat(inbound.rows).isEmpty();
        verifyNoInteractions(warehouseMapper, statisticsMapper);
    }

    private static BusinessAccessContext access(
            Long canonicalOwnerUserId,
            Set<String> storeCodes,
            Map<String, Long> storeOwnerUserIds
    ) {
        return BusinessAccessContext.builder()
                .sessionUserId(900L)
                .businessOwnerUserId(canonicalOwnerUserId)
                .storeCodes(storeCodes)
                .storeOwnerUserIds(storeOwnerUserIds)
                .build();
    }
}
