package com.nuono.next.competitoranalysis;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.List;
import java.util.Locale;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/competitor-analysis")
public class CompetitorAnalysisController {

    private final CompetitorAnalysisService service;
    private final BusinessAccessResolver businessAccessResolver;

    public CompetitorAnalysisController(
            CompetitorAnalysisService service,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.service = service;
        this.businessAccessResolver = businessAccessResolver;
    }

    @GetMapping("/watch-products")
    public CompetitorWatchProductListView watchProducts(
            @RequestParam(required = false) String storeCode,
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) String productSearch,
            @RequestParam(required = false) String keywordSearch,
            @RequestParam(required = false) String competitorSearch,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            HttpServletRequest request
    ) {
        CompetitorWatchProductQuery query = CompetitorWatchProductQuery.fromRequest(
                storeCode,
                siteCode,
                productSearch,
                keywordSearch,
                competitorSearch,
                status,
                page,
                pageSize
        );
        BusinessAccessContext context = StringUtils.hasText(query.getStoreCode())
                ? businessAccessResolver.requireStoreAccess(
                        request,
                        BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS,
                        query.getStoreCode()
                )
                : businessAccessResolver.requireBusinessContext(
                        request,
                        BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS
                );
        return service.listWatchProducts(context, query);
    }

    @GetMapping("/watch-products/{watchProductId}")
    public CompetitorWatchProductDetailView watchProductDetail(
            @PathVariable Long watchProductId,
            HttpServletRequest request
    ) {
        CompetitorWatchProductScopeRow scope = service.requireWatchProductScope(watchProductId);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS,
                scope.getStoreCode()
        );
        return service.detail(context, watchProductId);
    }

    @GetMapping("/product-options")
    public List<CompetitorProductOptionView> productOptions(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request
    ) {
        String normalizedStoreCode = requireStoreCode(storeCode);
        String normalizedSiteCode = requireSiteCode(siteCode);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS,
                normalizedStoreCode
        );
        return service.productOptions(context, normalizedStoreCode, normalizedSiteCode, keyword, limit);
    }

    @PostMapping("/watch-products")
    public CompetitorWatchProductDetailView createWatchProduct(
            @RequestBody CompetitorWatchProductCreateCommand command,
            HttpServletRequest request
    ) {
        if (command == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COMPETITOR_REQUEST_REQUIRED");
        }
        String normalizedStoreCode = requireStoreCode(command.getStoreCode());
        command.setStoreCode(normalizedStoreCode);
        if (StringUtils.hasText(command.getSiteCode())) {
            command.setSiteCode(command.getSiteCode().trim().toUpperCase(Locale.ROOT));
        }
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS,
                normalizedStoreCode
        );
        return service.createWatchProduct(context, command);
    }

    private String requireStoreCode(String storeCode) {
        if (!StringUtils.hasText(storeCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COMPETITOR_STORE_REQUIRED");
        }
        return storeCode.trim().toUpperCase(Locale.ROOT);
    }

    private String requireSiteCode(String siteCode) {
        if (!StringUtils.hasText(siteCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COMPETITOR_SITE_REQUIRED");
        }
        return siteCode.trim().toUpperCase(Locale.ROOT);
    }
}
