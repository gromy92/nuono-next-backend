package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProductGroupMapper;
import java.lang.reflect.Method;
import java.util.List;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class ProductGroupMapperPskuIdentitySqlTest {

    @Test
    void activeGroupMemberHydrationUsesPartnerSkuBeforeLegacySkuParent() throws Exception {
        String sql = selectSql("selectActiveProductGroupMembers", Long.class);

        assertPskuFirstProductMasterJoin(sql);
        assertThat(sql)
                .doesNotContain("ON pm.logical_store_id = pg.logical_store_id "
                        + "AND pm.sku_parent = pgm.sku_parent AND pm.is_deleted = 0");
    }

    @Test
    void currentGroupProjectionUsesPartnerSkuBeforeLegacySkuParentForMemberJoin() throws Exception {
        String sql = selectSql("selectCurrentProductGroupProjection", Long.class, String.class, String.class);

        assertPskuFirstProductMasterJoin(sql);
        assertThat(sql)
                .doesNotContain("ON pgm.sku_parent = pm.sku_parent AND pgm.member_status = 'active'");
    }

    @Test
    void groupFieldSyncUsesPartnerSkuBeforeLegacySkuParentForMemberJoin() throws Exception {
        String sql = updateSql(
                "syncProductMasterGroupFieldsForActiveMembers",
                Long.class,
                String.class,
                String.class,
                String.class,
                Integer.class,
                Long.class
        );

        assertPskuFirstProductMasterJoin(sql);
        assertThat(sql)
                .doesNotContain("ON pgm.product_group_id = pg.id "
                        + "AND pgm.sku_parent = pm.sku_parent AND pgm.member_status = 'active'");
    }

    @Test
    void staleGroupMemberCleanupOnlyUsesSkuParentForLegacyRowsWithoutPartnerSku() throws Exception {
        String sql = updateSql("markStaleProductGroupMembersDeleted", Long.class, List.class, Long.class);

        assertThat(sql)
                .contains("partner_sku IS NULL OR partner_sku = ''")
                .contains("sku_parent NOT IN")
                .doesNotContain("partner_sku NOT IN");
    }

    @Test
    void groupMemberResolverHasPartnerSkuIdentityPath() throws Exception {
        String sql = selectSql("selectProductGroupMemberIdByPartnerSku", Long.class, String.class);

        assertThat(sql)
                .contains("product_group_id = #{productGroupId}")
                .contains("partner_sku = #{partnerSku}")
                .doesNotContain("sku_parent = #{skuParent}");
    }

    @Test
    void staleGroupMemberCleanupHasPartnerSkuIdentityPath() throws Exception {
        String sql = updateSql(
                "markStaleProductGroupMembersDeletedByPartnerSku",
                Long.class,
                List.class,
                List.class,
                Long.class
        );

        assertThat(sql)
                .contains("partner_sku IS NOT NULL")
                .contains("partner_sku != ''")
                .contains("partner_sku NOT IN")
                .contains("sku_parent NOT IN");
    }

    @Test
    void inactiveProductMasterCleanupOnlyUsesSkuParentForLegacyMastersWithoutPartnerSku() throws Exception {
        String sql = updateSql(
                "clearProductMasterGroupFieldsForInactiveMembers",
                Long.class,
                String.class,
                String.class,
                String.class,
                List.class,
                Long.class
        );

        assertThat(sql)
                .contains("pm.partner_sku IS NULL OR pm.partner_sku = ''")
                .contains("pm.sku_parent NOT IN")
                .doesNotContain("pm.partner_sku NOT IN");
    }

    @Test
    void inactiveProductMasterCleanupHasPartnerSkuIdentityPath() throws Exception {
        String sql = updateSql(
                "clearProductMasterGroupFieldsForInactivePartnerSkuMembers",
                Long.class,
                String.class,
                String.class,
                String.class,
                List.class,
                List.class,
                Long.class
        );

        assertThat(sql)
                .contains("pm.partner_sku IS NOT NULL")
                .contains("pm.partner_sku != ''")
                .contains("pm.partner_sku NOT IN")
                .contains("pm.sku_parent NOT IN");
    }

    @Test
    void scriptedProductGroupUpdatesParseAsMyBatisXml() throws Exception {
        assertScriptParses("markStaleProductGroupMembersDeleted", Long.class, List.class, Long.class);
        assertScriptParses("markStaleProductGroupMembersDeletedByPartnerSku", Long.class, List.class, List.class, Long.class);
        assertScriptParses(
                "clearProductMasterGroupFieldsForInactiveMembers",
                Long.class,
                String.class,
                String.class,
                String.class,
                List.class,
                Long.class
        );
        assertScriptParses(
                "clearProductMasterGroupFieldsForInactivePartnerSkuMembers",
                Long.class,
                String.class,
                String.class,
                String.class,
                List.class,
                List.class,
                Long.class
        );
    }

    private static void assertPskuFirstProductMasterJoin(String sql) {
        assertThat(sql)
                .contains("pgm.partner_sku IS NOT NULL")
                .contains("pgm.partner_sku <> ''")
                .contains("pm.partner_sku = pgm.partner_sku")
                .contains("pgm.partner_sku IS NULL OR pgm.partner_sku = ''")
                .contains("pm.sku_parent = pgm.sku_parent");
    }

    private static String selectSql(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = mapperMethod(methodName, parameterTypes);
        return normalize(method.getAnnotation(Select.class).value());
    }

    private static String updateSql(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = mapperMethod(methodName, parameterTypes);
        return normalize(method.getAnnotation(Update.class).value());
    }

    private static void assertScriptParses(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = mapperMethod(methodName, parameterTypes);
        String rawSql = String.join("\n", method.getAnnotation(Update.class).value());
        new XMLLanguageDriver().createSqlSource(new Configuration(), rawSql, Object.class);
    }

    private static Method mapperMethod(String methodName, Class<?>... parameterTypes) throws Exception {
        try {
            return ProductGroupMapper.class.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException exception) {
            assertThat((Method) null)
                    .as("ProductGroupMapper method %s should exist", methodName)
                    .isNotNull();
            throw exception;
        }
    }

    private static String normalize(String[] sqlLines) {
        return String.join(" ", sqlLines).replaceAll("\\s+", " ").trim();
    }
}
