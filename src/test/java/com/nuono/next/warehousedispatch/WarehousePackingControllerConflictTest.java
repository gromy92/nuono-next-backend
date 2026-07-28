package com.nuono.next.warehousedispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ReplacePackingBoxesCommand;
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
class WarehousePackingControllerConflictTest {

    @Mock
    private ObjectProvider<LocalDbWarehouseDispatchService> serviceProvider;

    @Mock
    private LocalDbWarehouseDispatchService service;

    @Mock
    private BusinessAccessResolver accessResolver;

    @Mock
    private HttpServletRequest request;

    private WarehousePackingController controller;
    private BusinessAccessContext access;

    @BeforeEach
    void setUp() {
        controller = new WarehousePackingController(serviceProvider, accessResolver);
        access = BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .build();
    }

    @Test
    void replacePackingBoxesMapsStateChangeToConflict() {
        ReplacePackingBoxesCommand command = new ReplacePackingBoxesCommand();
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.WAREHOUSE_DISPATCH))
                .thenReturn(access);
        when(service.replacePackingBoxes(access, "830001", command))
                .thenThrow(new WarehouseInventoryStateConflictException(
                        "装箱单状态已变化，请刷新后重试。"
                ));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.replacePackingBoxes("830001", command, request)
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("装箱单状态已变化，请刷新后重试。", error.getReason());
    }
}
