package com.nuono.next.productselection;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionPluginIngestMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class ProductSelectionMapperSqlTest {

    @Test
    void sourceCollectionListsDoNotLeakNullSiteRowsAcrossSites() throws Exception {
        assertStrictSiteFilter(
                "listSourceCollections",
                Long.class,
                String.class,
                Integer.class
        );
        assertStrictSiteFilter(
                "listAnalysisItems",
                Long.class,
                String.class,
                Integer.class
        );
    }

    @Test
    void groupMaterialsOnlyUseActiveSourceCollections() throws Exception {
        Method method = ProductSelectionMapper.class.getMethod("listGroupMaterialsByGroupIds", java.util.List.class);
        Select select = method.getAnnotation(Select.class);
        String sql = String.join("\n", select.value()).replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("JOIN product_selection_source_collection source")
                .contains("source.is_deleted = b'0'");
    }

    @Test
    void selectionGroupListHidesGroupsWithoutActiveMaterials() throws Exception {
        Method method = ProductSelectionMapper.class.getMethod(
                "listSelectionGroups",
                Long.class,
                String.class,
                Integer.class
        );
        Select select = method.getAnnotation(Select.class);
        String sql = String.join("\n", select.value()).replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("COUNT(source.id) AS material_count")
                .contains("source.is_deleted = b'0'")
                .contains("HAVING COUNT(source.id) > 0");
    }

    @Test
    void pluginIngestDedupeIsScopedAndRefreshesCollectedFields() throws Exception {
        Method selectMethod = ProductSelectionPluginIngestMapper.class.getMethod(
                "listRecentForDedupe",
                Long.class,
                Long.class,
                String.class,
                String.class,
                Integer.class
        );
        String selectSql = String.join("\n", selectMethod.getAnnotation(Select.class).value()).replaceAll("\\s+", " ");
        assertThat(selectSql)
                .contains("source.owner_user_id = #{ownerUserId}")
                .contains("source.logical_store_id = #{logicalStoreId}")
                .contains("source.site_code = #{siteCode}")
                .contains("source.collection_source = 'plugin'")
                .contains("source.is_deleted = b'0'");

        Method updateMethod = ProductSelectionPluginIngestMapper.class.getMethod(
                "update",
                ProductSelectionSourceCollectionRow.class
        );
        String updateSql = String.join("\n", updateMethod.getAnnotation(Update.class).value()).replaceAll("\\s+", " ");
        assertThat(updateSql)
                .contains("category_links_json = #{row.categoryLinksJson}")
                .contains("source_description_en = #{row.sourceDescriptionEn}")
                .contains("source_selling_points_ar_json = #{row.sourceSellingPointsArJson}")
                .contains("collection_source = 'plugin'")
                .contains("is_deleted = b'0'");
    }
    private static void assertStrictSiteFilter(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = ProductSelectionMapper.class.getMethod(methodName, parameterTypes);
        Select select = method.getAnnotation(Select.class);
        String sql = String.join("\n", select.value()).replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("#{siteCode} IS NULL OR #{siteCode} = ''")
                .doesNotContain("source.site_code IS NULL OR source.site_code = ''")
                .doesNotContain("COALESCE(item.site_code, source.site_code) IS NULL OR COALESCE(item.site_code, source.site_code) = ''");
    }
}
