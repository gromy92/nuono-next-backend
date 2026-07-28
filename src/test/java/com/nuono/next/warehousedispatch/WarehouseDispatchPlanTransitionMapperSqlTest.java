package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class WarehouseDispatchPlanTransitionMapperSqlTest {

    @Test
    void planWriteLookupsLockByIdAndHandoffRequest() throws Exception {
        assertThat(lockSql("selectDispatchPlanByIdForUpdate", Long.class))
                .contains("id = #{dispatchPlanId}");
        assertThat(lockSql("selectDispatchPlanByHandoffRequestForUpdate", String.class))
                .contains("handoff_request_no = #{handoffRequestNo}");
    }

    private String lockSql(String methodName, Class<?> parameterType) throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(methodName, parameterType);
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("FROM procurement_dispatch_plan");
        assertThat(sql).contains("is_deleted = b'0'");
        assertThat(sql).endsWith("FOR UPDATE");
        return sql;
    }
}
