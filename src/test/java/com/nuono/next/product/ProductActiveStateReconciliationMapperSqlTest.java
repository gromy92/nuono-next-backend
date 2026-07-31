package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProductActiveStateReconciliationMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class ProductActiveStateReconciliationMapperSqlTest {

    @Test
    void candidatesUseTheSameSiteOfferAndPartnerSkuGrainAsReplenishment() throws Exception {
        String sql = selectSql("listUnknownCandidates", Long.class, String.class, String.class, int.class);

        assertThat(sql).contains("pso.id AS siteOfferId");
        assertThat(sql).contains("pv.id AS variantId");
        assertThat(sql).contains("pv.partner_sku AS partnerSku");
        assertThat(sql).contains("pso.is_active IS NULL");
        assertThat(sql).contains("pso.maintenance_enabled = b'1'");
        assertThat(sql).contains("'|siteOffer:', pso.id");
        assertThat(sql).contains("attempt.status IN ('QUEUED', 'RUNNING')");
        assertThat(sql).contains("ORDER BY");
        assertThat(sql).contains("LIMIT #{limit}");
        assertThat(sql).doesNotContain("MIN(NULLIF(TRIM(pv.partner_sku)");
        assertThat(sql).doesNotContain("GROUP BY pm.id");
    }

    @Test
    void reconciliationScopesCoverEveryEligibleStoreSiteWithUnknownOffers() throws Exception {
        String sql = selectSql("listUnknownScopes", int.class);

        assertThat(sql).contains("GROUP BY ls.owner_user_id, lss.store_code, lss.site");
        assertThat(sql).contains("COUNT(*) AS unknownCount");
        assertThat(sql).contains("pso.is_active IS NULL");
        assertThat(sql).contains("neverAttemptedCount");
        assertThat(sql).contains("oldestAttemptAt ASC");
        assertThat(sql).contains("CASE WHEN neverAttemptedCount > 0 THEN 0 ELSE 1 END");
        assertThat(sql).contains("active_scope.status IN ('QUEUED', 'RUNNING')");
        assertThat(sql).contains("active_scope.owner_user_id = ls.owner_user_id");
        assertThat(sql).contains("LIMIT #{limit}");
        assertThat(sql).doesNotContain("307");
        assertThat(sql).doesNotContain("STR108065");
    }

    @Test
    void authoritativeUpdateIsScopedToTheExactUnknownOffer() throws Exception {
        Method method = ProductActiveStateReconciliationMapper.class.getMethod(
                "resolveUnknownActiveState",
                Long.class,
                Long.class,
                String.class,
                String.class,
                String.class,
                Boolean.class,
                String.class,
                java.time.LocalDateTime.class
        );
        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ")
                .trim();

        assertThat(sql).contains("pso.id = #{siteOfferId}");
        assertThat(sql).contains("ls.owner_user_id = #{ownerUserId}");
        assertThat(sql).contains("UPPER(lss.store_code) = UPPER(#{storeCode})");
        assertThat(sql).contains("UPPER(lss.site) = UPPER(#{siteCode})");
        assertThat(sql).contains("BINARY pv.partner_sku = BINARY #{partnerSku}");
        assertThat(sql).contains("pso.is_active IS NULL");
        assertThat(sql).contains("pso.active_state_source = #{source}");
    }

    private static String selectSql(String name, Class<?>... parameterTypes) throws Exception {
        Method method = ProductActiveStateReconciliationMapper.class.getMethod(name, parameterTypes);
        return String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ")
                .trim();
    }
}
