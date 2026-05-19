package com.nuono.next.procurement;

import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.ITEM_REMOVED_TERMINATED;

import com.nuono.next.infrastructure.mapper.ProcurementRequirementConfirmationMapper;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.DemandDetailRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.PoolItemLockRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.PoolLockRow;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
class ProcurementPoolWriteSupport {

    private final ProcurementRequirementConfirmationMapper mapper;
    private final LocalDbProcurementRequirementConfirmationService readService;

    ProcurementPoolWriteSupport(
            ProcurementRequirementConfirmationMapper mapper,
            LocalDbProcurementRequirementConfirmationService readService
    ) {
        this.mapper = mapper;
        this.readService = readService;
    }

    PoolItemLockRow requireCurrentPoolItem(PoolLockRow pool, Long poolItemId, String message) {
        PoolItemLockRow item = mapper.selectPoolItemForUpdate(pool.getPoolId(), poolItemId);
        if (item == null || !Objects.equals(item.getDemandItemId(), pool.getDemandItemId())) {
            throw new IllegalArgumentException(message);
        }
        if (ITEM_REMOVED_TERMINATED.equals(upper(item.getStatus()))) {
            throw new IllegalStateException("已移出待选池的候选不能作为最终候选。");
        }
        List<PoolItemLockRow> currentItems = mapper.listCurrentPoolItemsForUpdate(pool.getPoolId());
        for (PoolItemLockRow currentItem : currentItems) {
            if (Objects.equals(currentItem.getPoolItemId(), poolItemId)) {
                return item;
            }
        }
        throw new IllegalArgumentException(message);
    }

    PoolLockRow requirePool(Long demandItemId) {
        PoolLockRow pool = mapper.selectCurrentPoolForUpdate(demandItemId);
        if (pool == null) {
            throw new IllegalStateException("当前采购需求还没有待选池，请先初始化待选池。");
        }
        return pool;
    }

    DemandDetailRow requireDemandForUpdate(Long demandItemId, Long ownerUserId) {
        if (demandItemId == null) {
            throw new IllegalArgumentException("缺少采购需求 ID。");
        }
        DemandDetailRow demand = mapper.selectDemandDetailForUpdate(demandItemId, ownerUserId);
        if (demand == null) {
            throw new IllegalArgumentException("采购需求不存在或当前账号无权查看。");
        }
        return demand;
    }

    String resolvePoolStatusAfterCurrentItems(Long poolId, String fallbackStatus) {
        return ProcurementCandidatePoolStatusPolicy.resolvePoolStatusAfterCurrentItems(mapper.listCurrentPoolItems(poolId), fallbackStatus);
    }

    void requirePoolItemChangeAllowed(String poolStatus, String message) {
        if (!ProcurementCandidatePoolStatusPolicy.canChangePoolItem(poolStatus)) {
            throw new IllegalStateException(message);
        }
    }

    void requireExpectedStatus(String expectedStatus, String actualStatus) {
        String normalizedExpected = upper(expectedStatus);
        if (StringUtils.hasText(normalizedExpected) && !normalizedExpected.equals(upper(actualStatus))) {
            throw new IllegalStateException("当前候选状态已变化，请刷新后重试。");
        }
    }

    Map<String, Object> detailMap(Object... values) {
        Map<String, Object> detail = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            Object key = values[index];
            Object value = values[index + 1];
            if (key != null && value != null) {
                detail.put(String.valueOf(key), value);
            }
        }
        return detail;
    }

    ProcurementRequirementConfirmationDetailView detail(Long demandItemId, Long ownerUserId, String message) {
        ProcurementRequirementConfirmationDetailView view = readService.getDemandDetail(demandItemId, ownerUserId);
        view.setMessage(message);
        return view;
    }

    String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    String upper(String value) {
        String normalized = normalize(value);
        return normalized == null ? "" : normalized.toUpperCase(Locale.ROOT);
    }

    String firstNonBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
