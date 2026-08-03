package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class WarehouseLinkedShippingMapperSqlTest {

    @Test
    void linkedReplayUsesCurrentExecutionStatus() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "selectLatestShippingBatchByDispatchPlan",
                Long.class
        );
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("pending_order.status != 'SHIPPED'")
                .contains("unpacked_order.status NOT IN ('PACKED', 'SHIPPED')")
                .contains("ELSE batch.status END AS status")
                .doesNotContain("batch_no AS batchNo, status,");
    }

    @Test
    void linkedSummaryIsRegisteredAndUsesExecutionStatusWithSourceScope() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "listLatestShippingBatchSummariesByDispatchPlanIds",
                Collection.class,
                Map.class
        );
        Configuration configuration = new Configuration();
        configuration.addMapper(WarehouseDispatchMapper.class);

        assertThat(new MapperMethod(WarehouseDispatchMapper.class, method, configuration))
                .isNotNull();
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
        assertThat(sql)
                .contains("pending_order.status != 'SHIPPED'")
                .contains("unpacked_order.status NOT IN ('PACKED', 'SHIPPED')")
                .contains("JOIN procurement_dispatch_plan dispatchPlan")
                .contains("dispatchPlan.owner_user_id = batch.owner_user_id")
                .contains("FROM warehouse_shipping_batch_source source")
                .contains("<otherwise> AND 1 = 0 </otherwise>");
    }

    @Test
    void shippingBatchInsertPersistsDispatchPlanLink() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "insertShippingBatch",
                WarehouseDispatchRecords.ShippingBatchRecord.class,
                Long.class
        );
        String sql = String.join(" ", method.getAnnotation(Insert.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("owner_user_id, dispatch_plan_id, client_request_id")
                .contains("#{row.ownerUserId}, #{row.dispatchPlanId}, #{row.clientRequestId}");
    }

    @Test
    void linkedPlanCompletionCasScopesPlanOwnerRequestAndState() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "markDispatchPlanHandoffSuccess",
                Long.class,
                Long.class,
                String.class,
                Long.class
        );
        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("WHERE id = #{dispatchPlanId}")
                .contains("owner_user_id = #{ownerUserId}")
                .contains("handoff_request_no = #{handoffRequestNo}")
                .contains("status IN ('READY_FOR_LOGISTICS', 'HANDOFF_FAILED')")
                .contains("is_deleted = b'0'");
    }
}
