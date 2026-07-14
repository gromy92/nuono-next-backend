package com.nuono.next.postsaleprofit;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.time.LocalDate;
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
@RequestMapping("/api/post-sale-profit")
public class PostSaleProfitController {
    private final PostSaleProfitReadService readService;
    private final PostSaleProfitRecalculationService recalculationService;
    private final PostSaleProfitAttributionAdjustmentService attributionAdjustmentService;
    private final PostSaleProfitFxRateService fxRateService;
    private final BusinessAccessResolver businessAccessResolver;

    public PostSaleProfitController(
            PostSaleProfitReadService readService,
            PostSaleProfitRecalculationService recalculationService,
            PostSaleProfitAttributionAdjustmentService attributionAdjustmentService,
            PostSaleProfitFxRateService fxRateService,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.readService = readService;
        this.recalculationService = recalculationService;
        this.attributionAdjustmentService = attributionAdjustmentService;
        this.fxRateService = fxRateService;
        this.businessAccessResolver = businessAccessResolver;
    }

    @GetMapping("/latest-run")
    public PostSaleProfitLatestRunView latestRun(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            HttpServletRequest request
    ) {
        ValidatedStoreScope scope = validateStoreScope(storeCode, siteCode);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                scope.storeCode
        );
        return readService.latestRun(
                ownerUserId(context, scope.storeCode),
                scope.storeCode,
                scope.siteCode
        );
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
        ValidatedScope scope = validateScope(storeCode, siteCode, dateFrom, dateTo);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                scope.storeCode
        );
        return readService.listBatches(new PostSaleProfitBatchQuery(
                ownerUserId(context, scope.storeCode),
                scope.storeCode,
                scope.siteCode,
                scope.dateFrom,
                scope.dateTo,
                trimmed(keyword),
                trimmed(quality),
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
        ValidatedStoreScope scope = validateStoreScope(storeCode, siteCode);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                scope.storeCode
        );
        return readService.getBatchDetail(
                ownerUserId(context, scope.storeCode),
                scope.storeCode,
                scope.siteCode,
                batchId
        );
    }

    @PostMapping("/recalculate-preview")
    public PostSaleProfitRecalculationView recalculatePreview(
            @RequestBody PostSaleProfitRecalculationRequest body,
            HttpServletRequest request
    ) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
        }
        ValidatedScope scope = validateScope(body.getStoreCode(), body.getSiteCode(), body.getDateFrom(), body.getDateTo());
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                scope.storeCode
        );
        return recalculationService.recalculatePreview(new PostSaleProfitRecalculationCommand(
                ownerUserId(context, scope.storeCode),
                scope.storeCode,
                scope.siteCode,
                scope.dateFrom,
                scope.dateTo
        ));
    }

    @PostMapping("/batch-attributions/lock")
    public PostSaleProfitAttributionAdjustmentView setBatchAttributionLock(
            @RequestBody PostSaleProfitBatchLockRequest body,
            HttpServletRequest request
    ) {
        if (body == null || body.getBatchId() == null || body.getBatchId() < 1 || body.getLocked() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storeCode, siteCode, batchId and locked are required.");
        }
        ValidatedStoreScope scope = validateStoreScope(body.getStoreCode(), body.getSiteCode());
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                scope.storeCode
        );
        return attributionAdjustmentService.setBatchLock(new PostSaleProfitBatchLockCommand(
                ownerUserId(context, scope.storeCode),
                scope.storeCode,
                scope.siteCode,
                body.getBatchId(),
                body.getLocked(),
                trimmed(body.getReason())
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
        ValidatedStoreScope scope = validateStoreScope(body.getStoreCode(), body.getSiteCode());
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                scope.storeCode
        );
        return attributionAdjustmentService.moveAttribution(new PostSaleProfitAttributionMoveCommand(
                ownerUserId(context, scope.storeCode),
                scope.storeCode,
                scope.siteCode,
                body.getSourceBatchId(),
                body.getTargetBatchId(),
                body.getQuantity(),
                trimmed(body.getReason())
        ));
    }

    @GetMapping("/fx-rates")
    public java.util.List<PostSaleProfitFxRateView> listFxRates(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            HttpServletRequest request
    ) {
        ValidatedStoreScope scope = validateStoreScope(storeCode, siteCode);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                scope.storeCode
        );
        return fxRateService.listRates(ownerUserId(context, scope.storeCode), scope.siteCode);
    }

    @PostMapping("/fx-rates")
    public PostSaleProfitFxRateView saveFxRate(
            @RequestBody PostSaleProfitFxRateRequest body,
            HttpServletRequest request
    ) {
        if (body == null || body.getRateToCny() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storeCode, siteCode, currency, rateToCny and effectiveFrom are required.");
        }
        ValidatedStoreScope scope = validateStoreScope(body.getStoreCode(), body.getSiteCode());
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                scope.storeCode
        );
        return fxRateService.saveRate(new PostSaleProfitFxRateCommand(
                ownerUserId(context, scope.storeCode),
                scope.siteCode,
                trimmed(body.getCurrency()),
                body.getRateToCny(),
                parseRequiredDate(body.getEffectiveFrom(), "effectiveFrom"),
                parseOptionalDate(body.getEffectiveTo(), "effectiveTo"),
                StringUtils.hasText(body.getSourceLabel()) ? body.getSourceLabel().trim() : "运营手工"
        ));
    }

    private ValidatedScope validateScope(String storeCode, String siteCode, String dateFrom, String dateTo) {
        ValidatedStoreScope storeScope = validateStoreScope(storeCode, siteCode);
        if (!StringUtils.hasText(dateFrom)
                || !StringUtils.hasText(dateTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storeCode, siteCode, dateFrom and dateTo are required.");
        }
        LocalDate from;
        LocalDate to;
        try {
            from = LocalDate.parse(dateFrom.trim());
            to = LocalDate.parse(dateTo.trim());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateFrom and dateTo must be ISO dates.", exception);
        }
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateTo must be on or after dateFrom.");
        }
        return new ValidatedScope(storeScope.storeCode, storeScope.siteCode, from, to);
    }

    private ValidatedStoreScope validateStoreScope(String storeCode, String siteCode) {
        if (!StringUtils.hasText(storeCode) || !StringUtils.hasText(siteCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storeCode and siteCode are required.");
        }
        return new ValidatedStoreScope(storeCode.trim(), siteCode.trim());
    }

    private LocalDate parseRequiredDate(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required.");
        }
        return parseDate(value, fieldName);
    }

    private LocalDate parseOptionalDate(String value, String fieldName) {
        return StringUtils.hasText(value) ? parseDate(value, fieldName) : null;
    }

    private LocalDate parseDate(String value, String fieldName) {
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be ISO date.", exception);
        }
    }

    private Long ownerUserId(BusinessAccessContext context, String storeCode) {
        Long ownerUserId = context.resolveOwnerUserIdForStore(storeCode);
        return ownerUserId == null ? context.getBusinessOwnerUserId() : ownerUserId;
    }

    private String trimmed(String value) {
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

    private static final class ValidatedStoreScope {
        private final String storeCode;
        private final String siteCode;

        private ValidatedStoreScope(String storeCode, String siteCode) {
            this.storeCode = storeCode;
            this.siteCode = siteCode;
        }
    }
}
