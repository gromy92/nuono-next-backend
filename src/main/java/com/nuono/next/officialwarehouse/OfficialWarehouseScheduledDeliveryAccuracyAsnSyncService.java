package com.nuono.next.officialwarehouse;

import com.nuono.next.infrastructure.mapper.OfficialWarehouseStatisticsMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.ScheduledDeliveryAccuracyMissingAsnSyncCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.ScheduledDeliveryAccuracyRematchCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.ScheduledDeliveryAccuracyMissingAsnSyncResultView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AsnListSyncView;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class OfficialWarehouseScheduledDeliveryAccuracyAsnSyncService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final OfficialWarehouseStatisticsMapper mapper;
    private final OfficialWarehouseAsnNumberSyncer asnNumberSyncer;
    private final LocalDbOfficialWarehouseStatisticsService statisticsService;

    public OfficialWarehouseScheduledDeliveryAccuracyAsnSyncService(
            OfficialWarehouseStatisticsMapper mapper,
            OfficialWarehouseAsnNumberSyncer asnNumberSyncer,
            LocalDbOfficialWarehouseStatisticsService statisticsService
    ) {
        this.mapper = mapper;
        this.asnNumberSyncer = asnNumberSyncer;
        this.statisticsService = statisticsService;
    }

    public ScheduledDeliveryAccuracyMissingAsnSyncResultView syncMissingAsns(
            BusinessAccessContext access,
            String importId,
            ScheduledDeliveryAccuracyMissingAsnSyncCommand command
    ) {
        ScheduledDeliveryAccuracyMissingAsnSyncCommand safeCommand =
                command == null ? new ScheduledDeliveryAccuracyMissingAsnSyncCommand() : command;
        String storeCode = requireText(safeCommand.storeCode, "请选择要同步历史 ASN 的店铺。");
        String siteCode = requireText(safeCommand.siteCode, "请选择要同步历史 ASN 的站点。")
                .toUpperCase(Locale.ROOT);
        Long parsedImportId = parseLong(requireText(importId, "缺少报表导入批次 ID。"), "报表导入批次 ID");
        Long ownerUserId = requireOwnerUserId(access, storeCode);
        boolean dryRun = safeCommand.dryRun == null || safeCommand.dryRun;
        int limit = normalizeLimit(safeCommand.limit);

        List<String> asnNumbers = mapper.listMissingDeliveryAccuracyNoonAsnNumbers(
                ownerUserId,
                storeCode,
                siteCode,
                parsedImportId,
                limit
        );
        AsnListSyncView sync = asnNumbers.isEmpty()
                ? new AsnListSyncView()
                : asnNumberSyncer.syncNoonAsnNumbers(access, storeCode, siteCode, asnNumbers, dryRun);

        ScheduledDeliveryAccuracyMissingAsnSyncResultView result =
                new ScheduledDeliveryAccuracyMissingAsnSyncResultView();
        result.importId = String.valueOf(parsedImportId);
        result.storeCode = storeCode;
        result.siteCode = siteCode;
        result.dryRun = dryRun;
        result.missingAsnCount = asnNumbers.size();
        result.requestedAsnCount = asnNumbers.size();
        result.foundAsnCount = Math.max(0, sync.fetched);
        result.notFoundAsnCount = Math.max(0, result.requestedAsnCount - result.foundAsnCount);
        result.created = Math.max(0, sync.created);
        result.updated = Math.max(0, sync.updated);
        result.scheduled = Math.max(0, sync.scheduled);
        result.corrected = Math.max(0, sync.corrected);
        result.failed = Math.max(0, sync.failed);
        result.skipped = Math.max(0, sync.skipped);

        if (!dryRun && Boolean.TRUE.equals(safeCommand.rematchAfterSync)) {
            ScheduledDeliveryAccuracyRematchCommand rematchCommand = new ScheduledDeliveryAccuracyRematchCommand();
            rematchCommand.storeCode = storeCode;
            rematchCommand.siteCode = siteCode;
            result.rematch = statisticsService.rematchScheduledDeliveryAccuracy(
                    access,
                    String.valueOf(parsedImportId),
                    rematchCommand
            );
        }
        return result;
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

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private Long parseLong(String value, String fieldName) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldName + "格式不正确。");
        }
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
