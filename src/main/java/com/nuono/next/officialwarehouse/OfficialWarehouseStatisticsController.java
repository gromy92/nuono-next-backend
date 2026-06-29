package com.nuono.next.officialwarehouse;

import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.StockCorrectionCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.FbnExportCreateCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.FbnReceivedImportCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.ScheduledDeliveryAccuracyMissingAsnSyncCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.ScheduledDeliveryAccuracyRematchCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.FbnExportCreateView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.FbnExportListView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.FbnExportStatusView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.InventorySyncCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.ScheduledDeliveryAccuracyImportCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.FbnReceivedImportResultView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.InboundStatisticsView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.InventorySyncResultView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.ProductInboundHistoryView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.ScheduledDeliveryAccuracyMissingAsnSyncResultView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.ScheduledDeliveryAccuracyRematchResultView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.ScheduledDeliveryAccuracyImportResultView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.StockStatisticsQuery;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.StockStatisticsRowView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.StockStatisticsView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
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
@RequestMapping("/api/warehouse/official-warehouse")
public class OfficialWarehouseStatisticsController {

    private final ObjectProvider<LocalDbOfficialWarehouseStatisticsService> serviceProvider;
    private final ObjectProvider<OfficialWarehouseInventorySyncService> inventorySyncServiceProvider;
    private final ObjectProvider<OfficialWarehouseFbnExportQueryService> fbnExportQueryServiceProvider;
    private final ObjectProvider<OfficialWarehouseFbnReceivedReportImportService> fbnReceivedReportImportServiceProvider;
    private final ObjectProvider<OfficialWarehouseScheduledDeliveryAccuracyImportService> scheduledDeliveryAccuracyImportServiceProvider;
    private final ObjectProvider<OfficialWarehouseScheduledDeliveryAccuracyAsnSyncService> scheduledDeliveryAccuracyAsnSyncServiceProvider;
    private final BusinessAccessResolver accessResolver;

    public OfficialWarehouseStatisticsController(
            ObjectProvider<LocalDbOfficialWarehouseStatisticsService> serviceProvider,
            ObjectProvider<OfficialWarehouseInventorySyncService> inventorySyncServiceProvider,
            ObjectProvider<OfficialWarehouseFbnExportQueryService> fbnExportQueryServiceProvider,
            ObjectProvider<OfficialWarehouseFbnReceivedReportImportService> fbnReceivedReportImportServiceProvider,
            ObjectProvider<OfficialWarehouseScheduledDeliveryAccuracyImportService> scheduledDeliveryAccuracyImportServiceProvider,
            ObjectProvider<OfficialWarehouseScheduledDeliveryAccuracyAsnSyncService> scheduledDeliveryAccuracyAsnSyncServiceProvider,
            BusinessAccessResolver accessResolver
    ) {
        this.serviceProvider = serviceProvider;
        this.inventorySyncServiceProvider = inventorySyncServiceProvider;
        this.fbnExportQueryServiceProvider = fbnExportQueryServiceProvider;
        this.fbnReceivedReportImportServiceProvider = fbnReceivedReportImportServiceProvider;
        this.scheduledDeliveryAccuracyImportServiceProvider = scheduledDeliveryAccuracyImportServiceProvider;
        this.scheduledDeliveryAccuracyAsnSyncServiceProvider = scheduledDeliveryAccuracyAsnSyncServiceProvider;
        this.accessResolver = accessResolver;
    }

