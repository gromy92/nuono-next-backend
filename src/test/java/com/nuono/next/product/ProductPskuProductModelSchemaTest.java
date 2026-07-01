package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductPskuProductModelSchemaTest {

    @Test
    void migrationPromotesProductMasterToPskuMasterAndSiteOfferToPskuSiteState() throws Exception {
        String migration = Files.readString(Path.of("src/main/resources/db/init/152_psku_product_model_realignment.sql"));
        String bootstrap = Files.readString(Path.of("src/main/java/com/nuono/next/system/LocalDbBootstrapStatusService.java"));

        assertThat(bootstrap).contains("classpath:db/init/152_psku_product_model_realignment.sql");
        assertThat(migration)
                .contains("ALTER TABLE `product_master` ADD COLUMN `partner_sku`")
                .contains("ALTER TABLE `product_master` ADD COLUMN `current_z_code`")
                .contains("ALTER TABLE `product_site_offer` ADD COLUMN `product_master_id`")
                .contains("ALTER TABLE `product_site_offer` ADD COLUMN `logical_store_id`")
                .contains("ALTER TABLE `product_site_offer` ADD COLUMN `partner_sku`")
                .contains("ALTER TABLE `product_site_offer` ADD COLUMN `site_code`")
                .contains("uk_product_master_store_partner_sku")
                .contains("uk_product_site_offer_store_psku_site")
                .contains("product_psku_model_realignment_violation")
                .contains("MULTI_ACTIVE_PARTNER_SKU_PER_MASTER")
                .contains("DUPLICATE_PRODUCT_MASTER_STORE_PARTNER_SKU")
                .contains("DUPLICATE_PRODUCT_SITE_OFFER_STORE_PSKU_SITE")
                .contains("HAVING COUNT(DISTINCT TRIM(pv.partner_sku)) > 1")
                .contains("HAVING COUNT(DISTINCT TRIM(pv.partner_sku)) = 1")
                .contains("AND NOT EXISTS (")
                .contains("SET pm.partner_sku = chosen.partner_sku")
                .contains("NULLIF(TRIM(pm.partner_sku), '') IS NULL")
                .contains("OR TRIM(pm.partner_sku) <> chosen.partner_sku")
                .contains("pm.current_z_code = pm.sku_parent")
                .contains("pso.partner_sku = TRIM(pv.partner_sku)")
                .contains("pso.site_code = TRIM(lss.site)")
                .contains("ALTER TABLE `product_forwarder_declaration_attribute` ADD COLUMN `logical_store_id`")
                .contains("ALTER TABLE `product_forwarder_declaration_attribute` ADD COLUMN `source_store_code`")
                .contains("ALTER TABLE `product_forwarder_declaration_attribute` ADD COLUMN `partner_sku`")
                .contains("idx_pfda_owner_store_psku_attr")
                .contains("ALTER TABLE `product_forwarder_channel_quote` ADD COLUMN `logical_store_id`")
                .contains("ALTER TABLE `product_forwarder_channel_quote` ADD COLUMN `source_store_code`")
                .contains("ALTER TABLE `product_forwarder_channel_quote` ADD COLUMN `partner_sku`")
                .contains("idx_pfcq_owner_store_psku_channel")
                .contains("pv.is_deleted = b'0'")
                .contains("pm.is_deleted = b'0'")
                .contains("product_variant remains a migration compatibility table")
                .doesNotContain("DROP TABLE `product_variant`")
                .doesNotContain("DROP COLUMN `variant_id`");
    }

    @Test
    void forwarderLegacyBackfillUsesCanonicalPskuIdentityWithoutRewritingSourceStore() throws Exception {
        String migration = Files.readString(Path.of("src/main/resources/db/init/153_psku_product_model_forwarder_legacy_backfill.sql"));
        String bootstrap = Files.readString(Path.of("src/main/java/com/nuono/next/system/LocalDbBootstrapStatusService.java"));

        assertThat(bootstrap).contains("classpath:db/init/153_psku_product_model_forwarder_legacy_backfill.sql");
        assertThat(migration)
                .contains("UPDATE `product_forwarder_declaration_attribute` pfda")
                .contains("UPDATE `product_forwarder_channel_quote` pfcq")
                .contains("LEFT JOIN `product_variant_identity_merge_map` merge_map")
                .contains("merge_map.canonical_variant_id")
                .contains("canonical_pv.logical_store_id")
                .contains("canonical_pv.partner_sku")
                .contains("canonical_pm.partner_sku")
                .contains("ls.owner_user_id = pfda.owner_user_id")
                .contains("ls.owner_user_id = pfcq.owner_user_id")
                .contains("pfda.logical_store_id = ls.id")
                .contains("pfcq.logical_store_id = ls.id")
                .contains("pfda.partner_sku = COALESCE")
                .contains("pfcq.partner_sku = COALESCE")
                .doesNotContain("source_store_code =")
                .doesNotContain("DELETE FROM")
                .doesNotContain("DROP TABLE")
                .doesNotContain("DROP COLUMN");
    }
}
