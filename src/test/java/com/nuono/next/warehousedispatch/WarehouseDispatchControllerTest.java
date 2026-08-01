package com.nuono.next.warehousedispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ConfirmationCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateDispatchPlanCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.HandoffFailureCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.UpdateFulfillmentCommand;
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
    void legacyHandoffSuccessReturnsGoneWithoutCallingService() {
        BusinessAccessContext access = BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .build();
        when(accessResolver.requireBusinessContext(request, BusinessCapability.WAREHOUSE_DISPATCH))
                .thenReturn(access);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.markHandoffSuccess("HANDOFF-340001", request)
        );

        assertEquals(HttpStatus.GONE, error.getStatus());
        assertEquals("该物流交接成功接口已停用，请在装箱单中确认已交货代。", error.getReason());
        verifyNoInteractions(serviceProvider, service);
    }

    @Test
    void createConfirmationMapsInventoryStateChangeToConflict() {
        BusinessAccessContext access = BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .build();
        ConfirmationCommand command = new ConfirmationCommand();
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.WAREHOUSE_DISPATCH))
                .thenReturn(access);
        when(service.createConfirmation(access, command))
                .thenThrow(new WarehouseInventoryStateConflictException("收货库存状态已变化，请刷新后重试。"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.createConfirmation(command, request)
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("收货库存状态已变化，请刷新后重试。", error.getReason());
    }

    @Test
    void createCommandsMapChangedIdempotencyPayloadToConflict() {
        BusinessAccessContext access = BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .build();
        ConfirmationCommand confirmation = new ConfirmationCommand();
        CreateDispatchPlanCommand dispatchPlan = new CreateDispatchPlanCommand();
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.WAREHOUSE_DISPATCH))
                .thenReturn(access);
        when(service.createConfirmation(access, confirmation))
                .thenThrow(new WarehouseRequestConflictException("同一客户端请求号不能提交不同的收货数据。"));
        when(service.createDispatchPlan(access, dispatchPlan))
                .thenThrow(new WarehouseRequestConflictException("同一客户端请求号不能提交不同的发货商品。"));

        ResponseStatusException receiptError = assertThrows(
                ResponseStatusException.class,
                () -> controller.createConfirmation(confirmation, request)
        );
        ResponseStatusException dispatchError = assertThrows(
                ResponseStatusException.class,
                () -> controller.createDispatchPlan(dispatchPlan, request)
        );

        assertEquals(HttpStatus.CONFLICT, receiptError.getStatus());
        assertEquals(HttpStatus.CONFLICT, dispatchError.getStatus());
    }

    @Test
    void purchaseOrderScopeDenialsMapToForbidden() {
        BusinessAccessContext access = BusinessAccessContext.builder()
                .sessionUserId(401L)
                .businessOwnerUserId(307L)
                .build();
        ConfirmationCommand confirmation = new ConfirmationCommand();
        UpdateFulfillmentCommand fulfillment = new UpdateFulfillmentCommand();
        BusinessAccessDeniedException denied =
                new BusinessAccessDeniedException("当前账号不能操作该采购单。");
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.WAREHOUSE_DISPATCH))
                .thenReturn(access);
        when(service.updateItemFulfillment(access, "200001", "210001", fulfillment))
                .thenThrow(denied);
        when(service.createConfirmation(access, confirmation)).thenThrow(denied);

        ResponseStatusException fulfillmentError = assertThrows(
                ResponseStatusException.class,
                () -> controller.updateItemFulfillment("200001", "210001", fulfillment, request)
        );
        ResponseStatusException confirmationError = assertThrows(
                ResponseStatusException.class,
                () -> controller.createConfirmation(confirmation, request)
        );

        assertEquals(HttpStatus.FORBIDDEN, fulfillmentError.getStatus());
        assertEquals(HttpStatus.FORBIDDEN, confirmationError.getStatus());
    }

    @Test
    void fulfillmentStateConflictMapsToConflict() {
        BusinessAccessContext access = BusinessAccessContext.builder()
                .sessionUserId(401L)
                .businessOwnerUserId(307L)
                .build();
        UpdateFulfillmentCommand fulfillment = new UpdateFulfillmentCommand();
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.WAREHOUSE_DISPATCH))
                .thenReturn(access);
        when(service.updateItemFulfillment(access, "200001", "210001", fulfillment))
                .thenThrow(new WarehouseInventoryStateConflictException("采购单商品状态已变化。"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.updateItemFulfillment("200001", "210001", fulfillment, request)
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
    }

    @Test
    void dispatchPlanStateTransitionsMapConflicts() {
        BusinessAccessContext access = BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .build();
        HandoffFailureCommand command = new HandoffFailureCommand();
        command.handoffRequestNo = "HANDOFF-340001";
        WarehouseInventoryStateConflictException conflict =
                new WarehouseInventoryStateConflictException("发运计划状态已变化，请刷新后重试。");
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.WAREHOUSE_DISPATCH))
                .thenReturn(access);
        when(service.readyForLogistics(access, "340001")).thenThrow(conflict);
        when(service.reopenDraft(access, "340001")).thenThrow(conflict);
        when(service.markLogisticsHandoffFailure(access, command)).thenThrow(conflict);

        assertConflict(() -> controller.readyForLogistics("340001", request));
        assertConflict(() -> controller.reopenDraft("340001", request));
        assertConflict(() -> controller.markHandoffFailure(command, request));
    }

    @Test
    void createConfirmationDoesNotMaskInternalIllegalStateAsInventoryConflict() {
        BusinessAccessContext access = BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .build();
        ConfirmationCommand command = new ConfirmationCommand();
        IllegalStateException internalError = new IllegalStateException("内部序列化失败");
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.WAREHOUSE_DISPATCH))
                .thenReturn(access);
        when(service.createConfirmation(access, command)).thenThrow(internalError);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> controller.createConfirmation(command, request)
        );

        assertSame(internalError, error);
    }

    private void assertConflict(org.junit.jupiter.api.function.Executable operation) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, operation);
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("发运计划状态已变化，请刷新后重试。", error.getReason());
    }
}
