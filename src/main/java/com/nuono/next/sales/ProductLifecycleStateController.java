package com.nuono.next.sales;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/sales-data/lifecycle")
public class ProductLifecycleStateController {

    private final ProductLifecycleStateService lifecycleStateService;
    private final BusinessAccessResolver businessAccessResolver;

    public ProductLifecycleStateController(
            ProductLifecycleStateService lifecycleStateService,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.lifecycleStateService = lifecycleStateService;
        this.businessAccessResolver = businessAccessResolver;
    }

    @GetMapping("/overview")
    public ProductLifecycleStateOverview getLifecycleOverview(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam String partnerSku,
            @RequestParam(required = false) String sku,
            HttpServletRequest request
    ) {
        validateOverviewRequest(storeCode, siteCode, partnerSku, sku);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                storeCode
        );
        Long ownerUserId = context.resolveOwnerUserIdForStore(storeCode);
        if (ownerUserId == null) {
            ownerUserId = context.getBusinessOwnerUserId();
        }
        return lifecycleStateService.getOverview(new ProductLifecycleStateQuery(
                ownerUserId,
                storeCode,
                siteCode,
                partnerSku,
                sku
        ));
    }

    private void validateOverviewRequest(String storeCode, String siteCode, String partnerSku, String sku) {
        if (!StringUtils.hasText(storeCode)
                || !StringUtils.hasText(siteCode)
                || !StringUtils.hasText(partnerSku)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "店铺、站点和 Partner SKU 不能为空。");
        }
    }
}
