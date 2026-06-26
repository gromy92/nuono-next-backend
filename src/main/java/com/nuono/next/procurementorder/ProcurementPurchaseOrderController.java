package com.nuono.next.procurementorder;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.AddItemsCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.CreateOrderCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateItemCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateOrderCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateItemSourcingRequirementCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.ProductOptionView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderAli1688HistoryView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsPlanView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderView;
import com.nuono.next.productselection.Ali1688CollectionView;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/procurement/purchase-orders")
public class ProcurementPurchaseOrderController {

    private final ObjectProvider<LocalDbProcurementPurchaseOrderService> serviceProvider;
    private final BusinessAccessResolver accessResolver;

    public ProcurementPurchaseOrderController(
            ObjectProvider<LocalDbProcurementPurchaseOrderService> serviceProvider,
            BusinessAccessResolver accessResolver
    ) {
        this.serviceProvider = serviceProvider;
        this.accessResolver = accessResolver;
    }

    @GetMapping
    public List<PurchaseOrderView> listOrders(
            @RequestParam(required = false) String storeCode,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request
    ) {
        try {
            return service().listOrders(requireAccess(request, storeCode), storeCode, keyword);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/{orderId}")
    public PurchaseOrderView getOrder(
            @PathVariable String orderId,
            HttpServletRequest request
    ) {
        try {
            return service().getOrder(requireAccess(request, null), orderId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping
    public PurchaseOrderView createOrder(
            @RequestBody CreateOrderCommand command,
            HttpServletRequest request
    ) {
        try {
            String storeCode = command == null ? null : command.storeCode;
            return service().createOrder(requireAccess(request, storeCode), command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PutMapping("/{orderId}")
    public PurchaseOrderView updateOrder(
            @PathVariable String orderId,
            @RequestBody UpdateOrderCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().updateOrder(requireAccess(request, null), orderId, command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @DeleteMapping("/{orderId}")
    public PurchaseOrderView deleteOrder(
            @PathVariable String orderId,
            HttpServletRequest request
    ) {
        try {
            return service().deleteOrder(requireAccess(request, null), orderId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @DeleteMapping("/{orderId}/items/{itemId}")
    public PurchaseOrderView deleteItem(
            @PathVariable String orderId,
            @PathVariable String itemId,
            HttpServletRequest request
    ) {
        try {
            return service().deleteItem(requireAccess(request, null), orderId, itemId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/{orderId}/items")
    public PurchaseOrderView addItems(
            @PathVariable String orderId,
            @RequestBody AddItemsCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().addItems(requireAccess(request, null), orderId, command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PutMapping("/{orderId}/items/{itemId}")
    public PurchaseOrderView updateItem(
            @PathVariable String orderId,
            @PathVariable String itemId,
            @RequestBody UpdateItemCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().updateItem(requireAccess(request, null), orderId, itemId, command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/{orderId}/collect")
    public PurchaseOrderView collectOrder(
            @PathVariable String orderId,
            HttpServletRequest request
    ) {
        try {
            return service().collectOrder(requireAccess(request, null), orderId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/{orderId}/logistics-plan")
    public PurchaseOrderLogisticsPlanView generateLogisticsPlan(
            @PathVariable String orderId,
            HttpServletRequest request
    ) {
        try {
            return service().generateLogisticsPlan(requireAccess(request, null), orderId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/{orderId}/logistics-plan/preview")
    public PurchaseOrderLogisticsPlanView previewLogisticsPlan(
            @PathVariable String orderId,
            HttpServletRequest request
    ) {
        try {
            return service().previewLogisticsPlan(requireAccess(request, null), orderId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/{orderId}/items/{itemId}/collect")
    public PurchaseOrderView collectItem(
            @PathVariable String orderId,
            @PathVariable String itemId,
            HttpServletRequest request
    ) {
        try {
            return service().collectItem(requireAccess(request, null), orderId, itemId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PutMapping("/{orderId}/items/{itemId}/sourcing-requirement")
    public PurchaseOrderView updateItemSourcingRequirement(
            @PathVariable String orderId,
            @PathVariable String itemId,
            @RequestBody(required = false) UpdateItemSourcingRequirementCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().updateItemSourcingRequirement(requireAccess(request, null), orderId, itemId, command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/product-options")
    public List<ProductOptionView> productOptions(
            @RequestParam String storeCode,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request
    ) {
        try {
            return service().listProductOptions(requireAccess(request, storeCode), storeCode, keyword);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/items/{itemId}/ali1688")
    public Ali1688CollectionView itemAli1688(
            @PathVariable String itemId,
            HttpServletRequest request
    ) {
        try {
            return service().getItemAli1688(requireAccess(request, null), itemId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/{orderId}/ali1688-history")
    public PurchaseOrderAli1688HistoryView orderAli1688History(
            @PathVariable String orderId,
            HttpServletRequest request
    ) {
        try {
            return service().getOrderAli1688History(requireAccess(request, null), orderId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    private BusinessAccessContext requireAccess(HttpServletRequest request, String storeCode) {
        if (StringUtils.hasText(storeCode)) {
            return accessResolver.requireStoreAccess(request, BusinessCapability.PROCUREMENT, storeCode);
        }
        return accessResolver.requireBusinessContext(request, BusinessCapability.PROCUREMENT);
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
