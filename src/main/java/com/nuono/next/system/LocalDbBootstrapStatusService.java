package com.nuono.next.system;

import com.nuono.next.infrastructure.mapper.CoreTableStatusMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
@Profile("local-db")
public class LocalDbBootstrapStatusService {

    private static final Long MANUAL_SELECTION_MENU_ID = 9102L;
    private static final String MANUAL_SELECTION_MENU_PATH = "/product/manual-selection";
    private static final Long LEGACY_AUTO_SELECTION_MENU_ID = 9101L;
    private static final String LEGACY_AUTO_SELECTION_MENU_PATH = "/product-selection";
    private static final Long BOSS_ROLE_ID = 2L;
    private static final List<String> MANUAL_SELECTION_TABLES = List.of("product_selection_source_collection");
    private static final List<String> MANUAL_SELECTION_REQUIRED_COLUMNS = List.of(
            "collection_started_at",
            "collection_finished_at"
    );
    private static final List<String> PRODUCT_MANAGEMENT_TABLES = List.of(
            "product_master",
            "noon_brand_dictionary",
            "noon_product_fulltype_dictionary"
    );
    private static final List<String> PRODUCT_MASTER_REQUIRED_COLUMNS = List.of("product_source_type");

    private final CoreTableStatusMapper coreTableStatusMapper;
    private final BootstrapProperties bootstrapProperties;

    public LocalDbBootstrapStatusService(
            CoreTableStatusMapper coreTableStatusMapper,
            BootstrapProperties bootstrapProperties
    ) {
        this.coreTableStatusMapper = coreTableStatusMapper;
        this.bootstrapProperties = bootstrapProperties;
    }

