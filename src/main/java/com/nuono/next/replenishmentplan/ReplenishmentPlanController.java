package com.nuono.next.replenishmentplan;

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
@RequestMapping("/api/replenishment-plan")
public class ReplenishmentPlanController {

    private final ReplenishmentPlanService service;
    private final BusinessAccessResolver businessAccessResolver;

    public ReplenishmentPlanController(
            ReplenishmentPlanService service,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.service = service;
        this.businessAccessResolver = businessAccessResolver;
    }

    @GetMapping("/overview")
    public ReplenishmentPlanRecords.PlanOverviewView overview(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            HttpServletRequest request
    ) {
        validateOverviewRequest(storeCode, siteCode);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.PROCUREMENT,
                storeCode
        );
        Long ownerUserId = context.resolveOwnerUserIdForStore(storeCode);
        if (ownerUserId == null) {
            ownerUserId = context.getBusinessOwnerUserId();
        }
        return service.getOverview(new ReplenishmentPlanRecords.PlanQuery(ownerUserId, storeCode, siteCode));
    }

    private void validateOverviewRequest(String storeCode, String siteCode) {
        if (!StringUtils.hasText(storeCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少店铺编码。");
        }
        if (!StringUtils.hasText(siteCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少站点。");
        }
    }
}
