package com.nuono.next.postsaleprofit;

import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/post-sale-profit")
public class PostSaleProfitCommandController {
    private final PostSaleProfitRecalculationService recalculationService;
    private final PostSaleProfitAttributionAdjustmentService attributionAdjustmentService;
    private final PostSaleProfitFxRateService fxRateService;
    private final PostSaleProfitHttpSupport httpSupport;

    public PostSaleProfitCommandController(
            PostSaleProfitRecalculationService recalculationService,
            PostSaleProfitAttributionAdjustmentService attributionAdjustmentService,
            PostSaleProfitFxRateService fxRateService,
            PostSaleProfitHttpSupport httpSupport
    ) {
        this.recalculationService = recalculationService;
        this.attributionAdjustmentService = attributionAdjustmentService;
        this.fxRateService = fxRateService;
        this.httpSupport = httpSupport;
    }

    @PostMapping("/recalculate-preview")
    public PostSaleProfitRecalculationView recalculatePreview(
            @RequestBody PostSaleProfitRecalculationRequest body,
            HttpServletRequest request
    ) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
        }
        PostSaleProfitHttpSupport.DatedStoreScope scope = httpSupport.validateDatedStoreScope(
                body.getStoreCode(),
                body.getSiteCode(),
                body.getDateFrom(),
                body.getDateTo()
        );
        Long ownerUserId = httpSupport.requireOwnerUserId(request, scope.storeCode());
        return recalculationService.recalculatePreview(new PostSaleProfitRecalculationCommand(
                ownerUserId,
                scope.storeCode(),
                scope.siteCode(),
                scope.dateFrom(),
                scope.dateTo()
        ));
    }

    @PostMapping("/batch-attributions/lock")
    public PostSaleProfitAttributionAdjustmentView setBatchAttributionLock(
            @RequestBody PostSaleProfitBatchLockRequest body,
            HttpServletRequest request
    ) {
        if (body == null || body.getBatchId() == null || body.getBatchId() < 1 || body.getLocked() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "storeCode, siteCode, batchId and locked are required."
            );
        }
        PostSaleProfitHttpSupport.StoreScope scope = httpSupport.validateStoreScope(
                body.getStoreCode(),
                body.getSiteCode()
        );
        Long ownerUserId = httpSupport.requireOwnerUserId(request, scope.storeCode());
        return attributionAdjustmentService.setBatchLock(new PostSaleProfitBatchLockCommand(
                ownerUserId,
                scope.storeCode(),
                scope.siteCode(),
                body.getBatchId(),
                body.getLocked(),
                httpSupport.trimmed(body.getReason())
        ));
    }

    @PostMapping("/batch-attributions/move")
    public PostSaleProfitAttributionMoveView moveAttribution(
            @RequestBody PostSaleProfitAttributionMoveRequest body,
            HttpServletRequest request
    ) {
        if (body == null
                || body.getSourceBatchId() == null
                || body.getTargetBatchId() == null
                || body.getQuantity() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "storeCode, siteCode, sourceBatchId, targetBatchId and quantity are required."
            );
        }
        PostSaleProfitHttpSupport.StoreScope scope = httpSupport.validateStoreScope(
                body.getStoreCode(),
                body.getSiteCode()
        );
        Long ownerUserId = httpSupport.requireOwnerUserId(request, scope.storeCode());
        return attributionAdjustmentService.moveAttribution(new PostSaleProfitAttributionMoveCommand(
                ownerUserId,
                scope.storeCode(),
                scope.siteCode(),
                body.getSourceBatchId(),
                body.getTargetBatchId(),
                body.getQuantity(),
                httpSupport.trimmed(body.getReason())
        ));
    }

    @PostMapping("/fx-rates")
    public PostSaleProfitFxRateView saveFxRate(
            @RequestBody PostSaleProfitFxRateRequest body,
            HttpServletRequest request
    ) {
        if (body == null || body.getRateToCny() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "storeCode, siteCode, currency, rateToCny and effectiveFrom are required."
            );
        }
        PostSaleProfitHttpSupport.StoreScope scope = httpSupport.validateStoreScope(
                body.getStoreCode(),
                body.getSiteCode()
        );
        Long ownerUserId = httpSupport.requireOwnerUserId(request, scope.storeCode());
        return fxRateService.saveRate(new PostSaleProfitFxRateCommand(
                ownerUserId,
                scope.siteCode(),
                httpSupport.trimmed(body.getCurrency()),
                body.getRateToCny(),
                httpSupport.parseRequiredDate(body.getEffectiveFrom(), "effectiveFrom"),
                httpSupport.parseOptionalDate(body.getEffectiveTo(), "effectiveTo"),
                StringUtils.hasText(body.getSourceLabel()) ? body.getSourceLabel().trim() : "运营手工"
        ));
    }
}
