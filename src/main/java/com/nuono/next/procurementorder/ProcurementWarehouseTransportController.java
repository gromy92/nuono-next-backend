package com.nuono.next.procurementorder;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.ReassignShippingOrderLinesCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateShippingOrderLineEligibilityCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.ShippingOrderView;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/procurement/purchase-orders")
class ProcurementWarehouseTransportController {

    private final ObjectProvider<LocalDbProcurementPurchaseOrderService> serviceProvider;
    private final BusinessAccessResolver accessResolver;

    ProcurementWarehouseTransportController(
            ObjectProvider<LocalDbProcurementPurchaseOrderService> serviceProvider,
            BusinessAccessResolver accessResolver
    ) {
        this.serviceProvider = serviceProvider;
        this.accessResolver = accessResolver;
    }

    @PutMapping("/shipping-orders/{shippingOrderId}/lines/{lineId}/eligibility")
    ShippingOrderView updateShippingOrderLineEligibility(
            @PathVariable String shippingOrderId,
            @PathVariable String lineId,
            @RequestBody UpdateShippingOrderLineEligibilityCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().updateShippingOrderLineEligibility(
                    requireAccess(request, BusinessCapability.LOGISTICS_QUOTE),
                    shippingOrderId,
                    lineId,
                    command
            );
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/shipping-orders/{shippingOrderId}/lines/reassign")
    ShippingOrderView reassignShippingOrderLines(
            @PathVariable String shippingOrderId,
            @RequestBody ReassignShippingOrderLinesCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().reassignShippingOrderLines(
                    requireAccess(request, BusinessCapability.WAREHOUSE_DISPATCH),
                    shippingOrderId,
                    command
            );
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    private BusinessAccessContext requireAccess(
            HttpServletRequest request,
            BusinessCapability capability
    ) {
        return accessResolver.requireBusinessContext(request, capability);
    }

    private LocalDbProcurementPurchaseOrderService service() {
        LocalDbProcurementPurchaseOrderService service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "采购单服务未启用。");
        }
        return service;
    }

    private ResponseStatusException badRequest(IllegalArgumentException exception) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }
}
