package com.nuono.next.productpublicdetail;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/product-public-details")
public class ProductPublicDetailController {
    private final ObjectProvider<ProductPublicDetailSyncService> syncServiceProvider;
    private final BusinessAccessResolver businessAccessResolver;

    public ProductPublicDetailController(
            ObjectProvider<ProductPublicDetailSyncService> syncServiceProvider,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.syncServiceProvider = syncServiceProvider;
        this.businessAccessResolver = businessAccessResolver;
    }

    @GetMapping("/sync-status")
    public ProductPublicDetailStatusView syncStatus(
            HttpServletRequest request,
            @RequestParam("storeCode") String storeCode,
            @RequestParam("siteCode") String siteCode
    ) {
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.PRODUCT_MASTER,
                storeCode
        );
        return requireService().syncStatus(context, storeCode, siteCode);
    }

    @GetMapping("/latest")
    public ProductPublicDetailSnapshot latest(
            HttpServletRequest request,
            @RequestParam("storeCode") String storeCode,
            @RequestParam("siteCode") String siteCode,
            @RequestParam("productMasterId") Long productMasterId,
            @RequestParam("productVariantId") Long productVariantId
    ) {
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.PRODUCT_MASTER,
                storeCode
        );
        ProductPublicDetailSnapshot snapshot = requireService().latest(context, storeCode, siteCode, productMasterId, productVariantId);
        if (snapshot == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到商品前台详情快照。");
        }
        return snapshot;
    }

    @PostMapping("/sync-tasks")
    public ProductPublicDetailTaskView submitSyncTask(
            HttpServletRequest request,
            @RequestBody ProductPublicDetailSyncTaskRequest command
    ) {
        if (command == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required.");
        }
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.PRODUCT_MASTER,
                command.getStoreCode()
        );
        return requireService().submitManual(context, command.getStoreCode(), command.getSiteCode());
    }

    private ProductPublicDetailSyncService requireService() {
        ProductPublicDetailSyncService service = syncServiceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "商品前台详情同步服务不可用。");
        }
        return service;
    }
}
