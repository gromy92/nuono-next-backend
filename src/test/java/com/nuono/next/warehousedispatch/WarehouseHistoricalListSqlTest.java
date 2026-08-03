package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.infrastructure.mapper.WarehouseDispatchLifecycleMapper;
import com.nuono.next.infrastructure.mapper.WarehouseShippingQueryMapper;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class WarehouseHistoricalListSqlTest {

    @Test
    void shippingQueryMapperDynamicSqlRegistersSuccessfully() {
        Configuration configuration = new Configuration();
        configuration.addMapper(WarehouseShippingQueryMapper.class);
    }

    @Test
    void stageListsAreNotTruncatedAfterStatusTransitions() throws Exception {
        assertHasNoLimit(ProcurementPurchaseOrderMapper.class.getMethod(
                "listShippingOrders",
                Long.class,
                String.class
        ));
        assertHasNoLimit(WarehouseDispatchLifecycleMapper.class.getMethod(
                "listDispatchPlans",
                Map.class
        ));
        assertHasNoLimit(WarehouseShippingQueryMapper.class.getMethod(
                "listShippingBatches",
                Map.class
        ));
    }

    @Test
    void routeQuotesUsePositivePricesFromTheCurrentPublishedVersion() throws Exception {
        Method method = WarehouseShippingQueryMapper.class.getMethod(
                "listForwarderRouteQuotes",
                Collection.class
        );
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("price.unit_price AS minUnitPrice");
        assertThat(sql).contains("price.unit_price > 0");
        assertThat(sql).contains("version.effective_from &lt;= CURRENT_DATE");
        assertThat(sql).contains("version.effective_to IS NULL OR version.effective_to >= CURRENT_DATE");
        assertThat(sql).doesNotContain("forwarder_quote_numeric_adjustment");
    }

    private void assertHasNoLimit(Method method) {
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
        assertThat(sql).doesNotContain("LIMIT");
        assertThat(sql).doesNotContain("status IN");
    }
}
