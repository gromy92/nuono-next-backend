package com.nuono.next.noonads;

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
@RequestMapping("/api/noon-ads")
public class NoonAdvertisingController {
    private final NoonAdvertisingAnalyticsService analyticsService;
    private final NoonAdvertisingImportService importService;
    private final BusinessAccessResolver businessAccessResolver;

    public NoonAdvertisingController(
            NoonAdvertisingAnalyticsService analyticsService,
            NoonAdvertisingImportService importService,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.analyticsService = analyticsService;
        this.importService = importService;
        this.businessAccessResolver = businessAccessResolver;
    }

    @GetMapping("/dashboard")
    public NoonAdvertisingDashboardView dashboard(
            @RequestParam String projectCode,
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam String dateFrom,
            @RequestParam String dateTo,
            HttpServletRequest request
    ) {
        validateDashboardRequest(projectCode, storeCode, siteCode, dateFrom, dateTo);
        String normalizedProjectCode = projectCode.trim();
        String normalizedStoreCode = storeCode.trim();
        String normalizedSiteCode = siteCode.trim();
        LocalDate from = parseDate(dateFrom, "dateFrom");
        LocalDate to = parseDate(dateTo, "dateTo");
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateTo must be on or after dateFrom.");
        }

        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                normalizedStoreCode
        );
        Long ownerUserId = ownerUserId(context, normalizedStoreCode);
        return analyticsService.dashboard(new NoonAdvertisingDashboardQuery(
                ownerUserId,
                normalizedProjectCode,
                normalizedStoreCode,
                normalizedSiteCode,
                from,
                to
        ));
    }

    @GetMapping("/report-windows/latest")
    public NoonAdvertisingLatestReportWindowView latestReportWindow(
            @RequestParam String projectCode,
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            HttpServletRequest request
    ) {
        validateScopeRequest(projectCode, storeCode, siteCode);
        String normalizedProjectCode = projectCode.trim();
        String normalizedStoreCode = storeCode.trim();
        String normalizedSiteCode = siteCode.trim();

        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                normalizedStoreCode
        );
        Long ownerUserId = ownerUserId(context, normalizedStoreCode);
        return analyticsService.latestReportWindow(new NoonAdvertisingScopeQuery(
                ownerUserId,
                normalizedProjectCode,
                normalizedStoreCode,
                normalizedSiteCode
        ));
    }

    @PostMapping("/reports/import")
    public NoonAdvertisingImportResult importReport(
            @RequestBody NoonAdvertisingReportImportRequest body,
            HttpServletRequest request
    ) {
        validateImportRequest(body);
        String normalizedProjectCode = body.getProjectCode().trim();
        String normalizedStoreCode = body.getStoreCode().trim();
        String normalizedSiteCode = body.getSiteCode().trim();
        LocalDate from = parseDate(body.getDateFrom(), "dateFrom");
        LocalDate to = parseDate(body.getDateTo(), "dateTo");
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateTo must be on or after dateFrom.");
        }

        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                normalizedStoreCode
        );
        Long ownerUserId = ownerUserId(context, normalizedStoreCode);
        return importService.importReport(new NoonAdvertisingReportImportCommand(
                ownerUserId,
                context.getSessionUserId(),
                normalizedProjectCode,
                normalizedStoreCode,
                normalizedSiteCode,
                from,
                to,
                body.getSourceName(),
                body.getSourceDigestSha256(),
                body.getNotes(),
                body.getCampaignRows(),
                body.getQueryRows()
        ));
    }

    private void validateDashboardRequest(
            String projectCode,
            String storeCode,
            String siteCode,
            String dateFrom,
            String dateTo
    ) {
        validateScopeRequest(projectCode, storeCode, siteCode);
        if (!StringUtils.hasText(dateFrom)
                || !StringUtils.hasText(dateTo)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "projectCode, storeCode, siteCode, dateFrom and dateTo are required."
            );
        }
    }

    private void validateScopeRequest(String projectCode, String storeCode, String siteCode) {
        if (!StringUtils.hasText(projectCode)
                || !StringUtils.hasText(storeCode)
                || !StringUtils.hasText(siteCode)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "projectCode, storeCode and siteCode are required."
            );
        }
    }

    private void validateImportRequest(NoonAdvertisingReportImportRequest body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
        }
        validateDashboardRequest(
                body.getProjectCode(),
                body.getStoreCode(),
                body.getSiteCode(),
                body.getDateFrom(),
                body.getDateTo()
        );
    }

    private LocalDate parseDate(String value, String fieldName) {
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + " must be an ISO date.",
                    exception
            );
        }
    }

    private Long ownerUserId(BusinessAccessContext context, String storeCode) {
        Long ownerUserId = context.resolveOwnerUserIdForStore(storeCode);
        return ownerUserId == null ? context.getBusinessOwnerUserId() : ownerUserId;
    }
}
