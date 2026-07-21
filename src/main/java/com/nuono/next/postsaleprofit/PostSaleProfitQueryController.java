package com.nuono.next.postsaleprofit;

import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/post-sale-profit")
public class PostSaleProfitQueryController {
    private final PostSaleProfitReadService readService;
    private final PostSaleProfitFxRateService fxRateService;
    private final PostSaleProfitHttpSupport httpSupport;

    public PostSaleProfitQueryController(
            PostSaleProfitReadService readService,
            PostSaleProfitFxRateService fxRateService,
            PostSaleProfitHttpSupport httpSupport
    ) {
        this.readService = readService;
        this.fxRateService = fxRateService;
        this.httpSupport = httpSupport;
    }

    @GetMapping("/latest-run")
    public PostSaleProfitLatestRunView latestRun(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            HttpServletRequest request
    ) {
        PostSaleProfitHttpSupport.StoreScope scope = httpSupport.validateStoreScope(storeCode, siteCode);
        Long ownerUserId = httpSupport.requireOwnerUserId(request, scope.storeCode());
        return readService.latestRun(ownerUserId, scope.storeCode(), scope.siteCode());
    }

    @GetMapping("/batches")
    public PostSaleProfitBatchListView listBatches(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam String dateFrom,
            @RequestParam String dateTo,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String quality,
            @RequestParam(required = false) Boolean onlyLoss,
            @RequestParam(required = false) Boolean onlyLowMargin,
            @RequestParam(required = false) Boolean onlyMissing,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "50") Integer pageSize,
            HttpServletRequest request
    ) {
        PostSaleProfitHttpSupport.DatedStoreScope scope = httpSupport.validateDatedStoreScope(
                storeCode,
                siteCode,
                dateFrom,
                dateTo
        );
        Long ownerUserId = httpSupport.requireOwnerUserId(request, scope.storeCode());
        return readService.listBatches(new PostSaleProfitBatchQuery(
                ownerUserId,
                scope.storeCode(),
                scope.siteCode(),
                scope.dateFrom(),
                scope.dateTo(),
                httpSupport.trimmed(keyword),
                httpSupport.trimmed(quality),
                Boolean.TRUE.equals(onlyLoss),
                Boolean.TRUE.equals(onlyLowMargin),
                Boolean.TRUE.equals(onlyMissing),
                page == null || page < 1 ? 1 : page,
                pageSize == null || pageSize < 1 ? 50 : Math.min(pageSize, 200)
        ));
    }

    @GetMapping("/batch-detail")
    public PostSaleProfitBatchDetailView batchDetail(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam Long batchId,
            HttpServletRequest request
    ) {
        if (batchId == null || batchId < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "batchId is required.");
        }
        PostSaleProfitHttpSupport.StoreScope scope = httpSupport.validateStoreScope(storeCode, siteCode);
        Long ownerUserId = httpSupport.requireOwnerUserId(request, scope.storeCode());
        return readService.getBatchDetail(ownerUserId, scope.storeCode(), scope.siteCode(), batchId);
    }

    @GetMapping("/fx-rates")
    public List<PostSaleProfitFxRateView> listFxRates(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            HttpServletRequest request
    ) {
        PostSaleProfitHttpSupport.StoreScope scope = httpSupport.validateStoreScope(storeCode, siteCode);
        Long ownerUserId = httpSupport.requireOwnerUserId(request, scope.storeCode());
        return fxRateService.listRates(ownerUserId, scope.siteCode());
    }
}
