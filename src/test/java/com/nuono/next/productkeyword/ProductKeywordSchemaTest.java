package com.nuono.next.productkeyword;

import static com.nuono.next.schema.DbInitScriptAssertions.assertInitScriptsInclude;
import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.permission.access.BusinessCapability;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ProductKeywordSchemaTest {
    private static final Path SCHEMA_PATH = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "174_product_keyword_management.sql"
    );

    @Test
    void schemaDefinesKeywordAssetsAndIdempotentUsageEvents() throws Exception {
        String sql = Files.readString(SCHEMA_PATH);

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS `product_keyword_id_sequence`")
                .contains("CREATE TABLE IF NOT EXISTS `product_keyword`")
                .contains("CREATE TABLE IF NOT EXISTS `product_keyword_usage_event`")
                .contains("`site_code` VARCHAR(32) NOT NULL")
                .contains("`status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'")
                .contains("`active_unique_key` TINYINT")
                .contains("`event_natural_key` VARCHAR(512) NOT NULL")
                .contains("UNIQUE KEY `uk_product_keyword_scope`")
                .contains("UNIQUE KEY `uk_product_keyword_event_natural`")
                .contains("KEY `idx_product_keyword_store_status`")
                .contains("KEY `idx_product_keyword_event_source_key`")
                .contains("`source_type` VARCHAR(40) NOT NULL")
                .contains("`payload_json` LONGTEXT DEFAULT NULL")
                .contains("`metrics_json` LONGTEXT DEFAULT NULL")
                .contains("GREATEST(COALESCE(MAX(`id`), 0) + 1, 300000)");
        assertThat(sql)
                .doesNotContain("product_title_snapshot")
                .doesNotContain("product_keyword_product_rollup")
                .doesNotContain("product_keyword_source_anchor");
    }

    @Test
    void schemaSeedsKeywordOperationsMenuAndBusinessRoleGrants() throws Exception {
        String sql = Files.readString(SCHEMA_PATH);

        assertThat(sql)
                .contains("9800")
                .contains("9804")
                .contains("运营")
                .contains("关键词数据")
                .contains("/operations")
                .contains("/operations/product-keywords");
        assertThat(roleGrant(1, 9804).matcher(sql).find())
                .as("system admin must not receive store-level keyword access by default")
                .isFalse();
        assertThat(roleGrant(1, 9800).matcher(sql).find())
                .as("system admin must not receive operations business access by default")
                .isFalse();
        assertThat(roleGrant(2, 9800).matcher(sql).find()).isTrue();
        assertThat(roleGrant(2, 9804).matcher(sql).find()).isTrue();
        assertThat(roleGrant(3, 9800).matcher(sql).find()).isTrue();
        assertThat(roleGrant(3, 9804).matcher(sql).find()).isTrue();
        assertThat(roleGrant(4, 9800).matcher(sql).find()).isTrue();
        assertThat(roleGrant(4, 9804).matcher(sql).find()).isTrue();
    }

    @Test
    void localDbBootstrapIncludesKeywordSchema() throws Exception {
        assertInitScriptsInclude(
                "classpath:db/init/174_product_keyword_management.sql",
                "classpath:db/init/175_product_keyword_competitor_link.sql"
        );
    }

    @Test
    void keywordCapabilityCoversPageAndApiRoutes() {
        assertThat(BusinessCapability.PRODUCT_KEYWORD_MANAGEMENT.getMenuPathPrefixes())
                .contains("/operations/product-keywords", "/api/product-keywords");
    }

    private static Pattern roleGrant(int roleId, int menuId) {
        return Pattern.compile(
                "(\\(\\s*" + roleId + "\\s*,\\s*" + menuId + "\\s*\\))"
                        + "|(SELECT\\s+" + roleId + "\\s+AS\\s+`role_id`,\\s*" + menuId + "\\s+AS\\s+`menu_id`)"
                        + "|(UNION\\s+ALL\\s+SELECT\\s+" + roleId + "\\s*,\\s*" + menuId + ")",
                Pattern.CASE_INSENSITIVE
        );
    }
}
