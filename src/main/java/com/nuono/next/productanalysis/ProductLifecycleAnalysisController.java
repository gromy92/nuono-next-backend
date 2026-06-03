package com.nuono.next.productanalysis;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/product-analysis/lifecycle")
public class ProductLifecycleAnalysisController {

    private final ProductLifecycleAnalysisService service;
    private final BusinessAccessResolver businessAccessResolver;

    public ProductLifecycleAnalysisController(
            ProductLifecycleAnalysisService service,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.service = service;
        this.businessAccessResolver = businessAccessResolver;
    }

    @GetMapping("/overview")
    public ProductLifecycleAnalysisOverviewView getOverview(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            HttpServletRequest request
    ) {
        validateOverviewRequest(storeCode, siteCode);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                storeCode
        );
        Long ownerUserId = context.resolveOwnerUserIdForStore(storeCode);
        if (ownerUserId == null) {
            ownerUserId = context.getBusinessOwnerUserId();
        }
        return service.getOverview(new ProductLifecycleAnalysisQuery(ownerUserId, storeCode, siteCode));
    }

    @PostMapping("/recalculate")
    public ProductLifecycleAnalysisRecalculationView recalculate(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            HttpServletRequest request
    ) {
        validateOverviewRequest(storeCode, siteCode);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                storeCode
        );
        Long ownerUserId = context.resolveOwnerUserIdForStore(storeCode);
        if (ownerUserId == null) {
            ownerUserId = context.getBusinessOwnerUserId();
        }
        return service.recalculate(
                new ProductLifecycleAnalysisQuery(ownerUserId, storeCode, siteCode),
                context.getSessionUserId()
        );
    }

    private void validateOverviewRequest(String storeCode, String siteCode) {
        if (!StringUtils.hasText(storeCode) || !StringUtils.hasText(siteCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "店铺和站点不能为空。");
        }
    }
}
