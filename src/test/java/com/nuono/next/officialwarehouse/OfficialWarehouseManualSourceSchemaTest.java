package com.nuono.next.officialwarehouse;

import static com.nuono.next.schema.DbInitScriptAssertions.assertInitScriptsInclude;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OfficialWarehouseManualSourceSchemaTest {

    @Test
    void manualLineQuantityMigrationIsRegisteredAsAnExplicitNullableFact() throws Exception {
        assertInitScriptsInclude("classpath:db/init/249_official_warehouse_asn_line_source_allocation.sql");
        String migration = Files.readString(Path.of(
                "src/main/resources/db/init/249_official_warehouse_asn_line_source_allocation.sql"
        ));
        String postcheck = Files.readString(Path.of(
                "src/main/resources/db/postcheck/249_official_warehouse_asn_line_source_allocation.sql"
        ));
        String catalog = Files.readString(Path.of("src/main/resources/db/init/release-migrations.tsv"));

        assertThat(migration)
                .contains("COLUMN_NAME = 'manual_quantity'")
                .contains("ADD COLUMN manual_quantity INT DEFAULT NULL AFTER qty");
        assertThat(postcheck)
                .contains("COLUMN_NAME = 'manual_quantity'")
                .contains("IS_NULLABLE = 'YES'");
        assertThat(catalog).contains(
                "249\t249_official_warehouse_asn_line_source_allocation.sql\tAUTO_ADDITIVE"
        );
    }
}
