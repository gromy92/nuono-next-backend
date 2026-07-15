package com.nuono.next.productpublicdetail;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertSyncableSiteStatusCondition("selectPreferredScope", Long.class, Long.class, int.class);
        assertSyncableSiteStatusCondition("listDueScopes", int.class, int.class, int.class);
        assertSyncableSiteStatusCondition(
                "listCandidates",
                Long.class,
                String.class,
                String.class,
                int.class,
                int.class,
                int.class,
                boolean.class,
                boolean.class
        );
        assertSyncableSiteStatusCondition("countCandidates", Long.class, String.class, String.class, int.class, boolean.class);
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
                sql.contains("sku_parent = #{skuParent} OR pp.noon_product_code = #{skuParent}")
                        || sql.contains("sku_parent = #{skuParent} OR noon_product_code = #{skuParent}"),
                "Legacy Z-code and Noon-code fallback should remain available for existing callers."
        );
        assertTrue(
                sql.contains("requested_lss.logical_store_id = pp.logical_store_id"),
                "Public-detail fallback should be reusable across site stores in the same logical store."
        );
        assertTrue(
                sql.contains("UPPER(requested_lss.store_code) = UPPER(#{storeCode})"),
                "The requested store code should resolve the logical store scope for sibling-site fallback."
        );
        assertTrue(
                sql.indexOf("pp.fetched_at DESC") < sql.indexOf("CASE WHEN UPPER(pp.store_code)"),
                "Sibling-site fallback should prefer the freshest usable snapshot over an older same-store snapshot."
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
                boolean.class,
                boolean.class
        );
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");

        assertTrue(
                sql.contains("preferred_lss"),
                "Candidate selection must compare against other live sites for the same product."
        );
        assertTrue(
                sql.contains("preferred_lss.logical_store_id = lss.logical_store_id"),
                "Preferred-site selection must be product/logical-store scoped, not site-store scoped."
        );
        assertFalse(
                sql.contains("UPPER(preferred_lss.store_code) = UPPER(lss.store_code)"),
                "Different site store codes in the same logical store should not cause duplicate public-detail pulls."
        );
        assertTrue(
                sql.contains("UPPER(preferred_lss.site) = 'SA'"),
                "SA must be the preferred public-detail site when the product is live there."
        );
        assertTrue(
                sql.contains("preferred_lss.id &lt; lss.id") || sql.contains("preferred_lss.id < lss.id"),
                "When SA is not available, one deterministic fallback site should be selected."
        );
        assertTrue(
                sql.contains("<if test='enforcePreferredSite'>"),
                "Manual requests that cannot access the globally preferred sibling site must be able to keep the requested site."
        );
    }

    @Test
    void publicDetailCandidateCountUsesScriptSqlForEscapedPreferredSiteComparison() throws NoSuchMethodException {
        Method method = ProductPublicDetailMapper.class.getMethod(
                "countCandidates",
                Long.class,
                String.class,
                String.class,
                int.class,
                boolean.class
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
        assertTrue(
                sql.contains("<if test='enforcePreferredSite'>"),
                "Candidate counts should be able to bypass preferred-site exclusion for inaccessible sibling stores."
        );
    }

    @Test
    void preferredSiteConditionIgnoresSitesCoolingAfterRecentFailure() throws NoSuchMethodException {
        Method method = ProductPublicDetailMapper.class.getMethod(
                "listCandidates",
                Long.class,
                String.class,
                String.class,
                int.class,
                int.class,
                int.class,
                boolean.class,
                boolean.class
        );
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("preferred_fail"));
        assertTrue(sql.contains("preferred_fail.sync_status IN ('FAILED', 'NOT_FOUND')"));
        assertTrue(sql.contains("preferred_fail.fetched_at &gt;= DATE_SUB(NOW(), INTERVAL #{failureCooldownHours} HOUR)"));
    }

    @Test
    void dueCandidateFreshnessUsesLogicalStoreSiblingSnapshots() throws NoSuchMethodException {
        Method method = ProductPublicDetailMapper.class.getMethod(
                "listCandidates",
                Long.class,
                String.class,
                String.class,
                int.class,
                int.class,
                int.class,
                boolean.class,
                boolean.class
        );
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("FROM product_public_detail_snapshot fresh"));
        assertTrue(sql.contains("fresh.logical_store_id = ls.id"));
        assertTrue(sql.contains("fresh.sync_status IN ('SUCCEEDED', 'PARTIAL')"));
        assertTrue(sql.contains("fresh.fetched_at &gt;= DATE_SUB(NOW(), INTERVAL #{staleDays} DAY)"));
    }

    @Test
    void preferredScopeUsesCandidateAwareLogicalStoreAndSaFirst() throws NoSuchMethodException {
        Method method = ProductPublicDetailMapper.class.getMethod("selectPreferredScope", Long.class, Long.class, int.class);
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");

        assertTrue(sql.trim().startsWith("<script>"));
        assertTrue(sql.contains("WHERE ls.owner_user_id = #{ownerUserId}"));
        assertTrue(sql.contains("lss.logical_store_id = #{logicalStoreId}"));
        assertTrue(sql.contains("JOIN product_master pm ON pm.logical_store_id = ls.id"));
        assertTrue(sql.contains("JOIN product_variant pv ON pv.product_master_id = pm.id"));
        assertTrue(sql.contains("JOIN product_site_offer pso ON pso.variant_id = pv.id AND pso.site_id = lss.id"));
        assertTrue(sql.contains("NULLIF(TRIM(pm.sku_parent), '') IS NOT NULL"));
        assertTrue(sql.contains("preferred_lss.logical_store_id = lss.logical_store_id"));
        assertTrue(sql.contains("fail.sync_status IN ('FAILED', 'NOT_FOUND')"));
        assertTrue(sql.contains("fail.fetched_at &gt;= DATE_SUB(NOW(), INTERVAL #{failureCooldownHours} HOUR)"));
        assertTrue(sql.contains("UPPER(lss.site) = 'SA'"));
        assertTrue(sql.contains("COALESCE(lss.is_mounted, b'1') = b'1'"));
        assertTrue(sql.contains("UPPER(COALESCE(lss.site_status, 'ACTIVE')) IN ('ACTIVE', 'LOCAL_READY')"));
    }

    @Test
    void publicDetailCandidatesRespectStoreSiteAndProductMaintenanceBoundary() throws NoSuchMethodException {
        for (String methodName : new String[] {"selectPreferredScope", "listDueScopes", "listCandidates", "countCandidates"}) {
            Method method = publicDetailScopeMethod(methodName);
            Select select = method.getAnnotation(Select.class);
            String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");

            assertTrue(sql.contains("COALESCE(lss.site_enabled, b'1') = b'1'"), methodName);
            assertTrue(sql.contains("COALESCE(pso.maintenance_enabled, b'1') = b'1'"), methodName);
        }

        Method activeScope = ProductPublicDetailMapper.class.getMethod("selectActiveScope", Long.class, String.class, String.class);
        String activeScopeSql = String.join(" ", activeScope.getAnnotation(Select.class).value()).replaceAll("\\s+", " ");
        assertTrue(activeScopeSql.contains("COALESCE(lss.site_enabled, b'1') = b'1'"));
    }

    @Test
    void preferredSiteConditionAlsoRespectsMaintenanceBoundary() throws NoSuchMethodException {
        Method method = ProductPublicDetailMapper.class.getMethod(
                "listCandidates",
                Long.class,
                String.class,
                String.class,
                int.class,
                int.class,
                int.class,
                boolean.class,
                boolean.class
        );
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("COALESCE(preferred_lss.site_enabled, b'1') = b'1'"));
        assertTrue(sql.contains("COALESCE(preferred_pso.maintenance_enabled, b'1') = b'1'"));
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

    private static Method publicDetailScopeMethod(String methodName) throws NoSuchMethodException {
        if ("selectPreferredScope".equals(methodName)) {
            return ProductPublicDetailMapper.class.getMethod(methodName, Long.class, Long.class, int.class);
        }
        if ("listDueScopes".equals(methodName)) {
            return ProductPublicDetailMapper.class.getMethod(methodName, int.class, int.class, int.class);
        }
        if ("listCandidates".equals(methodName)) {
            return ProductPublicDetailMapper.class.getMethod(
                    methodName,
                    Long.class,
                    String.class,
                    String.class,
                    int.class,
                    int.class,
                    int.class,
                    boolean.class,
                    boolean.class
            );
        }
        if ("countCandidates".equals(methodName)) {
            return ProductPublicDetailMapper.class.getMethod(
                    methodName,
                    Long.class,
                    String.class,
                    String.class,
                    int.class,
                    boolean.class
            );
        }
        throw new IllegalArgumentException(methodName);
    }
}
