package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ProductManagementClassificationMapperSqlTest {

    @Test
    void fulltypeDictionaryOptionsOnlyReturnOfficialNoonFulltypeCodes() throws Exception {
        String rawSql = selectSql("selectFulltypeDictionaryOptions");
        String sql = compact(rawSql);

        assertThat(sql)
                .contains("product_fulltype as value")
                .contains("substring_index(product_fulltype, '-', 1) as family")
                .contains("substring_index(substring_index(product_fulltype, '-', 2), '-', -1) as producttype")
                .contains("substring_index(product_fulltype, '-', -1) as productsubtype")
                .contains("product_fulltype regexp '^[a-z0-9_]+-[a-z0-9_]+-[a-z0-9_]+$'")
                .doesNotContain("max(family)")
                .doesNotContain("max(product_type)")
                .doesNotContain("max(product_subtype)");

        new XMLLanguageDriver().createSqlSource(new Configuration(), rawSql, Object.class);
    }

    @Test
    void fulltypeProjectionFallbackDoesNotReturnDisplayCategoryNames() throws Exception {
        String rawSql = selectSql("selectFulltypeProjectionClassificationOptions");
        String sql = compact(rawSql);

        assertThat(sql)
                .contains("pm.product_fulltype_cache as value")
                .contains("pm.product_fulltype_cache regexp '^[a-z0-9_]+-[a-z0-9_]+-[a-z0-9_]+$'");

        new XMLLanguageDriver().createSqlSource(new Configuration(), rawSql, Object.class);
    }

    private static String selectSql(String methodName) throws Exception {
        Method method = Arrays.stream(ProductManagementMapper.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        Select select = method.getAnnotation(Select.class);
        return Arrays.stream(select.value()).collect(Collectors.joining("\n"));
    }

    private static String compact(String rawSql) {
        return rawSql.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
