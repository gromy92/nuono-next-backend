package com.nuono.next.productselection;

import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class ProductSelectionAccessAdapter {

    private final ProductSelectionPermissionGuard permissionGuard;

    public ProductSelectionAccessAdapter(ProductSelectionPermissionGuard permissionGuard) {
        this.permissionGuard = permissionGuard;
    }

    public ProductSelectionAccessScope requireReadableStore(
            BusinessAccessContext access,
            String requestedStoreCode
    ) {
        return requireStore(access, requestedStoreCode, false);
    }

    public ProductSelectionAccessScope requireWritableStore(
            BusinessAccessContext access,
            String requestedStoreCode
    ) {
        return requireStore(access, requestedStoreCode, true);
    }

    private ProductSelectionAccessScope requireStore(
            BusinessAccessContext access,
            String requestedStoreCode,
            boolean writable
    ) {
        requireBusinessAccount(access);
        String storeCode = resolveStoreCode(access, requestedStoreCode);
        if (StringUtils.hasText(storeCode) && !access.canAccessStore(storeCode)) {
            throw new ProductSelectionAccessDeniedException("当前账号不能访问该店铺。");
        }
        ProductSelectionStoreScope scope = writable
                ? permissionGuard.requireWritableStore(access.getSessionUserId(), storeCode)
                : permissionGuard.requireReadableStore(access.getSessionUserId(), storeCode);
        return new ProductSelectionAccessScope(access, scope);
    }

    private String resolveStoreCode(BusinessAccessContext access, String requestedStoreCode) {
        String normalized = normalizeStoreCode(requestedStoreCode);
        if (normalized != null) {
            return normalized;
        }
        if (access.getStoreCodes().size() == 1) {
            return access.getStoreCodes().iterator().next();
        }
        return null;
    }

    private void requireBusinessAccount(BusinessAccessContext access) {
        if (access == null) {
            throw new ProductSelectionAccessDeniedException("缺少业务访问上下文。");
        }
        if (access.isSystemAdmin()) {
            throw new ProductSelectionAccessDeniedException("系统管理员不能操作店铺业务。");
        }
        if (!access.isBusinessAccount()) {
            throw new ProductSelectionAccessDeniedException("当前账号不能操作店铺业务。");
        }
    }

    private String normalizeStoreCode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
