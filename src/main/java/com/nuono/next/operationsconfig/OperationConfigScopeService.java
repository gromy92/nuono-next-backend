package com.nuono.next.operationsconfig;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OperationConfigScopeService {

    public static final String BOSS_WIDE_STORE_CODE = "*";
    public static final String BOSS_WIDE_SITE_CODE = "*";

    private final OperationConfigScopeRepository repository;

    public OperationConfigScopeService(OperationConfigScopeRepository repository) {
        this.repository = repository;
    }

    public OperationConfigScopeView resolveScope(
            BusinessAccessContext context,
            List<Long> selectedBossUserIds
    ) {
        requireOperationConfigCapability(context);
        if (context.isSystemAdmin()) {
            return resolveSystemAdminScope(context, selectedBossUserIds);
        }
        requireBusinessAccount(context);
        List<OperationConfigStoreScope> stores = repository.listStoreSitesByStoreCodes(context.getStoreCodes()).stream()
                .filter(store -> context.canAccessStore(store.getStoreCode()))
                .filter(store -> matchesBusinessOwner(context, store))
                .sorted(storeComparator())
                .collect(Collectors.toList());
        return view(context, false, List.of(), List.of(), stores, stores.isEmpty() ? "NO_STORE" : null);
    }

    public OperationConfigStoreScope requireStoreSiteScope(
            BusinessAccessContext context,
            List<Long> selectedBossUserIds,
            Long ownerUserId,
            String storeCode,
            String siteCode
    ) {
        requireOperationConfigCapability(context);
        if (context.isSystemAdmin()) {
            List<Long> selectedBossIds = normalizeIds(selectedBossUserIds);
            if (!selectedBossIds.contains(ownerUserId)) {
                throw new BusinessAccessDeniedException("选择的老板范围不包含该店铺。");
            }
            if (isBossWideScope(storeCode, siteCode)) {
                return new OperationConfigStoreScope(
                        ownerUserId,
                        null,
                        null,
                        "全部店铺",
                        BOSS_WIDE_STORE_CODE,
                        BOSS_WIDE_SITE_CODE
                );
            }
            return findRequired(repository.listStoreSitesByBossUserIds(selectedBossIds), ownerUserId, storeCode, siteCode);
        }
        requireBusinessAccount(context);
        if (!context.canAccessStore(storeCode)) {
            throw new BusinessAccessDeniedException("当前账号不能操作该店铺。");
        }
        Long mappedOwnerUserId = context.resolveOwnerUserIdForStore(storeCode);
        if (mappedOwnerUserId == null) {
            throw new BusinessAccessDeniedException("当前账号不能操作该店铺。");
        }
        Long requiredOwnerUserId = ownerUserId == null ? mappedOwnerUserId : ownerUserId;
        if (!requiredOwnerUserId.equals(mappedOwnerUserId)) {
            throw new BusinessAccessDeniedException("当前账号不能操作该店铺。");
        }
        return findRequired(repository.listStoreSitesByStoreCodes(context.getStoreCodes()), requiredOwnerUserId, storeCode, siteCode);
    }

    private OperationConfigScopeView resolveSystemAdminScope(
            BusinessAccessContext context,
            List<Long> selectedBossUserIds
    ) {
        List<OperationConfigBossOption> bosses = repository.listBossOptions();
        Set<Long> allowedBossIds = bosses.stream()
                .map(OperationConfigBossOption::getOwnerUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Long> selectedBossIds = normalizeIds(selectedBossUserIds);
        for (Long bossUserId : selectedBossIds) {
            if (!allowedBossIds.contains(bossUserId)) {
                throw new BusinessAccessDeniedException("选择的老板范围不存在或不可访问。");
            }
        }
        List<OperationConfigStoreScope> stores = selectedBossIds.isEmpty()
                ? List.of()
                : selectedBossIds.stream()
                        .map(ownerUserId -> new OperationConfigStoreScope(
                                ownerUserId,
                                null,
                                null,
                                bossDisplayName(bosses, ownerUserId),
                                BOSS_WIDE_STORE_CODE,
                                BOSS_WIDE_SITE_CODE
                        ))
                        .collect(Collectors.toList());
        return view(
                context,
                true,
                bosses,
                selectedBossIds,
                stores,
                selectedBossIds.isEmpty() ? "SELECT_BOSS" : (stores.isEmpty() ? "NO_STORE" : null)
        );
    }

    private OperationConfigScopeView view(
            BusinessAccessContext context,
            boolean systemAdmin,
            List<OperationConfigBossOption> bosses,
            List<Long> selectedBossIds,
            List<OperationConfigStoreScope> stores,
            String emptyReason
    ) {
        OperationConfigStoreScope defaultStore = stores.isEmpty() ? null : stores.get(0);
        return new OperationConfigScopeView(
                systemAdmin,
                context.getRoleName(),
                bosses,
                selectedBossIds,
                stores,
                defaultStore == null ? null : defaultStore.getOwnerUserId(),
                defaultStore == null ? null : defaultStore.getStoreCode(),
                defaultStore == null ? null : defaultStore.getSiteCode(),
                emptyReason
        );
    }

    private OperationConfigStoreScope findRequired(
            List<OperationConfigStoreScope> stores,
            Long ownerUserId,
            String storeCode,
            String siteCode
    ) {
        String normalizedStoreCode = normalize(storeCode);
        String normalizedSiteCode = normalize(siteCode);
        return stores.stream()
                .filter(store -> ownerUserId == null || ownerUserId.equals(store.getOwnerUserId()))
                .filter(store -> normalizedStoreCode.equals(normalize(store.getStoreCode())))
                .filter(store -> normalizedSiteCode.equals(normalize(store.getSiteCode())))
                .findFirst()
                .orElseThrow(() -> new BusinessAccessDeniedException("当前账号不能操作该店铺。"));
    }

    private boolean matchesBusinessOwner(BusinessAccessContext context, OperationConfigStoreScope store) {
        Long mappedOwnerUserId = context.resolveOwnerUserIdForStore(store.getStoreCode());
        return mappedOwnerUserId != null && mappedOwnerUserId.equals(store.getOwnerUserId());
    }

    private boolean isBossWideScope(String storeCode, String siteCode) {
        return BOSS_WIDE_STORE_CODE.equals(normalize(storeCode))
                && BOSS_WIDE_SITE_CODE.equals(normalize(siteCode));
    }

    private String bossDisplayName(List<OperationConfigBossOption> bosses, Long ownerUserId) {
        return bosses.stream()
                .filter(boss -> ownerUserId.equals(boss.getOwnerUserId()))
                .map(boss -> StringUtils.hasText(boss.getDisplayName())
                        ? boss.getDisplayName()
                        : (StringUtils.hasText(boss.getAccountNo()) ? boss.getAccountNo() : "老板 " + ownerUserId))
                .findFirst()
                .orElse("老板 " + ownerUserId);
    }

    private void requireOperationConfigCapability(BusinessAccessContext context) {
        if (context == null) {
            throw new BusinessAccessDeniedException("缺少业务访问上下文。");
        }
        if (!context.hasCapability(BusinessCapability.ADVANCED_OPERATIONS_CONFIG)) {
            throw new BusinessAccessDeniedException("当前账号没有对应业务菜单权限。");
        }
    }

    private void requireBusinessAccount(BusinessAccessContext context) {
        if (!context.isBusinessAccount()) {
            throw new BusinessAccessDeniedException("当前账号不能操作店铺业务。");
        }
    }

    private List<Long> normalizeIds(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (Long value : values) {
            if (value != null && value > 0 && !result.contains(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private Comparator<OperationConfigStoreScope> storeComparator() {
        return Comparator
                .comparing(OperationConfigStoreScope::getOwnerUserId, Comparator.nullsLast(Long::compareTo))
                .thenComparing(OperationConfigStoreScope::getProjectCode, Comparator.nullsLast(String::compareTo))
                .thenComparing(OperationConfigStoreScope::getStoreCode, Comparator.nullsLast(String::compareTo))
                .thenComparing(OperationConfigStoreScope::getSiteCode, Comparator.nullsLast(String::compareTo));
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
