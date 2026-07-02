package com.nuono.next.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CoreTableStatusMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalDbBootstrapStatusServiceTest {

    @Mock
    private CoreTableStatusMapper coreTableStatusMapper;

    @Test
    void localBootstrapScriptsIncludeLatestAli1688HistoricalOrderMigrations() {
        BootstrapProperties properties = new BootstrapProperties();
        properties.setSchema("nuono_new_dev");
        properties.setExpectedCoreTables(List.of());
        when(coreTableStatusMapper.findExistingTableNames("nuono_new_dev", List.of()))
                .thenReturn(List.of());
        LocalDbBootstrapStatusService service =
                new LocalDbBootstrapStatusService(coreTableStatusMapper, properties);

        Map<String, Object> status = service.describe();

        assertThat(status.get("initScripts"))
                .asList()
                .contains(
                        "classpath:db/init/096_procurement_ali1688_sku_purchase_batch.sql",
                        "classpath:db/init/097_procurement_ali1688_order_assignment_active_guards.sql",
                        "classpath:db/init/145_procurement_purchase_order_logistics_quote_confirmation.sql",
                        "classpath:db/init/146_procurement_shipping_order.sql",
                        "classpath:db/init/147_product_forwarder_declaration_attribute.sql",
                        "classpath:db/init/153_psku_product_model_forwarder_legacy_backfill.sql",
                        "classpath:db/init/159_product_image_logical_store_scope.sql",
                        "classpath:db/init/160_noon_advertising_read_model.sql"
                );
    }

    @Test
    void releaseMigrationNumbersFrom149OnwardAreUnique() throws IOException {
        Map<String, List<String>> scriptsByNumber;
        try (Stream<Path> paths = Files.list(Path.of("src/main/resources/db/init"))) {
            scriptsByNumber = paths
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("\\d+_.*\\.sql"))
                    .filter(name -> Integer.parseInt(name.substring(0, name.indexOf('_'))) >= 149)
                    .collect(Collectors.groupingBy(name -> name.substring(0, name.indexOf('_'))));
        }

        List<String> duplicateNumbers = scriptsByNumber.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.toList());

        assertThat(duplicateNumbers).isEmpty();
    }
}
