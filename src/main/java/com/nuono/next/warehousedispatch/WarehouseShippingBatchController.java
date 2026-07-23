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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
public class WarehouseShippingBatchController extends WarehouseDispatchEndpointSupport {

    public WarehouseShippingBatchController(
            ObjectProvider<LocalDbWarehouseDispatchService> serviceProvider,
            BusinessAccessResolver accessResolver
    ) {
        super(serviceProvider, accessResolver);
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

@GetMapping("/shipping-batches/{shippingBatchId}/packing-list-export")
    public ResponseEntity<byte[]> exportPackingList(
            @PathVariable String shippingBatchId,
            @RequestParam(required = false) String forwarderCode,
            @RequestParam(required = false) String routeCode,
            HttpServletRequest request
    ) {
        try {
            BusinessAccessContext access = access(request);
            ShippingBatchView batch = service().getShippingBatch(access, shippingBatchId);
            List<OutboundOrderView> orders = service().listOutboundOrders(access, shippingBatchId);
            Map<String, List<PackingListView>> packingListsByOrder = new LinkedHashMap<>();
            for (OutboundOrderView order : orders) {
                packingListsByOrder.put(order.id, service().listPackingLists(access, order.id));
            }
            WarehousePackingWorkbookExporter.ExportFile export =
                    WarehousePackingWorkbookExporter.export(
                            batch, orders, packingListsByOrder, forwarderCode, routeCode
                    );
            String encoded = URLEncoder.encode(export.filename, StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(WarehousePackingWorkbookExporter.CONTENT_TYPE))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                    .body(export.content);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }
}
