package com.nuono.next.productlogisticscost;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.PublishedProductLogisticsRateCardMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class PublishedProductLogisticsRateCardMapperSqlTest {

    @Test
    void publishedRateCardReadUsesActivePurchaseRoutesAndPublishedNormalPrices() throws Exception {
        Method method = PublishedProductLogisticsRateCardMapper.class.getMethod(
                "listPublishedRateCards",
                Long.class,
                String.class,
                String.class,
                String.class
        );
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ")
                .trim();

        assertThat(sql).contains("FROM forwarder_quote_route_template route");
        assertThat(sql).contains("JOIN forwarder_quote_route_template_segment segment");
        assertThat(sql).contains("segment.segment_role = 'HEADHAUL'");
        assertThat(sql).contains("JOIN forwarder_quote_version version");
        assertThat(sql).contains("version.status = 'PUBLISHED'");
        assertThat(sql).contains("JOIN forwarder_quote_base_price price");
        assertThat(sql).contains("price.price_status = 'NORMAL'");
        assertThat(sql).contains("route.active_for_purchase_order = b'1'");
        assertThat(sql).contains("route.site_code = #{siteCode}");
        assertThat(sql).contains("route.forwarder_code = #{forwarderCode}");
        assertThat(sql).contains("#{forwarderCode} = 'YITE' AND route.forwarder_code = 'YT'");
        assertThat(sql).contains("route.transport_mode = #{transportMode}");
        assertThat(sql).contains("'PUBLISHED_FORWARDER_QUOTE' AS sourceType");
        assertThat(sql).contains("version.version_no AS sourceReference");
        assertThat(sql).doesNotContain("version.quote_version_code");
        String orderBy = sql.substring(sql.indexOf("ORDER BY"));
        assertThat(orderBy)
                .contains("ORDER BY route.site_code, forwarderCode, route.transport_mode")
                .doesNotContain("route.forwarder_code");
    }
}
