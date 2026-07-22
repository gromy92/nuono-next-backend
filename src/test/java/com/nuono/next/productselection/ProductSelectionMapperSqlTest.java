package com.nuono.next.productselection;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionPluginIngestMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
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
    void pluginIngestRetryLookupIsScopedToBatchItemAndNeverUpdatesHistoricalProducts() throws Exception {
        Method selectMethod = ProductSelectionPluginIngestMapper.class.getMethod(
                "selectByBatchItem",
                Long.class,
                Long.class,
                String.class,
                String.class
        );
        String selectSql = String.join("\n", selectMethod.getAnnotation(Select.class).value()).replaceAll("\\s+", " ");
        assertThat(selectSql)
                .contains("source.owner_user_id = #{ownerUserId}")
                .contains("source.logical_store_id = #{logicalStoreId}")
                .contains("source.plugin_batch_id = #{pluginBatchId}")
                .contains("source.plugin_item_key = #{pluginItemKey}")
                .contains("source.collection_source = 'plugin'")
                .contains("FOR UPDATE")
                .contains("source.is_deleted = b'0'");
    }

    @Test
    void sourceCollectionInsertPersistsPluginBatchItemAndExtractorVersion() throws Exception {
        Method insertMethod = ProductSelectionPluginIngestMapper.class.getMethod(
                "insert",
                ProductSelectionSourceCollectionRow.class,
                String.class,
                String.class,
                String.class
        );
        String insertSql = String.join("\n", insertMethod.getAnnotation(Insert.class).value()).replaceAll("\\s+", " ");

        assertThat(insertSql)
                .contains("plugin_batch_id")
                .contains("plugin_item_key")
                .contains("extractor_version")
                .contains("#{pluginBatchId}")
                .contains("#{pluginItemKey}")
                .contains("#{extractorVersion}");
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
