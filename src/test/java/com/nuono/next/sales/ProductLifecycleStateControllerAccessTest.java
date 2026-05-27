package com.nuono.next.sales;

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
class ProductLifecycleStateControllerAccessTest {

    @Mock
    private ProductLifecycleStateService lifecycleStateService;

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    private ProductLifecycleStateController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductLifecycleStateController(lifecycleStateService, businessAccessResolver);
    }

    @Test
    void overviewUsesAuthorizedOwnerAndStoreScope() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR245027-SAU"))
                .thenReturn(salesContext());
        when(lifecycleStateService.getOverview(any()))
                .thenReturn(new ProductLifecycleStateOverview(null, java.util.List.of(), java.util.List.of()));

        controller.getLifecycleOverview(
                "STR245027-SAU",
                "SA",
                "SAMPLE-SKU-001",
                "Z57C90A4184D0CFD75218Z-1",
                request
        );

        ArgumentCaptor<ProductLifecycleStateQuery> captor = ArgumentCaptor.forClass(ProductLifecycleStateQuery.class);
        verify(lifecycleStateService).getOverview(captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals("STR245027-SAU", captor.getValue().getStoreCode());
        assertEquals("SA", captor.getValue().getSiteCode());
        assertEquals("SAMPLE-SKU-001", captor.getValue().getPartnerSku());
        assertEquals("Z57C90A4184D0CFD75218Z-1", captor.getValue().getSku());
    }

    @Test
    void overviewDoesNotCallServiceWhenStoreAccessIsRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR245027-SAU"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不能操作该店铺。"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.getLifecycleOverview(
                        "STR245027-SAU",
                        "SA",
                        "SAMPLE-SKU-001",
                        "Z57C90A4184D0CFD75218Z-1",
                        request
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(lifecycleStateService, never()).getOverview(any());
    }

    @Test
    void blankProductIdentityIsRejectedBeforeServiceCall() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.getLifecycleOverview(
                        "STR245027-SAU",
                        "SA",
                        "",
                        "Z57C90A4184D0CFD75218Z-1",
                        request
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        verify(businessAccessResolver, never()).requireStoreAccess(any(), any(), any());
        verify(lifecycleStateService, never()).getOverview(any());
    }

    private BusinessAccessContext salesContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(10003L)
                .businessOwnerUserId(10002L)
                .accountType(BusinessAccountType.OPERATOR)
                .storeCodes(Set.of("STR245027-SAU"))
                .storeOwnerUserIds(Map.of("STR245027-SAU", 10002L))
                .menuPaths(Set.of("/data/sales-analysis"))
                .build();
    }
}
