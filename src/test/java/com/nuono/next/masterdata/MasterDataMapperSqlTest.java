package com.nuono.next.masterdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.MasterDataMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class MasterDataMapperSqlTest {

    @Test
    void listMenusShouldNormalizeLegacyVarcharParentIdBeforeMappingToLong() throws Exception {
        String sql = mapperSql("listMenus");

        assertThat(sql).contains("CASE");
        assertThat(sql).contains("m.parent_id REGEXP '^[0-9]+$'");
        assertThat(sql).contains("CAST(m.parent_id AS UNSIGNED)");
        assertThat(sql).contains("AS parent_id");
        assertThat(sql).contains("LEFT JOIN menu parent_menu");
        assertThat(sql).contains("TRIM(BOTH '/' FROM parent_menu.url_path)");
    }

    private static String mapperSql(String methodName) throws Exception {
        Method method = MasterDataMapper.class.getMethod(methodName);
        Select select = method.getAnnotation(Select.class);
        if (select == null) {
            throw new IllegalArgumentException("No @Select found on " + methodName);
        }
        return String.join(" ", select.value()).replaceAll("\\s+", " ").trim();
    }
}
