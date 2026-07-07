package com.nuono.next.intransit.autosync;

import com.nuono.next.auth.RoleAccessSupport;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccessGuard;
import com.nuono.next.permission.access.BusinessAccessMapper;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.permission.access.BusinessStoreScopeRow;
import com.nuono.next.permission.access.BusinessUserAccessRow;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LogisticsAutoSyncAccessContextFactory {
    private final BusinessAccessMapper accessMapper;
    private final BusinessAccessGuard guard;

    public LogisticsAutoSyncAccessContextFactory(
            BusinessAccessMapper accessMapper,
            BusinessAccessGuard guard
    ) {
        this.accessMapper = accessMapper;
        this.guard = guard;
    }

    public BusinessAccessContext requireAccessContext(LogisticsAutoSyncAccount account) {
        if (account == null || account.getOwnerUserId() == null || account.getOperatorUserId() == null) {
            throw new IllegalArgumentException("物流自动同步账号缺少 owner/operator 绑定。");
        }
        BusinessAccessContext context = buildContext(account.getOperatorUserId(), account.getOwnerUserId());
        guard.requireBusinessCapability(context, BusinessCapability.IN_TRANSIT_GOODS);
        if (!account.getOwnerUserId().equals(context.getBusinessOwnerUserId())) {
            throw new BusinessAccessDeniedException("物流自动同步执行账号业务归属不匹配。");
        }
        return context;
    }

    private BusinessAccessContext buildContext(Long operatorUserId, Long targetOwnerUserId) {
        BusinessUserAccessRow user = accessMapper.selectUserAccess(operatorUserId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new IllegalArgumentException("物流自动同步执行账号不存在或已停用。");
        }

        List<String> menuPaths = emptyIfNull(accessMapper.selectGrantedMenuPaths(operatorUserId));
        List<BusinessStoreScopeRow> storeScope = emptyIfNull(accessMapper.selectStoreScope(operatorUserId));
        BusinessAccountType accountType = resolveAccountType(user);
        Long ownerUserId = resolveOwnerUserId(user, storeScope, accountType, targetOwnerUserId);

        Set<String> storeCodes = new LinkedHashSet<>();
        Map<String, Long> storeOwnerUserIds = new LinkedHashMap<>();
        for (BusinessStoreScopeRow store : storeScope) {
            if (store == null || !StringUtils.hasText(store.getStoreCode())) {
                continue;
            }
            storeCodes.add(store.getStoreCode());
            if (store.getOwnerUserId() != null) {
                storeOwnerUserIds.put(store.getStoreCode(), store.getOwnerUserId());
            }
        }

        return BusinessAccessContext.builder()
                .sessionUserId(operatorUserId)
                .businessOwnerUserId(ownerUserId)
                .accountType(accountType)
                .roleId(user.getRoleId())
                .roleLevel(resolveLevel(user))
                .roleName(user.getRoleName())
                .storeCodes(storeCodes)
                .storeOwnerUserIds(storeOwnerUserIds)
                .menuPaths(new LinkedHashSet<>(menuPaths))
                .build();
    }

    private Long resolveOwnerUserId(
            BusinessUserAccessRow user,
            List<BusinessStoreScopeRow> storeScope,
            BusinessAccountType accountType,
            Long targetOwnerUserId
    ) {
        if (accountType == BusinessAccountType.BOSS) {
            return user.getUserId();
        }
        for (BusinessStoreScopeRow store : storeScope) {
            if (store != null && targetOwnerUserId != null && targetOwnerUserId.equals(store.getOwnerUserId())) {
                return targetOwnerUserId;
            }
        }
        for (BusinessStoreScopeRow store : storeScope) {
            if (store != null && store.getOwnerUserId() != null) {
                return store.getOwnerUserId();
            }
        }
        return user.getCreatedBy();
    }

    private BusinessAccountType resolveAccountType(BusinessUserAccessRow user) {
        if (user != null && "external".equalsIgnoreCase(String.valueOf(user.getAccountType()))) {
            return BusinessAccountType.UNKNOWN;
        }
        String roleName = normalize(user == null ? null : user.getRoleName());
        Integer effectiveLevel = resolveLevel(user);
        if (RoleAccessSupport.isSystemAdmin(user.getRoleId(), effectiveLevel)
                || "系统管理员".equals(roleName)
                || "管理员".equals(roleName)) {
            return BusinessAccountType.SYSTEM_ADMIN;
        }
        if (effectiveLevel != null && effectiveLevel == 1 || "老板".equals(roleName)) {
            return BusinessAccountType.BOSS;
        }
        return BusinessAccountType.OPERATOR;
    }

    private Integer resolveLevel(BusinessUserAccessRow user) {
        return user.getRoleLevel() == null ? user.getUserLevel() : user.getRoleLevel();
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim();
    }

    private static <T> List<T> emptyIfNull(List<T> values) {
        return values == null ? List.of() : values;
    }
}
