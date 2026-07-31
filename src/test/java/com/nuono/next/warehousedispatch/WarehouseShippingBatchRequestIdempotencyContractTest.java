package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class WarehouseShippingBatchRequestIdempotencyContractTest {

    @Test
    void mapperPersistsAndLocksOwnerScopedShippingBatchRequestIdentity() throws Exception {
        String insertSql = sql(
                WarehouseDispatchMapper.class.getMethod(
                        "insertShippingBatch",
                        WarehouseDispatchRecords.ShippingBatchRecord.class,
                        Long.class
                ).getAnnotation(Insert.class).value()
        );
        String selectSql = sql(
                WarehouseDispatchMapper.class.getMethod(
                        "selectShippingBatchByClientRequestId",
                        Long.class,
                        String.class
                ).getAnnotation(Select.class).value()
        );

        assertThat(insertSql)
                .contains("client_request_id")
                .contains("request_fingerprint")
                .contains("#{row.clientRequestId}")
                .contains("#{row.requestFingerprint}");
        assertThat(selectSql)
                .contains("owner_user_id = #{ownerUserId}")
                .contains("client_request_id = #{clientRequestId}")
                .contains("is_deleted = b'0'")
                .contains("FOR UPDATE");
    }

    @Test
    void migrationAddsBinaryRequestIdentityAndExactUniqueFenceWithoutRewritingRows()
            throws Exception {
        String migration = read(
                "src/main/resources/db/init/235_warehouse_shipping_batch_request_idempotency.sql"
        );
        String postcheck = read(
                "src/main/resources/db/postcheck/235_warehouse_shipping_batch_request_idempotency.sql"
        );
        String catalog = read("src/main/resources/db/init/release-migrations.tsv");

        assertThat(catalog).contains(
                "235\t235_warehouse_shipping_batch_request_idempotency.sql\tAUTO_ADDITIVE"
        );
        assertThat(migration)
                .contains("VARCHAR(100) CHARACTER SET utf8mb4")
                .contains("COLLATE utf8mb4_bin NULL DEFAULT NULL")
                .contains("CHAR(64) CHARACTER SET ascii")
                .contains("COLLATE ascii_bin NULL DEFAULT NULL")
                .contains("uk_shipping_batch_owner_client_request")
                .contains("(`owner_user_id`, `client_request_id`)")
                .contains("HAVING COUNT(*) > 1")
                .contains("ALGORITHM=INPLACE, LOCK=NONE")
                .doesNotContain("UPDATE `warehouse_shipping_batch`")
                .doesNotContain("DELETE FROM `warehouse_shipping_batch`");
        assertThat(postcheck)
                .contains("utf8mb4_bin")
                .contains("ascii_bin")
                .contains("uk_shipping_batch_owner_client_request")
                .contains("1:owner_user_id,2:client_request_id")
                .contains("HAVING COUNT(*) > 1");
    }

    private String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private String sql(String[] fragments) {
        return String.join(" ", fragments).replaceAll("\\s+", " ");
    }
}
