package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProductAttributeTemplateMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class ProductAttributeTemplateMapperSqlTest {

    @Test
    void refreshCandidatesUseGlobalTemplateScopeAndAnyMatchingAuthStore() throws Exception {
        String sql = selectSql("selectRefreshCandidates", Integer.class, Integer.class);

        assertThat(sql)
                .contains("row_number() over (")
                .contains("partition by t.product_fulltype")
                .contains("(t.project_code = '*' or auth.project_code = t.project_code)")
                .contains("(t.store_code = '*' or auth.store_code = t.store_code)")
                .contains("auth.project_code as authprojectcode")
                .contains("auth.store_code as authstorecode")
                .doesNotContain("lss.store_code = t.store_code");
    }

    @Test
    void dictionaryLookupReadsChineseLabelsWithoutOverwritingThemOnOfficialRefresh() throws Exception {
        assertThat(selectSql("selectDictionaryFields", String.class, String.class, String.class))
                .contains("label_zh");
        assertThat(selectSql("selectDictionaryOptions", List.class))
                .contains("label_zh");
        assertThat(selectSql("selectDictionaryUnitOptions", List.class))
                .contains("label_zh");
        assertThat(insertSql("upsertDictionaryField"))
                .doesNotContain("label_zh");
        assertThat(insertSql("upsertDictionaryOption"))
                .doesNotContain("label_zh");
        assertThat(insertSql("upsertDictionaryUnitOption"))
                .doesNotContain("label_zh");
    }

    private static String selectSql(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = ProductAttributeTemplateMapper.class.getMethod(methodName, parameterTypes);
        Select select = method.getAnnotation(Select.class);
        return Arrays.stream(select.value())
                .collect(Collectors.joining(" "))
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private static String insertSql(String methodName) throws Exception {
        Method method = Arrays.stream(ProductAttributeTemplateMapper.class.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        return Arrays.stream(method.getAnnotation(org.apache.ibatis.annotations.Insert.class).value())
                .collect(Collectors.joining(" "))
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}