    public Map<String, Object> describe() {
        CoreTableInspection inspection = inspect();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mode", "local-db");
        payload.put("ready", inspection.isReady());
        payload.put("existingCoreTables", inspection.getExistingTables());
        payload.put("missingCoreTables", inspection.getMissingTables());
        Map<String, Object> featureReadiness = new LinkedHashMap<>();
        featureReadiness.put("manualSelection", describeManualSelectionReadiness());
        featureReadiness.put("productManagement", describeProductManagementReadiness());
        payload.put("featureReadiness", featureReadiness);
        payload.put("initScripts", List.of(
                "classpath:db/init/000_local_dev_bootstrap.sql",
                "classpath:db/init/001_clone_legacy_core_tables.sql",
                "classpath:db/init/002_import_whitelist_sample.sql",
                "classpath:db/init/003_product_management_v1.sql",
                "classpath:db/init/004_profit_pending_acceptance_fixture.sql",
                "classpath:db/init/005_master_data_replica_baseline.sql",
                "classpath:db/init/006_procurement_auto_inquiry_acceptance_alignment.sql",
                "classpath:db/init/007_controlled_internal_account_export_scope.sql",
                "classpath:db/init/008_import_legacy_internal_account_master_data.sql",
                "classpath:db/init/009_master_data_payment_records.sql",
                "classpath:db/init/010_align_core_tables_to_online_schema.sql",
                "classpath:db/init/011_product_selection_v1.sql",
                "classpath:db/init/012_procurement_candidate_pool_v1.sql",
                "classpath:db/init/013_local_admin_account.sql",
                "classpath:db/init/014_legacy_project_store_model.sql",
                "classpath:db/init/015_legacy_prod_hotfix_role_store_noon.sql",
                "classpath:db/init/016_local_procurement_operator.sql",
                "classpath:db/init/017_clear_deprecated_user_noon_project_credentials.sql",
                "classpath:db/init/018_align_role_menu_to_latest_prod.sql",
                "classpath:db/init/019_restore_bicuithong_canman_bind_status.sql",
                "classpath:db/init/020_restore_xingyao_product_management_credentials.sql",
                "classpath:db/init/021_product_management_supermall_stock.sql",
                "classpath:db/init/022_backfill_legacy_account_noon_binding_to_matching_project.sql",
                "classpath:db/init/022_product_management_schema_hardening.sql",
                "classpath:db/init/023_correct_chenwu_site_scope.sql",
                "classpath:db/init/025_product_management_pricing_engine.sql",
                "classpath:db/init/026_procurement_dual_inquiry_channel.sql",
                "classpath:db/init/027_noon_attribute_template_cache.sql",
                "classpath:db/init/028_procurement_unpaid_order_inquiry_channel.sql",
                "classpath:db/init/029_product_management_official_detail_alignment.sql",
                "classpath:db/init/030_logistics_quote_operations_v1.sql",
                "classpath:db/init/032_product_management_remove_conflict_status.sql",
                "classpath:db/init/033_product_management_offer_list_official_metrics.sql",
                "classpath:db/init/034_product_publish_task.sql",
                "classpath:db/init/035_align_supported_permission_menus.sql",
                "classpath:db/init/035_noon_attribute_dictionary.sql",
                "classpath:db/init/036_noon_brand_fulltype_dictionary.sql",
                "classpath:db/init/037_file_management_parse_v1.sql",
                "classpath:db/init/038_procurement_acceptance_purchase_list_fixture.sql",
                "classpath:db/init/039_file_management_commission_tier_standard.sql",
                "classpath:db/init/040_file_management_parse_iteration.sql",
                "classpath:db/init/041_product_selection_source_collection_localization.sql",
                "classpath:db/init/042_product_selection_source_collection_spec_count.sql",
                "classpath:db/init/043_product_selection_source_collection_fixed_specs.sql",
                "classpath:db/init/044_product_group_projection_consistency.sql",
                "classpath:db/init/044_product_selection_source_collection_descriptions.sql",
                "classpath:db/init/045_file_management_parse_stable_base.sql",
                "classpath:db/init/046_product_selection_source_collection_selling_points.sql",
                "classpath:db/init/047_product_management_noop_draft_cleanup.sql",
                "classpath:db/init/048_product_manual_selection_menu_permission.sql",
                "classpath:db/init/049_product_selection_source_collection_hardening.sql",
                "classpath:db/init/050_product_management_product_source_type.sql",
                "classpath:db/init/051_product_selection_scope_cleanup.sql",
                "classpath:db/init/052_product_selection_ali1688_collection.sql",
                "classpath:db/init/053_sales_data_analysis_foundation.sql",
                "classpath:db/init/054_sales_data_menu_permission.sql",
                "classpath:db/init/055_file_management_logistics_output_structure.sql",
                "classpath:db/init/055_product_selection_ali1688_plugin_assignment.sql",
                "classpath:db/init/056_product_lifecycle_state_engine.sql",
                "classpath:db/init/057_product_lifecycle_listing_source.sql",
                "classpath:db/init/058_product_selection_source_collection_timing.sql",
                "classpath:db/init/059_advanced_operations_config_publish_foundation.sql",
                "classpath:db/init/060_advanced_operations_config_menu_permission.sql",
                "classpath:db/init/061_operation_calendar_rule.sql",
                "classpath:db/init/062_operation_lifecycle_rule.sql",
                "classpath:db/init/063_product_lifecycle_job_recalculation_audit.sql",
                "classpath:db/init/064_product_selection_ali1688_task_backfill.sql",
                "classpath:db/init/064_system_reports_store_data_menu_permission.sql",
                "classpath:db/init/065_operation_config_version_source.sql",
                "classpath:db/init/065_unified_logistics_quote_facts.sql",
                "classpath:db/init/066_operation_config_bundle.sql",
                "classpath:db/init/067_official_fbn_outbound_fee_facts.sql",
                "classpath:db/init/067_operation_calendar_rule_bundle_link.sql",
                "classpath:db/init/068_operation_lifecycle_rule_bundle_link.sql",
                "classpath:db/init/069_operation_config_typed_version_library.sql",
                "classpath:db/init/070_product_selection_ali1688_real_price_snapshot.sql"
            ));
        return payload;
    }

    private Map<String, Object> describeProductManagementReadiness() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("expectedTables", PRODUCT_MANAGEMENT_TABLES);
        payload.put("requiredColumns", Map.of("product_master", PRODUCT_MASTER_REQUIRED_COLUMNS));

        List<String> existingTables = List.of();
        List<String> missingTables = PRODUCT_MANAGEMENT_TABLES;
        Map<String, Object> missingColumns = new LinkedHashMap<>();
        String readinessCheckFailure = null;
        try {
            List<String> queriedExistingTables = coreTableStatusMapper.findExistingTableNames(
                    bootstrapProperties.getSchema(),
                    PRODUCT_MANAGEMENT_TABLES
            );
            existingTables = queriedExistingTables;
            missingTables = PRODUCT_MANAGEMENT_TABLES.stream()
                    .filter(table -> !queriedExistingTables.contains(table))
                    .collect(Collectors.toList());
            if (queriedExistingTables.contains("product_master")) {
                List<String> existingProductMasterColumns = coreTableStatusMapper.findExistingColumnNames(
                        bootstrapProperties.getSchema(),
                        "product_master",
                        PRODUCT_MASTER_REQUIRED_COLUMNS
                );
                List<String> missingProductMasterColumns = PRODUCT_MASTER_REQUIRED_COLUMNS.stream()
                        .filter(column -> !existingProductMasterColumns.contains(column))
                        .collect(Collectors.toList());
                if (!missingProductMasterColumns.isEmpty()) {
                    missingColumns.put("product_master", missingProductMasterColumns);
                }
            } else {
                missingColumns.put("product_master", PRODUCT_MASTER_REQUIRED_COLUMNS);
            }
        } catch (DataAccessException exception) {
            readinessCheckFailure = exception.getMessage();
        }