    @GetMapping("/stock-statistics")
    public StockStatisticsView stockStatistics(
            @RequestParam(required = false) String storeCode,
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String stockBucket,
            HttpServletRequest request
    ) {
        try {
            StockStatisticsQuery query = new StockStatisticsQuery();
            query.storeCode = storeCode;
            query.siteCode = siteCode;
            query.keyword = keyword;
            query.warehouseCode = warehouseCode;
            query.stockBucket = stockBucket;
            return service().stockStatistics(access(request, storeCode), query);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/inbound-statistics")
    public InboundStatisticsView inboundStatistics(
            @RequestParam(required = false) String storeCode,
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String asn,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String receiptStatus,
            HttpServletRequest request
    ) {
        try {
            return service().inboundStatistics(
                    access(request, storeCode),
                    storeCode,
                    siteCode,
                    keyword,
                    asn,
                    warehouseCode,
                    receiptStatus
            );
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/products/{productSiteOfferId}/inbound-history")
    public ProductInboundHistoryView productInboundHistory(
            @PathVariable String productSiteOfferId,
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            HttpServletRequest request
    ) {
        try {
            return service().productInboundHistory(access(request, storeCode), storeCode, siteCode, productSiteOfferId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/stock-corrections")
    public StockStatisticsRowView correctStock(
            @RequestBody StockCorrectionCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().correctStock(access(request, command == null ? null : command.storeCode), command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/stock-statistics/sync")
    public InventorySyncResultView syncInventory(
            @RequestBody InventorySyncCommand command,
            HttpServletRequest request
    ) {
        try {
            return inventorySyncService().sync(access(request, command == null ? null : command.storeCode), command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/fbn-report-exports")
    public FbnExportListView listFbnReportExports(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer perPage,
            HttpServletRequest request
    ) {
        try {
            return fbnExportQueryService().listExports(access(request, storeCode), storeCode, siteCode, page, perPage);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/fbn-report-exports/create")
    public FbnExportCreateView createFbnReportExport(
            @RequestBody FbnExportCreateCommand command,
            HttpServletRequest request
    ) {
        try {
            return fbnExportQueryService().createExport(
                    access(request, command == null ? null : command.storeCode),
                    command
            );
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/fbn-report-exports/{exportCode}/status")
    public FbnExportStatusView fbnReportExportStatus(
            @PathVariable String exportCode,
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam(required = false) Boolean log,
            HttpServletRequest request
    ) {
        try {
            return fbnExportQueryService().exportStatus(
                    access(request, storeCode),
                    storeCode,
                    siteCode,
                    exportCode,
                    log
            );
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/fbn-report-exports/{exportCode}/import-fbn-received")
    public FbnReceivedImportResultView importFbnReceivedReport(
            @PathVariable String exportCode,
            @RequestBody FbnReceivedImportCommand command,
            HttpServletRequest request
    ) {
        try {
            return fbnReceivedReportImportService().importByExportCode(
                    access(request, command == null ? null : command.storeCode),
                    exportCode,
                    command
            );
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/fbn-report-exports/{exportCode}/import-scheduled-delivery-accuracy")
    public ScheduledDeliveryAccuracyImportResultView importScheduledDeliveryAccuracyReport(
            @PathVariable String exportCode,
            @RequestBody ScheduledDeliveryAccuracyImportCommand command,
            HttpServletRequest request
    ) {
        try {
            return scheduledDeliveryAccuracyImportService().importByExportCode(
                    access(request, command == null ? null : command.storeCode),
                    exportCode,
                    command
            );
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/fbn-report-imports/{importId}/rematch-scheduled-delivery-accuracy")
    public ScheduledDeliveryAccuracyRematchResultView rematchScheduledDeliveryAccuracy(
            @PathVariable String importId,
            @RequestBody ScheduledDeliveryAccuracyRematchCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().rematchScheduledDeliveryAccuracy(
                    access(request, command == null ? null : command.storeCode),
                    importId,
                    command
            );
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/fbn-report-imports/{importId}/sync-missing-asns")
    public ScheduledDeliveryAccuracyMissingAsnSyncResultView syncMissingScheduledDeliveryAccuracyAsns(
            @PathVariable String importId,
            @RequestBody ScheduledDeliveryAccuracyMissingAsnSyncCommand command,
            HttpServletRequest request
    ) {
        try {
            return scheduledDeliveryAccuracyAsnSyncService().syncMissingAsns(
                    access(request, command == null ? null : command.storeCode),
                    importId,
                    command
            );
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    private BusinessAccessContext access(HttpServletRequest request, String storeCode) {
        if (StringUtils.hasText(storeCode)) {
            return accessResolver.requireStoreAccess(request, BusinessCapability.OFFICIAL_WAREHOUSE, storeCode);
        }
        return accessResolver.requireBusinessContext(request, BusinessCapability.OFFICIAL_WAREHOUSE);
    }

    private LocalDbOfficialWarehouseStatisticsService service() {
        LocalDbOfficialWarehouseStatisticsService service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Noon 官方仓统计服务未启用。");
        }
        return service;
    }

    private OfficialWarehouseInventorySyncService inventorySyncService() {
        OfficialWarehouseInventorySyncService service = inventorySyncServiceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Noon 官方仓库存同步服务未启用。");
        }
        return service;
    }

    private OfficialWarehouseFbnExportQueryService fbnExportQueryService() {
        OfficialWarehouseFbnExportQueryService service = fbnExportQueryServiceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Noon 官方仓 FBN 报表查询服务未启用。");
        }
        return service;
    }

    private OfficialWarehouseFbnReceivedReportImportService fbnReceivedReportImportService() {
        OfficialWarehouseFbnReceivedReportImportService service = fbnReceivedReportImportServiceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Noon 官方仓 FBN 入仓报表导入服务未启用。");
        }
        return service;
    }

    private OfficialWarehouseScheduledDeliveryAccuracyImportService scheduledDeliveryAccuracyImportService() {
        OfficialWarehouseScheduledDeliveryAccuracyImportService service =
                scheduledDeliveryAccuracyImportServiceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Noon 官方仓预约到货准确率报表导入服务未启用。");
        }
        return service;
    }

    private OfficialWarehouseScheduledDeliveryAccuracyAsnSyncService scheduledDeliveryAccuracyAsnSyncService() {
        OfficialWarehouseScheduledDeliveryAccuracyAsnSyncService service =
                scheduledDeliveryAccuracyAsnSyncServiceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Noon 官方仓历史 ASN 同步服务未启用。");
        }
        return service;
    }

    private ResponseStatusException badRequest(IllegalArgumentException exception) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }
}
