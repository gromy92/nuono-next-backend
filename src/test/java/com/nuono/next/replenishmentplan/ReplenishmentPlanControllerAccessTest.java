package com.nuono.next.replenishmentplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ReplenishmentPlanControllerAccessTest {

    @Mock
    private ReplenishmentPlanService service;
    @Mock
    private BusinessAccessResolver businessAccessResolver;

    private ReplenishmentPlanController controller;

    @BeforeEach
    void setUp() {
        controller = new ReplenishmentPlanController(service, businessAccessResolver);
    }

    @Test
    void overviewUsesAuthorizedSalesDataStoreScope() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR108065-NSA"))
                .thenReturn(contextWithStoreOwner());
        when(service.getOverview(any())).thenReturn(new ReplenishmentPlanRecords.PlanOverviewView(
                "ready",
                "STR108065-NSA",
                "SA",
                ReplenishmentPlanConfig.CALCULATION_VERSION,
                ReplenishmentPlanConfig.defaultBasicV1(),
                LocalDate.of(2026, 7, 6),
                List.of()
        ));

        ReplenishmentPlanRecords.PlanOverviewView view = controller.overview("STR108065-NSA", "SA", request);

        assertEquals("ready", view.getState());
        ArgumentCaptor<ReplenishmentPlanRecords.PlanQuery> captor =
                ArgumentCaptor.forClass(ReplenishmentPlanRecords.PlanQuery.class);
        verify(service).getOverview(captor.capture());
        assertEquals(307L, captor.getValue().getOwnerUserId());
        assertEquals("STR108065-NSA", captor.getValue().getStoreCode());
        assertEquals("SA", captor.getValue().getSiteCode());
    }

    @Test
    void overviewFallsBackToBusinessOwnerWhenStoreOwnerIsNotMapped() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR108065-NSA"))
                .thenReturn(contextWithoutStoreOwner());
        when(service.getOverview(any())).thenReturn(new ReplenishmentPlanRecords.PlanOverviewView(
                "empty",
                "STR108065-NSA",
                "SA",
                ReplenishmentPlanConfig.CALCULATION_VERSION,
                ReplenishmentPlanConfig.defaultBasicV1(),
                LocalDate.of(2026, 7, 6),
                List.of()
        ));

        controller.overview("STR108065-NSA", "SA", request);

        ArgumentCaptor<ReplenishmentPlanRecords.PlanQuery> captor =
                ArgumentCaptor.forClass(ReplenishmentPlanRecords.PlanQuery.class);
        verify(service).getOverview(captor.capture());
        assertEquals(307L, captor.getValue().getOwnerUserId());
    }

    @Test
    void overviewRejectsUnauthorizedStoreBeforeCallingService() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR108065-NSA"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "系统管理员不能操作店铺业务。"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.overview("STR108065-NSA", "SA", request)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(service, never()).getOverview(any());
    }

    @Test
    void overviewRejectsBlankStoreBeforeAccessCheck() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.overview(" ", "SA", request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        verify(businessAccessResolver, never()).requireStoreAccess(any(), any(), any());
        verify(service, never()).getOverview(any());
    }

    @Test
    void overviewRejectsBlankSiteBeforeAccessCheck() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.overview("STR108065-NSA", " ", request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        verify(businessAccessResolver, never()).requireStoreAccess(any(), any(), any());
        verify(service, never()).getOverview(any());
    }

    private static BusinessAccessContext contextWithStoreOwner() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(999L)
                .accountType(BusinessAccountType.BOSS)
                .roleId(2L)
                .roleLevel(1)
                .roleName("老板")
                .storeCodes(Set.of("STR108065-NSA"))
                .storeOwnerUserIds(Map.of("STR108065-NSA", 307L))
                .menuPaths(Set.of("/data/sales-forecast"))
                .build();
    }

    private static BusinessAccessContext contextWithoutStoreOwner() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.BOSS)
                .roleId(2L)
                .roleLevel(1)
                .roleName("老板")
                .storeCodes(Set.of("STR108065-NSA"))
                .storeOwnerUserIds(Map.of())
                .menuPaths(Set.of("/data/sales-forecast"))
                .build();
    }
}
