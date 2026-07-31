package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class ProductActiveStateUpsertSqlTest {
    @Test
    void siteOfferUpsertPreservesBusinessIdentityAndOmittedActiveState() throws Exception {
        Method method = ProductManagementMapper.class.getMethod(
                "upsertProductSiteOffer",
                Long.class,
                Long.class,
                Long.class,
                String.class,
                Long.class,
                Long.class,
                String.class,
                String.class,
                String.class,
                String.class,
                java.math.BigDecimal.class,
                java.math.BigDecimal.class,
                java.time.LocalDateTime.class,
                java.time.LocalDateTime.class,
                java.math.BigDecimal.class,
                java.math.BigDecimal.class,
                java.math.BigDecimal.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                java.time.LocalDateTime.class,
                String.class,
                String.class,
                java.math.BigDecimal.class,
                java.math.BigDecimal.class,
                Integer.class,
                String.class,
                String.class,
                Boolean.class,
                Boolean.class,
                String.class,
                java.time.LocalDateTime.class,
                String.class,
                String.class,
                java.time.LocalDateTime.class,
                String.class,
                Integer.class,
                Integer.class,
                Integer.class,
                Long.class,
                Long.class,
                java.math.BigDecimal.class,
                String.class,
                java.time.LocalDateTime.class,
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Insert.class).value()).replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("id, product_master_id, logical_store_id, partner_sku, variant_id, site_id, site_code")
                .contains("#{productMasterId}")
                .contains("#{logicalStoreId}")
                .contains("#{partnerSku}")
                .contains("#{siteCode}")
                .contains("product_master_id = COALESCE(VALUES(product_master_id), product_master_id)")
                .contains("logical_store_id = COALESCE(VALUES(logical_store_id), logical_store_id)")
                .contains("partner_sku = COALESCE(NULLIF(VALUES(partner_sku), ''), partner_sku)")
                .contains("site_code = COALESCE(NULLIF(VALUES(site_code), ''), site_code)")
                .contains("is_active = COALESCE(VALUES(is_active), is_active)")
                .contains("active_state_source = CASE WHEN VALUES(is_active) IS NULL")
                .contains("active_state_synced_at = CASE WHEN VALUES(is_active) IS NULL")
                .contains("live_status = COALESCE(VALUES(live_status), live_status)");
    }

    @Test
    void completeProductListMarksOnlyMissingProductsInactiveInExactScope() throws Exception {
        Method method = ProductManagementMapper.class.getMethod(
                "markProductOffersMissingFromCompleteListInactive",
                Long.class,
                String.class,
                String.class,
                java.util.List.class,
                String.class,
                java.time.LocalDateTime.class,
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Update.class).value()).replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("ls.owner_user_id = #{ownerUserId}")
                .contains("UPPER(lss.store_code) = UPPER(#{storeCode})")
                .contains("UPPER(lss.site) = UPPER(#{siteCode})")
                .contains("pso.maintenance_enabled = b'1'")
                .contains("pso.is_active = b'0'")
                .contains("pso.active_state_source = #{activeStateSource}")
                .contains("UPPER(TRIM(pv.partner_sku)) NOT IN")
                .contains("collection='presentPartnerSkus'");
    }
}
