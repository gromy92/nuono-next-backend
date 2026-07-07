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
                "classpath:db/init/082_product_management_schema_hardening.sql",
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
                "classpath:db/init/083_noon_attribute_dictionary.sql",
                "classpath:db/init/036_noon_brand_fulltype_dictionary.sql",
                "classpath:db/init/037_file_management_parse_v1.sql",
                "classpath:db/init/038_procurement_acceptance_purchase_list_fixture.sql",
                "classpath:db/init/039_file_management_commission_tier_standard.sql",
                "classpath:db/init/040_file_management_parse_iteration.sql",
                "classpath:db/init/041_product_selection_source_collection_localization.sql",
                "classpath:db/init/042_product_selection_source_collection_spec_count.sql",
                "classpath:db/init/043_product_selection_source_collection_fixed_specs.sql",
                "classpath:db/init/044_product_group_projection_consistency.sql",
                "classpath:db/init/084_product_selection_source_collection_descriptions.sql",
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
                "classpath:db/init/071_procurement_ali1688_historical_order_sync.sql",
                "classpath:db/init/072_procurement_ali1688_historical_order_excel_import.sql",
                "classpath:db/init/073_procurement_ali1688_order_assignment.sql",
                "classpath:db/init/074_product_site_offer_listing_started_at.sql",
                "classpath:db/init/089_product_variant_spec.sql",
                "classpath:db/init/079_product_site_offer_data_missing_site_coverage.sql",
                "classpath:db/init/090_product_listing_dry_run.sql",
                "classpath:db/init/091_product_listing_menu_permission.sql",
                "classpath:db/init/092_procurement_ali1688_order_cleanup_audit.sql",
                "classpath:db/init/093_procurement_ali1688_order_consumable_assignment.sql",
                "classpath:db/init/094_procurement_ali1688_order_product_link.sql",
                "classpath:db/init/095_procurement_ali1688_order_product_link_audit.sql",
                "classpath:db/init/096_procurement_ali1688_sku_purchase_batch.sql",
                "classpath:db/init/097_procurement_ali1688_order_assignment_active_guards.sql",
                "classpath:db/init/098_operational_task_foundation.sql",
                "classpath:db/init/099_operations_competitor_analysis.sql",
                "classpath:db/init/100_operations_competitor_analysis_menu_permission.sql",
                "classpath:db/init/101_operations_competitor_watch_product_remove_confirmation.sql",
                "classpath:db/init/102_operations_competitor_keyword_product_ignore_cleanup.sql",
                "classpath:db/init/103_operations_competitor_product_snapshot_change.sql",
                "classpath:db/init/104_operations_competitor_rank_fact_channel_scan_depth.sql",
                "classpath:db/init/118_procurement_ali1688_order_sku_allocation.sql",
                "classpath:db/init/119_procurement_purchase_order.sql",
                "classpath:db/init/120_product_psku_lifecycle_archive.sql",
                "classpath:db/init/121_procurement_archived_purchase_history.sql",
                "classpath:db/init/122_procurement_purchase_order_logistics_plan.sql",
                "classpath:db/init/123_procurement_purchase_order_transport_mode.sql",
                "classpath:db/init/124_forwarder_quote_data_quality.sql",
                "classpath:db/init/125_forwarder_quote_et_20260604.sql",
                "classpath:db/init/126_product_variant_logistics_profile.sql",
                "classpath:db/init/127_procurement_ali1688_history_read_model.sql",
                "classpath:db/init/128_procurement_logistics_route_cost_components.sql",
                "classpath:db/init/129_warehouse_dispatch_procurement_flow.sql",
                "classpath:db/init/130_warehouse_dispatch_menu_permission.sql",
                "classpath:db/init/131_product_logistics_profile_split_sensitive_attributes.sql",
                "classpath:db/init/135_warehouse_shipping_recommendation_options.sql",
                "classpath:db/init/136_warehouse_shipping_evaluation_snapshot.sql",
                "classpath:db/init/137_forwarder_quote_et_ae_route_templates.sql",
                "classpath:db/init/138_warehouse_dispatch_balance_new_product_flag.sql",
                "classpath:db/init/133_product_variant_spec_source_storage_type_code.sql",
                "classpath:db/init/134_official_warehouse_asn.sql",
                "classpath:db/init/135_product_variant_spec_source_noon_partner_psku.sql",
                "classpath:db/init/136_official_warehouse_appointment.sql",
                "classpath:db/init/137_official_warehouse_asn_noon_updated_at.sql",
                "classpath:db/init/138_official_warehouse_appointment_gate_docks.sql",
                "classpath:db/init/140_in_transit_super_search_indexes.sql",
                "classpath:db/init/141_procurement_ali1688_sku_purchase_batch_combo_support.sql",
                "classpath:db/init/142_in_transit_batch_active_reference_unique.sql",
                "classpath:db/init/143_official_warehouse_statistics.sql",
                "classpath:db/init/144_official_warehouse_asn_shipping_batch_link.sql",
                "classpath:db/init/145_procurement_purchase_order_logistics_quote_confirmation.sql",
                "classpath:db/init/146_procurement_shipping_order.sql",
                "classpath:db/init/147_product_forwarder_declaration_attribute.sql",
                "classpath:db/init/148_procurement_logistics_billing.sql",
                "classpath:db/init/149_product_variant_psku_identity.sql",
                "classpath:db/init/150_sales_product_psku_identity.sql",
                "classpath:db/init/152_psku_product_model_realignment.sql",
                "classpath:db/init/153_psku_product_model_forwarder_legacy_backfill.sql",
                "classpath:db/init/154_operations_image_skin_management.sql",
                "classpath:db/init/155_product_image_profile.sql",
                "classpath:db/init/156_product_image_asset_metadata.sql",
                "classpath:db/init/157_product_image_profile_fact_text.sql",
                "classpath:db/init/158_product_image_ai_suite_draft.sql",
                "classpath:db/init/159_product_image_logical_store_scope.sql",
                "classpath:db/init/160_noon_advertising_read_model.sql",
                "classpath:db/init/162_product_listing_real_run.sql",
                "classpath:db/init/163_product_logistics_cost_ledger.sql",
                "classpath:db/init/169_noon_risk_backoff_state.sql",
                "classpath:db/init/170_product_selection_source_collection_backoff.sql",
                "classpath:db/init/171_operations_competitor_rank_search_metadata.sql",
                "classpath:db/init/172_product_site_offer_logistics_history.sql",
                "classpath:db/init/174_product_keyword_management.sql",
                "classpath:db/init/175_product_keyword_competitor_link.sql",
                "classpath:db/init/176_product_operation_stage.sql",
                "classpath:db/init/177_in_transit_batch_estimated_arrival_source.sql",
                "classpath:db/init/178_logistics_auto_sync.sql"
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
