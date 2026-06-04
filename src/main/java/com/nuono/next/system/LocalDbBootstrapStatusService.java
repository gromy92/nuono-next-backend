package com.nuono.next.system;

import com.nuono.next.infrastructure.mapper.CoreTableStatusMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local-db")
public class LocalDbBootstrapStatusService {

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
                "classpath:db/init/051_product_selection_scope_cleanup.sql",
                "classpath:db/init/055_file_management_logistics_output_structure.sql",
                "classpath:db/init/065_unified_logistics_quote_facts.sql",
                "classpath:db/init/067_official_fbn_outbound_fee_facts.sql",
                "classpath:db/init/068_noon_call_store_data_system_reports_path.sql",
                "classpath:db/init/074_product_site_offer_listing_started_at.sql",
                "classpath:db/init/074_product_variant_spec.sql",
                "classpath:db/init/079_product_site_offer_data_missing_site_coverage.sql",
                "classpath:db/init/080_product_listing_dry_run.sql",
                "classpath:db/init/081_product_listing_menu_permission.sql"
            ));
        return payload;
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
