package com.nuono.next.aiopsdashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class AiOperationsDashboardControllerAccessTest {

    @Mock
    private AiOperationsDashboardService service;

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    private AiOperationsDashboardController controller;

    @BeforeEach
    void setUp() {
        controller = new AiOperationsDashboardController(service, businessAccessResolver);
    }

    @Test
    void overviewWithStoreRequiresStoreAccessAndIgnoresForgedOwnerParameter() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("ownerUserId", "99999");
        when(businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.AI_OPERATIONS_DASHBOARD,
                "STR245027-NAE"
        )).thenReturn(businessContext());
        when(service.overview(any())).thenReturn(stubOverview());

        controller.overview("STR245027-NAE", "AE", "last30Days", request);

        ArgumentCaptor<AiOperationsDashboardQuery> captor =
                ArgumentCaptor.forClass(AiOperationsDashboardQuery.class);
        verify(service).overview(captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(555L, captor.getValue().getOperatorUserId());
        assertEquals("STR245027-NAE", captor.getValue().getStoreCode());
        assertEquals("AE", captor.getValue().getSiteCode());
        assertEquals("last30Days", captor.getValue().getDatePreset());
    }

    @Test
    void overviewWithoutStoreRequiresBusinessCapabilityOnly() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("ownerUserId", "99999");
        when(businessAccessResolver.requireBusinessContext(
                request,
                BusinessCapability.AI_OPERATIONS_DASHBOARD
        )).thenReturn(businessContext());
        when(service.overview(any())).thenReturn(stubOverview());

        controller.overview(null, null, null, request);

        ArgumentCaptor<AiOperationsDashboardQuery> captor =
                ArgumentCaptor.forClass(AiOperationsDashboardQuery.class);
        verify(service).overview(captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(555L, captor.getValue().getOperatorUserId());
        assertEquals("last7Days", captor.getValue().getDatePreset());
    }

    private BusinessAccessContext businessContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(555L)
                .businessOwnerUserId(10002L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleLevel(3)
                .roleName("运营")
                .storeCodes(Set.of("STR245027-NAE"))
                .storeOwnerUserIds(Map.of("STR245027-NAE", 10002L))
                .menuPaths(Set.of("/operations/dashboard"))
                .build();
    }

    private AiOperationsDashboardOverview stubOverview() {
        return new AiOperationsDashboardOverview(
                new AiOperationsDashboardScope(
                        10002L,
                        555L,
                        "STR245027-NAE",
                        "AE",
                        "last7Days",
                        LocalDate.of(2026, 5, 15),
                        LocalDate.of(2026, 5, 21)
                ),
                new AiOperationsDashboardOverview.Summary("AI运营看板", "empty", "empty"),
                java.util.List.of(),
                java.util.List.of(),
                new AiOperationsDashboardOverview.AiSummary("AI今日总结", "runtime_disabled", "runtime_disabled", null, null),
                new AiOperationsDashboardOverview.ItemCollection<>("runtime_disabled", "AI建议", java.util.List.of()),
                new AiOperationsDashboardOverview.ItemCollection<>("not_connected", "证据来源", java.util.List.of()),
                java.util.List.of("not_connected")
        );
    }
}
