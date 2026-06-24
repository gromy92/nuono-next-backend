package com.nuono.next.procurement.aliorder;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

@RestController
@RequestMapping("/api/procurement/ali1688-orders")
public class Ali1688HistoricalOrderController {

    private final ObjectProvider<LocalDbAli1688HistoricalOrderService> serviceProvider;
    private final ObjectProvider<Ali1688HistoricalOrderOAuthService> oauthServiceProvider;
    private final BusinessAccessResolver accessResolver;

    public Ali1688HistoricalOrderController(
            ObjectProvider<LocalDbAli1688HistoricalOrderService> serviceProvider,
            ObjectProvider<Ali1688HistoricalOrderOAuthService> oauthServiceProvider,
            BusinessAccessResolver accessResolver
    ) {
        this.serviceProvider = serviceProvider;
        this.oauthServiceProvider = oauthServiceProvider;
        this.accessResolver = accessResolver;
    }

    @GetMapping("/workbench")
    public Ali1688HistoricalOrderWorkbenchView workbench(
            @RequestParam(value = "placedTimeFrom", required = false) String placedTimeFrom,
            @RequestParam(value = "placedTimeTo", required = false) String placedTimeTo,
            @RequestParam(value = "orderStatus", required = false) String orderStatus,
            @RequestParam(value = "supplierKeyword", required = false) String supplierKeyword,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "storeCode", required = false) String storeCode,
            @RequestParam(value = "siteCode", required = false) String siteCode,
            @RequestParam(value = "assignmentState", required = false) String assignmentState,
            @RequestParam(value = "assignmentTargetStoreCode", required = false) String assignmentTargetStoreCode,
            @RequestParam(value = "assignmentTargetSiteCode", required = false) String assignmentTargetSiteCode,
            @RequestParam(value = "productLinkState", required = false) String productLinkState,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorizedContext(request);
        return historicalOrderService().buildWorkbench(
                context,
                Ali1688HistoricalOrderQuery.fromRequest(
                        placedTimeFrom,
                        placedTimeTo,
                        orderStatus,
                        supplierKeyword,
                        keyword,
                        storeCode,
                        siteCode,
                        assignmentState,
                        assignmentTargetStoreCode,
                        assignmentTargetSiteCode,
                        productLinkState,
                        page,
                        pageSize
                )
        );
    }

    Ali1688HistoricalOrderWorkbenchView workbench(HttpServletRequest request) {
        BusinessAccessContext context = authorizedContext(request);
        return historicalOrderService().buildWorkbench(context);
    }

    @GetMapping("/excel-imports/sources")
    public List<Ali1688HistoricalOrderExcelImportView.SourceView> listExcelImportSources(
            @RequestParam(value = "storeCode", required = false) String storeCode,
            @RequestParam(value = "siteCode", required = false) String siteCode,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorizedContext(request);
        return historicalOrderService().listExcelUploadSources(context, storeCode, siteCode);
    }

    @PostMapping("/excel-imports/sources")
    public Ali1688HistoricalOrderExcelImportView.SourceView createExcelImportSource(
            @RequestBody Ali1688HistoricalOrderExcelImportView.SourceCreateRequest body,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireBoss(authorizedContext(request));
        return historicalOrderService().createExcelUploadSource(context, body);
    }

    @GetMapping("/excel-imports")
    public List<Ali1688HistoricalOrderExcelImportView.BatchView> listExcelImportBatches(
            @RequestParam("storeCode") String storeCode,
            @RequestParam(value = "siteCode", required = false) String siteCode,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorizedContext(request);
        return historicalOrderService().listExcelImportBatches(context, storeCode, siteCode);
    }

    @GetMapping("/excel-imports/{batchId}")
    public Ali1688HistoricalOrderExcelImportView.BatchDetailView excelImportBatchDetail(
            @PathVariable Long batchId,
            @RequestParam("storeCode") String storeCode,
            @RequestParam(value = "siteCode", required = false) String siteCode,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorizedContext(request);
        return historicalOrderService().excelImportBatchDetail(context, batchId, storeCode, siteCode);
    }

    @PostMapping(value = "/excel-imports/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Ali1688HistoricalOrderExcelImportView.PreviewView previewExcelImport(
            @RequestPart("file") MultipartFile file,
            @RequestParam("authorizationId") Long authorizationId,
            @RequestParam("storeCode") String storeCode,
            @RequestParam(value = "siteCode", required = false) String siteCode,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireBoss(authorizedContext(request));
        Ali1688HistoricalOrderExcelImportView.PreviewRequest previewRequest =
                new Ali1688HistoricalOrderExcelImportView.PreviewRequest();
        previewRequest.setAuthorizationId(authorizationId);
        previewRequest.setStoreCode(storeCode);
        previewRequest.setSiteCode(siteCode);
        return historicalOrderService().previewExcelImport(context, previewRequest, file);
    }

    @PostMapping("/excel-imports/{batchId}/commit")
    public Ali1688HistoricalOrderExcelImportView.CommitView commitExcelImport(
            @PathVariable Long batchId,
            @RequestParam("storeCode") String storeCode,
            @RequestParam(value = "siteCode", required = false) String siteCode,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireBoss(authorizedContext(request));
        Ali1688HistoricalOrderExcelImportView.CommitRequest commitRequest =
                new Ali1688HistoricalOrderExcelImportView.CommitRequest();
        commitRequest.setBatchId(batchId);
        commitRequest.setStoreCode(storeCode);
        commitRequest.setSiteCode(siteCode);
        return historicalOrderService().commitExcelImport(context, commitRequest);
    }

    @PostMapping("/authorizations/dev")
    public Ali1688HistoricalOrderWorkbenchView createDevAuthorization(HttpServletRequest request) {
        BusinessAccessContext context = requireBoss(authorizedContext(request));
        return historicalOrderService().createDevAuthorization(context);
    }

    @GetMapping("/authorizations/open-api/start")
    public Ali1688HistoricalOrderAuthorizationView.StartView startOpenApiAuthorization(
            @RequestParam(value = "storeCode", required = false) String storeCode,
            @RequestParam(value = "siteCode", required = false) String siteCode,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireBoss(authorizedContext(request));
        return oauthService().startAuthorization(context, storeCode, siteCode);
    }

    @GetMapping(value = "/authorizations/open-api/callback", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> openApiAuthorizationCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            HttpServletRequest request
    ) {
        if (error != null && !error.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(callbackHtml("1688 授权失败", error));
        }
        Ali1688HistoricalOrderAuthorizationView.CompleteView completed =
                oauthService().completeAuthorization(code, state);
        String message = completed.getMessage() + " 授权账号：" + completed.getProviderAccountId();
        return ResponseEntity.ok(callbackHtml("1688 授权已完成", message));
    }

    @PostMapping("/authorizations/{authorizationId}/revoke")
    public Ali1688HistoricalOrderWorkbenchView revokeAuthorization(
            @PathVariable Long authorizationId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireBoss(authorizedContext(request));
        return historicalOrderService().revokeAuthorization(context, authorizationId);
    }

    @PostMapping("/sync-tasks/initial-backfill")
    public Ali1688HistoricalOrderWorkbenchView runInitialBackfill(HttpServletRequest request) {
        BusinessAccessContext context = requireBoss(authorizedContext(request));
        return historicalOrderService().runInitialBackfill(context);
    }

    @PostMapping("/sync-tasks/manual-refresh")
    public Ali1688HistoricalOrderWorkbenchView runManualRefresh(HttpServletRequest request) {
        BusinessAccessContext context = requireRefreshOperator(authorizedContext(request));
        return historicalOrderService().runManualRefresh(context);
    }

    @GetMapping("/sku-purchase-history")
    public Ali1688SkuPurchaseHistoryView skuPurchaseHistory(
            @RequestParam("storeCode") String storeCode,
            @RequestParam(value = "siteCode", required = false) String siteCode,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "linkStatus", required = false) String linkStatus,
            @RequestParam(value = "purchaseTimeFrom", required = false) String purchaseTimeFrom,
            @RequestParam(value = "purchaseTimeTo", required = false) String purchaseTimeTo,
            @RequestParam(value = "purchaseCountMin", required = false) Integer purchaseCountMin,
            @RequestParam(value = "purchaseCountMax", required = false) Integer purchaseCountMax,
            @RequestParam(value = "priceAnomalyOnly", required = false) Boolean priceAnomalyOnly,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorizedContext(request);
        return historicalOrderService().listSkuPurchaseHistory(
                context,
                Ali1688SkuPurchaseHistoryQuery.fromRequest(
                        storeCode,
                        siteCode,
                        keyword,
                        linkStatus,
                        purchaseTimeFrom,
                        purchaseTimeTo,
                        purchaseCountMin,
                        purchaseCountMax,
                        priceAnomalyOnly,
                        page,
                        pageSize
                )
        );
    }

    @PostMapping("/sku-purchase-history/batches")
    public Ali1688SkuPurchaseBatchView.SaveResult saveSkuPurchaseBatches(
            @RequestBody Ali1688SkuPurchaseBatchView.SaveRequest body,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorizedContext(request);
        return historicalOrderService().saveSkuPurchaseBatches(context, body);
    }

    @PostMapping("/assignments")
    public Ali1688HistoricalOrderAssignmentView.AssignResult assignProductLines(
            @RequestBody Ali1688HistoricalOrderAssignmentView.AssignRequest body,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorizedContext(request);
        return historicalOrderService().assignProductLines(context, body);
    }

    @PostMapping("/assignments/batch")
    public Ali1688HistoricalOrderAssignmentView.AssignResult assignProductLineBatches(
            @RequestBody Ali1688HistoricalOrderAssignmentView.AssignBatchRequest body,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorizedContext(request);
        return historicalOrderService().assignProductLineBatches(context, body);
    }

    @GetMapping("/items/{itemId}/assignments")
    public List<Ali1688HistoricalOrderAssignmentView.RecordView> listProductLineAssignments(
            @PathVariable Long itemId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorizedContext(request);
        return historicalOrderService().listProductLineAssignments(context, itemId);
    }

    @PostMapping("/assignments/{assignmentId}/adjust")
    public Ali1688HistoricalOrderAssignmentView.AssignResult adjustAssignmentQuantity(
            @PathVariable Long assignmentId,
            @RequestBody Ali1688HistoricalOrderAssignmentView.AdjustRequest body,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorizedContext(request);
        return historicalOrderService().adjustAssignmentQuantity(context, assignmentId, body);
    }

    @PostMapping("/assignments/{assignmentId}/revoke")
    public Ali1688HistoricalOrderAssignmentView.AssignResult revokeAssignment(
            @PathVariable Long assignmentId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorizedContext(request);
        return historicalOrderService().revokeAssignment(context, assignmentId);
    }

    @PostMapping("/product-links")
    public Ali1688HistoricalOrderProductLinkView.LinkResult linkProductLine(
            @RequestBody Ali1688HistoricalOrderProductLinkView.LinkRequest body,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorizedContext(request);
        return historicalOrderService().linkProductLine(context, body);
    }

    @PostMapping("/product-links/batch")
    public Ali1688HistoricalOrderProductLinkView.LinkBatchResult linkProductLineBatch(
            @RequestBody Ali1688HistoricalOrderProductLinkView.LinkBatchRequest body,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorizedContext(request);
        return historicalOrderService().linkProductLineBatch(context, body);
    }

    @GetMapping("/product-link-candidates")
    public List<Ali1688HistoricalOrderProductLinkView.CandidateView> listProductLinkCandidates(
            @RequestParam("assignmentId") Long assignmentId,
            @RequestParam(value = "linkStatus", required = false) String linkStatus,
            @RequestParam(value = "keyword", required = false) String keyword,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorizedContext(request);
        return historicalOrderService().listProductLinkCandidates(context, assignmentId, linkStatus, keyword);
    }

    @PostMapping("/product-links/{assignmentId}/unlink")
    public Ali1688HistoricalOrderProductLinkView.LinkResult unlinkProductLine(
            @PathVariable Long assignmentId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorizedContext(request);
        return historicalOrderService().unlinkProductLine(context, assignmentId);
    }

    @GetMapping("/product-links/{assignmentId}/audits")
    public List<Ali1688HistoricalOrderProductLinkView.AuditView> listProductLinkAudits(
            @PathVariable Long assignmentId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorizedContext(request);
        return historicalOrderService().listProductLinkAudits(context, assignmentId);
    }

    @DeleteMapping("/{orderId}")
    public Ali1688HistoricalOrderCleanupView.DeleteOrderResult deleteHistoricalOrder(
            @PathVariable Long orderId,
            @RequestBody(required = false) Ali1688HistoricalOrderCleanupView.DeleteOrderRequest body,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireBoss(authorizedContext(request));
        return historicalOrderService().deleteHistoricalOrder(context, orderId, body);
    }

    @GetMapping("/{orderId}")
    public Ali1688HistoricalOrderWorkbenchView.OrderDetailView orderDetail(
            @PathVariable Long orderId,
            @RequestParam(value = "storeCode", required = false) String storeCode,
            @RequestParam(value = "siteCode", required = false) String siteCode,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorizedContext(request);
        return historicalOrderService().orderDetail(
                context,
                orderId,
                Ali1688HistoricalOrderQuery.fromRequest(null, null, null, null, null, storeCode, siteCode, null, null)
        );
    }

    private LocalDbAli1688HistoricalOrderService historicalOrderService() {
        LocalDbAli1688HistoricalOrderService service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "1688 历史订单服务未启用。");
        }
        return service;
    }

    private Ali1688HistoricalOrderOAuthService oauthService() {
        Ali1688HistoricalOrderOAuthService service = oauthServiceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "1688 OpenAPI 授权服务未启用。");
        }
        return service;
    }

    private String callbackHtml(String title, String message) {
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>"
                + HtmlUtils.htmlEscape(title)
                + "</title></head><body><h1>"
                + HtmlUtils.htmlEscape(title)
                + "</h1><p>"
                + HtmlUtils.htmlEscape(message)
                + "</p></body></html>";
    }

    private BusinessAccessContext authorizedContext(HttpServletRequest request) {
        return accessResolver.requireBusinessContext(
                request,
                BusinessCapability.ALI1688_HISTORICAL_ORDERS
        );
    }

    private BusinessAccessContext requireBoss(BusinessAccessContext context) {
        if (context == null || !context.isBossAccount()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有老板可以变更 1688 历史订单授权。");
        }
        return context;
    }

    private BusinessAccessContext requireRefreshOperator(BusinessAccessContext context) {
        if (context == null || !Ali1688HistoricalOrderPermission.canTriggerSync(context)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不能刷新 1688 历史订单。");
        }
        return context;
    }
}
