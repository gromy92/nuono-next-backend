package com.nuono.next.salesforecast;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/sales-forecast")
public class SalesForecastController {

    private final SalesForecastService forecastService;
    private final BusinessAccessResolver businessAccessResolver;

    public SalesForecastController(
            SalesForecastService forecastService,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.forecastService = forecastService;
        this.businessAccessResolver = businessAccessResolver;
    }

    @GetMapping("/overview")
    public SalesForecastOverviewView getOverview(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            HttpServletRequest request
    ) {
        validateOverviewRequest(storeCode, siteCode);
        return forecastService.getOverview(authorizedQuery(storeCode, siteCode, request));
    }

    @PostMapping("/follow-ups")
    public SalesForecastFollowUpView setFollowUp(
            @RequestBody SalesForecastFollowUpRequest body,
            HttpServletRequest request
    ) {
        validateFollowUpRequest(body);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                body.getStoreCode()
        );
        Long ownerUserId = context.resolveOwnerUserIdForStore(body.getStoreCode());
        if (ownerUserId == null) {
            ownerUserId = context.getBusinessOwnerUserId();
        }
        return forecastService.setFollowUp(new SalesForecastFollowUpCommand(
                ownerUserId,
                body.getStoreCode(),
                body.getSiteCode(),
                body.getPartnerSku(),
                body.getSku(),
                body.isMarked(),
                context.getSessionUserId()
        ));
    }

    @PostMapping("/recalculate")
    public SalesForecastRunStatusView recalculate(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            HttpServletRequest request
    ) {
        validateOverviewRequest(storeCode, siteCode);
        return forecastService.recalculate(authorizedQuery(storeCode, siteCode, request));
    }

    @GetMapping("/export")
    public ResponseEntity<String> export(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam(defaultValue = "30") int forecastWindow,
            @RequestParam(required = false) String searchKeyword,
            @RequestParam(defaultValue = "all") String lifecycleFilter,
            @RequestParam(defaultValue = "all") String riskFilter,
            @RequestParam(defaultValue = "all") String confidenceFilter,
            HttpServletRequest request
    ) {
        validateOverviewRequest(storeCode, siteCode);
        SalesForecastExportView export = forecastService.exportCsv(
                authorizedQuery(storeCode, siteCode, request),
                new SalesForecastExportQuery(
                        forecastWindow,
                        searchKeyword,
                        lifecycleFilter,
                        riskFilter,
                        confidenceFilter
                )
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, export.getContentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + export.getFilename() + "\"")
                .body(export.getContent());
    }

    private SalesForecastQuery authorizedQuery(String storeCode, String siteCode, HttpServletRequest request) {
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                storeCode
        );
        Long ownerUserId = context.resolveOwnerUserIdForStore(storeCode);
        if (ownerUserId == null) {
            ownerUserId = context.getBusinessOwnerUserId();
        }
        return new SalesForecastQuery(ownerUserId, storeCode, siteCode);
    }

    private void validateOverviewRequest(String storeCode, String siteCode) {
        if (!StringUtils.hasText(storeCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少店铺编码。");
        }
        if (!StringUtils.hasText(siteCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少站点。");
        }
    }

    private void validateFollowUpRequest(SalesForecastFollowUpRequest body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少重点跟进请求。");
        }
        validateOverviewRequest(body.getStoreCode(), body.getSiteCode());
        if (!StringUtils.hasText(body.getPartnerSku())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 Partner SKU。");
        }
        if (!StringUtils.hasText(body.getSku())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 SKU。");
        }
    }
}
