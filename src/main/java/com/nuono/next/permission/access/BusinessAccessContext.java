package com.nuono.next.permission.access;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

public final class BusinessAccessContext {

    private final Long sessionUserId;
    private final Long businessOwnerUserId;
    private final BusinessAccountType accountType;
    private final Long roleId;
    private final Integer roleLevel;
    private final String roleName;
    private final Set<String> storeCodes;
    private final Map<String, Long> storeOwnerUserIds;
    private final Set<String> menuPaths;

    private BusinessAccessContext(Builder builder) {
        this.sessionUserId = builder.sessionUserId;
        this.businessOwnerUserId = builder.businessOwnerUserId;
        this.accountType = builder.accountType == null ? BusinessAccountType.UNKNOWN : builder.accountType;
        this.roleId = builder.roleId;
        this.roleLevel = builder.roleLevel;
        this.roleName = builder.roleName;
        this.storeOwnerUserIds = normalizeStoreOwnerUserIds(builder.storeOwnerUserIds);
        this.storeCodes = normalizeStoreCodeSet(builder.storeCodes, this.storeOwnerUserIds.keySet());
        this.menuPaths = normalizePathSet(builder.menuPaths);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getSessionUserId() {
        return sessionUserId;
    }

    public Long getBusinessOwnerUserId() {
        return businessOwnerUserId;
    }

    public BusinessAccountType getAccountType() {
        return accountType;
    }

    public Long getRoleId() {
        return roleId;
    }

    public Integer getRoleLevel() {
        return roleLevel;
    }

    public String getRoleName() {
        return roleName;
    }

    public Set<String> getStoreCodes() {
        return storeCodes;
    }

    public Map<String, Long> getStoreOwnerUserIds() {
        return storeOwnerUserIds;
    }

    public Set<String> getMenuPaths() {
        return menuPaths;
    }

    public boolean isSystemAdmin() {
        return accountType == BusinessAccountType.SYSTEM_ADMIN;
    }

    public boolean isBossAccount() {
        return accountType == BusinessAccountType.BOSS;
    }

    public boolean isOperatorAccount() {
        return accountType == BusinessAccountType.OPERATOR;
    }

    public boolean isBusinessAccount() {
        return isBossAccount() || isOperatorAccount();
    }

    public boolean canAccessStore(String storeCode) {
        String normalized = normalizeStoreCode(storeCode);
        return normalized != null && storeCodes.contains(normalized);
    }

    public Long resolveOwnerUserIdForStore(String storeCode) {
        String normalized = normalizeStoreCode(storeCode);
        if (normalized == null) {
            return null;
        }
        return storeOwnerUserIds.get(normalized);
    }

    public boolean hasCapability(BusinessCapability capability) {
        if (capability == null) {
            return false;
        }
        for (String menuPath : menuPaths) {
            for (String prefix : capability.getMenuPathPrefixes()) {
                String normalizedPrefix = normalizePath(prefix);
                if (matchesPathPrefix(menuPath, normalizedPrefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<String> normalizeStoreCodeSet(Set<String> storeCodes, Set<String> mappedStoreCodes) {
        if ((storeCodes == null || storeCodes.isEmpty()) && (mappedStoreCodes == null || mappedStoreCodes.isEmpty())) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        if (storeCodes != null) {
            for (String item : storeCodes) {
                String normalized = normalizeStoreCode(item);
                if (normalized != null) {
                    result.add(normalized);
                }
            }
        }
        if (mappedStoreCodes != null) {
            for (String item : mappedStoreCodes) {
                String normalized = normalizeStoreCode(item);
                if (normalized != null) {
                    result.add(normalized);
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private static Map<String, Long> normalizeStoreOwnerUserIds(Map<String, Long> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : source.entrySet()) {
            String normalizedStoreCode = normalizeStoreCode(entry.getKey());
            Long ownerUserId = entry.getValue();
            if (normalizedStoreCode != null && ownerUserId != null) {
                result.put(normalizedStoreCode, ownerUserId);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static Set<String> normalizePathSet(Set<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String item : source) {
            String normalized = normalizePath(item);
            if (normalized != null) {
                result.add(normalized);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private static String normalizeStoreCode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizePath(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static boolean matchesPathPrefix(String path, String prefix) {
        if (path == null || prefix == null) {
            return false;
        }
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    public static final class Builder {
        private Long sessionUserId;
        private Long businessOwnerUserId;
        private BusinessAccountType accountType;
        private Long roleId;
        private Integer roleLevel;
        private String roleName;
        private Set<String> storeCodes = Set.of();
        private Map<String, Long> storeOwnerUserIds = Map.of();
        private Set<String> menuPaths = Set.of();

        public Builder sessionUserId(Long sessionUserId) {
            this.sessionUserId = sessionUserId;
            return this;
        }

        public Builder businessOwnerUserId(Long businessOwnerUserId) {
            this.businessOwnerUserId = businessOwnerUserId;
            return this;
        }

        public Builder accountType(BusinessAccountType accountType) {
            this.accountType = accountType;
            return this;
        }

        public Builder roleId(Long roleId) {
            this.roleId = roleId;
            return this;
        }

        public Builder roleLevel(Integer roleLevel) {
            this.roleLevel = roleLevel;
            return this;
        }

        public Builder roleName(String roleName) {
            this.roleName = roleName;
            return this;
        }

        public Builder storeCodes(Set<String> storeCodes) {
            this.storeCodes = storeCodes;
            return this;
        }

        public Builder storeOwnerUserIds(Map<String, Long> storeOwnerUserIds) {
            this.storeOwnerUserIds = storeOwnerUserIds;
            return this;
        }

        public Builder menuPaths(Set<String> menuPaths) {
            this.menuPaths = menuPaths;
            return this;
        }

        public BusinessAccessContext build() {
            return new BusinessAccessContext(this);
        }
    }
}
