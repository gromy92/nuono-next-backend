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
public class WarehousePackingController extends WarehouseDispatchEndpointSupport {

    public WarehousePackingController(
            ObjectProvider<LocalDbWarehouseDispatchService> serviceProvider,
            BusinessAccessResolver accessResolver
    ) {
        super(serviceProvider, accessResolver);
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

@PutMapping("/packing-lists/{packingListId}/boxes/{boxNo}")
    public PackingListView savePackingBox(
            @PathVariable String packingListId,
            @PathVariable String boxNo,
            @RequestBody PackingBoxCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().savePackingBox(access(request), packingListId, boxNo, command);
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
            return service().confirmPackingLists(
                    access(request),
                    command == null ? null : command.packingListIds
            );
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

@PostMapping("/packing-lists/{packingListId}/ship")
    public PackingListView shipPackingList(
            @PathVariable String packingListId,
            HttpServletRequest request
    ) {
        try {
            return service().shipPackingList(access(request), packingListId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }
}
