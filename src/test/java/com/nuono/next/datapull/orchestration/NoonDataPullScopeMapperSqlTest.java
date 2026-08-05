package com.nuono.next.datapull.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.NoonDataPullScopeMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class NoonDataPullScopeMapperSqlTest {

    @Test
    void exposesEveryPhysicalBindingRowWithoutDistinctMasking() {
        Method method = Arrays.stream(NoonDataPullScopeMapper.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("listActiveBoundScopes"))
                .findFirst()
                .orElseThrow();
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");

        assertThat(sql.toUpperCase(java.util.Locale.ROOT)).doesNotContain("SELECT DISTINCT");
        assertThat(sql).contains(
                "lss.id AS logicalStoreSiteId",
                "up.id AS userProjectId",
                "us.id AS userStoreId"
        );
        assertThat(sql).contains("ORDER BY ls.owner_user_id ASC, ls.id ASC, lss.id ASC");
    }
}
