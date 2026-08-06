package com.nuono.next.product;

import com.nuono.next.infrastructure.mapper.Dp04ProjectionSchemaMapper;
import com.nuono.next.system.BootstrapProperties;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Fails before DP-04 writes when its real projection SQL contract is not installed. */
@Component
public class Dp04ProjectionSchemaPreflight {

    private static final Map<String, List<String>> REQUIRED_COLUMNS = Map.of(
            "logical_store", List.of(
                    "id", "owner_user_id", "project_code", "is_deleted"
            ),
            "logical_store_site", List.of(
                    "id", "logical_store_id", "store_code", "site", "site_enabled", "is_deleted"
            ),
            "product_management_id_sequence", List.of(
                    "sequence_name", "next_id", "gmt_create", "gmt_updated"
            ),
            "product_master", List.of(
                    "id", "logical_store_id", "partner_sku", "current_z_code", "sku_parent",
                    "product_source_type", "brand_cache", "title_cache", "title_cn_cache",
                    "product_fulltype_cache", "cover_image_url", "sku_group", "group_name_cache",
                    "group_ref", "group_member_count", "issue_count", "issue_summary_json",
                    "variant_count_cache", "sync_status", "last_synced_at", "is_deleted",
                    "created_by", "updated_by", "gmt_create", "gmt_updated"
            ),
            "product_variant", List.of(
                    "id", "logical_store_id", "product_master_id", "child_sku", "partner_sku",
                    "size_en", "size_ar", "variant_ix", "is_deleted", "created_by", "updated_by",
                    "gmt_create", "gmt_updated"
            ),
            "product_barcode", List.of(
                    "id", "product_master_id", "logical_store_id", "partner_sku", "variant_id",
                    "barcode", "barcode_type", "is_primary", "is_deleted", "created_by",
                    "updated_by", "gmt_create", "gmt_updated"
            ),
            "product_site_offer", List.of(
                    "id", "product_master_id", "logical_store_id", "partner_sku", "variant_id",
                    "site_id", "site_code", "psku_code", "offer_code", "currency", "price",
                    "sale_price", "sale_start", "sale_end", "price_min", "price_max", "final_price",
                    "final_price_source", "active_promotion_code", "active_promotion_name",
                    "active_promotion_url", "promotion_payload_json", "price_synced_at",
                    "pricing_method", "pricing_rule", "price_engine_min", "price_engine_max",
                    "id_warranty", "offer_note", "delivery_method", "is_winning_buybox", "is_active",
                    "active_state_source", "active_state_synced_at", "maintenance_enabled",
                    "live_status", "status_code", "listing_started_at", "listing_started_source",
                    "fbn_stock", "supermall_stock", "fbp_stock", "views_count", "units_sold",
                    "sales_amount", "sales_currency", "logistics_has_history",
                    "logistics_first_flow_at", "logistics_last_flow_at", "logistics_history_source",
                    "last_synced_at", "is_deleted", "created_by", "updated_by", "gmt_create",
                    "gmt_updated"
            ),
            "product_image_asset", List.of(
                    "id", "product_master_id", "source_type", "url", "storage_key",
                    "original_filename", "content_type", "size_bytes", "width_px", "height_px",
                    "sha256", "asset_status", "is_deleted", "created_by", "updated_by",
                    "gmt_create", "gmt_updated"
            ),
            "product_issue", List.of(
                    "id", "product_master_id", "site_id", "variant_id", "issue_scope_key",
                    "issue_source", "issue_code", "issue_hash", "severity", "title", "message",
                    "raw_json", "issue_status", "first_seen_at", "last_seen_at", "resolved_at",
                    "is_deleted", "created_by", "updated_by", "gmt_create", "gmt_updated"
            )
    );

    private final Dp04ProjectionSchemaMapper schemaMapper;
    private final BootstrapProperties bootstrapProperties;

    public Dp04ProjectionSchemaPreflight(
            Dp04ProjectionSchemaMapper schemaMapper,
            BootstrapProperties bootstrapProperties
    ) {
        this.schemaMapper = Objects.requireNonNull(schemaMapper, "schemaMapper");
        this.bootstrapProperties = Objects.requireNonNull(bootstrapProperties, "bootstrapProperties");
    }

    public void requireReady() {
        List<String> tables = new ArrayList<>(REQUIRED_COLUMNS.keySet());
        List<String> found = schemaMapper.findExistingColumnKeys(
                bootstrapProperties.getSchema(),
                tables
        );
        Set<String> existing = new HashSet<>();
        if (found != null) {
            for (String columnKey : found) {
                if (columnKey != null) {
                    existing.add(columnKey.toLowerCase(Locale.ROOT));
                }
            }
        }
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, List<String>> table : REQUIRED_COLUMNS.entrySet()) {
            for (String column : table.getValue()) {
                String key = table.getKey() + '.' + column;
                if (!existing.contains(key)) {
                    missing.add(key);
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "DP-04 product projection schema is missing: " + String.join(",", missing)
            );
        }
    }
}
