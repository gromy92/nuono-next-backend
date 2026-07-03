package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProductImageProfileMapper;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ProductImageProfileMapperSqlTest {

    @Test
    void profileIdentityLookupUsesLogicalStoreScopeAndCanonicalAssetRichRecord() throws Exception {
        Method method = ProductImageProfileMapper.class.getMethod(
                "selectProfileByIdentity",
                Long.class,
                String.class,
                String.class,
                String.class
        );
        String sql = annotationSql(method.getAnnotation(Select.class));

        assertThat(sql)
                .contains("p.logical_store_id = (")
                .contains("FROM logical_store_site lss")
                .contains("ls.owner_user_id = #{ownerUserId}")
                .contains("COALESCE(ac.asset_count, 0) DESC")
                .contains("p.updated_at DESC");
    }

    @Test
    void profileListGroupsHistoricalSiteDuplicatesIntoOneCanonicalProfile() throws Exception {
        Method method = ProductImageProfileMapper.class.getMethod(
                "selectProfilesForStore",
                Long.class,
                String.class,
                String.class
        );
        Select select = method.getAnnotation(Select.class);
        String rawSql = String.join("\n", select.value());
        String sql = rawSql.replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("canonical_id")
                .contains("p2.logical_store_id = (")
                .contains("GROUP BY p2.owner_user_id, p2.psku_code, p2.product_identity_key")
                .contains("COALESCE(ac2.asset_count, 0) DESC");

        new XMLLanguageDriver().createSqlSource(new Configuration(), rawSql, Object.class);
    }

    @Test
    void productCandidatesUseAllSitesUnderRequestedLogicalStore() throws Exception {
        Method method = ProductImageProfileMapper.class.getMethod(
                "selectProductCandidates",
                Long.class,
                String.class,
                String.class
        );
        Select select = method.getAnnotation(Select.class);
        String rawSql = String.join("\n", select.value());
        String sql = rawSql.replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("FROM logical_store_site request_lss")
                .contains("pm.logical_store_id = ls.id")
                .contains("pso.logical_store_id = ls.id")
                .contains("COALESCE(NULLIF(pm.partner_sku, ''), NULLIF(MAX(pso.partner_sku), '')) AS psku_code")
                .contains("CONCAT('psku:', COALESCE(NULLIF(pm.partner_sku, ''), NULLIF(MAX(pso.partner_sku), ''))) AS product_identity_key")
                .contains("HAVING psku_code IS NOT NULL")
                .doesNotContain("pso.site_id = request_lss.id")
                .doesNotContain("pso.site_code = request_lss.site")
                .doesNotContain("NULLIF(pm.current_z_code, ''), NULLIF(MAX(pso.psku_code), ''), pm.sku_parent");

        new XMLLanguageDriver().createSqlSource(new Configuration(), rawSql, Object.class);
    }

    @Test
    void allProductCandidatesUseOnlySystemPskuForIdentity() throws Exception {
        Method method = ProductImageProfileMapper.class.getMethod(
                "selectAllProductCandidatesForStore",
                Long.class,
                String.class
        );
        String sql = annotationSql(method.getAnnotation(Select.class));

        assertThat(sql)
                .contains("COALESCE(NULLIF(pm.partner_sku, ''), NULLIF(MAX(pso.partner_sku), '')) AS psku_code")
                .contains("CONCAT('psku:', COALESCE(NULLIF(pm.partner_sku, ''), NULLIF(MAX(pso.partner_sku), ''))) AS product_identity_key")
                .contains("HAVING psku_code IS NOT NULL")
                .doesNotContain("NULLIF(pm.current_z_code, ''), NULLIF(MAX(pso.psku_code), ''), pm.sku_parent");
    }

    @Test
    void profileInsertPersistsLogicalStoreIdFromStoreCode() throws Exception {
        Method method = ProductImageProfileMapper.class.getMethod(
                "insertProfile",
                ProductImageProfileRecord.class
        );
        String sql = String.join(" ", method.getAnnotation(Insert.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("logical_store_id")
                .contains("COALESCE(#{logicalStoreId},")
                .contains("lss.store_code = #{storeCode}")
                .contains("ls.owner_user_id = #{ownerUserId}");
    }

    @Test
    void profileUpdateDoesNotRequireHistoricalStoreCodeMatch() throws Exception {
        Method method = ProductImageProfileMapper.class.getMethod(
                "updateProfile",
                ProductImageProfileRecord.class
        );
        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("logical_store_id = COALESCE")
                .contains("WHERE id = #{id}")
                .contains("AND owner_user_id = #{ownerUserId}")
                .doesNotContain("AND store_code = #{storeCode}");
    }

    @Test
    void migrationAddsLogicalStoreScopeAndAssetSourceColumns() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/init/159_product_image_logical_store_scope.sql"));
        String bootstrap = Files.readString(Path.of("src/main/java/com/nuono/next/system/LocalDbBootstrapStatusService.java"));

        assertThat(sql)
                .contains("ADD COLUMN `logical_store_id`")
                .contains("UPDATE product_image_profile p")
                .contains("CONVERT(lss.store_code USING utf8mb4) COLLATE utf8mb4_unicode_ci")
                .contains("CONVERT(p.store_code USING utf8mb4) COLLATE utf8mb4_unicode_ci")
                .contains("ADD INDEX `idx_product_image_profile_logical_scope`")
                .contains("ADD INDEX `idx_product_image_profile_logical_identity`")
                .contains("ADD COLUMN `source_store_code`")
                .contains("ADD COLUMN `source_site_code`")
                .contains("ADD COLUMN `source_snapshot_id`")
                .contains("ADD COLUMN `source_field`")
                .contains("ADD COLUMN `source_kind`");
        assertThat(bootstrap).contains("classpath:db/init/159_product_image_logical_store_scope.sql");
    }

    private static String annotationSql(Select select) {
        return String.join(" ", select.value()).replaceAll("\\s+", " ");
    }
}
