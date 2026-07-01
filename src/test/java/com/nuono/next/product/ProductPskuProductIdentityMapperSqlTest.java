package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class ProductPskuProductIdentityMapperSqlTest {

    @Test
    void productIdentityTrimsPskuAndRequiresStore() {
        ProductIdentity identity = new ProductIdentity(50003L, " SGGRB113 ");

        assertThat(identity.logicalStoreId()).isEqualTo(50003L);
        assertThat(identity.partnerSku()).isEqualTo("SGGRB113");
        assertThat(identity.isComplete()).isTrue();
        assertThat(new ProductIdentity(50003L, " ").isComplete()).isFalse();
        assertThat(new ProductIdentity(null, "SGGRB113").isComplete()).isFalse();
    }

    @Test
    void productSiteIdentityTrimsPskuAndSiteAndRequiresAllParts() {
        ProductSiteIdentity identity = new ProductSiteIdentity(50003L, " SGGRB113 ", " NSA ");

        assertThat(identity.logicalStoreId()).isEqualTo(50003L);
        assertThat(identity.partnerSku()).isEqualTo("SGGRB113");
        assertThat(identity.siteCode()).isEqualTo("NSA");
        assertThat(identity.isComplete()).isTrue();
        assertThat(new ProductSiteIdentity(50003L, "SGGRB113", " ").isComplete()).isFalse();
    }

    @Test
    void productMasterUpsertStoresPartnerSkuAndCurrentZCode() throws Exception {
        Method method = ProductManagementMapper.class.getMethod(
                "upsertProductMaster",
                Long.class,
                Long.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                Integer.class,
                Integer.class,
                String.class,
                Integer.class,
                String.class,
                java.time.LocalDateTime.class,
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Insert.class).value()).replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("logical_store_id, partner_sku, current_z_code, sku_parent")
                .contains("#{partnerSku}")
                .contains("#{currentZCode}")
                .contains("partner_sku = VALUES(partner_sku)")
                .contains("current_z_code = VALUES(current_z_code)")
                .contains("sku_parent = VALUES(sku_parent)");
    }

    @Test
    void productMasterResolverUsesStoreAndPartnerSku() throws Exception {
        Method method = ProductManagementMapper.class.getMethod(
                "selectProductMasterIdByStorePartnerSku",
                Long.class,
                String.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value()).replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("logical_store_id = #{logicalStoreId}")
                .contains("partner_sku = #{partnerSku}")
                .doesNotContain("sku_parent = #{skuParent}");
    }

    @Test
    void siteOfferUpsertStoresPskuSiteBusinessKey() throws Exception {
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
                .contains("product_master_id = VALUES(product_master_id)")
                .contains("logical_store_id = VALUES(logical_store_id)")
                .contains("partner_sku = VALUES(partner_sku)")
                .contains("site_code = VALUES(site_code)");
    }

    @Test
    void siteOfferResolverUsesStorePartnerSkuAndSite() throws Exception {
        Method method = ProductManagementMapper.class.getMethod(
                "selectProductSiteOfferIdByStorePartnerSkuSite",
                Long.class,
                String.class,
                String.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value()).replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("logical_store_id = #{logicalStoreId}")
                .contains("partner_sku = #{partnerSku}")
                .contains("site_code = #{siteCode}")
                .doesNotContain("variant_id = #{variantId}");
    }
}
