package com.nuono.next.postsaleprofit.batchattribution;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.postsaleprofit.batchattribution.PostSaleProfitBatchAttributionRecords.BatchAttributionListView;
import com.nuono.next.postsaleprofit.batchattribution.PostSaleProfitBatchAttributionRecords.BatchAttributionSkuDetailView;
import java.time.LocalDate;
import javax.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/post-sale-profit/batch-attribution")
public class PostSaleProfitBatchAttributionController {
    private final PostSaleProfitBatchAttributionService service;
    private final BusinessAccessResolver businessAccessResolver;

    public PostSaleProfitBatchAttributionController(
            PostSaleProfitBatchAttributionService service,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.service = service;
        this.businessAccessResolver = businessAccessResolver;
    }

    @GetMapping("/summary")
    public BatchAttributionListView summary(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request
    ) {
        ValidatedScope scope = validateScope(storeCode, siteCode, dateFrom, dateTo);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                scope.storeCode
        );
        return service.listSummary(
                ownerUserId(context, scope.storeCode),
                scope.storeCode,
                scope.siteCode,
                scope.dateFrom,
                scope.dateTo,
                trim(keyword)
        );
    }

    @GetMapping("/sku-detail")
    public BatchAttributionSkuDetailView skuDetail(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam String partnerSku,
            HttpServletRequest request
    ) {
        ValidatedScope scope = validateScope(storeCode, siteCode, dateFrom, dateTo);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                scope.storeCode
        );
        String sku = trim(partnerSku);
        if (!StringUtils.hasText(sku)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "partnerSku is required.");
        }
        return service.getSkuDetail(
                ownerUserId(context, scope.storeCode),
                scope.storeCode,
                scope.siteCode,
                scope.dateFrom,
                scope.dateTo,
                sku
        );
    }

    private ValidatedScope validateScope(String storeCode, String siteCode, LocalDate dateFrom, LocalDate dateTo) {
        if (!StringUtils.hasText(storeCode) || !StringUtils.hasText(siteCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storeCode and siteCode are required.");
        }
        if ((dateFrom == null) != (dateTo == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateFrom and dateTo must be provided together.");
        }
        if (dateFrom != null && dateTo.isBefore(dateFrom)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateTo must be on or after dateFrom.");
        }
        return new ValidatedScope(storeCode.trim(), siteCode.trim(), dateFrom, dateTo);
    }

    private Long ownerUserId(BusinessAccessContext context, String storeCode) {
        Long ownerUserId = context.resolveOwnerUserIdForStore(storeCode);
        return ownerUserId == null ? context.getBusinessOwnerUserId() : ownerUserId;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static final class ValidatedScope {
        private final String storeCode;
        private final String siteCode;
        private final LocalDate dateFrom;
        private final LocalDate dateTo;

        private ValidatedScope(String storeCode, String siteCode, LocalDate dateFrom, LocalDate dateTo) {
            this.storeCode = storeCode;
            this.siteCode = siteCode;
            this.dateFrom = dateFrom;
            this.dateTo = dateTo;
        }
    }
}
