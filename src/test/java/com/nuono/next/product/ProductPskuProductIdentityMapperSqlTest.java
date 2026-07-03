package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;

class ProductPskuProductIdentityMapperSqlTest {

    @Test
    void productIdentityTrimsPskuAndRequiresStore() {
        ProductIdentity identity = new ProductIdentity(50003L, " SGGRB113 ");

        assertThat(identity.logicalStoreId()).isEqualTo(50003L);
        assertThat(identity.partnerSku()).isEqualTo("SGGRB113");
        assertThat(identity.isComplete()).isTrue();
        assertThat(identity).isEqualTo(new ProductIdentity(50003L, "SGGRB113"));
        assertThat(identity).hasSameHashCodeAs(new ProductIdentity(50003L, "SGGRB113"));
        assertThat(new ProductIdentity(50003L, " ").isComplete()).isFalse();
        assertThat(new ProductIdentity(null, "SGGRB113").isComplete()).isFalse();
        assertThat(new ProductIdentity(50003L, "SGGRB113")).isNotEqualTo(new ProductIdentity(50003L, "OTHER"));
    }

    @Test
    void productSiteIdentityTrimsPskuAndSiteAndRequiresAllParts() {
        ProductSiteIdentity identity = new ProductSiteIdentity(50003L, " SGGRB113 ", " NSA ");

        assertThat(identity.logicalStoreId()).isEqualTo(50003L);
        assertThat(identity.partnerSku()).isEqualTo("SGGRB113");
        assertThat(identity.siteCode()).isEqualTo("NSA");
        assertThat(identity.isComplete()).isTrue();
        assertThat(identity).isEqualTo(new ProductSiteIdentity(50003L, "SGGRB113", "NSA"));
        assertThat(identity).hasSameHashCodeAs(new ProductSiteIdentity(50003L, "SGGRB113", "NSA"));
        assertThat(new ProductSiteIdentity(50003L, "SGGRB113", " ").isComplete()).isFalse();
        assertThat(new ProductSiteIdentity(50003L, "SGGRB113", "NSA"))
                .isNotEqualTo(new ProductSiteIdentity(50003L, "SGGRB113", "NAE"));
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
                .contains("partner_sku = COALESCE(NULLIF(VALUES(partner_sku), ''), partner_sku)")
                .contains("current_z_code = COALESCE(NULLIF(VALUES(current_z_code), ''), current_z_code)")
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
                .contains("ORDER BY gmt_updated DESC, id DESC")
                .doesNotContain("sku_parent = #{skuParent}");
    }

    @Test
    void legacyProductMasterResolverPrefersCurrentZBeforeSkuParentFallback() {
        ProductManagementMapper mapper = Mockito.mock(ProductManagementMapper.class, Answers.CALLS_REAL_METHODS);
        when(mapper.selectProductMasterIdByCurrentZCode(50003L, "ZCURRENT001")).thenReturn(52001L);

        Long productMasterId = mapper.selectProductMasterId(50003L, " ZCURRENT001 ");

        assertThat(productMasterId).isEqualTo(52001L);
        verify(mapper).selectProductMasterIdByCurrentZCode(50003L, "ZCURRENT001");
        verify(mapper, never()).selectProductMasterIdByLegacySkuParent(any(), any());
    }

    @Test
    void legacyProductMasterResolverFallsBackToSkuParentAfterCurrentZMiss() {
        ProductManagementMapper mapper = Mockito.mock(ProductManagementMapper.class, Answers.CALLS_REAL_METHODS);
        when(mapper.selectProductMasterIdByCurrentZCode(50003L, "ZLEGACY001")).thenReturn(null);
        when(mapper.selectProductMasterIdByLegacySkuParent(50003L, "ZLEGACY001")).thenReturn(52002L);

        Long productMasterId = mapper.selectProductMasterId(50003L, "ZLEGACY001");

        assertThat(productMasterId).isEqualTo(52002L);
        verify(mapper).selectProductMasterIdByCurrentZCode(50003L, "ZLEGACY001");
        verify(mapper).selectProductMasterIdByLegacySkuParent(50003L, "ZLEGACY001");
    }

    @Test
    void partnerSkuProjectionResolverUsesSingleProductProjectionInsteadOfStoreScan() {
        ProductManagementMapper mapper = Mockito.mock(ProductManagementMapper.class, Answers.CALLS_REAL_METHODS);
        ProductListProjectionRecord record = new ProductListProjectionRecord();
        when(mapper.selectProductMasterIdByStorePartnerSku(50003L, "SGGRB113")).thenReturn(52001L);
        when(mapper.selectLogicalStoreOwnerUserId(50003L)).thenReturn(307L);
        when(mapper.selectProductListProjectionByProductMasterId(307L, "STR245027-NSA", 52001L)).thenReturn(record);

        ProductListProjectionRecord resolved = mapper.selectProductListProjectionByStorePartnerSku(
                50003L,
                " STR245027-NSA ",
                " SGGRB113 "
        );

        assertThat(resolved).isSameAs(record);
        verify(mapper, never()).selectProductListProjection(any(), any());
    }

