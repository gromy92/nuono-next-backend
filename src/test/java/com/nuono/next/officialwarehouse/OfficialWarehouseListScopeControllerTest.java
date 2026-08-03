package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class OfficialWarehouseListScopeControllerTest {

    @Mock
    private ObjectProvider<LocalDbOfficialWarehouseService> serviceProvider;

    @Mock
    private LocalDbOfficialWarehouseService service;

    @Mock
    private BusinessAccessResolver accessResolver;

    private OfficialWarehouseController controller;

    @BeforeEach
    void setUp() {
        controller = new OfficialWarehouseController(serviceProvider, accessResolver);
        when(serviceProvider.getIfAvailable()).thenReturn(service);
    }

    @Test
    void asnListWithStoreRequiresStoreAccess() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext access = access();
        when(accessResolver.requireStoreAccess(request, BusinessCapability.OFFICIAL_WAREHOUSE, "STORE-A"))
                .thenReturn(access);
        when(service.listAsns(access, "STORE-A", "SA", null)).thenReturn(List.of());

        assertThat(controller.listAsns("STORE-A", "SA", null, request)).isEmpty();

        verify(accessResolver).requireStoreAccess(request, BusinessCapability.OFFICIAL_WAREHOUSE, "STORE-A");
    }

    @Test
    void appointmentListWithStoreRequiresStoreAccess() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext access = access();
        when(accessResolver.requireStoreAccess(request, BusinessCapability.OFFICIAL_WAREHOUSE, "STORE-A"))
                .thenReturn(access);
        when(service.listAppointments(access, "STORE-A", "SA", null, null)).thenReturn(List.of());

        assertThat(controller.appointments("STORE-A", "SA", null, null, request)).isEmpty();

        verify(accessResolver).requireStoreAccess(request, BusinessCapability.OFFICIAL_WAREHOUSE, "STORE-A");
    }

    private static BusinessAccessContext access() {
        return BusinessAccessContext.builder()
                .sessionUserId(900L)
                .businessOwnerUserId(307L)
                .storeCodes(Set.of("STORE-A"))
                .storeOwnerUserIds(Map.of("STORE-A", 307L))
                .build();
    }
}
