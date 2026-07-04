package com.nuono.next.productkeyword;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProductKeywordMapper;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class ProductKeywordAdsMapperSqlTest {

    @Test
    void adsQueryFactTableExistenceCheckUsesInformationSchema() throws NoSuchMethodException {
        String sql = selectSql("adsQueryFactTableExists");

        assertThat(sql)
                .contains("information_schema.tables")
                .contains("table_schema = database()")
                .contains("table_name = 'noon_ad_query_fact'");
    }

    @Test
    void adsQueryIndexingReadsOnlyQueryFactsAndResolvesPartnerSku() throws NoSuchMethodException {
        String sql = selectSql(
                "listAdsQueryFactsForKeywordIndexing",
                Long.class,
                String.class,
                String.class,
                LocalDate.class,
                LocalDate.class,
                Integer.class
        );

        assertThat(sql)
                .contains("from noon_ad_query_fact q")
                .contains("coalesce(nullif(q.partner_sku, ''), ow.partner_sku, pp.partner_sku) as partnersku")
                .contains("official_warehouse_inventory_snapshot_line")
                .contains("product_public_detail_snapshot")
                .contains("q.owner_user_id = #{owneruserid}")
                .contains("q.store_code = #{storecode}")
                .contains("q.site_code = #{sitecode}")
                .contains("q.report_date_from &gt;= #{datefrom}")
                .contains("q.report_date_to &lt;= #{dateto}")
                .doesNotContain("insert into noon_ad")
                .doesNotContain("update noon_ad")
                .doesNotContain("delete from noon_ad");
    }

    private static String selectSql(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = ProductKeywordMapper.class.getMethod(methodName, parameterTypes);
        Select select = method.getAnnotation(Select.class);
        return Arrays.stream(select.value())
                .collect(java.util.stream.Collectors.joining(" "))
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}
