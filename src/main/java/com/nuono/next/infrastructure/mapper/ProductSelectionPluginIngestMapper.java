package com.nuono.next.infrastructure.mapper;

import com.nuono.next.productselection.ProductSelectionSourceCollectionRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProductSelectionPluginIngestMapper {

    @Select({
            "SELECT",
            "  source.id, source.owner_user_id, source.logical_store_id, source.site_code, source.collection_no,",
            "  source.source_type, source.collection_source, source.plugin_batch_id, source.plugin_item_key,",
            "  source.extractor_version, source.source_platform, source.source_url, source.page_url,",
            "  source.source_title, source.source_title_cn, source.source_title_ar, source.source_image_url, source.image_urls_json,",
            "  source.price_summary, source.moq_hint, source.shipping_from, source.brand_name, source.unit_count, source.color_name,",
            "  source.spec_hints_json, source.category_links_json, source.spec_attribute_count,",
            "  source.source_description_en, source.source_description_ar,",
            "  source.source_selling_points_en_json, source.source_selling_points_ar_json,",
            "  source.selected_text, source.selected_text_ar, source.notes, source.status,",
            "  source.failure_code, source.failure_message, source.created_by, source.updated_by",
            "FROM product_selection_source_collection source",
            "WHERE source.owner_user_id = #{ownerUserId}",
            "  AND source.logical_store_id = #{logicalStoreId}",
            "  AND source.plugin_batch_id = #{pluginBatchId}",
            "  AND source.plugin_item_key = #{pluginItemKey}",
            "  AND source.source_type = 'marketplace-url'",
            "  AND source.collection_source = 'plugin'",
            "  AND source.is_deleted = b'0'",
            "LIMIT 1",
            "FOR UPDATE"
    })
    ProductSelectionSourceCollectionRow selectByBatchItem(
            @Param("ownerUserId") Long ownerUserId,
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("pluginBatchId") String pluginBatchId,
            @Param("pluginItemKey") String pluginItemKey
    );
}
