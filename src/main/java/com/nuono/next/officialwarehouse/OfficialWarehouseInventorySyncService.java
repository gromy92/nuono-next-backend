package com.nuono.next.officialwarehouse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseStatisticsMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider.InventoryItem;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider.InventoryPage;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider.PullRequest;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.InventorySyncCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySyncScopeRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.InventorySyncResultView;
import com.nuono.next.officialwarehouse.datapull.Dp07InventorySnapshotItem;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Manual compatibility entry; external reads stay outside the short replacement transaction. */
@Service
@Profile("local-db")
@ConditionalOnBean(OfficialWarehouseFbnInventoryProvider.class)
public class OfficialWarehouseInventorySyncService {

    private final OfficialWarehouseStatisticsMapper mapper;
    private final OfficialWarehouseFbnInventoryProvider provider;
    private final ObjectMapper objectMapper;
    private final OfficialWarehouseInventoryReplacement replacement;

    @Autowired
    public OfficialWarehouseInventorySyncService(
            OfficialWarehouseStatisticsMapper mapper,
            OfficialWarehouseFbnInventoryProvider provider,
            ObjectMapper objectMapper,
            OfficialWarehouseInventoryReplacement replacement
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.replacement = Objects.requireNonNull(replacement, "replacement");
    }

    /** Unit-test/backward-compatible constructor; production uses the proxied replacement bean. */
    OfficialWarehouseInventorySyncService(
            OfficialWarehouseStatisticsMapper mapper,
            OfficialWarehouseFbnInventoryProvider provider,
            ObjectMapper objectMapper
    ) {
        this(mapper, provider, objectMapper, new OfficialWarehouseInventoryReplacement(mapper, objectMapper));
    }

    /** Narrow seam for cross-package test doubles that override {@link #sync}. */
    protected OfficialWarehouseInventorySyncService() {
        this.mapper = null;
        this.provider = null;
        this.objectMapper = null;
        this.replacement = null;
    }

    public InventorySyncResultView sync(BusinessAccessContext access, InventorySyncCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("缺少官方仓库存同步参数。");
        }
        String storeCode = requireText(command.storeCode, "请选择要同步的店铺。");
        String siteCode = requireText(command.siteCode, "请选择要同步的站点。")
                .toUpperCase(Locale.ROOT);
        Long ownerUserId = requireOwnerUserId(access, storeCode);
        InventorySyncScopeRecord scope = mapper.selectInventorySyncScope(ownerUserId, storeCode, siteCode);
        if (scope == null || scope.logicalStoreId == null || !StringUtils.hasText(scope.projectCode)) {
            throw new IllegalArgumentException("无法识别官方仓库存同步店铺范围。");
        }

        List<InventoryPage> pages = fetchAllPages(ownerUserId, storeCode, siteCode);
        List<Dp07InventorySnapshotItem> items = new ArrayList<>();
        int skipped = 0;
        for (InventoryPage page : pages) {
            for (InventoryItem item : page.items) {
                java.util.Optional<Dp07InventorySnapshotItem> accepted =
                        Dp07InventorySnapshotItem.fromProvider(item, objectMapper);
                if (accepted.isPresent()) {
                    items.add(accepted.get());
                } else {
                    skipped += 1;
                }
            }
        }

        OfficialWarehouseInventoryReplacementResult result = replacement.replace(
                new OfficialWarehouseInventoryReplacementCommand(
                        ownerUserId,
                        scope.logicalStoreId,
                        scope.projectCode,
                        storeCode,
                        siteCode,
                        access.getSessionUserId(),
                        "manual-complete-inventory-sync",
                        pages.size(),
                        skipped,
                        items
                )
        );
        InventorySyncResultView view = new InventorySyncResultView();
        view.syncBatchId = String.valueOf(result.syncBatchId);
        view.storeCode = result.storeCode;
        view.siteCode = result.siteCode;
        view.pageCount = result.pageCount;
        view.fetchedRows = result.fetchedRows;
        view.insertedRows = result.insertedRows;
        view.sourceType = OfficialWarehouseInventoryReplacement.SOURCE_TYPE;
        view.syncedAt = result.syncedAt;
        return view;
    }

    private List<InventoryPage> fetchAllPages(
            Long ownerUserId,
            String storeCode,
            String siteCode
    ) {
        List<InventoryPage> pages = new ArrayList<>();
        int pageNo = 1;
        Integer declaredTotalPages = null;
        while (true) {
            InventoryPage page = Objects.requireNonNull(
                    provider.fetchPage(new PullRequest(ownerUserId, storeCode, siteCode), pageNo),
                    "inventory provider page"
            );
            requirePaginationEvidence(page, pageNo);
            if (page.totalPages != null) {
                if (declaredTotalPages != null
                        && !declaredTotalPages.equals(page.totalPages)) {
                    throw new IllegalStateException(
                            "official-warehouse inventory total pages changed during traversal"
                    );
                }
                declaredTotalPages = page.totalPages;
            }
            pages.add(page);
            if (!page.hasNextPage) {
                return List.copyOf(pages);
            }
            pageNo = Math.addExact(pageNo, 1);
        }
    }

    private void requirePaginationEvidence(InventoryPage page, int expectedPage) {
        if (page.page != expectedPage || page.items == null || page.rawResponse == null
                || (page.hasNextPageEvidence == null && page.totalPages == null)) {
            throw new IllegalStateException("official-warehouse inventory page is incomplete");
        }
        if (page.completeExport && (page.page != 1 || page.hasNextPage
                || !Objects.equals(page.totalPages, 1))) {
            throw new IllegalStateException("official-warehouse inventory export metadata conflicts");
        }
        if (page.totalPages != null && (page.totalPages < page.page
                || (page.hasNextPage && page.page >= page.totalPages)
                || (!page.hasNextPage && page.page < page.totalPages))) {
            throw new IllegalStateException("official-warehouse inventory pagination metadata conflicts");
        }
        if (page.hasNextPage && page.items.isEmpty()) {
            throw new IllegalStateException("official-warehouse inventory non-last page is empty");
        }
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
        return value.trim();
    }
}
