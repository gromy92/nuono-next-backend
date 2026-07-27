package com.nuono.next.infrastructure.mapper;

import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.RateCardRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface PublishedProductLogisticsRateCardMapper {

    @Select({
            "<script>",
            "SELECT DISTINCT",
            "  price.id, #{ownerUserId} AS ownerUserId, route.site_code AS siteCode,",
            "  CASE WHEN route.forwarder_code = 'YT' THEN 'YITE' ELSE route.forwarder_code END AS forwarderCode,",
            "  COALESCE(forwarder.name, route.forwarder_code) AS forwarderName,",
            "  route.transport_mode AS transportMode, 'HEADHAUL' AS feeType,",
            "  price.cargo_category_code AS cargoCategoryCode, price.cargo_category_name AS cargoCategoryName,",
            "  price.billing_unit AS chargeUnit, price.unit_price AS unitCostCny, price.currency AS currencyCode,",
            "  'PUBLISHED_FORWARDER_QUOTE' AS sourceType, version.version_no AS sourceReference,",
            "  price.gmt_updated AS effectiveAt, price.remark, NULL AS evidenceJson",
            "FROM forwarder_quote_route_template route",
            "JOIN forwarder_quote_route_template_segment segment",
            "  ON segment.route_code = route.route_code",
            " AND segment.segment_role = 'HEADHAUL'",
            "JOIN forwarder_quote_version version",
            "  ON version.id = route.quote_version_id",
            " AND version.status = 'PUBLISHED'",
            "JOIN forwarder",
            "  ON forwarder.id = version.forwarder_id",
            " AND forwarder.status = 'ACTIVE'",
            "JOIN forwarder_quote_service_line line",
            "  ON line.service_code = segment.service_code",
            " AND line.quote_version_id = version.id",
            " AND line.active_for_mvp = b'1'",
            "JOIN forwarder_quote_base_price price",
            "  ON price.service_code = line.service_code",
            " AND price.quote_version_id = version.id",
            " AND price.price_status = 'NORMAL'",
            " AND price.unit_price > 0",
            " AND UPPER(price.currency) IN ('RMB', 'CNY')",
            "WHERE route.active_for_purchase_order = b'1'",
            "<if test='siteCode != null and siteCode != \"\"'>",
            "  AND route.site_code = #{siteCode}",
            "</if>",
            "<if test='forwarderCode != null and forwarderCode != \"\"'>",
            "  AND (route.forwarder_code = #{forwarderCode}",
            "       OR (#{forwarderCode} = 'YITE' AND route.forwarder_code = 'YT'))",
            "</if>",
            "<if test='transportMode != null and transportMode != \"\"'>",
            "  AND route.transport_mode = #{transportMode}",
            "</if>",
            "ORDER BY route.site_code, forwarderCode, route.transport_mode,",
            "         price.cargo_category_name, price.id",
            "</script>"
    })
    List<RateCardRow> listPublishedRateCards(
            @Param("ownerUserId") Long ownerUserId,
            @Param("siteCode") String siteCode,
            @Param("forwarderCode") String forwarderCode,
            @Param("transportMode") String transportMode
    );
}
