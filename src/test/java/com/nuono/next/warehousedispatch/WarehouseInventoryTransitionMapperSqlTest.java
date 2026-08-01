package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.BalanceQuantityDelta;
import java.lang.reflect.Method;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class WarehouseInventoryTransitionMapperSqlTest {

    @Test
    void logisticsHandoffBalanceTransitionReportsAffectedRowsAndRequiresPositiveQuantity() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "moveReservedToLogisticsHandoff",
                Long.class,
                Integer.class,
                Long.class
        );

        String sql = normalizedUpdateSql(method);

        assertThat(method.getReturnType()).isEqualTo(int.class);
        assertThat(sql).contains("#{quantity} > 0");
        assertThat(sql).contains("reserved_quantity >= #{quantity}");
    }

    @Test
    void receiptBalanceTransitionRejectsNegativeOrUnusableQuantity() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "updateBalanceQuantities",
                BalanceQuantityDelta.class
        );

        String sql = normalizedUpdateSql(method);

        assertThat(method.getReturnType()).isEqualTo(int.class);
        assertThat(sql).contains("#{row.confirmedDelta} >= 0");
        assertThat(sql).contains("#{row.abnormalDelta} >= 0");
        assertThat(sql).contains(">= abnormal_quantity + #{row.abnormalDelta}");
        assertThat(sql).doesNotContain("<= planned_quantity");
    }

    @Test
    void logisticsHandoffLocksAllBalancesInStableOrder() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "selectBalancesForUpdate",
                List.class
        );
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("ORDER BY balance.id ASC");
        assertThat(sql).contains("FOR UPDATE");
    }

    @Test
    void outboundSourcesExposeExactBalanceAndQuantity() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "listOutboundOrderLineSources",
                Long.class
        );
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("source.fulfillment_balance_id AS fulfillmentBalanceId");
        assertThat(sql).contains("source.quantity");
        assertThat(sql).contains("source.outbound_order_id = #{outboundOrderId}");
    }

    private String normalizedUpdateSql(Method method) {
        return String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");
    }
}
