package com.nuono.next.officialwarehouse;

import com.nuono.next.officialwarehouse.OfficialWarehouseNoonInboundClient.NoonCallContext;
import com.nuono.next.sales.NoonSalesReportBinding;
import java.util.Locale;
import java.util.Objects;
import org.springframework.util.StringUtils;

/** Immutable business scope attached to an ASN product preflight proof. */
final class OfficialWarehouseAsnPreflightScope {
    private final Long ownerUserId;
    private final Long logicalStoreId;
    private final String projectCode;
    private final String storeCode;
    private final String siteCode;
    private final String partnerId;
    private final String businessType;
    private final String businessId;
    private final String businessRef;

    private OfficialWarehouseAsnPreflightScope(
            NoonSalesReportBinding binding,
            NoonCallContext context
    ) {
        if (binding == null || context == null
                || binding.getOwnerUserId() == null || binding.getLogicalStoreId() == null) {
            throw new IllegalArgumentException("官方仓商品预检缺少业务范围。");
        }
        this.ownerUserId = binding.getOwnerUserId();
        this.logicalStoreId = binding.getLogicalStoreId();
        this.projectCode = required(binding.getProjectCode());
        this.storeCode = required(binding.getStoreCode());
        this.siteCode = required(binding.getSiteCode());
        this.partnerId = required(binding.getPartnerId());
        this.businessType = required(context.businessType);
        this.businessId = required(context.businessId);
        this.businessRef = required(context.businessRef);
    }

    static OfficialWarehouseAsnPreflightScope capture(
            NoonSalesReportBinding binding,
            NoonCallContext context
    ) {
        return new OfficialWarehouseAsnPreflightScope(binding, context);
    }

    void assertMatches(NoonSalesReportBinding binding, NoonCallContext context) {
        if (!equals(capture(binding, context))) {
            throw new IllegalArgumentException("官方仓商品预检凭证与当前业务范围不一致。");
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OfficialWarehouseAsnPreflightScope)) {
            return false;
        }
        OfficialWarehouseAsnPreflightScope scope = (OfficialWarehouseAsnPreflightScope) other;
        return Objects.equals(ownerUserId, scope.ownerUserId)
                && Objects.equals(logicalStoreId, scope.logicalStoreId)
                && Objects.equals(projectCode, scope.projectCode)
                && Objects.equals(storeCode, scope.storeCode)
                && Objects.equals(siteCode, scope.siteCode)
                && Objects.equals(partnerId, scope.partnerId)
                && Objects.equals(businessType, scope.businessType)
                && Objects.equals(businessId, scope.businessId)
                && Objects.equals(businessRef, scope.businessRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                ownerUserId, logicalStoreId, projectCode, storeCode, siteCode,
                partnerId, businessType, businessId, businessRef
        );
    }

    private static String required(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("官方仓商品预检缺少业务范围。");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
