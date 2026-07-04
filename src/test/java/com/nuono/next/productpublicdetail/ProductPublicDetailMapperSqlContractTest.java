package com.nuono.next.productpublicdetail;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.ProductPublicDetailMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class ProductPublicDetailMapperSqlContractTest {
    private static final String SYNCABLE_SITE_STATUS_CONDITION =
            "UPPER(COALESCE(lss.site_status, 'ACTIVE')) IN ('ACTIVE', 'LOCAL_READY')";

    @Test
    void productPublicDetailScopeQueriesTreatLocalReadySitesAsSyncable() throws NoSuchMethodException {
        assertSyncableSiteStatusCondition("selectActiveScope", Long.class, String.class, String.class);
        assertSyncableSiteStatusCondition("listDueScopes", int.class, int.class, int.class);
        assertSyncableSiteStatusCondition(
                "listCandidates",
                Long.class,
                String.class,
                String.class,
                int.class,
                int.class,
                int.class,
                boolean.class
        );
        assertSyncableSiteStatusCondition("countCandidates", Long.class, String.class, String.class);
    }

    @Test
    void latestUsableSnapshotLookupAcceptsSystemPsku() throws NoSuchMethodException {
        Method method = ProductPublicDetailMapper.class.getMethod(
                "selectLatestUsableSnapshotBySkuParent",
                Long.class,
                String.class,
                String.class
        );
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");

        assertTrue(
                sql.contains("partner_sku = #{skuParent}"),
                "Product image profiles pass system PSKU into the public-detail fallback lookup."
        );
        assertTrue(
                sql.contains("sku_parent = #{skuParent} OR noon_product_code = #{skuParent}"),
                "Legacy Z-code and Noon-code fallback should remain available for existing callers."
        );
    }

    @Test
    void publicDetailCandidatesUseSinglePreferredSitePerProductWithSaFirst() throws NoSuchMethodException {
        Method method = ProductPublicDetailMapper.class.getMethod(
                "listCandidates",
                Long.class,
                String.class,
                String.class,
                int.class,
                int.class,
                int.class,
                boolean.class
        );
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");

        assertTrue(
                sql.contains("preferred_lss"),
                "Candidate selection must compare against other live sites for the same product."
        );
        assertTrue(
                sql.contains("UPPER(preferred_lss.site) = 'SA'"),
                "SA must be the preferred public-detail site when the product is live there."
        );
        assertTrue(
                sql.contains("preferred_lss.id &lt; lss.id") || sql.contains("preferred_lss.id < lss.id"),
                "When SA is not available, one deterministic fallback site should be selected."
        );
    }

    @Test
    void publicDetailCandidateCountUsesScriptSqlForEscapedPreferredSiteComparison() throws NoSuchMethodException {
        Method method = ProductPublicDetailMapper.class.getMethod(
                "countCandidates",
                Long.class,
                String.class,
                String.class
        );
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ").trim();

        assertTrue(
                sql.startsWith("<script>"),
                "countCandidates reuses preferred-site comparison with XML-escaped '<', so it must be a MyBatis script."
        );
        assertTrue(
                sql.contains("preferred_lss.id &lt; lss.id"),
                "Preferred-site fallback comparison should stay XML-escaped inside the MyBatis script."
        );
    }

    private static void assertSyncableSiteStatusCondition(String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = ProductPublicDetailMapper.class.getMethod(methodName, parameterTypes);
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value());
        assertTrue(
                sql.contains(SYNCABLE_SITE_STATUS_CONDITION),
                methodName + " should allow LOCAL_READY product sites to sync public detail snapshots."
        );
    }
}
