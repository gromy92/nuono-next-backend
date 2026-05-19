package com.nuono.next.permission.access;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import com.nuono.next.auth.RoleAccessSupport;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BusinessAccessResolver {

    private final AuthSessionTokenService sessionTokenService;
    private final Supplier<BusinessAccessMapper> accessMapperSupplier;
    private final BusinessAccessGuard guard;

    @Autowired
    public BusinessAccessResolver(
            AuthSessionTokenService sessionTokenService,
            ObjectProvider<BusinessAccessMapper> accessMapperProvider,
            BusinessAccessGuard guard
    ) {
        this(sessionTokenService, accessMapperProvider::getIfAvailable, guard);
    }

    BusinessAccessResolver(
            AuthSessionTokenService sessionTokenService,
            BusinessAccessMapper accessMapper,
            BusinessAccessGuard guard
    ) {
        this(sessionTokenService, () -> accessMapper, guard);
    }

    private BusinessAccessResolver(
            AuthSessionTokenService sessionTokenService,
            Supplier<BusinessAccessMapper> accessMapperSupplier,
            BusinessAccessGuard guard
    ) {
        this.sessionTokenService = sessionTokenService;
        this.accessMapperSupplier = accessMapperSupplier;
        this.guard = guard;
    }

    public BusinessAccessContext requireBusinessContext(
            HttpServletRequest request,
            BusinessCapability capability
    ) {
        try {
            BusinessAccessContext context = resolve(request);
            return guard.requireBusinessCapability(context, capability);
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        }
    }

    public BusinessAccessContext requireStoreAccess(
            HttpServletRequest request,
            BusinessCapability capability,
            String storeCode
    ) {
        BusinessAccessContext context = requireBusinessContext(request, capability);
        try {
            return guard.requireStore(context, storeCode);
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        }
    }

    public BusinessAccessContext resolve(HttpServletRequest request) {
        AuthenticatedSession session = sessionTokenService.requireSession(request);
        Long sessionUserId = session.getUserId();
        BusinessAccessMapper accessMapper = requireAccessMapper();
        BusinessUserAccessRow user = accessMapper.selectUserAccess(sessionUserId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前账号不存在或已停用。");
        }

        List<String> menuPaths = emptyIfNull(accessMapper.selectGrantedMenuPaths(sessionUserId));
        List<BusinessStoreScopeRow> storeScope = emptyIfNull(accessMapper.selectStoreScope(sessionUserId));
        BusinessAccountType accountType = resolveAccountType(user);
        Long ownerUserId = resolveOwnerUserId(user, storeScope, accountType);

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
                .sessionUserId(sessionUserId)
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
            BusinessAccountType accountType
    ) {
        if (accountType == BusinessAccountType.BOSS) {
            return user.getUserId();
        }
        for (BusinessStoreScopeRow store : storeScope) {
            if (store != null && store.getOwnerUserId() != null) {
                return store.getOwnerUserId();
            }
        }
        return user.getCreatedBy();
    }

    private BusinessAccountType resolveAccountType(BusinessUserAccessRow user) {
        if (isExternalAccount(user)) {
            return BusinessAccountType.UNKNOWN;
        }
        String roleName = normalize(user.getRoleName());
        Integer effectiveLevel = resolveLevel(user);
        if (RoleAccessSupport.isSystemAdmin(user.getRoleId(), effectiveLevel)
                || "系统管理员".equals(roleName)
                || "管理员".equals(roleName)) {
            return BusinessAccountType.SYSTEM_ADMIN;
        }
        if (isLevel(effectiveLevel, 1) || "老板".equals(roleName)) {
            return BusinessAccountType.BOSS;
        }
        return BusinessAccountType.OPERATOR;
    }

    private Integer resolveLevel(BusinessUserAccessRow user) {
        return user.getRoleLevel() == null ? user.getUserLevel() : user.getRoleLevel();
    }

    private boolean isLevel(Integer value, int expected) {
        return value != null && value == expected;
    }

    private boolean isExternalAccount(BusinessUserAccessRow user) {
        return user != null && "external".equalsIgnoreCase(String.valueOf(user.getAccountType()));
    }

    private BusinessAccessMapper requireAccessMapper() {
        BusinessAccessMapper accessMapper = accessMapperSupplier.get();
        if (accessMapper == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "业务访问控制暂时不可用。");
        }
        return accessMapper;
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
