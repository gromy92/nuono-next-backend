package com.nuono.next.systemreports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import java.time.LocalDateTime;
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
class StoreDataReportControllerAccessTest {

    @Mock
    private StoreDataReportService service;

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    private StoreDataReportController controller;

    @BeforeEach
    void setUp() {
        controller = new StoreDataReportController(service, businessAccessResolver);
    }

    @Test
    void overviewWithoutStoreRequiresSystemReportsCapability() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = businessContext();
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.SYSTEM_REPORTS))
                .thenReturn(context);
        when(service.overview(any(), any()))
                .thenReturn(new StoreDataReportOverview("店铺数据", LocalDateTime.now(), java.util.List.of(), java.util.List.of()));

        controller.overview(null, request);

        ArgumentCaptor<BusinessAccessContext> captor = ArgumentCaptor.forClass(BusinessAccessContext.class);
        verify(service).overview(captor.capture(), isNull());
        assertEquals(10002L, captor.getValue().getBusinessOwnerUserId());
    }

    @Test
    void overviewWithStoreOnlyRequiresSystemReportsCapabilityAndIgnoresStoreScope() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("ownerUserId", "99999");
        BusinessAccessContext context = businessContext();
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.SYSTEM_REPORTS))
                .thenReturn(context);
        when(service.overview(any(), any()))
                .thenReturn(new StoreDataReportOverview("店铺数据", LocalDateTime.now(), java.util.List.of(), java.util.List.of()));

        controller.overview("STR-OUTSIDE-SESSION", request);

        verify(businessAccessResolver, never()).requireStoreAccess(any(), any(), any());
        verify(service).overview(context, "STR-OUTSIDE-SESSION");
    }

    private BusinessAccessContext businessContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(555L)
                .businessOwnerUserId(10002L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleLevel(3)
                .roleName("运营")
                .storeCodes(Set.of("STR245027-NAE", "STR245027-NSA"))
                .storeOwnerUserIds(Map.of("STR245027-NAE", 10002L, "STR245027-NSA", 10002L))
                .menuPaths(Set.of("/system-reports/store-data"))
                .build();
    }
}
