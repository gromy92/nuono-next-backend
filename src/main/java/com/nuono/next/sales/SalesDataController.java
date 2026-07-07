package com.nuono.next.sales;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/sales-data")
public class SalesDataController {

    private final NoonSalesCsvImportService importService;
    private final SalesAnalyticsService analyticsService;
    private final SalesSyncTaskService syncTaskService;
    private final SalesImportQualityService importQualityService;
    private final SalesActivityWindowService activityWindowService;
    private final BusinessAccessResolver businessAccessResolver;

    public SalesDataController(
            NoonSalesCsvImportService importService,
            SalesAnalyticsService analyticsService,
            SalesSyncTaskService syncTaskService,
            SalesImportQualityService importQualityService,
            SalesActivityWindowService activityWindowService,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.importService = importService;
        this.analyticsService = analyticsService;
        this.syncTaskService = syncTaskService;
        this.importQualityService = importQualityService;
        this.activityWindowService = activityWindowService;
        this.businessAccessResolver = businessAccessResolver;
    }

    static SalesDataController analyticsOnly(
            SalesAnalyticsService analyticsService,
            BusinessAccessResolver businessAccessResolver
    ) {
        return new SalesDataController(null, analyticsService, null, null, null, businessAccessResolver);
    }

    @PostMapping("/noon/productviewsandsalesdata/import")
    public NoonSalesCsvImportResult importNoonProductViewsAndSalesData(
            @RequestBody SalesCsvImportRequest body,
            HttpServletRequest request
    ) {
        validateImportRequest(body);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                body.getStoreCode()
        );
        Long ownerUserId = context.resolveOwnerUserIdForStore(body.getStoreCode());
        if (ownerUserId == null) {
            ownerUserId = context.getBusinessOwnerUserId();
        }
        return importService.importCsv(new NoonSalesCsvImportCommand(
                ownerUserId,
                body.getLogicalStoreId(),
                body.getStoreCode(),
                body.getSiteCode(),
                body.getSourceFilename(),
                body.getCsv()
        ));
    }

    @GetMapping("/daily-facts")
    public SalesDailyFactsView listDailyFacts(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam String dateFrom,
            @RequestParam String dateTo,
            @RequestParam(required = false) String partnerSku,
            @RequestParam(required = false) String sku,
            HttpServletRequest request
    ) {
        validateListRequest(storeCode, siteCode, dateFrom, dateTo);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                storeCode
        );
        Long ownerUserId = context.resolveOwnerUserIdForStore(storeCode);
        if (ownerUserId == null) {
            ownerUserId = context.getBusinessOwnerUserId();
        }
        return analyticsService.listDailyFacts(new SalesFactQuery(
                ownerUserId,
                storeCode,
                siteCode,
                LocalDate.parse(dateFrom),
                LocalDate.parse(dateTo),
                partnerSku,
                sku,
                null
        ));
    }

    @GetMapping("/analytics/summary")
    public SalesAnalyticsSummary getSalesAnalyticsSummary(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam String dateFrom,
            @RequestParam String dateTo,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String productFulltype,
            @RequestParam(required = false) String dataQualityCode,
            @RequestParam(required = false) String partnerSkuList,
            HttpServletRequest request
    ) {
        return analyticsService.getSummary(authorizedSalesFactQuery(
                storeCode,
                siteCode,
                dateFrom,
                dateTo,
                null,
                null,
                search,
                brand,
                productFulltype,
                dataQualityCode,
                partnerSkuList,
                request
        ));
    }

    @GetMapping("/analytics/trends")
    public List<SalesTrendBucket> getSalesAnalyticsTrends(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam String dateFrom,
            @RequestParam String dateTo,
            @RequestParam(defaultValue = "day") String granularity,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String productFulltype,
            @RequestParam(required = false) String dataQualityCode,
            @RequestParam(required = false) String partnerSkuList,
            HttpServletRequest request
    ) {
        return analyticsService.getTrends(authorizedSalesFactQuery(
                storeCode,
                siteCode,
                dateFrom,
                dateTo,
                null,
                null,
                search,
                brand,
                productFulltype,
                dataQualityCode,
                partnerSkuList,
                request
        ), granularity);
    }

    @GetMapping("/analytics/products")
    public List<SalesProductRow> listSalesAnalyticsProducts(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam String dateFrom,
            @RequestParam String dateTo,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String productFulltype,
            @RequestParam(required = false) String dataQualityCode,
            @RequestParam(required = false) String partnerSkuList,
            HttpServletRequest request
    ) {
        return analyticsService.listProductRows(authorizedSalesFactQuery(
                storeCode,
                siteCode,
                dateFrom,
                dateTo,
                null,
                null,
                search,
                brand,
                productFulltype,
                dataQualityCode,
                partnerSkuList,
                request
        ));
    }

    @GetMapping("/analytics/product-detail")
    public SalesProductDetail getSalesAnalyticsProductDetail(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam String dateFrom,
            @RequestParam String dateTo,
            @RequestParam String partnerSku,
            @RequestParam(required = false) String sku,
            HttpServletRequest request
    ) {
        return analyticsService.getProductDetail(authorizedSalesFactQuery(
                storeCode,
                siteCode,
                dateFrom,
                dateTo,
                partnerSku,
                sku,
                null,
                null,
                null,
                null,
                null,
                request
        ));
    }

    @PostMapping("/analytics/history-backfill")
    public SalesHistoryBackfillResult requestSalesAnalyticsHistoryBackfill(
            @RequestBody SalesHistoryBackfillRequest body,
            HttpServletRequest request
    ) {
        validateHistoryBackfillRequest(body);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                body.getStoreCode()
        );
        Long ownerUserId = context.resolveOwnerUserIdForStore(body.getStoreCode());
        if (ownerUserId == null) {
            ownerUserId = context.getBusinessOwnerUserId();
        }
        return analyticsService.requestHistoryBackfill(new SalesHistoryBackfillCommand(
                ownerUserId,
                body.getStoreCode(),
                body.getSiteCode(),
                parseDate(body.getDateFrom()),
                parseDate(body.getDateTo())
        ));
    }

    @GetMapping("/analytics/export")
    public ResponseEntity<String> exportSalesAnalyticsDailyFacts(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam String dateFrom,
            @RequestParam String dateTo,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String productFulltype,
            @RequestParam(required = false) String dataQualityCode,
            @RequestParam(required = false) String partnerSkuList,
            HttpServletRequest request
    ) {
        String csv = analyticsService.exportDailyFactsCsv(authorizedSalesFactQuery(
                storeCode,
                siteCode,
                dateFrom,
                dateTo,
                null,
                null,
                search,
                brand,
                productFulltype,
                dataQualityCode,
                partnerSkuList,
                request
        ));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header("Content-Disposition", "attachment; filename=\"sales-analytics.csv\"")
                .body(csv);
    }

    @PostMapping("/activity-windows")
    public SalesActivityWindowRecord saveActivityWindow(
            @RequestBody SalesActivityWindowRequest body,
            HttpServletRequest request
    ) {
        validateActivityWindowRequest(body);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                body.getStoreCode()
        );
        Long ownerUserId = context.resolveOwnerUserIdForStore(body.getStoreCode());
        if (ownerUserId == null) {
            ownerUserId = context.getBusinessOwnerUserId();
        }
        return activityWindowService.save(new SalesActivityWindowCommand(
                body.getId(),
                ownerUserId,
                body.getStoreCode(),
                body.getSiteCode(),
                body.getName(),
                body.getActivityType(),
                body.getCategoryScope(),
                parseDate(body.getDateFrom()),
                parseDate(body.getDateTo()),
                body.getFactor(),
                body.isEnabled(),
                context.getSessionUserId()
        ));
    }

    @GetMapping("/activity-windows/active")
    public SalesActivityWindowSnapshot getActiveActivityWindows(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam String dateFrom,
            @RequestParam String dateTo,
            HttpServletRequest request
    ) {
        return activityWindowService.activeSnapshot(authorizedActivityScope(
                storeCode,
                siteCode,
                dateFrom,
                dateTo,
                request
        ));
    }

    @GetMapping("/activity-windows/history")
    public List<SalesActivityWindowRecord> listActivityWindowHistory(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam String dateFrom,
            @RequestParam String dateTo,
            HttpServletRequest request
    ) {
        return activityWindowService.history(authorizedActivityScope(
                storeCode,
                siteCode,
                dateFrom,
                dateTo,
                request
        ));
    }

    @PostMapping("/noon/productviewsandsalesdata/sync-tasks")
    public SalesSyncTaskRecord triggerNoonProductViewsAndSalesDataSync(
            @RequestBody SalesSyncTaskRequest body,
            HttpServletRequest request
    ) {
        validateSyncRequest(body);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                body.getStoreCode()
        );
        Long ownerUserId = context.resolveOwnerUserIdForStore(body.getStoreCode());
        if (ownerUserId == null) {
            ownerUserId = context.getBusinessOwnerUserId();
        }
        return syncTaskService.triggerAndRun(new SalesSyncTaskCommand(
                ownerUserId,
                body.getLogicalStoreId(),
                body.getStoreCode(),
                body.getSiteCode(),
                parseDate(body.getDateFrom()),
                parseDate(body.getDateTo()),
                context.getSessionUserId(),
                "manual",
                listingCoverageMode(body)
        ));
    }

    @GetMapping("/sync-tasks/{taskId}")
    public SalesSyncTaskRecord getSalesSyncTask(
            @PathVariable Long taskId,
            HttpServletRequest request
    ) {
        SalesSyncTaskRecord task = syncTaskService.getTask(taskId);
        businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, task.getStoreCode());
        return task;
    }

    @GetMapping("/import-batches")
    public SalesImportBatchListView listImportBatches(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam String dateFrom,
            @RequestParam String dateTo,
            HttpServletRequest request
    ) {
        validateListRequest(storeCode, siteCode, dateFrom, dateTo);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                storeCode
        );
        Long ownerUserId = context.resolveOwnerUserIdForStore(storeCode);
        if (ownerUserId == null) {
            ownerUserId = context.getBusinessOwnerUserId();
        }
        return importQualityService.listBatches(new SalesImportBatchQuery(
                ownerUserId,
                storeCode,
                siteCode,
                parseDate(dateFrom),
                parseDate(dateTo)
        ));
    }

    @GetMapping("/import-batches/{batchId}")
    public SalesImportBatchDetail getImportBatch(
            @PathVariable Long batchId,
            HttpServletRequest request
    ) {
        SalesImportBatchDetail detail = importQualityService.getBatchDetail(batchId);
        businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                detail.getBatch().getStoreCode()
        );
        return detail;
    }

    private void validateImportRequest(SalesCsvImportRequest body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少销量导入请求。");
        }
        if (!StringUtils.hasText(body.getStoreCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少店铺编码。");
        }
        if (!StringUtils.hasText(body.getSiteCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少站点。");
        }
        if (!StringUtils.hasText(body.getCsv())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 Noon 销量 CSV 内容。");
        }
    }

    private void validateListRequest(String storeCode, String siteCode, String dateFrom, String dateTo) {
        if (!StringUtils.hasText(storeCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少店铺编码。");
        }
        if (!StringUtils.hasText(siteCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少站点。");
        }
        if (!StringUtils.hasText(dateFrom) || !StringUtils.hasText(dateTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少销量日期范围。");
        }
    }

    private void validateSyncRequest(SalesSyncTaskRequest body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少销量同步请求。");
        }
        if (!StringUtils.hasText(body.getStoreCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少店铺编码。");
        }
        if (!StringUtils.hasText(body.getSiteCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少站点。");
        }
        if (!StringUtils.hasText(body.getDateFrom()) || !StringUtils.hasText(body.getDateTo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少销量同步日期范围。");
        }
    }

    private void validateHistoryBackfillRequest(SalesHistoryBackfillRequest body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少历史补全请求。");
        }
        validateListRequest(body.getStoreCode(), body.getSiteCode(), body.getDateFrom(), body.getDateTo());
    }

    private void validateActivityWindowRequest(SalesActivityWindowRequest body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少活动窗口请求。");
        }
        validateListRequest(body.getStoreCode(), body.getSiteCode(), body.getDateFrom(), body.getDateTo());
        if (!StringUtils.hasText(body.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少活动名称。");
        }
        if (!StringUtils.hasText(body.getActivityType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少活动类型。");
        }
        if (body.getFactor() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少活动系数。");
        }
    }

    private SalesActivityWindowScope authorizedActivityScope(
            String storeCode,
            String siteCode,
            String dateFrom,
            String dateTo,
            HttpServletRequest request
    ) {
        validateListRequest(storeCode, siteCode, dateFrom, dateTo);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                storeCode
        );
        Long ownerUserId = context.resolveOwnerUserIdForStore(storeCode);
        if (ownerUserId == null) {
            ownerUserId = context.getBusinessOwnerUserId();
        }
        return new SalesActivityWindowScope(
                ownerUserId,
                storeCode,
                siteCode,
                parseDate(dateFrom),
                parseDate(dateTo)
        );
    }

    private SalesFactQuery authorizedSalesFactQuery(
            String storeCode,
            String siteCode,
            String dateFrom,
            String dateTo,
            String partnerSku,
            String sku,
            String search,
            String brand,
            String productFulltype,
            String dataQualityCode,
            String partnerSkuList,
            HttpServletRequest request
    ) {
        validateListRequest(storeCode, siteCode, dateFrom, dateTo);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                storeCode
        );
        Long ownerUserId = context.resolveOwnerUserIdForStore(storeCode);
        if (ownerUserId == null) {
            ownerUserId = context.getBusinessOwnerUserId();
        }
        return new SalesFactQuery(
                ownerUserId,
                storeCode,
                siteCode,
                parseDate(dateFrom),
                parseDate(dateTo),
                partnerSku,
                sku,
                search,
                brand,
                productFulltype,
                dataQualityCode,
                parsePartnerSkuList(partnerSkuList)
        );
    }

    private List<String> parsePartnerSkuList(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (String raw : value.split("[\\n\\r,，;；]+")) {
            String item = raw.trim();
            if (StringUtils.hasText(item) && !items.contains(item)) {
                items.add(item);
            }
        }
        return items;
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "销量日期格式必须为 yyyy-MM-dd。", exception);
        }
    }

    private SalesListingCoverageMode listingCoverageMode(SalesSyncTaskRequest body) {
        if (body == null || !StringUtils.hasText(body.getListingCoverageMode())) {
            return SalesListingCoverageMode.NONE;
        }
        try {
            return SalesListingCoverageMode.valueOf(body.getListingCoverageMode().trim());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知的销量覆盖确认模式。", exception);
        }
    }
}
