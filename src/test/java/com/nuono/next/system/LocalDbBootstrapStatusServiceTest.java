package com.nuono.next.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CoreTableStatusMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalDbBootstrapStatusServiceTest {

    @Mock
    private CoreTableStatusMapper coreTableStatusMapper;

    private LocalDbBootstrapStatusService service;

    @BeforeEach
    void setUp() {
        BootstrapProperties properties = new BootstrapProperties();
        properties.setSchema("nuono_new_dev");
        properties.setExpectedCoreTables(List.of("user", "menu", "role_menu"));
        service = new LocalDbBootstrapStatusService(coreTableStatusMapper, properties);
    }

    @Test
    void describeExposesManualSelectionFeatureReadinessSeparatelyFromCoreReadiness() {
        when(coreTableStatusMapper.findExistingTableNames(
                "nuono_new_dev",
                List.of("user", "menu", "role_menu")
        )).thenReturn(List.of("user", "menu", "role_menu"));
        when(coreTableStatusMapper.findExistingTableNames(
                "nuono_new_dev",
                List.of("product_selection_source_collection")
        )).thenReturn(List.of());
        when(coreTableStatusMapper.countActiveMenuByIdAndPath(9102L, "/product/manual-selection")).thenReturn(0);
        when(coreTableStatusMapper.countActiveRoleMenu(2L, 9102L)).thenReturn(0);
        when(coreTableStatusMapper.countActiveMenuByIdAndPath(9101L, "/product-selection")).thenReturn(1);

        Map<String, Object> payload = service.describe();

        assertEquals(true, payload.get("ready"));
        Map<String, Object> featureReadiness = castMap(payload.get("featureReadiness"));
        Map<String, Object> manualSelection = castMap(featureReadiness.get("manualSelection"));
        assertNotNull(manualSelection);
        assertEquals(false, manualSelection.get("ready"));
        assertEquals(List.of("product_selection_source_collection"), manualSelection.get("missingTables"));
        assertEquals(false, manualSelection.get("activeMenuReady"));
        assertEquals(false, manualSelection.get("bossRoleGrantReady"));
        assertEquals(false, manualSelection.get("legacyAutoSelectionMenuRetired"));
    }

    @Test
    void describeListsProductManagementSourceTypeMigrationScript() {
        when(coreTableStatusMapper.findExistingTableNames(
                "nuono_new_dev",
                List.of("user", "menu", "role_menu")
        )).thenReturn(List.of("user", "menu", "role_menu"));
        when(coreTableStatusMapper.findExistingTableNames(
                "nuono_new_dev",
                List.of("product_selection_source_collection")
        )).thenReturn(List.of("product_selection_source_collection"));
        when(coreTableStatusMapper.findExistingColumnNames(
                "nuono_new_dev",
                "product_selection_source_collection",
                List.of("collection_started_at", "collection_finished_at")
        )).thenReturn(List.of("collection_started_at", "collection_finished_at"));
        when(coreTableStatusMapper.countActiveMenuByIdAndPath(9102L, "/product/manual-selection")).thenReturn(1);
        when(coreTableStatusMapper.countActiveRoleMenu(2L, 9102L)).thenReturn(1);
        when(coreTableStatusMapper.countActiveMenuByIdAndPath(9101L, "/product-selection")).thenReturn(0);
        when(coreTableStatusMapper.findExistingTableNames(
                "nuono_new_dev",
                List.of("product_master", "noon_brand_dictionary", "noon_product_fulltype_dictionary")
        )).thenReturn(List.of("product_master", "noon_brand_dictionary", "noon_product_fulltype_dictionary"));
        when(coreTableStatusMapper.findExistingColumnNames(
                "nuono_new_dev",
                "product_master",
                List.of("product_source_type")
        )).thenReturn(List.of("product_source_type"));

        Map<String, Object> payload = service.describe();

        @SuppressWarnings("unchecked")
        List<String> initScripts = (List<String>) payload.get("initScripts");
        assertTrue(initScripts.contains("classpath:db/init/050_product_management_product_source_type.sql"));
    }

    @Test
    void describeListsOperationsConfigSuiteMigrationScripts() {
        when(coreTableStatusMapper.findExistingTableNames(
                "nuono_new_dev",
                List.of("user", "menu", "role_menu")
        )).thenReturn(List.of("user", "menu", "role_menu"));
        when(coreTableStatusMapper.findExistingTableNames(
                "nuono_new_dev",
                List.of("product_selection_source_collection")
        )).thenReturn(List.of("product_selection_source_collection"));
        when(coreTableStatusMapper.findExistingColumnNames(
                "nuono_new_dev",
                "product_selection_source_collection",
                List.of("collection_started_at", "collection_finished_at")
        )).thenReturn(List.of("collection_started_at", "collection_finished_at"));
        when(coreTableStatusMapper.countActiveMenuByIdAndPath(9102L, "/product/manual-selection")).thenReturn(1);
        when(coreTableStatusMapper.countActiveRoleMenu(2L, 9102L)).thenReturn(1);
        when(coreTableStatusMapper.countActiveMenuByIdAndPath(9101L, "/product-selection")).thenReturn(0);
        when(coreTableStatusMapper.findExistingTableNames(
                "nuono_new_dev",
                List.of("product_master", "noon_brand_dictionary", "noon_product_fulltype_dictionary")
        )).thenReturn(List.of("product_master", "noon_brand_dictionary", "noon_product_fulltype_dictionary"));
        when(coreTableStatusMapper.findExistingColumnNames(
                "nuono_new_dev",
                "product_master",
                List.of("product_source_type")
        )).thenReturn(List.of("product_source_type"));

        Map<String, Object> payload = service.describe();

        @SuppressWarnings("unchecked")
        List<String> initScripts = (List<String>) payload.get("initScripts");
        assertTrue(initScripts.contains("classpath:db/init/059_advanced_operations_config_publish_foundation.sql"));
        assertTrue(initScripts.contains("classpath:db/init/060_advanced_operations_config_menu_permission.sql"));
        assertTrue(initScripts.contains("classpath:db/init/061_operation_calendar_rule.sql"));
        assertTrue(initScripts.contains("classpath:db/init/062_operation_lifecycle_rule.sql"));
        assertTrue(initScripts.contains("classpath:db/init/065_operation_config_version_source.sql"));
        assertTrue(initScripts.contains("classpath:db/init/066_operation_config_bundle.sql"));
        assertTrue(initScripts.contains("classpath:db/init/067_operation_calendar_rule_bundle_link.sql"));
        assertTrue(initScripts.contains("classpath:db/init/068_operation_lifecycle_rule_bundle_link.sql"));
        assertTrue(initScripts.contains("classpath:db/init/070_product_selection_ali1688_real_price_snapshot.sql"));
    }

    @Test
    void describeExposesProductManagementReadinessForMissingMigrationTablesAndColumns() {
        when(coreTableStatusMapper.findExistingTableNames(
                "nuono_new_dev",
                List.of("user", "menu", "role_menu")
        )).thenReturn(List.of("user", "menu", "role_menu"));
        when(coreTableStatusMapper.findExistingTableNames(
                "nuono_new_dev",
                List.of("product_selection_source_collection")
        )).thenReturn(List.of("product_selection_source_collection"));
        when(coreTableStatusMapper.findExistingColumnNames(
                "nuono_new_dev",
                "product_selection_source_collection",
                List.of("collection_started_at", "collection_finished_at")
        )).thenReturn(List.of("collection_started_at", "collection_finished_at"));
        when(coreTableStatusMapper.countActiveMenuByIdAndPath(9102L, "/product/manual-selection")).thenReturn(1);
        when(coreTableStatusMapper.countActiveRoleMenu(2L, 9102L)).thenReturn(1);
        when(coreTableStatusMapper.countActiveMenuByIdAndPath(9101L, "/product-selection")).thenReturn(0);
        when(coreTableStatusMapper.findExistingTableNames(
                "nuono_new_dev",
                List.of("product_master", "noon_brand_dictionary", "noon_product_fulltype_dictionary")
        )).thenReturn(List.of("product_master"));
        when(coreTableStatusMapper.findExistingColumnNames(
                "nuono_new_dev",
                "product_master",
                List.of("product_source_type")
        )).thenReturn(List.of());

        Map<String, Object> payload = service.describe();

        Map<String, Object> featureReadiness = castMap(payload.get("featureReadiness"));
        Map<String, Object> productManagement = castMap(featureReadiness.get("productManagement"));
        assertNotNull(productManagement);
        assertEquals(false, productManagement.get("ready"));
        assertEquals(
                List.of("noon_brand_dictionary", "noon_product_fulltype_dictionary"),
                productManagement.get("missingTables")
        );
        Map<String, Object> missingColumns = castMap(productManagement.get("missingColumns"));
        assertEquals(List.of("product_source_type"), missingColumns.get("product_master"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
