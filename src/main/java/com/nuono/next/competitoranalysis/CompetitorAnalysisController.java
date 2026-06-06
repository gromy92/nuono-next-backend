package com.nuono.next.competitoranalysis;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.List;
import java.util.Locale;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/competitor-analysis")
public class CompetitorAnalysisController {

    private final CompetitorAnalysisService service;
    private final CompetitorAnalysisRefreshService refreshService;
    private final BusinessAccessResolver businessAccessResolver;

    @Autowired
    public CompetitorAnalysisController(
            CompetitorAnalysisService service,
            CompetitorAnalysisRefreshService refreshService,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.service = service;
        this.refreshService = refreshService;
        this.businessAccessResolver = businessAccessResolver;
    }

    CompetitorAnalysisController(
            CompetitorAnalysisService service,
            BusinessAccessResolver businessAccessResolver
    ) {
        this(service, null, businessAccessResolver);
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

    @PostMapping("/watch-products/{watchProductId}/keywords")
    public CompetitorWatchProductDetailView addKeyword(
            @PathVariable Long watchProductId,
            @RequestBody CompetitorKeywordCommand command,
            HttpServletRequest request
    ) {
        CompetitorWatchProductScopeRow scope = service.requireWatchProductScope(watchProductId);
        BusinessAccessContext context = requireScopedStore(request, scope);
        return service.addKeyword(context, watchProductId, command);
    }

    @PatchMapping("/keywords/{keywordId}")
    public CompetitorWatchProductDetailView updateKeyword(
            @PathVariable Long keywordId,
            @RequestBody CompetitorKeywordCommand command,
            HttpServletRequest request
    ) {
        CompetitorWatchProductScopeRow scope = service.requireKeywordWatchProductScope(keywordId);
        BusinessAccessContext context = requireScopedStore(request, scope);
        return service.updateKeyword(context, keywordId, command);
    }

    @DeleteMapping("/keywords/{keywordId}")
    public CompetitorWatchProductDetailView deleteKeyword(
            @PathVariable Long keywordId,
            HttpServletRequest request
    ) {
        CompetitorWatchProductScopeRow scope = service.requireKeywordWatchProductScope(keywordId);
        BusinessAccessContext context = requireScopedStore(request, scope);
        return service.deleteKeyword(context, keywordId);
    }

    @PostMapping("/watch-products/{watchProductId}/manual-competitors")
    public CompetitorWatchProductDetailView addManualCompetitor(
            @PathVariable Long watchProductId,
            @RequestBody CompetitorManualCompetitorCommand command,
            HttpServletRequest request
    ) {
        CompetitorWatchProductScopeRow scope = service.requireWatchProductScope(watchProductId);
        BusinessAccessContext context = requireScopedStore(request, scope);
        return service.addManualCompetitor(context, watchProductId, command);
    }

    @PostMapping("/keywords/{keywordId}/candidates/{competitorProductId}/confirm")
    public CompetitorWatchProductDetailView confirmCandidate(
            @PathVariable Long keywordId,
            @PathVariable Long competitorProductId,
            HttpServletRequest request
    ) {
        CompetitorWatchProductScopeRow scope = service.requireKeywordCandidateScope(keywordId, competitorProductId);
        BusinessAccessContext context = requireScopedStore(request, scope);
        return service.confirmCandidate(context, keywordId, competitorProductId);
    }

    @PostMapping("/keywords/{keywordId}/candidates/{competitorProductId}/ignore")
    public CompetitorWatchProductDetailView ignoreCandidate(
            @PathVariable Long keywordId,
            @PathVariable Long competitorProductId,
            HttpServletRequest request
    ) {
        CompetitorWatchProductScopeRow scope = service.requireKeywordCandidateScope(keywordId, competitorProductId);
        BusinessAccessContext context = requireScopedStore(request, scope);
        return service.ignoreCandidate(context, keywordId, competitorProductId);
    }

    @PostMapping("/watch-products/{watchProductId}/refresh")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompetitorRefreshRunView refreshWatchProduct(
            @PathVariable Long watchProductId,
            HttpServletRequest request
    ) {
        CompetitorWatchProductScopeRow scope = service.requireWatchProductScope(watchProductId);
        BusinessAccessContext context = requireScopedStore(request, scope);
        return requireRefreshService().requestRefresh(context, watchProductId);
    }

    @GetMapping("/refresh-runs/{runId}")
    public CompetitorRefreshRunView refreshRun(
            @PathVariable Long runId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = businessAccessResolver.requireBusinessContext(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS
        );
        return requireRefreshService().getRefreshRun(context, runId);
    }

    @GetMapping("/tasks/{taskId}")
    public CompetitorTaskView task(
            @PathVariable Long taskId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = businessAccessResolver.requireBusinessContext(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS
        );
        return requireRefreshService().getTask(context, taskId);
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

    private CompetitorAnalysisRefreshService requireRefreshService() {
        if (refreshService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "COMPETITOR_REFRESH_UNAVAILABLE");
        }
        return refreshService;
    }

    private BusinessAccessContext requireScopedStore(
            HttpServletRequest request,
            CompetitorWatchProductScopeRow scope
    ) {
        return businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS,
                scope.getStoreCode()
        );
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
