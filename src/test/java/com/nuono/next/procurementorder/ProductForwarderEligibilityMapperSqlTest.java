package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class ProductForwarderEligibilityMapperSqlTest {

    @Test
    void anchorEnsureIsExplicitNoOpUpsertWithoutIgnoringDatabaseErrors() throws Exception {
        Method method = method("ensureProductForwarderEligibilityScopeAnchors", java.util.List.class);

        String statement = sql(method, Insert.class);

        assertThat(statement).contains("INSERT INTO product_forwarder_eligibility_scope_anchor");
        assertThat(statement).contains("ON DUPLICATE KEY UPDATE gmt_updated = gmt_updated");
        assertThat(statement).doesNotContain("INSERT IGNORE");
    }

    @Test
    void anchorLockUsesPrimaryKeyOrderWithoutNonSargableCasts() throws Exception {
        Method method = method("lockProductForwarderEligibilityScopeAnchors", java.util.List.class);

        String statement = sql(method, Select.class);

        assertThat(statement).contains("owner_user_id = #{scope.ownerUserId}");
        assertThat(statement).contains("logical_store_id = #{scope.logicalStoreId}");
        assertThat(statement).contains("partner_sku_normalized = #{scope.partnerSkuNormalized}");
        assertThat(statement).contains(
                "ORDER BY owner_user_id ASC, logical_store_id ASC, partner_sku_normalized ASC FOR UPDATE");
        assertThat(statement).doesNotContain("CAST(");
        assertThat(statement).doesNotContain("product_variant");
    }

    @Test
    void currentRulesUseStableScopeIndexPrefixInsteadOfVariantIdentity() throws Exception {
        Method method = method("listCurrentProductForwarderTransportEligibilities", java.util.List.class);

        String statement = sql(method, Select.class);
        String whereClause = statement.substring(statement.indexOf("WHERE"));

        assertThat(whereClause).contains("owner_user_id = #{scope.ownerUserId}");
        assertThat(whereClause).contains("logical_store_id = #{scope.logicalStoreId}");
        assertThat(whereClause).contains("partner_sku = #{scope.partnerSkuNormalized}");
        assertThat(whereClause).doesNotContain("product_variant_id");
    }

    @Test
    void decisionReadIsTheSameOrderedStableScopeQueryWithCurrentReadLock() throws Exception {
        String projection = sql(method(
                "listCurrentProductForwarderTransportEligibilities", java.util.List.class), Select.class);
        String decision = sql(method(
                "listCurrentProductForwarderTransportEligibilitiesForUpdate", java.util.List.class), Select.class);

        assertThat(projection).doesNotContain("FOR UPDATE");
        assertThat(decision).contains("FOR UPDATE");
        assertThat(decision.replace(" FOR UPDATE </script>", " </script>"))
                .isEqualTo(projection);
        assertThat(decision).contains("site_code ASC, forwarder_code ASC, transport_mode ASC");
    }

    @Test
    void activeAndLatestRuleLocksUseAllSixStableScopeSegments() throws Exception {
        String active = sql(method(
                "selectActiveProductForwarderTransportEligibilityForUpdate",
                Long.class, Long.class, String.class, String.class, String.class, String.class
        ), Select.class);
        String latest = sql(method(
                "selectLatestProductForwarderTransportEligibilityVersionForUpdate",
                Long.class, Long.class, String.class, String.class, String.class, String.class
        ), Select.class);

        for (String statement : java.util.List.of(active, latest)) {
            assertThat(statement).contains("owner_user_id = #{ownerUserId}");
            assertThat(statement).contains("logical_store_id = #{logicalStoreId}");
            assertThat(statement).contains("partner_sku = #{partnerSkuNormalized}");
            assertThat(statement).contains("site_code = #{siteCode}");
            assertThat(statement).contains("forwarder_code = #{forwarderCode}");
            assertThat(statement).contains("transport_mode = #{transportMode}");
            assertThat(statement).contains("ORDER BY version DESC, id DESC");
            assertThat(statement.substring(statement.indexOf("WHERE"))).doesNotContain("product_variant_id");
        }
    }

    @Test
    void eligibilitySnapshotCannotRewriteSubmittedShippingLine() throws Exception {
        Method method = method(
                "snapshotShippingOrderLineEligibility",
                Long.class, Long.class, ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord.class,
                Long.class
        );

        String statement = sql(method, org.apache.ibatis.annotations.Update.class);

        assertThat(statement).contains("shipping_submit_status = 'NOT_SUBMITTED'");
    }

    private static Method method(String name, Class<?>... parameterTypes) throws Exception {
        return ProcurementPurchaseOrderMapper.class.getMethod(name, parameterTypes);
    }

    private static <T extends Annotation> String sql(Method method, Class<T> annotationType) {
        Annotation annotation = method.getAnnotation(annotationType);
        String[] parts = annotation instanceof Select ? ((Select) annotation).value()
                : annotation instanceof Insert ? ((Insert) annotation).value()
                : ((org.apache.ibatis.annotations.Update) annotation).value();
        return String.join(" ", parts).replaceAll("\\s+", " ");
    }
}
