package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProductImageProfileMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class ProductImageSuiteAssetMutationMapperSqlTest {

    @Test
    void inheritedAssetMutationsShouldFencePublishingSuites() throws Exception {
        Method deleteMethod = ProductImageProfileMapper.class.getMethod(
                "deleteSuiteAsset",
                Long.class,
                Long.class
        );
        String deleteSql = sql(deleteMethod.getAnnotation(Delete.class).value());
        assertThat(deleteSql)
                .contains("JOIN product_image_suite suite")
                .contains("suite.suite_status <> 'PUBLISHING'");

        Method reorderMethod = ProductImageProfileMapper.class.getMethod(
                "updateSuiteAssetSortOrder",
                Long.class,
                Long.class,
                Integer.class
        );
        String reorderSql = sql(reorderMethod.getAnnotation(Update.class).value());
        assertThat(reorderSql)
                .contains("JOIN product_image_suite suite")
                .contains("suite.suite_status <> 'PUBLISHING'");
    }

    @Test
    void inheritedMoveShouldFenceBothSourceAndTargetSuites() throws Exception {
        Method method = ProductImageProfileMapper.class.getMethod(
                "moveSuiteAssetToSuite",
                Long.class,
                Long.class,
                Long.class,
                Integer.class
        );
        String sql = sql(method.getAnnotation(Update.class).value());

        assertThat(sql)
                .contains("source_suite.suite_status <> 'PUBLISHING'")
                .contains("target_suite.suite_status <> 'PUBLISHING'")
                .contains("source_suite.deleted = b'0'")
                .contains("target_suite.deleted = b'0'");
    }

    private static String sql(String[] fragments) {
        return String.join(" ", fragments).replaceAll("\\s+", " ");
    }
}
