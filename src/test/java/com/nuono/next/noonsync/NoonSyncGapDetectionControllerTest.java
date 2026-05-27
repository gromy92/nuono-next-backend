package com.nuono.next.noonsync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class NoonSyncGapDetectionControllerTest {

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    private NoonSyncGapDetectionController controller;

    @BeforeEach
    void setUp() {
        controller = new NoonSyncGapDetectionController(
                new NoonSyncGapDetectionService(new NoonSyncFoundationService()),
                businessAccessResolver
        );
    }

    @Test
    void previewDerivesOwnerScopeFromBackendSessionAndIgnoresForgedOwnerField() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR108065-NSA"))
                .thenReturn(bossContext());

        NoonSyncGapDetectionPreviewRequest body = new NoonSyncGapDetectionPreviewRequest();
        body.setOwnerUserId(99999L);
        body.setLogicalStoreId(108065L);
        body.setAccountOrigin(NoonSyncAccountOrigin.LEGACY_IMPORTED);
        body.setNoonBindingReady(true);
        body.setProviderConfigured(true);
        body.setProductWorkspaceState(NoonProductWorkspaceState.MISSING);
        body.setSalesCoverageState(NoonSalesCoverageState.MISSING);
        body.setSalesBackfillFrom(LocalDate.of(2025, 11, 1));
        body.setSalesBackfillTo(LocalDate.of(2026, 5, 19));

        NoonSyncReadinessView readiness = controller.preview("STR108065-NSA", "SA", body, request);

        assertEquals(307L, readiness.getScope().getOwnerUserId());
        assertEquals("STR108065-NSA", readiness.getScope().getStoreCode());
        assertEquals("SA", readiness.getScope().getSiteCode());
        verify(businessAccessResolver).requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR108065-NSA");
    }

    private BusinessAccessContext bossContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.BOSS)
                .roleId(2L)
                .roleLevel(1)
                .roleName("老板")
                .storeCodes(Set.of("STR108065-NSA"))
                .storeOwnerUserIds(Map.of("STR108065-NSA", 307L))
                .menuPaths(Set.of("/data/sales-analysis"))
                .build();
    }
}
