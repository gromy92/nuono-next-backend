package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.infrastructure.mapper.WarehouseDispatchLifecycleMapper;
import com.nuono.next.infrastructure.mapper.WarehouseShippingQueryMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class WarehouseHistoricalListSqlTest {

    @Test
    void stageListsAreNotTruncatedAfterStatusTransitions() throws Exception {
        assertHasNoLimit(ProcurementPurchaseOrderMapper.class.getMethod(
                "listShippingOrders",
                Long.class,
                String.class
        ));
        assertHasNoLimit(WarehouseDispatchLifecycleMapper.class.getMethod(
                "listDispatchPlans",
                Long.class
        ));
        assertHasNoLimit(WarehouseShippingQueryMapper.class.getMethod(
                "listShippingBatches",
                Long.class
        ));
    }

    private void assertHasNoLimit(Method method) {
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
        assertThat(sql).doesNotContain("LIMIT");
        assertThat(sql).doesNotContain("status IN");
    }
}
