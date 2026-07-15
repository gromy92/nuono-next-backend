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
    void searchSqlMatchesSkuFieldsWithoutExpandingLiteProjection() throws Exception {
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
                .contains("pm.title_cn_cache")
                .contains("$.content.titleEn")
                .contains("$.content.titleCn")
                .contains("pm.sku_parent")
                .contains("EXISTS (")
                .contains("FROM product_variant pv_search")
                .contains("LEFT JOIN product_site_offer pso_search")
                .contains("pv_search.product_master_id = pm.id")
                .contains("pv_search.partner_sku")
                .contains("pv_search.child_sku")
                .contains("pso_search.psku_code")
                .contains("pso_search.offer_code")
                .doesNotContain("sku_parent AS")
                .doesNotContain("child_sku AS")
                .doesNotContain("offer_code AS")
                .doesNotContain("sale_price")
                .doesNotContain("stock")
                .doesNotContain("variant_id AS")
                .doesNotContain("effective_source");

        new XMLLanguageDriver().createSqlSource(new Configuration(), rawSql, Object.class);
    }

    @Test
    void classificationFallbackSqlUsesProductLiteScopeWithoutSkuOfferTables() throws Exception {
        assertClassificationFallbackSql(
                "selectBrandProjectionClassificationOptions",
                "pm.brand_cache",
                "GROUP BY pm.brand_cache",
                false
        );
        assertClassificationFallbackSql(
                "selectFulltypeProjectionClassificationOptions",
                "pm.product_fulltype_cache",
                "GROUP BY pm.product_fulltype_cache",
                true
        );
    }

    private static void assertClassificationFallbackSql(
            String methodName,
            String selectedColumn,
            String groupByColumn,
            boolean officialFulltypeOnly
    ) throws Exception {
        Method method = ProductLiteMapper.class.getMethod(
                methodName,
                Long.class,
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
                .contains("ls.owner_user_id = #{ownerUserId}")
                .contains("(ls.project_code = #{storeCode} OR lss.store_code = #{storeCode})")
                .contains(selectedColumn)
                .contains(groupByColumn)
                .doesNotContain("product_variant")
                .doesNotContain("product_site_offer")
                .doesNotContain("variant_id")
                .doesNotContain("offer_code")
                .doesNotContain("stock")
                .doesNotContain("price");
        if (officialFulltypeOnly) {
            assertThat(sql)
                    .contains("pm.product_fulltype_cache REGEXP '^[a-z0-9_]+-[a-z0-9_]+-[a-z0-9_]+$'");
        }

        new XMLLanguageDriver().createSqlSource(new Configuration(), rawSql, Object.class);
    }
}
