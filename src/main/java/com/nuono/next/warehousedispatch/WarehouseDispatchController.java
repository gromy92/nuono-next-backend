package com.nuono.next.warehousedispatch;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ConfirmationCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ConfirmPackingListsCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateDispatchPlanCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreatePackingListCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateShippingBatchCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateShippingBatchFromDispatchPlanCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateShippingTargetOptionCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.HandoffFailureCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.IssueShippingBatchCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ReplacePackingBoxesCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.UpdateDispatchTargetCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.UpdateFulfillmentCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.ConfirmationView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.DispatchPlanView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.FulfillmentItemView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.LogisticsHandoffView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.IssuedShippingBatchView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.OutboundOrderView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PackingListView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PurchaseReceiptOrderView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PurchaseOrderLogisticsComparisonView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.ReadyItemView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.ReadySourceView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.ShippingRouteOptionView;
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
public class WarehouseDispatchController {

    private final ObjectProvider<LocalDbWarehouseDispatchService> serviceProvider;
    private final BusinessAccessResolver accessResolver;

    public WarehouseDispatchController(
            ObjectProvider<LocalDbWarehouseDispatchService> serviceProvider,
            BusinessAccessResolver accessResolver
    ) {
        this.serviceProvider = serviceProvider;
        this.accessResolver = accessResolver;
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

    @PutMapping("/ready-items/{fulfillmentBalanceId}/dispatch-target")
    public ReadySourceView updateReadyItemDispatchTarget(
            @PathVariable String fulfillmentBalanceId,
            @RequestBody UpdateDispatchTargetCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().updateDispatchTarget(access(request), fulfillmentBalanceId, command);
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

    @GetMapping("/shipping-batches")
    public List<ShippingBatchView> shippingBatches(HttpServletRequest request) {
        try {
            return service().listShippingBatches(access(request));
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/shipping-batches")
    public ShippingBatchView createShippingBatch(
            @RequestBody CreateShippingBatchCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().createShippingBatch(access(request), command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/dispatch-plans/{dispatchPlanId}/shipping-batch")
    public ShippingBatchView createShippingBatchFromDispatchPlan(
            @PathVariable String dispatchPlanId,
            @RequestBody(required = false) CreateShippingBatchFromDispatchPlanCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().createShippingBatchFromDispatchPlan(access(request), dispatchPlanId, command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/dispatch-plans/{dispatchPlanId}/shipping-route-options")
    public List<ShippingRouteOptionView> dispatchPlanShippingRouteOptions(
            @PathVariable String dispatchPlanId,
            HttpServletRequest request
    ) {
        try {
            return service().listDispatchPlanShippingRouteOptions(access(request), dispatchPlanId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/shipping-batches/{shippingBatchId}")
    public ShippingBatchView shippingBatch(
            @PathVariable String shippingBatchId,
            HttpServletRequest request
    ) {
        try {
            return service().getShippingBatch(access(request), shippingBatchId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/shipping-batches/{shippingBatchId}/options")
    public WarehouseDispatchViews.ShippingSuggestionOptionView createShippingTargetOption(
            @PathVariable String shippingBatchId,
            @RequestBody CreateShippingTargetOptionCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().createShippingTargetOption(access(request), shippingBatchId, command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/shipping-batches/{shippingBatchId}/options/{optionId}/select")
    public ShippingBatchView selectShippingOption(
            @PathVariable String shippingBatchId,
            @PathVariable String optionId,
            HttpServletRequest request
    ) {
        try {
            return service().selectShippingOption(access(request), shippingBatchId, optionId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/shipping-batches/{shippingBatchId}/outbound-orders")
    public List<OutboundOrderView> createOutboundOrders(
            @PathVariable String shippingBatchId,
            HttpServletRequest request
    ) {
        try {
            return service().createOutboundOrders(access(request), shippingBatchId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/shipping-batches/{shippingBatchId}/issue")
    public IssuedShippingBatchView issueShippingBatch(
            @PathVariable String shippingBatchId,
            @RequestBody IssueShippingBatchCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().issueShippingBatch(access(request), shippingBatchId, command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/shipping-batches/{shippingBatchId}/outbound-orders")
    public List<OutboundOrderView> outboundOrders(
            @PathVariable String shippingBatchId,
            HttpServletRequest request
    ) {
        try {
            return service().listOutboundOrders(access(request), shippingBatchId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/outbound-orders/{outboundOrderId}/packing-lists")
    public PackingListView createPackingList(
            @PathVariable String outboundOrderId,
            @RequestBody(required = false) CreatePackingListCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().createPackingList(access(request), outboundOrderId, command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/outbound-orders/{outboundOrderId}/packing-lists")
    public List<PackingListView> packingLists(
            @PathVariable String outboundOrderId,
            HttpServletRequest request
    ) {
        try {
            return service().listPackingLists(access(request), outboundOrderId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PutMapping("/packing-lists/{packingListId}/boxes")
    public PackingListView replacePackingBoxes(
            @PathVariable String packingListId,
            @RequestBody ReplacePackingBoxesCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().replacePackingBoxes(access(request), packingListId, command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/packing-lists/{packingListId}/confirm")
    public PackingListView confirmPackingList(
            @PathVariable String packingListId,
            HttpServletRequest request
    ) {
        try {
            return service().confirmPackingList(access(request), packingListId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/packing-lists/confirm-batch")
    public List<PackingListView> confirmPackingLists(
            @RequestBody ConfirmPackingListsCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().confirmPackingLists(access(request), command);
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

    private BusinessAccessContext access(HttpServletRequest request) {
        return accessResolver.requireBusinessContext(request, BusinessCapability.WAREHOUSE_DISPATCH);
    }

    private LocalDbWarehouseDispatchService service() {
        LocalDbWarehouseDispatchService service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "仓库发运服务未启用。");
        }
        return service;
    }

    private ResponseStatusException badRequest(IllegalArgumentException exception) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }
}
