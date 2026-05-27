package com.nuono.next.operationsconfig;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccessResolver;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/operations-config/product-dimensions")
public class OperationConfigProductDimensionController {

    private final BusinessAccessResolver accessResolver;
    private final OperationConfigProductDimensionOptionsService service;

    public OperationConfigProductDimensionController(
            BusinessAccessResolver accessResolver,
            OperationConfigProductDimensionOptionsService service
    ) {
        this.accessResolver = accessResolver;
        this.service = service;
    }

    @GetMapping("/options")
    public OperationConfigProductDimensionOptionsView options(
            HttpServletRequest request,
            @RequestParam(value = "bossUserIds", required = false) List<Long> bossUserIds,
            @RequestParam("ownerUserId") Long ownerUserId,
            @RequestParam("storeCode") String storeCode,
            @RequestParam("siteCode") String siteCode,
            @RequestParam(value = "brandQuery", required = false) String brandQuery,
            @RequestParam(value = "fulltypeQuery", required = false) String fulltypeQuery,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        try {
            BusinessAccessContext context = accessResolver.resolve(request);
            return service.options(context, bossUserIds, ownerUserId, storeCode, siteCode, brandQuery, fulltypeQuery, limit);
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        }
    }
}
