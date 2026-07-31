package com.nuono.next.officialwarehouse;

import com.nuono.next.infrastructure.mapper.OfficialWarehouseBatchSummaryMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseBatchSummaryRecords.ShippingBatchRawLineRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseBatchSummaryViews.BatchProductSummaryView;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.ShippingBatchSourceAllocationRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.ProductCandidateView;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class OfficialWarehouseBatchSummaryService {

    private final LocalDbOfficialWarehouseService warehouseService;
    private final OfficialWarehouseMapper warehouseMapper;
    private final OfficialWarehouseBatchSummaryMapper summaryMapper;
    private final OfficialWarehouseBatchSummaryAssembler assembler =
            new OfficialWarehouseBatchSummaryAssembler();

    public OfficialWarehouseBatchSummaryService(
            LocalDbOfficialWarehouseService warehouseService,
            OfficialWarehouseMapper warehouseMapper,
            OfficialWarehouseBatchSummaryMapper summaryMapper
    ) {
        this.warehouseService = warehouseService;
        this.warehouseMapper = warehouseMapper;
        this.summaryMapper = summaryMapper;
    }

    public BatchProductSummaryView summarize(
            BusinessAccessContext access,
            String storeCode,
            String siteCode,
            Collection<String> shippingBatchIds
    ) {
        String currentStoreCode = requireUpper(storeCode, "请选择店铺。");
        String currentSiteCode = requireUpper(siteCode, "请选择站点。");
        List<Long> batchIds = normalizeBatchIds(shippingBatchIds);
        Long ownerUserId = requireStoreOwner(access, currentStoreCode);
        Map<String, Long> accessibleStoreOwners = access.getStoreOwnerUserIds();
        List<String> accessibleStoreCodes = accessibleStoreOwners.entrySet().stream()
                .filter(entry -> ownerUserId.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());

        Map<String, List<ShippingBatchSourceAllocationRecord>> allocationsByStore = new LinkedHashMap<>();
        for (String accessibleStoreCode : accessibleStoreCodes) {
            List<ShippingBatchSourceAllocationRecord> allocations =
                    warehouseMapper.listShippingBatchSourceAllocations(
                            ownerUserId,
                            accessibleStoreCode,
                            currentSiteCode,
                            batchIds,
                            List.of(),
                            List.of()
                    );
            if (!allocations.isEmpty()) {
                allocationsByStore.put(accessibleStoreCode, allocations);
            }
        }
        requireCurrentStoreBatches(currentStoreCode, batchIds, allocationsByStore.get(currentStoreCode));

        List<ShippingBatchRawLineRecord> rawLines = summaryMapper.listRawLines(ownerUserId, batchIds);
        requireAllBatches(batchIds, rawLines);
        List<ProductCandidateView> candidates = warehouseService.listProductCandidates(
                access,
                currentStoreCode,
                currentSiteCode,
                null,
                batchIds.stream().map(String::valueOf).collect(Collectors.toList()),
                List.of()
        );
        return assembler.assemble(
                currentStoreCode,
                currentSiteCode,
                rawLines,
                candidates,
                allocationsByStore
        );
    }

    private void requireCurrentStoreBatches(
            String storeCode,
            List<Long> batchIds,
            List<ShippingBatchSourceAllocationRecord> allocations
    ) {
        Set<Long> found = allocations == null ? Set.of() : allocations.stream()
                .map(item -> item.inTransitBatchId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (!found.containsAll(batchIds)) {
            throw new IllegalArgumentException("所选物流批次不属于当前店铺/站点，或已不可约仓：" + storeCode);
        }
    }

    private void requireAllBatches(List<Long> batchIds, List<ShippingBatchRawLineRecord> rawLines) {
        Set<Long> found = rawLines.stream().map(item -> item.batchId).collect(Collectors.toSet());
        if (!found.containsAll(batchIds)) {
            throw new IllegalArgumentException("所选物流批次不存在、已失效或没有可用商品。");
        }
    }

    private List<Long> normalizeBatchIds(Collection<String> source) {
        if (source == null || source.isEmpty()) {
            throw new IllegalArgumentException("请选择物流批次。");
        }
        Set<Long> result = new LinkedHashSet<>();
        for (String value : source) {
            try {
                long id = Long.parseLong(value == null ? "" : value.trim());
                if (id <= 0) {
                    throw new NumberFormatException();
                }
                result.add(id);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("物流批次编号无效。");
            }
        }
        return new ArrayList<>(result);
    }

    private static String requireUpper(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static Long requireStoreOwner(BusinessAccessContext access, String storeCode) {
        if (access == null || !access.canAccessStore(storeCode)) {
            throw new IllegalArgumentException("当前账号不能访问该店铺。");
        }
        Long ownerUserId = access.resolveOwnerUserIdForStore(storeCode);
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("无法识别当前店铺的业务老板账号。");
        }
        return ownerUserId;
    }
}
