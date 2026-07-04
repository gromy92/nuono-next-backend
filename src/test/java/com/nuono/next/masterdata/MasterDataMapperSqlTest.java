package com.nuono.next.masterdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.MasterDataMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class MasterDataMapperSqlTest {

    @Test
    void menuQueriesCoerceOnlyNumericParentIds() throws Exception {
        assertMenuParentIdProjection(MasterDataMapper.class.getMethod("listMenus"));
        assertMenuParentIdProjection(MasterDataMapper.class.getMethod("selectMenuView", Long.class));
    }

    private static void assertMenuParentIdProjection(Method method) {
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("WHEN parent_id REGEXP '^[0-9]+$' THEN CAST(parent_id AS UNSIGNED)")
                .contains("ELSE NULL")
                .contains("END AS parent_id")
                .doesNotContain(" name, parent_id, url_path");
    }
}
