package com.nuono.next.productselection;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
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
    void deleteFlowLocksSourceAndChecksBothAnalysisRelationTables() throws Exception {
        Method lockMethod = ProductSelectionMapper.class.getMethod("lockActiveSourceCollectionById", Long.class);
        String lockSql = String.join("\n", lockMethod.getAnnotation(Select.class).value()).replaceAll("\\s+", " ");
        assertThat(lockSql)
                .contains("product_selection_source_collection")
                .contains("FOR UPDATE");

        Method countMethod = ProductSelectionMapper.class.getMethod("countActiveSelectionReferences", Long.class);
        String countSql = String.join("\n", countMethod.getAnnotation(Select.class).value()).replaceAll("\\s+", " ");
        assertThat(countSql)
                .contains("product_selection_group_material")
                .contains("product_selection_analysis_item")
                .contains("is_deleted = b'0'");

        Method deleteMethod = ProductSelectionMapper.class.getMethod(
                "softDeleteSelectionGroupMaterial",
                Long.class,
                Long.class,
                Long.class
        );
        String deleteSql = String.join("\n", deleteMethod.getAnnotation(Update.class).value()).replaceAll("\\s+", " ");
        assertThat(deleteSql)
                .contains("group_id = #{groupId}")
                .contains("source_collection_id = #{sourceCollectionId}")
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
