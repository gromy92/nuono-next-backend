package com.nuono.next.officialwarehouse;

import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider.ExportItem;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider.ExportListPage;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider.ExportStatus;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider.PullRequest;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.FbnExportCreateCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.FbnExportCreateView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.FbnExportItemView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.FbnExportListView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.FbnExportStatusView;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
@ConditionalOnBean(OfficialWarehouseFbnExportProvider.class)
public class OfficialWarehouseFbnExportQueryService {

    private static final String SOURCE_TYPE = "FBN_REPORT_EXPORT_API";
    private static final Set<String> SUPPORTED_CREATE_REPORT_TYPES = Set.of(
            "fbn_inbound_fbnreceivedreport",
            "fbn_inbound_scheduleddeliveryaccuracy"
    );

    private final OfficialWarehouseFbnExportProvider provider;

    public OfficialWarehouseFbnExportQueryService(OfficialWarehouseFbnExportProvider provider) {
        this.provider = provider;
    }

    public FbnExportListView listExports(
            BusinessAccessContext access,
            String storeCode,
            String siteCode,
            Integer page,
            Integer perPage
    ) {
        String safeStoreCode = requireText(storeCode, "请选择要查询 FBN 报表的店铺。");
        String safeSiteCode = requireText(siteCode, "请选择要查询 FBN 报表的站点。").toUpperCase(Locale.ROOT);
        int safePage = page == null || page <= 0 ? 1 : page;
        int safePerPage = perPage == null || perPage <= 0 ? 20 : Math.min(perPage, 100);
        ExportListPage result = provider.listExports(
                new PullRequest(requireOwnerUserId(access, safeStoreCode), safeStoreCode, safeSiteCode),
                safePage,
                safePerPage
        );
        FbnExportListView view = new FbnExportListView();
        view.storeCode = safeStoreCode;
        view.siteCode = safeSiteCode;
        view.page = result.page;
        view.perPage = result.perPage;
        view.hasNextPage = result.hasNextPage;
        view.sourceType = SOURCE_TYPE;
        for (ExportItem item : result.items) {
            view.items.add(toItemView(item));
        }
        return view;
    }

    public FbnExportStatusView exportStatus(
            BusinessAccessContext access,
            String storeCode,
            String siteCode,
            String exportCode,
            Boolean log
    ) {
        String safeStoreCode = requireText(storeCode, "请选择要查询 FBN 报表的店铺。");
        String safeSiteCode = requireText(siteCode, "请选择要查询 FBN 报表的站点。").toUpperCase(Locale.ROOT);
        String safeExportCode = requireText(exportCode, "缺少 FBN 报表 exportCode。");
        ExportStatus result = provider.exportStatus(
                new PullRequest(requireOwnerUserId(access, safeStoreCode), safeStoreCode, safeSiteCode),
                safeExportCode,
                Boolean.TRUE.equals(log)
        );
        FbnExportStatusView view = new FbnExportStatusView();
        view.storeCode = safeStoreCode;
        view.siteCode = safeSiteCode;
        view.exportCode = result.exportCode;
        view.status = result.status;
        view.fileName = result.fileName;
        view.downloadUrl = result.downloadUrl;
        view.message = result.message;
        view.totalRows = result.totalRows;
        view.sourceType = SOURCE_TYPE;
        return view;
    }

    public FbnExportCreateView createExport(BusinessAccessContext access, FbnExportCreateCommand command) {
        FbnExportCreateCommand safeCommand = command == null ? new FbnExportCreateCommand() : command;
        String safeStoreCode = requireText(safeCommand.storeCode, "请选择要创建 FBN 报表的店铺。");
        String safeSiteCode = requireText(safeCommand.siteCode, "请选择要创建 FBN 报表的站点。").toUpperCase(Locale.ROOT);
        String reportType = requireText(safeCommand.exportCategoryCode, "请选择要创建的 FBN 报表类型。")
                .toLowerCase(Locale.ROOT);
        if (!SUPPORTED_CREATE_REPORT_TYPES.contains(reportType)) {
            throw new IllegalArgumentException("当前只支持创建 FBN 入仓明细和预约到货准确率报表。");
        }
        String fromDate = requireIsoDate(safeCommand.fromDate, "请选择 FBN 报表开始日期。");
        String toDate = requireIsoDate(safeCommand.toDate, "请选择 FBN 报表结束日期。");
        if (LocalDate.parse(toDate).isBefore(LocalDate.parse(fromDate))) {
            throw new IllegalArgumentException("FBN 报表结束日期不能早于开始日期。");
        }

        OfficialWarehouseFbnExportProvider.CreateExportResult result = provider.createExport(
                new PullRequest(requireOwnerUserId(access, safeStoreCode), safeStoreCode, safeSiteCode),
                new OfficialWarehouseFbnExportProvider.CreateExportRequest(reportType, fromDate, toDate)
        );
        FbnExportCreateView view = new FbnExportCreateView();
        view.storeCode = safeStoreCode;
        view.siteCode = safeSiteCode;
        view.exportCode = result.exportCode;
        view.status = result.status;
        view.reportType = result.reportType;
        view.fromDate = fromDate;
        view.toDate = toDate;
        view.sourceType = SOURCE_TYPE;
        return view;
    }

    private FbnExportItemView toItemView(ExportItem item) {
        FbnExportItemView view = new FbnExportItemView();
        view.exportCode = item.exportCode;
        view.status = item.status;
        view.reportType = item.reportType;
        view.fileName = item.fileName;
        view.createdAt = item.createdAt;
        view.downloadUrl = item.downloadUrl;
        return view;
    }

    private Long requireOwnerUserId(BusinessAccessContext access, String storeCode) {
        if (access == null) {
            throw new IllegalArgumentException("缺少业务访问上下文。");
        }
        Long ownerUserId = access.resolveOwnerUserIdForStore(storeCode);
        if (ownerUserId == null) {
            ownerUserId = access.getBusinessOwnerUserId();
        }
        if (ownerUserId == null) {
            throw new IllegalArgumentException("无法识别当前业务老板账号。");
        }
        return ownerUserId;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        String trimmed = value.trim();
        if (!StringUtils.hasText(trimmed)) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private String requireIsoDate(String value, String message) {
        String trimmed = requireText(value, message);
        try {
            return LocalDate.parse(trimmed).toString();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(message);
        }
    }
}
