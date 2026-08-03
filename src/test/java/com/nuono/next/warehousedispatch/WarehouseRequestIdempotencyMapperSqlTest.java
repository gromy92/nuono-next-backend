package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class WarehouseRequestIdempotencyMapperSqlTest {

    @Test
    void receiptConfirmationPersistsAndLoadsOwnerScopedRequestIdentity() throws Exception {
        String insertSql = insertSql(
                "insertConfirmation",
                WarehouseDispatchRecords.FulfillmentConfirmationInsertRecord.class
        );
        String selectSql = selectSql(
                "selectConfirmationByClientRequestId",
                Long.class,
                String.class
        );
        String lineSql = selectSql("listConfirmationLines", Long.class);

        assertThat(insertSql)
                .contains("client_request_id")
                .contains("request_fingerprint")
                .contains("#{row.clientRequestId}")
                .contains("#{row.requestFingerprint}");
        assertThat(selectSql)
                .contains("owner_user_id = #{ownerUserId}")
                .contains("client_request_id = #{clientRequestId}")
                .contains("FOR UPDATE");
        assertThat(lineSql).contains("confirmation_id = #{confirmationId}");
    }

    @Test
    void dispatchPlanPersistsAndLoadsOwnerScopedRequestIdentity() throws Exception {
        String insertSql = insertSql(
                "insertDispatchPlan",
                WarehouseDispatchRecords.DispatchPlanRecord.class,
                Long.class
        );
        String selectSql = selectSql(
                "selectDispatchPlanByClientRequestId",
                Long.class,
                String.class
        );
        String lockSql = selectSql("lockDispatchOwner", Long.class);

        assertThat(insertSql)
                .contains("client_request_id")
                .contains("request_fingerprint")
                .contains("#{row.clientRequestId}")
                .contains("#{row.requestFingerprint}");
        assertThat(selectSql)
                .contains("owner_user_id = #{ownerUserId}")
                .contains("client_request_id = #{clientRequestId}")
                .contains("FOR UPDATE");
        assertThat(lockSql)
                .contains("FROM `user`")
                .contains("FOR UPDATE");
    }

    private String insertSql(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(methodName, parameterTypes);
        return String.join(" ", method.getAnnotation(Insert.class).value()).replaceAll("\\s+", " ");
    }

    private String selectSql(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(methodName, parameterTypes);
        return String.join(" ", method.getAnnotation(Select.class).value()).replaceAll("\\s+", " ");
    }
}
