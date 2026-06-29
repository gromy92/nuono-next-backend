package com.nuono.next.procurementorder;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.AddItemsCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.CreateOrderCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.CreateShippingOrderCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.ShippingOrderSegmentScopeCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateItemCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateOrderCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateItemSourcingRequirementCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateShippingOrderLineYiteMaterialCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateShippingOrderCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.ProductOptionView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderAli1688HistoryView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsQuoteImportView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsQuoteOptionsView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsQuoteReportExportView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsPlanView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderShippingSubmitView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.LogisticsBillView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.ShippingOrderSubmitView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.ShippingOrderView;
import com.nuono.next.productselection.Ali1688CollectionView;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;
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
            @RequestParam(required = false) Boolean submittedOnly,
            @RequestParam(required = false) Boolean shippingAvailableOnly,
            HttpServletRequest request
    ) {
        try {
            return service().listOrders(
                    requireAccess(request, storeCode),
                    storeCode,
                    keyword,
                    Boolean.TRUE.equals(submittedOnly),
                    Boolean.TRUE.equals(shippingAvailableOnly)
            );
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/shipping-orders")
    public List<ShippingOrderView> listShippingOrders(
            @RequestParam(required = false) String keyword,
            HttpServletRequest request
    ) {
        try {
            return service().listShippingOrders(requireAccess(request, null), keyword);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/logistics-bills")
    public List<LogisticsBillView> listLogisticsBills(
            @RequestParam(required = false) String keyword,
            HttpServletRequest request
    ) {
        try {
            return service().listLogisticsBills(requireAccess(request, null), keyword);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/logistics-bills/{expectedBillId}")
    public LogisticsBillView getLogisticsBill(
            @PathVariable String expectedBillId,
            HttpServletRequest request
    ) {
        try {
            return service().getLogisticsBill(requireAccess(request, null), expectedBillId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/shipping-orders")
    public ShippingOrderView createShippingOrder(
            @RequestBody CreateShippingOrderCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().createShippingOrder(requireAccess(request, null), command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/shipping-orders/{shippingOrderId}/expected-bill")
    public LogisticsBillView generateShippingOrderExpectedBill(
            @PathVariable String shippingOrderId,
            @RequestBody(required = false) ShippingOrderSegmentScopeCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().generateShippingOrderExpectedBill(requireAccess(request, null), shippingOrderId, command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/shipping-orders/{shippingOrderId}")
    public ShippingOrderView getShippingOrder(
            @PathVariable String shippingOrderId,
            HttpServletRequest request
    ) {
        try {
            return service().getShippingOrder(requireAccess(request, null), shippingOrderId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PutMapping("/shipping-orders/{shippingOrderId}")
    public ShippingOrderView updateShippingOrder(
            @PathVariable String shippingOrderId,
            @RequestBody UpdateShippingOrderCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().updateShippingOrder(requireAccess(request, null), shippingOrderId, command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PutMapping("/shipping-orders/{shippingOrderId}/lines/{lineId}/yite-material")
    public ShippingOrderView updateShippingOrderLineYiteMaterial(
            @PathVariable String shippingOrderId,
            @PathVariable String lineId,
            @RequestBody UpdateShippingOrderLineYiteMaterialCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().updateShippingOrderLineYiteMaterial(requireAccess(request, null), shippingOrderId, lineId, command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/shipping-orders/{shippingOrderId}/logistics-quote-options")
    public PurchaseOrderLogisticsQuoteOptionsView shippingOrderLogisticsQuoteOptions(
            @PathVariable String shippingOrderId,
            @RequestParam(required = false) List<String> segmentIds,
            HttpServletRequest request
    ) {
        try {
            ShippingOrderSegmentScopeCommand command = new ShippingOrderSegmentScopeCommand();
            command.segmentIds = segmentIds == null ? List.of() : segmentIds;
            return service().listShippingOrderLogisticsQuoteOptions(requireAccess(request, null), shippingOrderId, command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/shipping-orders/{shippingOrderId}/logistics-quote-report")
    public ResponseEntity<byte[]> exportShippingOrderLogisticsQuoteReport(
            @PathVariable String shippingOrderId,
            @RequestParam(required = false) String forwarderCode,
            @RequestParam(required = false) String routeCode,
            @RequestParam(required = false) List<String> segmentIds,
            HttpServletRequest request
    ) {
        try {
            ShippingOrderSegmentScopeCommand command = new ShippingOrderSegmentScopeCommand();
            command.segmentIds = segmentIds == null ? List.of() : segmentIds;
            PurchaseOrderLogisticsQuoteReportExportView export =
                    service().exportShippingOrderLogisticsQuoteReport(
                            requireAccess(request, null),
                            shippingOrderId,
                            forwarderCode,
                            routeCode,
                            command
                    );
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(export.contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(export.filename))
                    .body(export.content);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/shipping-orders/{shippingOrderId}/logistics-quote-report/import")
    public PurchaseOrderLogisticsQuoteImportView importShippingOrderLogisticsQuoteReport(
            @PathVariable String shippingOrderId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) List<String> segmentIds,
            HttpServletRequest request
    ) {
        try {
            ShippingOrderSegmentScopeCommand command = new ShippingOrderSegmentScopeCommand();
            command.segmentIds = segmentIds == null ? List.of() : segmentIds;
            return service().importShippingOrderLogisticsQuoteReport(
                    requireAccess(request, null),
                    shippingOrderId,
                    file == null ? null : file.getInputStream(),
                    file == null ? null : file.getOriginalFilename(),
                    command
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "报价回传表读取失败。", exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/shipping-orders/{shippingOrderId}/submit-shipping")
    public ShippingOrderSubmitView submitShippingOrder(
            @PathVariable String shippingOrderId,
            @RequestBody(required = false) ShippingOrderSegmentScopeCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().submitShippingOrder(requireAccess(request, null), shippingOrderId, command);
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

    @PostMapping("/{orderId}/submit")
    public PurchaseOrderView submitOrder(
            @PathVariable String orderId,
            HttpServletRequest request
    ) {
        try {
            return service().submitOrder(requireAccess(request, null), orderId);
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

    @GetMapping("/{orderId}/logistics-quote-report")
    public ResponseEntity<byte[]> exportLogisticsQuoteReport(
            @PathVariable String orderId,
            @RequestParam(required = false) String forwarderCode,
            @RequestParam(required = false) String routeCode,
            HttpServletRequest request
    ) {
        try {
            PurchaseOrderLogisticsQuoteReportExportView export =
                    service().exportLogisticsQuoteReport(requireAccess(request, null), orderId, forwarderCode, routeCode);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(export.contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(export.filename))
                    .body(export.content);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/{orderId}/logistics-quote-options")
    public PurchaseOrderLogisticsQuoteOptionsView logisticsQuoteOptions(
            @PathVariable String orderId,
            HttpServletRequest request
    ) {
        try {
            return service().listLogisticsQuoteOptions(requireAccess(request, null), orderId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/{orderId}/logistics-quote-report/import")
    public PurchaseOrderLogisticsQuoteImportView importLogisticsQuoteReport(
            @PathVariable String orderId,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request
    ) {
        try {
            return service().importLogisticsQuoteReport(
                    requireAccess(request, null),
                    orderId,
                    file == null ? null : file.getInputStream(),
                    file == null ? null : file.getOriginalFilename()
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "报价回传表读取失败。", exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/{orderId}/submit-shipping")
    public PurchaseOrderShippingSubmitView submitShipping(
            @PathVariable String orderId,
            HttpServletRequest request
    ) {
        try {
            return service().submitShipping(requireAccess(request, null), orderId);
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

    private String contentDisposition(String filename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename*=UTF-8''" + encoded;
    }
}
