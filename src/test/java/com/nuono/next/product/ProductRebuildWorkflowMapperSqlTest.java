package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.infrastructure.mapper.ProductRebuildWorkflowMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ProductRebuildWorkflowMapperSqlTest {

    @Test
    void inheritedRebuildQueriesResolveTheSharedPublishTaskResultMap() {
        Configuration parentFirst = new Configuration();
        assertDoesNotThrow(() -> parentFirst.addMapper(ProductManagementMapper.class));
        assertDoesNotThrow(() -> parentFirst.addMapper(ProductRebuildWorkflowMapper.class));
        assertRebuildStatementResolves(parentFirst);

        Configuration childFirst = new Configuration();
        assertDoesNotThrow(() -> childFirst.addMapper(ProductRebuildWorkflowMapper.class));
        assertDoesNotThrow(() -> childFirst.addMapper(ProductManagementMapper.class));
        assertRebuildStatementResolves(childFirst);
    }

    private void assertRebuildStatementResolves(Configuration configuration) {
        assertDoesNotThrow(() -> configuration.getMappedStatement(
                ProductRebuildWorkflowMapper.class.getName()
                        + ".selectProductRebuildDeleteTasksReadyForListing"
        ));
    }

    @Test
    void readyLookupAndClaimAllowOnlyExpiredRunningLeaseToBeTakenOver() {
        String readySql = compact(method("selectProductRebuildDeleteTasksReadyForListing")
                .getAnnotation(Select.class).value());
        String claimSql = compact(method("claimProductRebuildDeleteTaskForListing")
                .getAnnotation(Update.class).value());

        for (String sql : new String[] {readySql, claimSql}) {
            assertTrue(sql.contains("$.rebuild.status"));
            assertTrue(sql.contains("= 'listing_running'"));
            assertTrue(sql.contains("gmt_updated < #{staleBefore}"));
            assertTrue(sql.contains("task_type = 'product-delete'"));
            assertTrue(sql.contains("status = 'synced'"));
        }
    }

    @Test
    void authPendingIsReconciledButNeverSelectedForAnotherCreateClaim() {
        String readySql = compact(method("selectProductRebuildDeleteTasksReadyForListing")
                .getAnnotation(Select.class).value());
        String reconcileSql = compact(method("selectProductRebuildDeleteTasksPendingListingReconciliation")
                .getAnnotation(Select.class).value());

        assertTrue(!readySql.contains("noon_auth_required"));
        assertTrue(reconcileSql.contains("noon_auth_required"));
        assertTrue(ProductRebuildWorkflowMapper.class.isAssignableFrom(ProductManagementMapper.class));
    }

    @Test
    void claimRenewalIsFencedByTheCurrentClaimToken() {
        for (String methodName : new String[]{
                "renewProductRebuildListingClaim",
                "completeProductRebuildListingClaim"
        }) {
            String sql = compact(method(methodName).getAnnotation(Update.class).value());

            assertTrue(sql.contains("$.rebuild.status"));
            assertTrue(sql.contains("= 'listing_running'"));
            assertTrue(sql.contains("$.rebuild.claimToken"));
            assertTrue(sql.contains("= #{claimToken}"));
            assertTrue(sql.contains("gmt_updated = NOW()"));
        }
    }

    private Method method(String name) {
        return Arrays.stream(ProductRebuildWorkflowMapper.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private String compact(String[] lines) {
        return String.join(" ", lines).replaceAll("\\s+", " ");
    }
}
