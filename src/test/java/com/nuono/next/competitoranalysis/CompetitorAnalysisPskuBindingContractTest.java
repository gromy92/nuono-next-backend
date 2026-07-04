package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CompetitorAnalysisPskuBindingContractTest {
    private static final Path MAPPER_PATH = Path.of(
            "src",
            "main",
            "java",
            "com",
            "nuono",
            "next",
            "infrastructure",
            "mapper",
            "CompetitorAnalysisMapper.java"
    );
    private static final Path SERVICE_PATH = Path.of(
            "src",
            "main",
            "java",
            "com",
            "nuono",
            "next",
            "competitoranalysis",
            "CompetitorAnalysisService.java"
    );
    private static final Path SCHEMA_PATH = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "099_operations_competitor_analysis.sql"
    );
    private static final Path MIGRATION_PATH = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "161_operations_competitor_watch_product_psku_identity.sql"
    );

    @Test
    void productBaselinesReuseWatchProductByCurrentOfferOrPartnerSkuOnly() throws IOException {
        String source = Files.readString(MAPPER_PATH).toLowerCase(Locale.ROOT);

        assertTrue(source.contains("wp.id = ("), "product baseline rows must choose one reusable watch product");
        assertTrue(source.contains("wp_match.product_site_offer_id = pso.id"), "current offer id remains the first match");
        assertTrue(source.contains("wp_match.partner_sku = pv.partner_sku"), "rebuilt product rows must bind by product_variant.partner_sku");
        assertTrue(source.contains("wp_match.logical_store_id = ls.id"), "rebuilt product rows must stay within the same logical store");
        assertTrue(source.contains("wp_match.store_code = #{storecode}"), "same partner_sku in another store must not be reused");
        assertTrue(source.contains("upper(wp_match.site_code) = upper(#{sitecode})"), "same partner_sku in another site must not be reused");
        assertFalse(source.contains("wp_match.psku_code = pso.psku_code"), "Noon pskuCode must not be a fallback binding key");
        assertFalse(source.contains("wp_match.sku_parent = pm.sku_parent"), "Z/N sku_parent must not be a fallback binding key");
        assertTrue(source.contains("collate utf8mb4_unicode_ci"), "partner_sku comparisons must avoid table collation drift");
        assertTrue(source.contains("case when wp_match.product_site_offer_id = pso.id and wp_match.partner_sku = pv.partner_sku"),
                "current offer binding must win over fallback identity matches");
        assertTrue(source.contains("pso.psku_code as pskucode"), "baseline rows may expose the current Noon pskuCode");
        assertTrue(source.contains("pv.partner_sku as partnersku"), "baseline rows must expose product_variant.partner_sku as system PSKU");
    }

    @Test
    void watchProductSchemaDoesNotUseNoonPskuCodeAsIdentity() throws IOException {
        String source = Files.readString(SCHEMA_PATH).toLowerCase(Locale.ROOT);

        assertTrue(source.contains("`psku_code`"), "watch product may store Noon pskuCode as an external code");
        assertTrue(source.contains("`partner_sku`"), "watch product identity must store product_variant.partner_sku");
        assertTrue(source.contains("`owner_user_id`, '|', `store_code`, '|', `site_code`, '|',"),
                "active identity must include owner, store and site");
        assertTrue(source.contains("`idx_ops_comp_watch_partner`"), "partner_sku lookup needs an index");
        assertFalse(source.contains("add column `active_psku_slot`"), "Noon pskuCode must not define active identity");
        assertFalse(source.contains("add unique key `uk_ops_comp_watch_active_psku`"), "Noon pskuCode must not have active identity uniqueness");
        assertTrue(source.contains("drop column `active_psku_slot`"), "old Noon pskuCode identity column must be cleaned up");
        assertTrue(source.contains("drop index `uk_ops_comp_watch_active_psku`"), "old Noon pskuCode identity index must be cleaned up");
        assertTrue(source.contains("alter table `operations_competitor_watch_product` add column `psku_code`"),
                "existing databases may receive the external Noon pskuCode column");
        assertTrue(source.contains("update operations_competitor_watch_product wp"),
                "existing watch products may be backfilled with current external Noon pskuCode");
    }

    @Test
    void existingDatabasesReceivePartnerSkuIdentityMigration() throws IOException {
        String source = Files.readString(MIGRATION_PATH).toLowerCase(Locale.ROOT);

        assertTrue(source.contains("add column `psku_code`"),
                "existing databases need the external Noon pskuCode snapshot column");
        assertTrue(source.contains("drop column `active_psku_slot`"),
                "existing databases must drop the old Noon pskuCode identity slot");
        assertTrue(source.contains("drop index `uk_ops_comp_watch_active_psku`"),
                "existing databases must drop the old Noon pskuCode identity key");
        assertTrue(source.contains("concat(`owner_user_id`, ''|'', `store_code`, ''|'', `site_code`, ''|'', `partner_sku`)"),
                "existing databases must align uniqueness to owner/store/site + partner_sku");
        assertTrue(source.contains("update operations_competitor_watch_product wp"),
                "existing watch products may backfill current Noon external code from product_site_offer");
    }

    @Test
    void createWatchProductReusesExistingPartnerSkuBindingBeforeInsert() throws IOException {
        String source = Files.readString(SERVICE_PATH);

        int lookupIndex = source.indexOf("mapper.selectReusableWatchProductByProductIdentity");
        int insertIndex = source.indexOf("mapper.insertWatchProduct");
        assertTrue(lookupIndex >= 0, "createWatchProduct must look up an existing partner_sku binding");
        assertTrue(insertIndex > lookupIndex, "existing partner_sku binding must be checked before inserting a new watch product");
        assertTrue(source.contains("mapper.updateWatchProductCurrentBinding("),
                "reused partner_sku binding must refresh current product ids and Noon external codes");
    }

    @Test
    void createWatchProductCanResolveCurrentOfferByPartnerSkuWithoutUsingNoonPskuCode() throws IOException {
        String source = Files.readString(MAPPER_PATH).toLowerCase(Locale.ROOT);

        assertTrue(source.contains("competitorproductoptionrow selectproductoptionbypartnersku"),
                "createWatchProduct needs a stable partnerSku option lookup for rebuilt baselines");
        assertTrue(source.contains("pv.partner_sku = cast(#{partnersku} as char character set utf8mb4) collate utf8mb4_unicode_ci"),
                "partnerSku option lookup must use product_variant.partner_sku as system PSKU");
        assertFalse(source.contains("pso.psku_code = cast(#{partnersku}"),
                "Noon external pskuCode must not be used as system partnerSku");
    }

    @Test
    void baselineAvailabilityComesFromPersistedProductMasterSnapshot() throws IOException {
        String source = Files.readString(MAPPER_PATH).toLowerCase(Locale.ROOT);

        assertTrue(source.contains("product_master_snapshot pms"), "competitor baseline rows must read persisted snapshots");
        assertTrue(source.contains("pms_latest.snapshot_type = 'baseline'"),
                "baseline availability must be based on product_master_snapshot snapshot_type='baseline'");
        assertFalse(source.contains("/open"), "product workbench /open readiness must not define baseline availability");
        assertFalse(source.contains("ready=true"), "frontend ready flags must not define baseline availability");
    }
}