    @Test
    void storeCodeProductMasterResolverPrefersCurrentZBeforeSkuParentFallback() {
        ProductManagementMapper mapper = Mockito.mock(ProductManagementMapper.class, Answers.CALLS_REAL_METHODS);
        when(mapper.selectProductMasterIdByStoreCodeCurrentZCode(307L, "STR245027-NSA", "ZCURRENT001"))
                .thenReturn(52001L);

        Long productMasterId = mapper.selectProductMasterIdByStoreCode(307L, " STR245027-NSA ", " ZCURRENT001 ");

        assertThat(productMasterId).isEqualTo(52001L);
        verify(mapper).selectProductMasterIdByStoreCodeCurrentZCode(307L, "STR245027-NSA", "ZCURRENT001");
        verify(mapper, never()).selectProductMasterIdByStoreCodeLegacySkuParent(any(), any(), any());
    }

    @Test
    void storeCodeProductMasterResolverFallsBackToLegacySkuParentAfterCurrentZMiss() {
        ProductManagementMapper mapper = Mockito.mock(ProductManagementMapper.class, Answers.CALLS_REAL_METHODS);
        when(mapper.selectProductMasterIdByStoreCodeCurrentZCode(307L, "STR245027-NSA", "ZLEGACY001"))
                .thenReturn(null);
        when(mapper.selectProductMasterIdByStoreCodeLegacySkuParent(307L, "STR245027-NSA", "ZLEGACY001"))
                .thenReturn(52002L);

        Long productMasterId = mapper.selectProductMasterIdByStoreCode(307L, "STR245027-NSA", "ZLEGACY001");

        assertThat(productMasterId).isEqualTo(52002L);
        verify(mapper).selectProductMasterIdByStoreCodeCurrentZCode(307L, "STR245027-NSA", "ZLEGACY001");
        verify(mapper).selectProductMasterIdByStoreCodeLegacySkuParent(307L, "STR245027-NSA", "ZLEGACY001");
    }

    @Test
    void skuParentProjectionResolverUsesProductMasterResolverAndSingleProjection() {
        ProductManagementMapper mapper = Mockito.mock(ProductManagementMapper.class, Answers.CALLS_REAL_METHODS);
        ProductListProjectionRecord record = new ProductListProjectionRecord();
        when(mapper.selectProductMasterIdByStoreCodeCurrentZCode(307L, "STR245027-NSA", "ZCURRENT001"))
                .thenReturn(52001L);
        when(mapper.selectProductListProjectionByProductMasterId(307L, "STR245027-NSA", 52001L)).thenReturn(record);

        ProductListProjectionRecord resolved = mapper.selectProductListProjectionBySkuParent(
                307L,
                " STR245027-NSA ",
                " ZCURRENT001 "
        );

        assertThat(resolved).isSameAs(record);
        verify(mapper).selectProductMasterIdByStoreCodeCurrentZCode(307L, "STR245027-NSA", "ZCURRENT001");
        verify(mapper).selectProductListProjectionByProductMasterId(307L, "STR245027-NSA", 52001L);
    }

    @Test
    void groupCandidateContextResolverUsesProductMasterResolverAndSingleContextQuery() {
        ProductManagementMapper mapper = Mockito.mock(ProductManagementMapper.class, Answers.CALLS_REAL_METHODS);
        ProductGroupCandidateContextRecord record = new ProductGroupCandidateContextRecord();
        when(mapper.selectProductMasterIdByStoreCodeCurrentZCode(307L, "STR245027-NSA", "ZCURRENT001"))
                .thenReturn(52001L);
        when(mapper.selectProductGroupCandidateContextByProductMasterId(307L, "STR245027-NSA", 52001L))
                .thenReturn(record);

        ProductGroupCandidateContextRecord resolved = mapper.selectProductGroupCandidateContext(
                307L,
                " STR245027-NSA ",
                " ZCURRENT001 "
        );

        assertThat(resolved).isSameAs(record);
        verify(mapper).selectProductMasterIdByStoreCodeCurrentZCode(307L, "STR245027-NSA", "ZCURRENT001");
        verify(mapper).selectProductGroupCandidateContextByProductMasterId(307L, "STR245027-NSA", 52001L);
    }

    @Test
    void singleProductProjectionSqlTargetsProductMasterId() throws Exception {
        Method method = ProductManagementMapper.class.getMethod(
                "selectProductListProjectionByProductMasterId",
                Long.class,
                String.class,
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value()).replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("pm.id = #{productMasterId}")
                .doesNotContain("pm.current_z_code = #{skuParent} OR pm.sku_parent = #{skuParent}");
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
                .contains("site_code = COALESCE(NULLIF(VALUES(site_code), ''), site_code)");
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
