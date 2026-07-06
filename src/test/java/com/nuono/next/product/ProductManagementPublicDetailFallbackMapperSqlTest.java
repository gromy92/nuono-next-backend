package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class ProductManagementPublicDetailFallbackMapperSqlTest {

    @Test
    void listProjectionTreatsLatestUsablePublicDetailSnapshotAsReadonlyBaselineFallback() throws Exception {
        Method method = ProductManagementMapper.class.getMethod(
                "selectProductListProjection",
                Long.class,
                String.class
        );

        String sql = compactSql(method);

        assertThat(sql)
                .contains("product_public_detail_snapshot ppds")
                .contains("ppds.sync_status IN ('SUCCEEDED', 'PARTIAL')")
                .contains("ppds.is_latest = b'1'")
                .contains("THEN 'public_detail_readonly'")
                .contains("MAX(ppds.fetched_at)");
    }

    @Test
    void singleProjectionTreatsLatestUsablePublicDetailSnapshotAsReadonlyBaselineFallback() throws Exception {
        Method method = ProductManagementMapper.class.getMethod(
                "selectProductListProjectionByProductMasterId",
                Long.class,
                String.class,
                Long.class
        );

        String sql = compactSql(method);

        assertThat(sql)
                .contains("product_public_detail_snapshot ppds")
                .contains("ppds.sync_status IN ('SUCCEEDED', 'PARTIAL')")
                .contains("ppds.is_latest = b'1'")
                .contains("THEN 'public_detail_readonly'")
                .contains("MAX(ppds.fetched_at)");
    }

    private String compactSql(Method method) {
        return String.join(" ", method.getAnnotation(Select.class).value()).replaceAll("\\s+", " ");
    }
}
