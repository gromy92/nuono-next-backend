package com.nuono.next.warehousedispatch;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ConfirmationCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateDispatchPlanCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreatePackingListCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateShippingBatchCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateShippingTargetOptionCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.HandoffFailureCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.PackingBoxCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ReplacePackingBoxesCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.UpdateFulfillmentCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.ConfirmationView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.DispatchPlanView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.FulfillmentItemView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.LogisticsHandoffView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.OutboundOrderView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PackingListView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PurchaseReceiptOrderView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PurchaseOrderLogisticsComparisonView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.ReadyItemView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.ShippingBatchView;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/warehouse/dispatch")
public class WarehouseDispatchController extends WarehouseDispatchEndpointSupport {

    public WarehouseDispatchController(
            ObjectProvider<LocalDbWarehouseDispatchService> serviceProvider,
            BusinessAccessResolver accessResolver
    ) {
        super(serviceProvider, accessResolver);
    }

@PutMapping("/purchase-orders/{purchaseOrderId}/items/{purchaseOrderItemId}/fulfillment")
    public FulfillmentItemView updateItemFulfillment(
            @PathVariable String purchaseOrderId,
            @PathVariable String purchaseOrderItemId,
            @RequestBody UpdateFulfillmentCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().updateItemFulfillment(access(request), purchaseOrderId, purchaseOrderItemId, command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

@PostMapping("/confirmations")
    public ConfirmationView createConfirmation(
            @RequestBody ConfirmationCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().createConfirmation(access(request), command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

@GetMapping("/receipt-orders")
    public List<PurchaseReceiptOrderView> receiptOrders(
            @RequestParam(required = false) String keyword,
            HttpServletRequest request
    ) {
        try {
            return service().listReceiptOrders(access(request), keyword);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

@GetMapping("/ready-items")
    public List<ReadyItemView> readyItems(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) String fulfillmentType,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request
    ) {
        try {
            return service().listReadyItems(access(request), siteCode, fulfillmentType, keyword);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

@GetMapping("/purchase-order-logistics-comparisons")
    public List<PurchaseOrderLogisticsComparisonView> purchaseOrderLogisticsComparisons(
            @RequestParam(required = false, defaultValue = "10") Integer limit,
            HttpServletRequest request
    ) {
        try {
            return service().listPurchaseOrderLogisticsComparisons(access(request), limit);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

@GetMapping("/dispatch-plans")
    public List<DispatchPlanView> dispatchPlans(HttpServletRequest request) {
        try {
            return service().listDispatchPlans(access(request));
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

@PostMapping("/dispatch-plans")
    public DispatchPlanView createDispatchPlan(
            @RequestBody CreateDispatchPlanCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().createDispatchPlan(access(request), command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

@PostMapping("/dispatch-plans/{dispatchPlanId}/ready-for-logistics")
    public DispatchPlanView readyForLogistics(
            @PathVariable String dispatchPlanId,
            HttpServletRequest request
    ) {
        try {
            return service().readyForLogistics(access(request), dispatchPlanId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

@PostMapping("/dispatch-plans/{dispatchPlanId}/reopen-draft")
    public DispatchPlanView reopenDraft(
            @PathVariable String dispatchPlanId,
            HttpServletRequest request
    ) {
        try {
            return service().reopenDraft(access(request), dispatchPlanId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

@GetMapping("/dispatch-plans/{dispatchPlanId}/logistics-handoff")
    public LogisticsHandoffView logisticsHandoff(
            @PathVariable String dispatchPlanId,
            HttpServletRequest request
    ) {
        try {
            return service().getLogisticsHandoff(access(request), dispatchPlanId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

@PostMapping("/handoffs/{handoffRequestNo}/success")
    public DispatchPlanView markHandoffSuccess(
            @PathVariable String handoffRequestNo,
            HttpServletRequest request
    ) {
        try {
            return service().markLogisticsHandoffSuccess(access(request), handoffRequestNo);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

@PostMapping("/handoffs/failure")
    public DispatchPlanView markHandoffFailure(
            @RequestBody HandoffFailureCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().markLogisticsHandoffFailure(access(request), command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }
}
