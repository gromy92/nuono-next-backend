package com.nuono.next.product;

import static com.nuono.next.schema.DbInitScriptAssertions.assertInitScriptsInclude;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductImageAiReviewMigrationTest {

    @Test
    void migrationIsRerunnableAndNumbersLegacyRoleDuplicatesBeforeUniqueIndex() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/init/197_product_image_ai_review_publish.sql")
        );

        assertThat(sql)
                .contains("INFORMATION_SCHEMA.COLUMNS")
                .contains("INFORMATION_SCHEMA.STATISTICS")
                .contains("ROW_NUMBER() OVER (PARTITION BY suite_id, image_role ORDER BY sort_order, id)")
                .contains("SET target.role_ordinal = ranked.expected_role_ordinal")
                .contains("CREATE TABLE IF NOT EXISTS `product_image_suite_review_target`")
                .contains("ADD UNIQUE KEY `uk_product_image_suite_asset_slot`")
                .doesNotContain("ALTER TABLE `product_image_suite`\n  ADD COLUMN");
        assertInitScriptsInclude("classpath:db/init/197_product_image_ai_review_publish.sql");
    }
}
