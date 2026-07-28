package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WarehouseRequestIdempotencyReleaseMigrationTest {

    @Test
    void releaseCatalogConvergesBothWarehouseRequestKeysWithoutRewritingBusinessRows() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/init/232_warehouse_command_request_idempotency.sql"
        ));
        String postcheck = Files.readString(Path.of(
                "src/main/resources/db/postcheck/232_warehouse_command_request_idempotency.sql"
        ));
        String catalog = Files.readString(Path.of(
                "src/main/resources/db/init/release-migrations.tsv"
        ));

        assertThat(catalog).contains(
                "232\t232_warehouse_command_request_idempotency.sql\tAUTO_ADDITIVE"
        );
        assertThat(migration)
                .contains("procurement_dispatch_plan")
                .contains("procurement_fulfillment_confirmation")
                .contains("client_request_id")
                .contains("request_fingerprint")
                .contains("client_request_id` IS NOT NULL")
                .contains("HAVING COUNT(*) > 1")
                .contains("uk_dispatch_plan_owner_client_request")
                .contains("uk_fulfillment_confirmation_owner_client_request")
                .doesNotContain("UPDATE `procurement_")
                .doesNotContain("DELETE FROM `procurement_");
        assertThat(postcheck)
                .contains("uk_dispatch_plan_owner_client_request")
                .contains("uk_fulfillment_confirmation_owner_client_request")
                .contains("HAVING COUNT(*) > 1");
    }
}