        payload.put("ready", missingTables.isEmpty() && missingColumns.isEmpty() && readinessCheckFailure == null);
        payload.put("existingTables", existingTables);
        payload.put("missingTables", missingTables);
        payload.put("missingColumns", missingColumns);
        payload.put("readinessCheckFailure", readinessCheckFailure);
        return payload;
    }

    private Map<String, Object> describeManualSelectionReadiness() {
        List<String> existingTables = coreTableStatusMapper.findExistingTableNames(
                bootstrapProperties.getSchema(),
                MANUAL_SELECTION_TABLES
        );
        List<String> missingTables = MANUAL_SELECTION_TABLES.stream()
                .filter(table -> !existingTables.contains(table))
                .collect(Collectors.toList());

        boolean activeMenuReady = false;
        boolean bossRoleGrantReady = false;
        boolean legacyAutoSelectionMenuRetired = false;
        List<String> missingColumns = List.of();
        String permissionCheckFailure = null;
        try {
            if (existingTables.contains("product_selection_source_collection")) {
                List<String> existingColumns = coreTableStatusMapper.findExistingColumnNames(
                        bootstrapProperties.getSchema(),
                        "product_selection_source_collection",
                        MANUAL_SELECTION_REQUIRED_COLUMNS
                );
                missingColumns = MANUAL_SELECTION_REQUIRED_COLUMNS.stream()
                        .filter(column -> !existingColumns.contains(column))
                        .collect(Collectors.toList());
            } else {
                missingColumns = MANUAL_SELECTION_REQUIRED_COLUMNS;
            }
            activeMenuReady = coreTableStatusMapper.countActiveMenuByIdAndPath(
                    MANUAL_SELECTION_MENU_ID,
                    MANUAL_SELECTION_MENU_PATH
            ) > 0;
            bossRoleGrantReady = coreTableStatusMapper.countActiveRoleMenu(BOSS_ROLE_ID, MANUAL_SELECTION_MENU_ID) > 0;
            legacyAutoSelectionMenuRetired = coreTableStatusMapper.countActiveMenuByIdAndPath(
                    LEGACY_AUTO_SELECTION_MENU_ID,
                    LEGACY_AUTO_SELECTION_MENU_PATH
            ) <= 0;
        } catch (DataAccessException exception) {
            permissionCheckFailure = exception.getMessage();
        }

        Map<String, Object> readiness = new LinkedHashMap<>();
        readiness.put("ready", missingTables.isEmpty()
                && missingColumns.isEmpty()
                && activeMenuReady
                && bossRoleGrantReady
                && legacyAutoSelectionMenuRetired
                && permissionCheckFailure == null);
        readiness.put("expectedTables", MANUAL_SELECTION_TABLES);
        readiness.put("requiredColumns", Map.of("product_selection_source_collection", MANUAL_SELECTION_REQUIRED_COLUMNS));
        readiness.put("existingTables", existingTables);
        readiness.put("missingTables", missingTables);
        readiness.put("missingColumns", Map.of("product_selection_source_collection", missingColumns));
        readiness.put("menuId", MANUAL_SELECTION_MENU_ID);
        readiness.put("menuPath", MANUAL_SELECTION_MENU_PATH);
        readiness.put("activeMenuReady", activeMenuReady);
        readiness.put("bossRoleGrantReady", bossRoleGrantReady);
        readiness.put("legacyAutoSelectionMenuRetired", legacyAutoSelectionMenuRetired);
        readiness.put("permissionCheckFailure", permissionCheckFailure);
        return readiness;
    }

    public CoreTableInspection inspect() {
        List<String> expectedTables = bootstrapProperties.getExpectedCoreTables();
        List<String> existingTables = coreTableStatusMapper.findExistingTableNames(
                bootstrapProperties.getSchema(),
                expectedTables
        );

        List<String> missingTables = expectedTables.stream()
                .filter(table -> !existingTables.contains(table))
                .collect(Collectors.toList());

        return new CoreTableInspection(
                bootstrapProperties.getSchema(),
                expectedTables,
                existingTables,
                missingTables
        );
    }
}
