package com.nuono.next.warehousedispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class WarehouseDispatchControllerTest {

    @Mock
    private ObjectProvider<LocalDbWarehouseDispatchService> serviceProvider;

    @Mock
    private LocalDbWarehouseDispatchService service;

    @Mock
    private BusinessAccessResolver accessResolver;

    @Mock
    private HttpServletRequest request;

    private WarehouseDispatchController controller;

    @BeforeEach
    void setUp() {
        controller = new WarehouseDispatchController(serviceProvider, accessResolver);
    }

    @Test
    void markHandoffSuccessMapsInventoryStateChangeToConflict() {
        BusinessAccessContext access = BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .build();
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.WAREHOUSE_DISPATCH))
                .thenReturn(access);
        when(service.markLogisticsHandoffSuccess(access, "HANDOFF-340001"))
                .thenThrow(new IllegalStateException("物流交接库存状态已变化，请刷新后重试。"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.markHandoffSuccess("HANDOFF-340001", request)
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("物流交接库存状态已变化，请刷新后重试。", error.getReason());
    }
}
