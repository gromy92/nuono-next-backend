package com.nuono.next.officialwarehouse;

import static org.mockito.Mockito.verify;

import com.nuono.next.infrastructure.mapper.OfficialWarehouseStatisticsMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfficialWarehouseProductInboundScopeOperationsTest {

    @Mock
    private OfficialWarehouseStatisticsMapper mapper;

    private LocalDbOfficialWarehouseStatisticsService service;

    @BeforeEach
    void setUp() {
        service = new LocalDbOfficialWarehouseStatisticsService(mapper);
    }

    @Test
    void legacyExplicitStoreUsesCanonicalOwner() {
        BusinessAccessContext access = access(307L, Set.of("STORE-A"), Map.of());

        service.productInboundHistory(access, "STORE-A", "SA", "PSKU_1");

        verify(mapper).listProductInboundReceiptHistory(
                307L, List.of("STORE-A"), "STORE-A", "SA", null, "PSKU_1", 200
        );
        verify(mapper).listProductStockSourceCandidates(307L, "STORE-A", "SA", null, "PSKU_1", 50);
    }

    @Test
    void explicitStoreUsesItsMappedOwnerInsteadOfTheCanonicalOwner() {
        BusinessAccessContext access = access(307L, Set.of("STORE-A"), Map.of("STORE-A", 408L));

        service.productInboundHistory(access, "STORE-A", "SA", "PSKU_1");

        verify(mapper).listProductInboundReceiptHistory(
                408L, List.of("STORE-A"), "STORE-A", "SA", null, "PSKU_1", 200
        );
        verify(mapper).listProductStockSourceCandidates(408L, "STORE-A", "SA", null, "PSKU_1", 50);
    }

    @Test
    void sameOwnerMultiStoreRequestPassesOnlyTheRequestedStore() {
        BusinessAccessContext access = access(
                307L,
                Set.of("STORE-A", "STORE-B"),
                Map.of("STORE-A", 307L, "STORE-B", 307L)
        );

        service.productInboundHistory(access, "STORE-A", "SA", "PSKU_1");

        verify(mapper).listProductInboundReceiptHistory(
                307L, List.of("STORE-A"), "STORE-A", "SA", null, "PSKU_1", 200
        );
        verify(mapper).listProductStockSourceCandidates(307L, "STORE-A", "SA", null, "PSKU_1", 50);
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
