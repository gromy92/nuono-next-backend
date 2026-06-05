package com.nuono.next.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CoreTableStatusMapper;
import java.util.List;
import java.util.Map;
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
                        "classpath:db/init/078_procurement_ali1688_sku_purchase_batch.sql",
                        "classpath:db/init/079_procurement_ali1688_order_assignment_active_guards.sql"
                );
    }
}
