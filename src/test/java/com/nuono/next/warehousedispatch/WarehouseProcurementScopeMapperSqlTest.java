package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.BalanceQuantityDelta;
import java.lang.reflect.Method;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class WarehouseProcurementScopeMapperSqlTest {

    @Test
    void purchaseOrderReadRequiresAnExactAuthorizedStoreOwnerPair() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "selectOrderAccess",
                Long.class,
                Map.class
        );

        String sql = normalizedSelectSql(method);

        assertThat(sql).contains("collection='storeOwnerUserIds'");
        assertThat(sql).contains("owner_user_id = #{ownerUserId}");
        assertThat(sql).contains("anchor_store_code_cache = #{storeCode}");
        assertThat(sql).contains("AND 1 = 0");
    }

    @Test
    void purchaseOrderChildReadsCarryOrderAndOwnerScope() throws Exception {
        String itemSql = normalizedSelectSql(WarehouseDispatchMapper.class.getMethod(
                "selectPurchaseOrderItem",
                Long.class,
                Long.class,
                Long.class
        ));
        String siteSql = normalizedSelectSql(WarehouseDispatchMapper.class.getMethod(
                "listItemSitesForBalance",
                Long.class,
                Long.class,
                Long.class
        ));
        String balanceSql = normalizedSelectSql(WarehouseDispatchMapper.class.getMethod(
                "listBalancesForItemForUpdate",
                Long.class,
                Long.class,
                Long.class
        ));

        assertOrderAndOwnerScope(itemSql);
        assertThat(itemSql).contains("FOR UPDATE");
        assertOrderAndOwnerScope(siteSql);
        assertThat(balanceSql).contains("balance.purchase_order_id = #{purchaseOrderId}");
        assertThat(balanceSql).contains("balance.owner_user_id = #{ownerUserId}");
    }

    @Test
    void fulfillmentWritesCarryOrderAndOwnerScope() throws Exception {
        String itemSql = normalizedUpdateSql(WarehouseDispatchMapper.class.getMethod(
                "updatePurchaseOrderItemFulfillment",
                Long.class,
                Long.class,
                Long.class,
                String.class,
                String.class,
                Long.class
        ));
        String activeBalanceSql = normalizedUpdateSql(WarehouseDispatchMapper.class.getMethod(
                "updateActiveBalancesFulfillment",
                Long.class,
                Long.class,
                Long.class,
                String.class,
                Long.class
        ));
        String quantitySql = normalizedUpdateSql(WarehouseDispatchMapper.class.getMethod(
                "updateBalanceQuantities",
                BalanceQuantityDelta.class
        ));
        String upsertSql = normalizedInsertSql(WarehouseDispatchMapper.class.getMethod(
                "upsertBalanceFromItemSite",
                Long.class,
                Long.class,
                Long.class,
                Long.class,
                String.class,
                Long.class
        ));

        assertOrderAndOwnerScope(itemSql);
        assertOrderAndOwnerScope(activeBalanceSql);
        assertThat(activeBalanceSql).contains("NOT (fulfillment_type <=> #{fulfillmentType})");
        assertThat(upsertSql).contains("site.purchase_order_id = #{purchaseOrderId}");
        assertThat(upsertSql).contains("site.owner_user_id = #{ownerUserId}");
        assertThat(quantitySql).contains("purchase_order_item_id = #{row.purchaseOrderItemId}");
        assertThat(quantitySql).contains("purchase_order_id = #{row.purchaseOrderId}");
        assertThat(quantitySql).contains("owner_user_id = #{row.ownerUserId}");
    }

    private void assertOrderAndOwnerScope(String sql) {
        assertThat(sql).contains("purchase_order_id = #{purchaseOrderId}");
        assertThat(sql).contains("owner_user_id = #{ownerUserId}");
    }

    private String normalizedSelectSql(Method method) {
        return String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
    }

    private String normalizedUpdateSql(Method method) {
        return String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");
    }

    private String normalizedInsertSql(Method method) {
        return String.join(" ", method.getAnnotation(Insert.class).value())
                .replaceAll("\\s+", " ");
    }
}
