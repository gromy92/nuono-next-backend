package com.nuono.next.infrastructure.mapper;

import com.nuono.next.productselection.ProductSelectionSourceCollectionRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ProductSelectionPluginIngestMapper {

    @Select({
            "SELECT",
            "  source.id, source.owner_user_id, source.logical_store_id, source.site_code, source.collection_no,",
            "  source.source_type, source.collection_source, source.source_platform, source.source_url, source.page_url,",
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
            "  AND (#{siteCode} IS NULL OR #{siteCode} = '' OR source.site_code = #{siteCode})",
            "  AND source.source_type = 'marketplace-url'",
            "  AND source.collection_source = 'plugin'",
            "  AND source.source_platform = #{sourcePlatform}",
            "  AND source.status = 'success'",
            "  AND source.is_deleted = b'0'",
            "ORDER BY source.collected_at DESC, source.id DESC",
            "LIMIT #{limit}"
    })
    List<ProductSelectionSourceCollectionRow> listRecentForDedupe(
            @Param("ownerUserId") Long ownerUserId,
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("siteCode") String siteCode,
            @Param("sourcePlatform") String sourcePlatform,
            @Param("limit") Integer limit
    );

    @Update({
            "UPDATE product_selection_source_collection",
            "SET source_url = #{row.sourceUrl}, page_url = #{row.pageUrl},",
            "    source_title = #{row.sourceTitle}, source_title_cn = #{row.sourceTitleCn}, source_title_ar = #{row.sourceTitleAr},",
            "    source_image_url = #{row.sourceImageUrl}, image_urls_json = #{row.imageUrlsJson},",
            "    price_summary = #{row.priceSummary}, moq_hint = #{row.moqHint}, shipping_from = #{row.shippingFrom},",
            "    brand_name = #{row.brandName}, unit_count = #{row.unitCount}, color_name = #{row.colorName},",
            "    spec_hints_json = #{row.specHintsJson}, category_links_json = #{row.categoryLinksJson},",
            "    spec_attribute_count = #{row.specAttributeCount},",
            "    source_description_en = #{row.sourceDescriptionEn}, source_description_ar = #{row.sourceDescriptionAr},",
            "    source_selling_points_en_json = #{row.sourceSellingPointsEnJson},",
            "    source_selling_points_ar_json = #{row.sourceSellingPointsArJson},",
            "    selected_text = #{row.selectedText}, selected_text_ar = #{row.selectedTextAr}, notes = #{row.notes},",
            "    failure_code = NULL, failure_message = NULL, collected_at = NOW(),",
            "    updated_by = #{row.updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{row.id}",
            "  AND source_type = 'marketplace-url'",
            "  AND collection_source = 'plugin'",
            "  AND status = 'success'",
            "  AND is_deleted = b'0'"
    })
    int update(@Param("row") ProductSelectionSourceCollectionRow row);
}
