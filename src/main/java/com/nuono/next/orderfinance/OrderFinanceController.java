package com.nuono.next.orderfinance;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/order-finance")
public class OrderFinanceController {
    private final OrderFinanceSyncService syncService;
    private final OrderFinanceAnalyticsService analyticsService;
    private final BusinessAccessResolver businessAccessResolver;

    public OrderFinanceController(
            OrderFinanceSyncService syncService,
            OrderFinanceAnalyticsService analyticsService,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.syncService = syncService;
        this.analyticsService = analyticsService;
        this.businessAccessResolver = businessAccessResolver;
    }

    @PostMapping("/noon/transactions/sync")
    public OrderFinanceSyncResult syncNoonFinanceTransactions(
            @RequestBody OrderFinanceSyncRequest body,
            HttpServletRequest request
    ) {
        validateSyncRequest(body);
        if (!syncService.isProviderAvailable()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Noon finance transaction provider is not configured.");
        }
        String storeCode = body.getStoreCode().trim();
        String siteCode = body.getSiteCode().trim();
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                storeCode
        );
        Long ownerUserId = ownerUserId(context, storeCode);
        requireActiveStoreSite(ownerUserId, storeCode, siteCode);
        return syncService.syncMissingWindow(
                ownerUserId,
                storeCode,
                siteCode
        );
    }

    @GetMapping("/sku-summary")
    public OrderFinanceSkuSummaryView skuSummary(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam String dateFrom,
            @RequestParam String dateTo,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String partnerSkuList,
            HttpServletRequest request
    ) {
        validateQuery(storeCode, siteCode, dateFrom, dateTo);
        String normalizedStoreCode = storeCode.trim();
        String normalizedSiteCode = siteCode.trim();
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                normalizedStoreCode
        );
        Long ownerUserId = ownerUserId(context, normalizedStoreCode);
        requireActiveStoreSite(ownerUserId, normalizedStoreCode, normalizedSiteCode);
        return analyticsService.skuSummary(OrderFinanceQuery.summary(
                ownerUserId,
                normalizedStoreCode,
                normalizedSiteCode,
                LocalDate.parse(dateFrom),
                LocalDate.parse(dateTo),
                currency,
                search,
                splitPartnerSkuList(partnerSkuList)
        ));
    }

    @GetMapping("/sku-orders")
    public List<OrderFinanceOrderGroup> skuOrders(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam String dateFrom,
            @RequestParam String dateTo,
            @RequestParam(required = false) String partnerSku,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) Boolean missingPartnerSku,
            HttpServletRequest request
    ) {
        validateQuery(storeCode, siteCode, dateFrom, dateTo);
        boolean includeMissingPartnerSku = Boolean.TRUE.equals(missingPartnerSku);
        if (!includeMissingPartnerSku && !StringUtils.hasText(partnerSku)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "partnerSku is required.");
        }
        String normalizedStoreCode = storeCode.trim();
        String normalizedSiteCode = siteCode.trim();
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                normalizedStoreCode
        );
        Long ownerUserId = ownerUserId(context, normalizedStoreCode);
        requireActiveStoreSite(ownerUserId, normalizedStoreCode, normalizedSiteCode);
        String selectedPartnerSku = includeMissingPartnerSku ? "__missing__" : partnerSku;
        return analyticsService.skuOrders(OrderFinanceQuery.detail(
                ownerUserId,
                normalizedStoreCode,
                normalizedSiteCode,
                LocalDate.parse(dateFrom),
                LocalDate.parse(dateTo),
                currency,
                selectedPartnerSku,
                sku
        ));
    }

    private void validateSyncRequest(OrderFinanceSyncRequest body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
        }
        if (!StringUtils.hasText(body.getStoreCode()) || !StringUtils.hasText(body.getSiteCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storeCode and siteCode are required.");
        }
    }

    private void validateQuery(String storeCode, String siteCode, String dateFrom, String dateTo) {
        if (!StringUtils.hasText(storeCode) || !StringUtils.hasText(siteCode)
                || !StringUtils.hasText(dateFrom) || !StringUtils.hasText(dateTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storeCode, siteCode, dateFrom and dateTo are required.");
        }
        LocalDate from;
        LocalDate to;
        try {
            from = LocalDate.parse(dateFrom);
            to = LocalDate.parse(dateTo);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateFrom and dateTo must be ISO dates.", exception);
        }
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateTo must be on or after dateFrom.");
        }
    }

    private Long ownerUserId(BusinessAccessContext context, String storeCode) {
        Long ownerUserId = context.resolveOwnerUserIdForStore(storeCode);
        return ownerUserId == null ? context.getBusinessOwnerUserId() : ownerUserId;
    }

    private void requireActiveStoreSite(Long ownerUserId, String storeCode, String siteCode) {
        if (!analyticsService.hasActiveStoreSite(ownerUserId, storeCode, siteCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storeCode and siteCode do not match.");
        }
    }

    private List<String> splitPartnerSkuList(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        String[] tokens = raw.split("[\\n,]");
        List<String> result = new ArrayList<>();
        for (String token : tokens) {
            if (StringUtils.hasText(token)) {
                result.add(token.trim());
            }
        }
        return result;
    }
}
