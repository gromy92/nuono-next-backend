package com.nuono.next.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CoreTableStatusMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LocalDbBootstrapStatusServiceTest {

    @Test
    void shouldReportMissingRequiredColumnsWhenProductSiteOfferMigrationWasNotApplied() {
        CoreTableStatusMapper mapper = Mockito.mock(CoreTableStatusMapper.class);
        BootstrapProperties properties = new BootstrapProperties();
        properties.setSchema("nuono_new_dev");
        properties.setExpectedCoreTables(List.of("product_site_offer"));

        when(mapper.findExistingTableNames("nuono_new_dev", List.of("product_site_offer")))
                .thenReturn(List.of("product_site_offer"));

        LocalDbBootstrapStatusService service = new LocalDbBootstrapStatusService(mapper, properties);

        Map<String, Object> payload = service.describe();

        assertEquals(false, payload.get("ready"));
        assertEquals(
                List.of(
                        "product_site_offer.listing_started_at",
                        "product_site_offer.listing_started_source"
                ),
                payload.get("missingRequiredColumns")
        );
    }

    @Test
    void shouldListCanmanSaSiteScopeRepairScript() {
        CoreTableStatusMapper mapper = Mockito.mock(CoreTableStatusMapper.class);
        BootstrapProperties properties = new BootstrapProperties();
        properties.setSchema("nuono_new_dev");
        properties.setExpectedCoreTables(List.of());

        LocalDbBootstrapStatusService service = new LocalDbBootstrapStatusService(mapper, properties);

        Map<String, Object> payload = service.describe();

        assertTrue(((List<?>) payload.get("initScripts")).contains(
                "classpath:db/init/080_restore_canman_sa_site_scope.sql"
        ));
    }
}
