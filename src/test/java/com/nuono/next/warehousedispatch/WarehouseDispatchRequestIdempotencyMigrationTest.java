package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WarehouseDispatchRequestIdempotencyMigrationTest {

    @Test
    void migrationAddsOwnerScopedDispatchRequestIdempotency() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/init/194_warehouse_dispatch_request_idempotency.sql"
        ));

        assertThat(migration)
                .contains("client_request_id")
                .contains("request_fingerprint")
                .contains("uk_dispatch_plan_owner_client_request")
                .contains("(`owner_user_id`, `client_request_id`)")
                .contains("INFORMATION_SCHEMA.COLUMNS")
                .contains("INFORMATION_SCHEMA.STATISTICS");
    }
}
