package com.nuono.next.productselection;

import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class ProductSelectionPermissionGuard {

    private final ProductSelectionMapper productSelectionMapper;

    public ProductSelectionPermissionGuard(ProductSelectionMapper productSelectionMapper) {
        this.productSelectionMapper = productSelectionMapper;
    }

    public ProductSelectionUserContext requireActiveUser(Long operatorUserId) {
        if (operatorUserId == null) {
            throw new ProductSelectionAccessDeniedException("缺少操作人，无法访问人工选品。");
        }
        ProductSelectionUserContext user = productSelectionMapper.selectUserContext(operatorUserId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new ProductSelectionAccessDeniedException("当前账号不存在或已停用。");
        }
        return user;
    }

    public ProductSelectionStoreScope requireReadableStore(Long operatorUserId, String storeCode) {
        ProductSelectionUserContext user = requireActiveUser(operatorUserId);
        ProductSelectionStoreScope scope = resolveScope(user, storeCode);
        if (scope == null) {
            throw new ProductSelectionAccessDeniedException("当前账号没有可访问的店铺范围。");
        }
        return ensureLogicalStore(scope, user);
    }

    public ProductSelectionStoreScope requireWritableStore(Long operatorUserId, String storeCode) {
        ProductSelectionStoreScope scope = requireReadableStore(operatorUserId, storeCode);
        if (!Boolean.TRUE.equals(scope.getAuthorized())) {
            throw new ProductSelectionAccessDeniedException("当前店铺未授权，不能维护人工选品源头采集。");
        }
        return scope;
    }

    public ProductSelectionStoreScope requireWritableLogicalStoreSite(
            Long operatorUserId,
            Long logicalStoreId,
            String siteCode
    ) {
        ProductSelectionUserContext user = requireActiveUser(operatorUserId);
        if (logicalStoreId == null || logicalStoreId <= 0) {
            throw new ProductSelectionAccessDeniedException(
                    "当前选品组缺少店铺归属，不能保存利润估算。");
        }
        String normalizedSiteCode = normalizeSiteCode(siteCode);
        if (!StringUtils.hasText(normalizedSiteCode)) {
            throw new ProductSelectionAccessDeniedException(
                    "当前选品组缺少站点归属，不能保存利润估算。");
        }
        List<String> storeCodes =
                productSelectionMapper.listLogicalStoreSiteCodes(
                        logicalStoreId,
                        normalizedSiteCode
                );
        if (storeCodes != null) {
            for (String storeCode : storeCodes) {
                ProductSelectionStoreScope scope = resolveScope(user, storeCode);
                if (scope != null
                        && Objects.equals(logicalStoreId, scope.getLogicalStoreId())
                        && Boolean.TRUE.equals(scope.getAuthorized())) {
                    return scope;
                }
            }
        }
        throw new ProductSelectionAccessDeniedException(
                "当前选品组所属店铺未授权，不能保存利润估算。");
    }

    ProductSelectionStoreScope requireWritableLogicalStoreSite(
            Long operatorUserId,
            ProductSelectionGroupRow group
    ) {
        if (group == null) {
            throw new IllegalArgumentException("选品组不存在或已被删除。");
        }
        return requireWritableLogicalStoreSite(
                operatorUserId,
                group.getLogicalStoreId(),
                group.getSiteCode()
        );
    }

    private String normalizeSiteCode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("AE")
                || normalized.equals("ARE")
                || normalized.equals("UAE")
                || normalized.equals("NAE")
                || normalized.contains("-NAE")
                || normalized.endsWith("NAE")
                || normalized.contains("阿联酋")) {
            return "AE";
        }
        if (normalized.equals("SA")
                || normalized.equals("SAU")
                || normalized.equals("KSA")
                || normalized.equals("NSA")
                || normalized.contains("-NSA")
                || normalized.endsWith("NSA")
                || normalized.contains("沙特")) {
            return "SA";
        }
        return normalized;
    }

    private ProductSelectionStoreScope resolveScope(ProductSelectionUserContext user, String storeCode) {
        ProductSelectionStoreScope scope = null;
        if (StringUtils.hasText(storeCode)) {
            scope = productSelectionMapper.selectVisibleStoreScope(user.getUserId(), storeCode.trim());
            if (scope == null) {
                scope = productSelectionMapper.selectOwnedLogicalStoreScope(user.getUserId(), storeCode.trim());
            }
            if (scope == null && isSuperAdmin(user)) {
                scope = productSelectionMapper.selectAnyStoreScope(storeCode.trim());
                if (scope == null) {
                    scope = productSelectionMapper.selectLogicalStoreScope(storeCode.trim());
                }
            }
            return scope;
        }
        scope = productSelectionMapper.selectFirstVisibleStoreScope(user.getUserId());
        if (scope == null) {
            scope = productSelectionMapper.selectFirstOwnedLogicalStoreScope(user.getUserId());
        }
        return scope;
    }

    private ProductSelectionStoreScope ensureLogicalStore(
            ProductSelectionStoreScope scope,
            ProductSelectionUserContext user
    ) {
        if (scope.getLogicalStoreId() != null) {
            return scope;
        }

        String projectCode = requiredText(scope.getProjectCode(), scope.getStoreCode());
        Long ownerUserId = scope.getOwnerUserId() == null ? user.getUserId() : scope.getOwnerUserId();
        Long logicalStoreId = productSelectionMapper.selectLogicalStoreId(ownerUserId, projectCode);
        if (logicalStoreId == null) {
            logicalStoreId = productSelectionMapper.nextLogicalStoreId();
            productSelectionMapper.upsertLogicalStore(
                    logicalStoreId,
                    ownerUserId,
                    projectCode,
                    defaultText(scope.getProjectName(), projectCode),
                    user.getUserId()
            );
        }
        productSelectionMapper.upsertLogicalStoreSite(
                productSelectionMapper.nextLogicalStoreSiteId(),
                logicalStoreId,
                scope.getStoreCode(),
                defaultText(scope.getSite(), "NOON"),
                user.getUserId()
        );
        scope.setLogicalStoreId(logicalStoreId);
        scope.setOwnerUserId(ownerUserId);
        return scope;
    }

    private boolean isSuperAdmin(ProductSelectionUserContext user) {
        return user != null
                && (Integer.valueOf(0).equals(user.getLevel()) || "admin".equalsIgnoreCase(user.getAccountNo()));
    }

    private String requiredText(String value, String fallback) {
        if (StringUtils.hasText(value)) {
            return value.trim();
        }
        if (StringUtils.hasText(fallback)) {
            return fallback.trim();
        }
        throw new IllegalArgumentException("缺少店铺项目编码，无法创建人工选品源头采集。");
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
