package com.nuono.next.productkeyword;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProductKeywordMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class ProductKeywordMapperSqlTest {

    @Test
    void mapperUpsertsKeywordAssetsAndUsageEvents() throws Exception {
        String keywordSql = mapperSql("upsertKeyword", ProductKeywordRecord.class);
        String eventSql = mapperSql("upsertUsageEvent", ProductKeywordUsageEventRecord.class);

        assertThat(keywordSql)
                .contains("INSERT INTO product_keyword")
                .contains("ON DUPLICATE KEY UPDATE")
                .contains("id = LAST_INSERT_ID(id)")
                .contains("status = VALUES(status)")
                .contains("intent_tags_json = VALUES(intent_tags_json)");
        assertThat(eventSql)
                .contains("INSERT INTO product_keyword_usage_event")
                .contains("ON DUPLICATE KEY UPDATE")
                .contains("event_natural_key")
                .contains("payload_json = VALUES(payload_json)")
                .contains("metrics_json = VALUES(metrics_json)");
    }

    @Test
    void mapperReadsByStableProductScopeAndKeywordNorm() throws Exception {
        String selectSql = mapperSql(
                "selectByScopeAndNorm",
                Long.class,
                String.class,
                String.class,
                String.class,
                String.class
        );
        String listSql = mapperSql("listKeywords", ProductKeywordListQuery.class);
        String eventsSql = mapperSql(
                "listEvents",
                Long.class,
                String.class,
                String.class,
                String.class,
                Integer.class
        );

        assertThat(selectSql)
                .contains("FROM product_keyword")
                .contains("owner_user_id = #{ownerUserId}")
                .contains("store_code = #{storeCode}")
                .contains("site_code = #{siteCode}")
                .contains("partner_sku = #{partnerSku}")
                .contains("keyword_norm = #{keywordNorm}")
                .contains("is_deleted = b'0'");
        assertThat(listSql)
                .contains("owner_user_id = #{query.ownerUserId}")
                .contains("store_code = #{query.storeCode}")
                .contains("ORDER BY last_seen_at DESC, id DESC");
        assertThat(eventsSql)
                .contains("FROM product_keyword_usage_event")
                .contains("owner_user_id = #{ownerUserId}")
                .contains("store_code = #{storeCode}")
                .contains("partner_sku = #{partnerSku}")
                .contains("LIMIT #{limit}");
    }

    private static String mapperSql(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = ProductKeywordMapper.class.getMethod(methodName, parameterTypes);
        Select select = method.getAnnotation(Select.class);
        if (select != null) {
            return normalizeSql(String.join(" ", select.value()));
        }
        Insert insert = method.getAnnotation(Insert.class);
        if (insert != null) {
            return normalizeSql(String.join(" ", insert.value()));
        }
        throw new IllegalArgumentException("No SQL annotation found on " + methodName);
    }

    private static String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
