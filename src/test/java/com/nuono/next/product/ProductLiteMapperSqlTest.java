package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProductLiteMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ProductLiteMapperSqlTest {

    @Test
    void searchSqlUsesOnlyMasterDraftAndSnapshotTables() throws Exception {
        Method method = ProductLiteMapper.class.getMethod(
                "search",
                Long.class,
                String.class,
                String.class,
                String.class,
                int.class
        );
        Select select = method.getAnnotation(Select.class);
        String rawSql = String.join("\n", select.value());
        String sql = rawSql.replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("FROM logical_store ls")
                .contains("JOIN logical_store_site lss")
                .contains("JOIN product_master pm")
                .contains("LEFT JOIN product_master_draft pmd")
                .contains("LEFT JOIN product_master_snapshot pms")
                .contains("pm.title_cache")
                .contains("$.content.titleEn")
                .contains("$.content.titleCn")
                .doesNotContain("product_variant")
                .doesNotContain("product_site_offer")
                .doesNotContain("sku_parent AS")
                .doesNotContain("child_sku")
                .doesNotContain("offer_code")
                .doesNotContain("sale_price")
                .doesNotContain("stock")
                .doesNotContain("variant_id")
                .doesNotContain("effective_source");

        new XMLLanguageDriver().createSqlSource(new Configuration(), rawSql, Object.class);
    }
}
