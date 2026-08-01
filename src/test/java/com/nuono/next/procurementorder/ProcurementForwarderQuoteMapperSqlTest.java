package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ProcurementForwarderQuoteMapperSqlTest {

    @Test
    void mapperDynamicSqlRegistersSuccessfully() {
        Configuration configuration = new Configuration();
        configuration.addMapper(ProcurementPurchaseOrderMapper.class);
    }

    @Test
    void routeSupplementFeeQueriesReadWarehouseAndLastMileFees() throws Exception {
        Method basePriceMethod = ProcurementPurchaseOrderMapper.class.getMethod(
                "listBasePricesByServiceCodes",
                java.util.List.class
        );
        Method warehouseMethod = ProcurementPurchaseOrderMapper.class.getMethod(
                "listWarehouseProcessingFeesByServiceCodes",
                java.util.List.class
        );
        Method transportMethod = ProcurementPurchaseOrderMapper.class.getMethod(
                "listTransportFeesByServiceCodes",
                java.util.List.class
        );

        String basePriceSql = sql(basePriceMethod);
        String warehouseSql = sql(warehouseMethod);
        String transportSql = sql(transportMethod);

        assertThat(basePriceSql).contains("FROM forwarder_quote_base_price");
        assertThat(basePriceSql).contains("base_price.unit_price AS unitPrice");
        assertThat(basePriceSql).contains("base_price.unit_price > 0");
        assertThat(basePriceSql).doesNotContain("forwarder_quote_numeric_adjustment");
        assertThat(warehouseSql).contains("FROM forwarder_warehouse_processing_fee");
        assertThat(transportSql).contains("FROM forwarder_quote_transport_fee");
    }

    @Test
    void eligibilityScopeLockIsOwnerScopedAndDeterministicallyOrdered() throws Exception {
        Method method = ProcurementPurchaseOrderMapper.class.getMethod(
                "lockProductVariantsForForwarderEligibility",
                Long.class,
                java.util.List.class
        );

        String statement = sql(method);

        assertThat(statement).contains("store.owner_user_id = #{ownerUserId}");
        assertThat(statement).contains("pv.id IN");
        assertThat(statement).contains("ORDER BY pv.id ASC");
        assertThat(statement).contains("FOR UPDATE");
    }

    private static String sql(Method method) {
        return String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
    }
}
