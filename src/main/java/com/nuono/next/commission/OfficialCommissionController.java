package com.nuono.next.commission;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/official-commission")
public class OfficialCommissionController {
    private final OfficialCommissionCalculationService service;
    private final BusinessAccessResolver businessAccessResolver;

    public OfficialCommissionController(OfficialCommissionCalculationService service, BusinessAccessResolver businessAccessResolver) {
        this.service = service;
        this.businessAccessResolver = businessAccessResolver;
    }

    @PostMapping("/calculate-by-product")
    public OfficialCommissionCalculationView calculateByProduct(
            @RequestBody OfficialCommissionCalculationCommand body,
            HttpServletRequest request
    ) {
        validateCalculationBody(body);
        String storeCode = body.getStoreCode().trim();
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(request, BusinessCapability.PROFIT, storeCode);
        body.setOwnerUserId(ownerUserId(context, storeCode));
        body.setStoreCode(storeCode);
        body.setOperatorUserId(context.getSessionUserId());
        try {
            return service.calculateByProduct(body);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/batch-calculate-by-product")
    public List<OfficialCommissionCalculationView> batchCalculateByProduct(
            @RequestBody OfficialCommissionBatchCalculationCommand body,
            HttpServletRequest request
    ) {
        validateBatchBody(body);
        String storeCode = body.getStoreCode().trim();
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(request, BusinessCapability.PROFIT, storeCode);
        body.setOwnerUserId(ownerUserId(context, storeCode));
        body.setStoreCode(storeCode);
        body.setOperatorUserId(context.getSessionUserId());
        try {
            return service.calculateBatch(body);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/latest-calculations")
    public List<OfficialCommissionCalculationView> latestCalculations(
            @RequestBody OfficialCommissionLatestCalculationQuery body,
            HttpServletRequest request
    ) {
        validateLatestBody(body);
        String storeCode = body.getStoreCode().trim();
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(request, BusinessCapability.PROFIT, storeCode);
        body.setOwnerUserId(ownerUserId(context, storeCode));
        body.setStoreCode(storeCode);
        try {
            return service.latestCalculations(body);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private void validateCalculationBody(OfficialCommissionCalculationCommand body) {
        if (body == null || !StringUtils.hasText(body.getStoreCode()) || !StringUtils.hasText(body.getSkuId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storeCode and skuId are required.");
        }
    }

    private void validateLatestBody(OfficialCommissionLatestCalculationQuery body) {
        if (body == null || !StringUtils.hasText(body.getStoreCode()) || !StringUtils.hasText(body.getSite())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storeCode and site are required.");
        }
    }

    private void validateBatchBody(OfficialCommissionBatchCalculationCommand body) {
        if (body == null || !StringUtils.hasText(body.getStoreCode()) || !body.hasItems()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storeCode and items are required.");
        }
    }

    private Long ownerUserId(BusinessAccessContext context, String storeCode) {
        Long ownerUserId = context.resolveOwnerUserIdForStore(storeCode);
        return ownerUserId == null ? context.getBusinessOwnerUserId() : ownerUserId;
    }
}
