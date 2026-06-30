package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class OfficialWarehouseAppointmentSchedulerSqlTest {

    @Test
    void schedulerDueQueryOnlyConsumesPendingAppointments() throws Exception {
        Method method = OfficialWarehouseMapper.class.getMethod("listDueAppointments", int.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("status = 'PENDING'");
        assertThat(sql).doesNotContain("status IN ('PENDING', 'RUNNING', 'FAILED')");
        assertThat(sql).doesNotContain("'RUNNING'");
        assertThat(sql).doesNotContain("'FAILED'");
    }
}
