package com.nuono.next.officialwarehouse;

import static com.nuono.next.officialwarehouse.OfficialWarehouseFbnImportSupport.requireOwnerUserId;
import static com.nuono.next.officialwarehouse.OfficialWarehouseFbnImportSupport.requireText;
import static com.nuono.next.officialwarehouse.OfficialWarehouseFbnImportSupport.sha256;

import com.nuono.next.infrastructure.mapper.OfficialWarehouseStatisticsMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnReceivedReportCsvParser.ParsedFile;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.FbnReceivedImportCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySyncScopeRecord;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.Locale;

/** Validates a durable DP-07-B artifact before the legacy fact transaction starts. */
final class OfficialWarehouseFbnDownloadedReportPreparer {
    private final OfficialWarehouseStatisticsMapper mapper;
    private final OfficialWarehouseFbnReceivedReportCsvParser parser;

    OfficialWarehouseFbnDownloadedReportPreparer(
            OfficialWarehouseStatisticsMapper mapper,
            OfficialWarehouseFbnReceivedReportCsvParser parser
    ) {
        this.mapper = mapper;
        this.parser = parser;
    }

    Prepared prepare(
            BusinessAccessContext access,
            String exportCode,
            FbnReceivedImportCommand command,
            byte[] content,
            String fileName,
            String expectedSha256
    ) {
        FbnReceivedImportCommand safeCommand = command == null ? new FbnReceivedImportCommand() : command;
        String storeCode = requireText(safeCommand.storeCode, "请选择要导入 FBN 入仓报表的店铺。");
        String siteCode = requireText(safeCommand.siteCode, "请选择要导入 FBN 入仓报表的站点。")
                .toUpperCase(Locale.ROOT);
        String safeExportCode = requireText(exportCode, "缺少 FBN 报表 exportCode。");
        byte[] safeContent = content == null ? new byte[0] : content.clone();
        if (!sha256(safeContent).equals(requireText(expectedSha256, "缺少 FBN 报表摘要。"))) {
            throw new IllegalArgumentException("FBN 报表摘要校验失败。");
        }
        Long ownerUserId = requireOwnerUserId(access, storeCode);
        Long operatorUserId = access.getSessionUserId() == null ? ownerUserId : access.getSessionUserId();
        InventorySyncScopeRecord scope = mapper.selectInventorySyncScope(ownerUserId, storeCode, siteCode);
        if (scope == null) {
            throw new IllegalArgumentException("当前店铺未配置官方仓统计范围。");
        }
        ParsedFile parsedFile = parser.parse(safeContent);
        return new Prepared(
                parsedFile,
                safeContent,
                scope,
                ownerUserId,
                operatorUserId,
                storeCode,
                siteCode,
                safeExportCode,
                requireText(fileName, "缺少 FBN 报表文件名。")
        );
    }

    static final class Prepared {
        final ParsedFile parsedFile;
        final byte[] content;
        final InventorySyncScopeRecord scope;
        final Long ownerUserId;
        final Long operatorUserId;
        final String storeCode;
        final String siteCode;
        final String exportCode;
        final String fileName;

        private Prepared(
                ParsedFile parsedFile,
                byte[] content,
                InventorySyncScopeRecord scope,
                Long ownerUserId,
                Long operatorUserId,
                String storeCode,
                String siteCode,
                String exportCode,
                String fileName
        ) {
            this.parsedFile = parsedFile;
            this.content = content;
            this.scope = scope;
            this.ownerUserId = ownerUserId;
            this.operatorUserId = operatorUserId;
            this.storeCode = storeCode;
            this.siteCode = siteCode;
            this.exportCode = exportCode;
            this.fileName = fileName;
        }
    }
}
