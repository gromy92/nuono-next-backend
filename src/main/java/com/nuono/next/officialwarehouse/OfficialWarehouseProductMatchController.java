package com.nuono.next.officialwarehouse;

import com.nuono.next.intransit.InTransitProductMatchService;
import com.nuono.next.intransit.InTransitProductMatchViews.PreparationView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/warehouse/official-warehouse/product-matches")
public class OfficialWarehouseProductMatchController {
    private final InTransitProductMatchService productMatchService;
    private final BusinessAccessResolver accessResolver;

    public OfficialWarehouseProductMatchController(
            InTransitProductMatchService productMatchService,
            BusinessAccessResolver accessResolver
    ) {
        this.productMatchService = productMatchService;
        this.accessResolver = accessResolver;
    }

    @PostMapping("/prepare")
    public PreparationView prepare(@RequestBody PrepareCommand command, HttpServletRequest request) {
        if (command == null || command.storeCode == null || command.siteCode == null) {
            throw new IllegalArgumentException("请选择店铺和站点。");
        }
        BusinessAccessContext access = accessResolver.requireStoreAccess(
                request,
                BusinessCapability.OFFICIAL_WAREHOUSE,
                command.storeCode
        );
        return productMatchService.prepareForStoreSite(access, command.storeCode, command.siteCode);
    }

    public static class PrepareCommand {
        public String storeCode;
        public String siteCode;
    }
}
