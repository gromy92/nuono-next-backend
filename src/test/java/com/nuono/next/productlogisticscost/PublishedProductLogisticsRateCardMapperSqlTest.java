package com.nuono.next.productlogisticscost;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.PublishedProductLogisticsRateCardMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class PublishedProductLogisticsRateCardMapperSqlTest {

    @Test
    void mapperDynamicSqlRegistersSuccessfully() {
        Configuration configuration = new Configuration();
        configuration.addMapper(PublishedProductLogisticsRateCardMapper.class);
    }

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
        assertThat(sql).contains("version.effective_from &lt;= CURRENT_DATE");
        assertThat(sql).contains("version.effective_to IS NULL OR version.effective_to >= CURRENT_DATE");
        assertThat(sql).contains("JOIN forwarder_quote_base_price price");
        assertThat(sql).contains("price.price_status = 'NORMAL'");
        assertThat(sql).contains("price.unit_price AS unitCostCny");
        assertThat(sql).contains("price.unit_price > 0");
        assertThat(sql).doesNotContain("forwarder_quote_numeric_adjustment");
        assertThat(sql).contains("LEFT JOIN forwarder_quote_cargo_category category");
        assertThat(sql).contains("category.product_examples");
        assertThat(sql).contains("AS cargoCategoryDescription");
        assertThat(sql).contains("version.effective_from AS effectiveAt");
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
                .contains("cargoCategoryCode, price.id")
                .doesNotContain("price.cargo_category_name")
                .doesNotContain("route.forwarder_code");
    }
}
