package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class WarehouseHandoffReceiptMapperSqlTest {

    @Test
    void currentReceiptMatchesTheWholeDocumentChainAndDedicatedOperation() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "selectInventoryHandoffCompletionReceiptId",
                Long.class,
                Long.class,
                Long.class,
                Long.class
        );
        String sql = normalizedSql(method);

        assertThat(sql)
                .contains("operation_type = 'INVENTORY_HANDOFF_COMPLETED'")
                .contains("after_status = 'SHIPPED'")
                .contains("dispatch_plan_id <=> #{dispatchPlanId}")
                .contains("JSON_VALID(detail_json)")
                .contains("'$.shippingBatchId'")
                .contains("'$.outboundOrderId'")
                .contains("'$.packingListId'")
                .contains("ORDER BY id DESC LIMIT 1")
                .doesNotContain("FOR UPDATE");
    }

    @Test
    void legacyReceiptRequiresTheOldAtomicPlanHandoffProof() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "selectLegacyDispatchPlanHandoffReceiptId",
                Long.class,
                String.class
        );
        String sql = normalizedSql(method);

        assertThat(sql)
                .contains("operation_type = 'HANDOFF_SUCCESS'")
                .contains("after_status = 'LOGISTICS_REQUESTED'")
                .contains("dispatch_plan_id = #{dispatchPlanId}")
                .contains("'$.detail'")
                .contains("= #{handoffRequestNo}")
                .contains("ORDER BY id DESC LIMIT 1")
                .doesNotContain("FOR UPDATE");
    }

    private String normalizedSql(Method method) {
        return String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
    }
}
