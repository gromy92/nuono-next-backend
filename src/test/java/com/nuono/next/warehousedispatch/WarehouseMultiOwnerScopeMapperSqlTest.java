package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import com.nuono.next.infrastructure.mapper.WarehouseOrderJourneyMapper;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class WarehouseMultiOwnerScopeMapperSqlTest {

    @Test
    void inventoryQueriesUseExactStoreOwnerPairsAndFailClosed() throws Exception {
        assertExactBalanceScope(select(
                WarehouseDispatchMapper.class,
                "listReadyBalances",
                Map.class,
                String.class,
                String.class
        ));
        assertExactBalanceScope(select(
                WarehouseDispatchMapper.class,
                "listPurchasePlanBalances",
                Map.class
        ));

        String receiptSql = select(
                WarehouseDispatchMapper.class,
                "listReceiptRows",
                Map.class,
                String.class
        );
        assertThat(receiptSql).contains("collection='storeOwnerUserIds'");
        assertThat(receiptSql).contains("so.owner_user_id = #{ownerUserId}");
        assertThat(receiptSql).contains("sol.source_store_code = #{storeCode}");
        assertThat(receiptSql).contains("sol.owner_user_id = so.owner_user_id");
        assertThat(receiptSql).contains("po.owner_user_id = so.owner_user_id");
        assertThat(receiptSql).contains("item.owner_user_id = so.owner_user_id");
        assertThat(receiptSql).contains("balance.owner_user_id = so.owner_user_id");
        assertThat(receiptSql).contains("AND 1 = 0");
    }

    @Test
    void selectedInventoryLockUsesTheSameExactScope() throws Exception {
        String probeSql = select(
                WarehouseDispatchMapper.class,
                "selectBalanceScopes",
                List.class,
                Map.class
        );
        assertExactBalanceScope(probeSql);
        assertThat(probeSql).doesNotContain("FOR UPDATE");

        String sql = select(
                WarehouseDispatchMapper.class,
                "selectAuthorizedBalancesForUpdate",
                List.class,
                Map.class
        );

        assertExactBalanceScope(sql);
        assertThat(sql).contains("balance.id IN");
        assertThat(sql).contains("ORDER BY balance.id ASC");
        assertThat(sql).contains("FOR UPDATE");

        Method reserve = WarehouseDispatchMapper.class.getMethod(
                "reserveBalance",
                Long.class,
                Long.class,
                Integer.class,
                Long.class
        );
        String reserveSql = String.join(" ", reserve.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");
        assertThat(reserveSql).contains("owner_user_id = #{ownerUserId}");
        assertThat(reserveSql).contains("available_quantity >= #{quantity}");
    }

    @Test
    void historicalAggregateListsRequireEverySourceToMatchAnExactAuthorizedPair() throws Exception {
        assertCompleteSourceScope(select(
                WarehouseDispatchMapper.class,
                "listDispatchPlans",
                Map.class
        ), "procurement_dispatch_plan_line_source", "plan");
        assertCompleteSourceScope(select(
                WarehouseDispatchMapper.class,
                "listShippingBatches",
                Map.class
        ), "warehouse_shipping_batch_source", "batch");
        assertCompleteSourceScope(select(
                WarehouseDispatchMapper.class,
                "listOutboundOrdersByBatch",
                Long.class,
                Map.class
        ), "warehouse_outbound_order_line_source", "outbound");
        assertCompleteSourceScope(select(
                WarehouseDispatchMapper.class,
                "listPackingListsByOutboundOrder",
                Long.class,
                Map.class
        ), "warehouse_outbound_order_line_source", "packing");
    }

    @Test
    void aggregateAccessProbesRequireNonEmptyAndFullyAuthorizedSources() throws Exception {
        assertCompleteSourceScope(select(
                WarehouseDispatchMapper.class,
                "isDispatchPlanSourceScopeAuthorized",
                Long.class,
                Map.class
        ), "procurement_dispatch_plan_line_source", "plan");
        assertCompleteSourceScope(select(
                WarehouseDispatchMapper.class,
                "isShippingBatchSourceScopeAuthorized",
                Long.class,
                Map.class
        ), "warehouse_shipping_batch_source", "batch");
        assertCompleteSourceScope(select(
                WarehouseDispatchMapper.class,
                "isOutboundOrderSourceScopeAuthorized",
                Long.class,
                Map.class
        ), "warehouse_outbound_order_line_source", "outbound");
        assertCompleteSourceScope(select(
                WarehouseDispatchMapper.class,
                "isPackingListSourceScopeAuthorized",
                Long.class,
                Map.class
        ), "warehouse_outbound_order_line_source", "packing");
    }

    @Test
    void aggregateScopeScriptsExpandExactPairsAndFailClosedForAnEmptyScope() throws Exception {
        Map<String, Object> authorizedParameters = Map.of(
                "dispatchPlanId", 340001L,
                "batchId", 700001L,
                "outboundOrderId", 800001L,
                "packingListId", 830001L,
                "storeOwnerUserIds", Map.of("STORE-A", 307L)
        );
        for (String methodName : List.of(
                "isDispatchPlanSourceScopeAuthorized",
                "isShippingBatchSourceScopeAuthorized",
                "isOutboundOrderSourceScopeAuthorized",
                "isPackingListSourceScopeAuthorized"
        )) {
            assertThat(boundSql(
                    WarehouseDispatchMapper.class,
                    methodName,
                    authorizedParameters,
                    Long.class,
                    Map.class
            )).doesNotContain("<foreach", "<choose");
        }
        String authorized = boundSql(
                WarehouseDispatchMapper.class,
                "isDispatchPlanSourceScopeAuthorized",
                authorizedParameters,
                Long.class,
                Map.class
        );
        assertThat(authorized)
                .contains("plan.owner_user_id IN ( ? )")
                .contains("source.owner_user_id = ?")
                .contains("source.source_store_code = ?")
                .doesNotContain("<foreach", "<choose");

        String empty = boundSql(
                WarehouseDispatchMapper.class,
                "isDispatchPlanSourceScopeAuthorized",
                Map.of(
                        "dispatchPlanId", 340001L,
                        "storeOwnerUserIds", Map.of()
                ),
                Long.class,
                Map.class
        );
        assertThat(empty).contains("AND 1 = 0");
    }

    @Test
    void outboundAndPackingScopesPreferSnapshotsButFailClosedWhenNoStoreCanBeResolved() throws Exception {
        String outboundSql = select(
                WarehouseDispatchMapper.class,
                "isOutboundOrderSourceScopeAuthorized",
                Long.class,
                Map.class
        );
        String packingSql = select(
                WarehouseDispatchMapper.class,
                "isPackingListSourceScopeAuthorized",
                Long.class,
                Map.class
        );

        assertThat(outboundSql)
                .contains("NULLIF(TRIM(source.source_store_code), '')")
                .contains("NULLIF(TRIM(batch_source.source_store_code), '')")
                .contains("NULLIF(TRIM(balance.source_store_code), '')")
                .contains(") = #{storeCode}")
                .contains("batch_source.owner_user_id != outbound.owner_user_id");
        assertThat(packingSql)
                .contains("NULLIF(TRIM(source.source_store_code), '')")
                .contains("batch_source.owner_user_id != packing.owner_user_id");
        assertThat(outboundSql).contains(") IS NULL");
    }

    @Test
    void outboundSourcesPersistTheStoreScopeSnapshot() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "insertOutboundOrderLineSource",
                WarehouseDispatchRecords.OutboundOrderLineSourceRecord.class,
                Long.class
        );
        String sql = String.join(" ", method.getAnnotation(Insert.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("source_store_code", "source_store_name");
        assertThat(sql).contains("#{row.sourceStoreCode}", "#{row.sourceStoreName}");
    }

    @Test
    void orderJourneyUsesExactStoreOwnerPairsAcrossEveryJoin() throws Exception {
        String sql = select(
                WarehouseOrderJourneyMapper.class,
                "listWarehouseOrderJourneys",
                Map.class
        );

        assertThat(sql).contains("collection='storeOwnerUserIds'");
        assertThat(sql).contains("shipping_order.owner_user_id = #{ownerUserId}");
        assertThat(sql).contains("shipping_line.source_store_code = #{storeCode}");
        assertThat(sql).contains("batch_source.owner_user_id = shipping_order.owner_user_id");
        assertThat(sql).contains("batch.owner_user_id = shipping_order.owner_user_id");
        assertThat(sql).contains("AND 1 = 0");
        assertCompleteSourceScope(sql, "warehouse_shipping_batch_source", "batch");
    }

    private void assertExactBalanceScope(String sql) {
        assertThat(sql).contains("collection='storeOwnerUserIds'");
        assertThat(sql).contains("balance.owner_user_id = #{ownerUserId}");
        assertThat(sql).contains("balance.source_store_code = #{storeCode}");
        assertThat(sql).contains("AND 1 = 0");
    }

    private void assertCompleteSourceScope(String sql, String sourceTable, String aggregateAlias) {
        assertThat(sql).contains(sourceTable);
        assertThat(sql).contains("EXISTS");
        assertThat(sql).contains("NOT EXISTS");
        assertThat(sql).contains("collection='storeOwnerUserIds'");
        assertThat(sql).containsPattern("\\w+\\.owner_user_id = #\\{ownerUserId}");
        assertThat(sql).contains("source_store_code").contains("= #{storeCode}");
        assertThat(sql).containsPattern(
                "\\w+\\.owner_user_id = " + aggregateAlias + "\\.owner_user_id"
        );
        assertThat(sql).contains("AND 1 = 0");
    }

    private String select(Class<?> mapper, String name, Class<?>... parameterTypes) throws Exception {
        Method method = mapper.getMethod(name, parameterTypes);
        return String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
    }

    private String boundSql(
            Class<?> mapper,
            String name,
            Map<String, Object> parameters,
            Class<?>... parameterTypes
    ) throws Exception {
        Method method = mapper.getMethod(name, parameterTypes);
        String script = String.join(" ", method.getAnnotation(Select.class).value());
        Configuration configuration = new Configuration();
        SqlSource sqlSource = new XMLLanguageDriver().createSqlSource(
                configuration,
                script,
                Map.class
        );
        return sqlSource.getBoundSql(parameters).getSql().replaceAll("\\s+", " ").trim();
    }
}
