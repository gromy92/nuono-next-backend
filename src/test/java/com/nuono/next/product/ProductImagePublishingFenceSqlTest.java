package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProductImageProfileMapper;
import com.nuono.next.infrastructure.mapper.ProductImagePublishWorkflowMapper;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class ProductImagePublishingFenceSqlTest {

    @Test
    void suiteStateMutationsShouldRejectPublishingSuites() throws Exception {
        assertThat(updateSql(
                ProductImageProfileMapper.class,
                "updateSuiteStatus",
                Long.class,
                Long.class,
                ProductImageSuiteStatus.class,
                Long.class
        )).contains("suite_status <> 'PUBLISHING'");
        assertThat(updateSql(
                ProductImageProfileMapper.class,
                "softDeleteSuite",
                Long.class,
                Long.class,
                Long.class
        )).contains("suite_status <> 'PUBLISHING'");
        assertThat(updateSql(
                ProductImageProfileMapper.class,
                "touchSuite",
                Long.class,
                Long.class,
                Long.class
        )).contains("suite_status <> 'PUBLISHING'");
    }

    @Test
    void assetMutationsShouldUseDatabasePublishingFence() throws Exception {
        String deleteSql = sql(
                ProductImageProfileMapper.class.getMethod(
                        "deleteSuiteAsset",
                        Long.class,
                        Long.class
                ).getAnnotation(Delete.class)
        );
        assertThat(deleteSql)
                .contains("JOIN product_image_suite suite")
                .contains("suite.suite_status <> 'PUBLISHING'");

        assertThat(updateSql(
                ProductImageProfileMapper.class,
                "moveSuiteAssetToSuite",
                Long.class,
                Long.class,
                Long.class,
                Integer.class
        ))
                .contains("source_suite.suite_status <> 'PUBLISHING'")
                .contains("target_suite.suite_status <> 'PUBLISHING'");

        assertThat(updateSql(
                ProductImageProfileMapper.class,
                "updateSuiteAssetSortOrder",
                Long.class,
                Long.class,
                Integer.class
        ))
                .contains("JOIN product_image_suite suite")
                .contains("suite.suite_status <> 'PUBLISHING'");
    }

    @Test
    void stalePublishingRecoveryShouldCreateExplicitManualRetryState() throws Exception {
        String sql = updateSql(
                ProductImagePublishWorkflowMapper.class,
                "failStalePublishingSuites",
                int.class,
                Long.class
        );

        assertThat(sql)
                .contains("suite_status = 'FAILED'")
                .contains("failure_stage = 'PUBLISH_STALE_RECOVERY'")
                .contains("WHERE suite_status = 'PUBLISHING'")
                .contains("DATE_SUB(NOW(), INTERVAL #{staleMinutes} MINUTE)");
    }

    @Test
    void publishAttemptMutationsShouldAllUseTheAttemptIdFence() throws Exception {
        String claimSql = updateSql(
                ProductImagePublishWorkflowMapper.class,
                "claimSuitePublishExecution",
                Long.class,
                String.class,
                String.class,
                Long.class
        );
        String checkpointSql = updateSql(
                ProductImagePublishWorkflowMapper.class,
                "updateSuitePublishManifest",
                Long.class,
                String.class,
                String.class,
                String.class,
                Long.class
        );
        String failureSql = updateSql(
                ProductImagePublishWorkflowMapper.class,
                "failPublishingSuiteWorkflow",
                Long.class,
                String.class,
                String.class,
                String.class,
                String.class,
                Long.class
        );
        String onlineSql = updateSql(
                ProductImagePublishWorkflowMapper.class,
                "markSuiteOnline",
                Long.class,
                String.class,
                String.class,
                String.class
        );

        for (String sql : new String[] {claimSql, checkpointSql, failureSql, onlineSql}) {
            assertThat(sql)
                    .contains("suite_status = 'PUBLISHING'")
                    .contains("JSON_UNQUOTE(JSON_EXTRACT(")
                    .contains("'$.attemptId'")
                    .contains("= #{attemptId}")
                    .contains("deleted = b'0'");
        }
        assertThat(claimSql)
                .contains("'$.executionToken'")
                .contains("IS NULL");
        for (String sql : new String[] {checkpointSql, failureSql, onlineSql}) {
            assertThat(sql)
                    .contains("'$.executionToken'")
                    .contains("= #{executionToken}");
        }
    }

    private static String updateSql(
            Class<?> mapperType,
            String methodName,
            Class<?>... parameterTypes
    ) throws Exception {
        return sql(mapperType.getMethod(methodName, parameterTypes).getAnnotation(Update.class));
    }

    private static String sql(Annotation annotation) {
        String[] statements;
        if (annotation instanceof Update) {
            statements = ((Update) annotation).value();
        } else if (annotation instanceof Delete) {
            statements = ((Delete) annotation).value();
        } else {
            throw new IllegalArgumentException("SQL annotation is required.");
        }
        return String.join(" ", statements).replaceAll("\\s+", " ");
    }
}
