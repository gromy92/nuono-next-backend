package com.nuono.next.infrastructure.mapper;

import com.nuono.next.competitoranalysis.CompetitorProductInsertCommand;
import org.apache.ibatis.annotations.Update;

public interface CompetitorProductDetailWriteMapper {
    @Update({
            "UPDATE operations_competitor_product",
            "SET canonical_url = COALESCE(#{canonicalUrl}, canonical_url),",
            "    title_snapshot = COALESCE(#{titleSnapshot}, title_snapshot),",
            "    title_en_snapshot = COALESCE(#{titleEnSnapshot}, title_en_snapshot),",
            "    title_ar_snapshot = COALESCE(#{titleArSnapshot}, title_ar_snapshot),",
            "    brand_snapshot = COALESCE(#{brandSnapshot}, brand_snapshot),",
            "    image_url_snapshot = COALESCE(#{imageUrlSnapshot}, image_url_snapshot),",
            "    price_amount_snapshot = COALESCE(#{priceAmountSnapshot}, price_amount_snapshot),",
            "    currency_code_snapshot = COALESCE(#{currencyCodeSnapshot}, currency_code_snapshot),",
            "    rating_snapshot = COALESCE(#{ratingSnapshot}, rating_snapshot),",
            "    review_count_snapshot = COALESCE(#{reviewCountSnapshot}, review_count_snapshot),",
            "    tags_snapshot_json = COALESCE(#{tagsSnapshotJson}, tags_snapshot_json),",
            "    source_type = COALESCE(#{sourceType}, source_type),",
            "    last_seen_at = NOW(),",
            "    updated_by = #{actorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND watch_product_id = #{watchProductId}",
            "  AND UPPER(noon_product_code) = UPPER(#{noonProductCode})",
            "  AND review_status = 'CONFIRMED'",
            "  AND is_deleted = b'0'"
    })
    int updateCompetitorProductFromDetail(CompetitorProductInsertCommand command);
}
