package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProductDetailBaselineCandidateMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class ProductDetailBaselineCandidateMapperSqlTest {

    @Test
    void shouldSelectOnlyMaintainedProductsWithoutAFullBaselineInTheCurrentActiveSite() throws Exception {
        String sql = mapperSql();

        assertThat(sql).contains("ls.owner_user_id = #{ownerUserId}");
        assertThat(sql).contains("UPPER(lss.store_code) = UPPER(#{storeCode})");
        assertThat(sql).contains("UPPER(lss.site) = UPPER(#{siteCode})");
        assertThat(sql).contains("COALESCE(lss.is_mounted, b'1') = b'1'");
        assertThat(sql).contains("COALESCE(lss.site_enabled, b'1') = b'1'");
        assertThat(sql).contains("IN ('ACTIVE', 'LOCAL_READY')");
        assertThat(sql).contains("COALESCE(pso.maintenance_enabled, b'1') = b'1'");
        assertThat(sql).contains("NOT EXISTS");
        assertThat(sql).contains("FROM product_master_snapshot baseline");
        assertThat(sql).contains("baseline.snapshot_type = 'baseline'");
        assertThat(sql).doesNotContain("product_public_detail_snapshot");
    }

    @Test
    void shouldReturnEveryEligibleCandidateInStableRetryOrderWithoutAQuantityCap() throws Exception {
        String sql = mapperSql();

        assertThat(sql).contains("LEFT JOIN operational_task attempt");
        assertThat(sql).contains("attempt.task_type = 'product.detail-baseline-backfill'");
        assertThat(sql).contains("BINARY attempt.natural_key = BINARY CONCAT");
        assertThat(sql).contains("attempt.status IN ('QUEUED', 'RUNNING')");
        assertThat(sql).contains("MAX(attempt.gmt_updated) IS NULL");
        assertThat(sql).contains("MAX(attempt.gmt_updated) ASC");
        assertThat(sql).doesNotContain("LIMIT");
    }

    private static String mapperSql() throws Exception {
        Method method = ProductDetailBaselineCandidateMapper.class.getMethod(
                "listMissingMaintainedCandidates",
                Long.class,
                String.class,
                String.class
        );
        Select select = method.getAnnotation(Select.class);
        return String.join(" ", select.value()).replaceAll("\\s+", " ").trim();
    }
}
