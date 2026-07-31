package com.nuono.next.warehousedispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateShippingBatchCommand;
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
class WarehouseShippingBatchControllerTest {

    @Mock
    private ObjectProvider<LocalDbWarehouseDispatchService> serviceProvider;

    @Mock
    private LocalDbWarehouseDispatchService service;

    @Mock
    private BusinessAccessResolver accessResolver;

    @Mock
    private HttpServletRequest request;

    private WarehouseShippingBatchController controller;
    private BusinessAccessContext access;

    @BeforeEach
    void setUp() {
        controller = new WarehouseShippingBatchController(serviceProvider, accessResolver);
        access = BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .build();
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.WAREHOUSE_DISPATCH))
                .thenReturn(access);
    }

    @Test
    void selectShippingOptionMapsStateConflictToHttp409() {
        when(service.selectShippingOption(access, "700001", "710001"))
                .thenThrow(new WarehouseInventoryStateConflictException("发货批次状态已变化，请刷新后重试。"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.selectShippingOption("700001", "710001", request)
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
    }

    @Test
    void createOutboundOrdersMapsSelectedOptionConflictToHttp409() {
        when(service.createOutboundOrders(access, "700001"))
                .thenThrow(new WarehouseInventoryStateConflictException("发货批次方案已变化，请刷新后重试。"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.createOutboundOrders("700001", request)
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
    }

    @Test
    void createShippingBatchMapsRequestConflictToHttp409() {
        CreateShippingBatchCommand command = new CreateShippingBatchCommand();
        when(service.createShippingBatch(access, command))
                .thenThrow(new WarehouseRequestConflictException("同一客户端请求号不能提交不同的发货批次商品。"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.createShippingBatch(command, request)
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("同一客户端请求号不能提交不同的发货批次商品。", error.getReason());
    }
}
