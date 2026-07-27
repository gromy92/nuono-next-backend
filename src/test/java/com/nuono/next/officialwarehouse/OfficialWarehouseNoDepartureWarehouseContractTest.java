package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OfficialWarehouseNoDepartureWarehouseContractTest {

    @Test
    void appointmentFlowDoesNotModelOrQueryDepartureWarehouse() throws Exception {
        String appointmentSources = String.join(
                "\n",
                read("src/main/java/com/nuono/next/officialwarehouse/OfficialWarehouseCommands.java"),
                read("src/main/java/com/nuono/next/officialwarehouse/OfficialWarehouseAppointmentRunner.java"),
                read("src/main/java/com/nuono/next/officialwarehouse/OfficialWarehouseAsnListSyncSupport.java"),
                read("src/main/java/com/nuono/next/officialwarehouse/OfficialWarehouseViews.java"),
                read("src/main/java/com/nuono/next/officialwarehouse/OfficialWarehouseRecords.java"),
                read("src/main/java/com/nuono/next/officialwarehouse/LocalDbOfficialWarehouseService.java"),
                read("src/main/java/com/nuono/next/officialwarehouse/OfficialWarehouseController.java"),
                read("src/main/java/com/nuono/next/infrastructure/mapper/OfficialWarehouseMapper.java")
        );

        assertThat(appointmentSources)
                .doesNotContain("warehouseFrom")
                .doesNotContain("warehouse_from AS warehouseFrom")
                .doesNotContain("出发仓库")
                .doesNotContain("/appointment/warehouses");
    }

    @Test
    void noonWarehouseUpdateSendsOnlyAsnAndDestinationWarehouse() throws Exception {
        String client = read(
                "src/main/java/com/nuono/next/officialwarehouse/OfficialWarehouseNoonInboundClient.java"
        );

        assertThat(client)
                .contains("body.put(\"asnNr\", asnNr)")
                .contains("body.put(\"warehouseTo\", warehouseTo)")
                .contains("body.put(\"asnNr\", task.noonAsnNr)")
                .contains("body.put(\"warehouseTo\", task.warehouseTo)")
                .doesNotContain("body.put(\"warehouseFrom\"");
    }

    @Test
    void appointmentSchemaAllowsLegacyDepartureWarehouseColumnToStayEmpty() throws Exception {
        String baseSchema = read(
                "src/main/resources/db/init/136_official_warehouse_appointment.sql"
        );
        String migration = read(
                "src/main/resources/db/init/201_official_warehouse_remove_departure_dependency.sql"
        );

        assertThat(baseSchema)
                .contains("`warehouse_from` VARCHAR(120) DEFAULT NULL")
                .doesNotContain("`warehouse_from` VARCHAR(120) NOT NULL");
        assertThat(migration)
                .contains("ALTER TABLE `official_warehouse_appointment`")
                .contains("MODIFY COLUMN `warehouse_from` VARCHAR(120) DEFAULT NULL");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
