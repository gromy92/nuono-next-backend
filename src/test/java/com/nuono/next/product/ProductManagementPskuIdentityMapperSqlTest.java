package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class ProductManagementPskuIdentityMapperSqlTest {

    @Test
    void productVariantUpsertPersistsStoreScopedPskuIdentity() throws Exception {
        Method method = ProductManagementMapper.class.getMethod(
                "upsertProductVariant",
                Long.class,
                Long.class,
                Long.class,
                String.class,
                String.class,
                String.class,
                String.class,
                Integer.class,
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Insert.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("logical_store_id")
                .contains("#{logicalStoreId}")
                .contains("partner_sku = VALUES(partner_sku)")
                .contains("product_master_id = VALUES(product_master_id)");
    }

    @Test
    void productVariantResolverUsesStoreAndPartnerSkuInsteadOfCurrentMaster() throws Exception {
        Method method = ProductManagementMapper.class.getMethod(
                "selectProductVariantIdByStorePartnerSku",
                Long.class,
                String.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("logical_store_id = #{logicalStoreId}")
                .contains("partner_sku = #{partnerSku}")
                .doesNotContain("product_master_id = #{productMasterId}");
    }

    @Test
    void productMasterIdentityResolverUsesStoreAndPartnerSkuWithoutPskuCodeFallback() throws Exception {
        Method method = ProductManagementMapper.class.getMethod(
                "selectProductMasterIdentityByStorePartnerSku",
                Long.class,
                String.class,
                String.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("anchor.store_code = #{storeCode}")
                .contains("pv.logical_store_id = ls.id")
                .contains("pv.partner_sku = #{partnerSku}")
                .contains("pso.site_id = anchor.id")
                .contains("anchor.site AS siteCode")
                .doesNotContain("pso.psku_code = #{partnerSku}")
                .doesNotContain("psku_code = #{partnerSku}")
                .doesNotContain("pm.sku_parent = #{partnerSku}");
    }

    @Test
    void productMasterUpsertCanRefreshSkuParentForStablePartnerSkuMaster() throws Exception {
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
                Integer.class,
                Integer.class,
                String.class,
                Integer.class,
                String.class,
                java.time.LocalDateTime.class,
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Insert.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("sku_parent = VALUES(sku_parent)");
    }

    @Test
    void pskuIdentityMigrationIsLoadedAndStoreScoped() throws Exception {
        String bootstrap = Files.readString(Path.of("src/main/java/com/nuono/next/system/LocalDbBootstrapStatusService.java"));
        String migration = Files.readString(Path.of("src/main/resources/db/init/149_product_variant_psku_identity.sql"));
        String baseline = Files.readString(Path.of("src/main/resources/db/init/003_product_management_v1.sql"));

        assertThat(bootstrap).contains("classpath:db/init/149_product_variant_psku_identity.sql");
        assertThat(migration)
                .contains("business PSKU = product_variant.partner_sku")
                .contains("product_variant_identity_merge_map")
                .contains("product_site_offer_identity_merge_map")
                .contains("DROP INDEX `uk_product_variant_master_partner_sku`")
                .contains("uk_product_variant_store_partner_sku")
                .contains("`logical_store_id`, `partner_sku`")
                .contains("MIN(CASE WHEN pv.is_deleted = b'0' THEN pv.id END)")
                .contains("canonical.product_master_id AS target_product_master_id")
                .contains("snapshot.product_master_id = CASE WHEN canonical_snapshot.id IS NULL THEN merge_map.target_product_master_id")
                .contains("MIN(inner_pso.id) AS canonical_duplicate_site_offer_id")
                .contains("candidate.existing_canonical_site_offer_id")
                .contains("selected_duplicate_offer.canonical_duplicate_site_offer_id")
                .doesNotContain("MIN(pv.id) AS canonical_variant_id")
                .doesNotContain("COALESCE(canonical_pso.id, duplicate_pso.id) AS canonical_site_offer_id")
                .doesNotContain("UNIQUE KEY `uk_product_variant_master_partner_sku`");
        assertThat(baseline.toLowerCase(Locale.ROOT)).doesNotContain("uk_product_variant_master_partner_sku");
    }
}
