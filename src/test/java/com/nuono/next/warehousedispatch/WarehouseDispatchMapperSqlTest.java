package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class WarehouseDispatchMapperSqlTest {

    @Test
    void splitMapperInterfacesRegisterInheritedStatementsForTheFacadeProxy() throws Exception {
        Configuration configuration = new Configuration();
        configuration.addMapper(WarehouseDispatchMapper.class);

        for (String methodName : List.of(
                "selectOrderAccess",
                "listReceiptRows",
                "insertShippingBatch",
                "listShippingBatches",
                "insertOutboundOrder",
                "insertPackingList",
                "listDispatchPlans"
        )) {
            Method method = findMethod(methodName);
            assertThat(new MapperMethod(WarehouseDispatchMapper.class, method, configuration))
                    .as(methodName)
                    .isNotNull();
        }
    }

    @Test
    void balanceSelectCarriesPurchaseOrderLogisticsQuoteGateState() {
        String sql = WarehouseDispatchMapper.BALANCE_SELECT.replaceAll("\\s+", " ");

        assertThat(sql).contains("LEFT JOIN procurement_purchase_order_logistics_quote_line quote");
        assertThat(sql).contains("quote.purchase_order_item_site_id = balance.purchase_order_item_site_id");
        assertThat(sql).contains("COALESCE(quote.quote_status, 'PENDING_QUOTE') AS logisticsQuoteStatus");
        assertThat(sql).contains("COALESCE(quote.shipping_submit_status, 'NOT_SUBMITTED') AS logisticsShippingSubmitStatus");
        assertThat(sql).doesNotContain("<>");
    }

    @Test
    void inventoryDispatchTargetUsesPersistedTargetColumns() throws Exception {
        String balanceSql = WarehouseDispatchMapper.BALANCE_SELECT.replaceAll("\\s+", " ");
        assertThat(balanceSql).contains("balance.target_site_code AS targetSiteCode");
        assertThat(balanceSql).contains("balance.target_transport_mode AS targetTransportMode");

        Method listMethod = WarehouseDispatchMapper.class.getMethod(
                "listReadyBalances",
                Long.class,
                Collection.class,
                String.class,
                String.class
        );
        String listSql = String.join(" ", listMethod.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
        assertThat(listSql).contains("COALESCE(NULLIF(balance.target_site_code, ''), balance.site_code)");

        Method updateMethod = WarehouseDispatchMapper.class.getMethod(
                "updateBalanceDispatchTarget",
                Long.class,
                Long.class,
                String.class,
                String.class,
                Long.class
        );
        String updateSql = String.join(" ", updateMethod.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");
        assertThat(updateSql).contains("target_site_code = #{targetSiteCode}");
        assertThat(updateSql).contains("target_transport_mode = #{targetTransportMode}");
        assertThat(updateSql).contains("available_quantity > 0");
    }

    @Test
    void preApplicationInventoryReadsAli1688SpecsOnly() {
        String sql = WarehouseDispatchMapper.BALANCE_SELECT.replaceAll("\\s+", " ");

        assertThat(sql).contains("LEFT JOIN product_variant_spec_source ali1688Spec");
        assertThat(sql).contains("ali1688Spec.source_type = 'ali1688'");
        assertThat(sql).contains("ali1688Spec.product_length_cm AS productLengthCm");
        assertThat(sql).contains("ali1688Spec.product_weight_g AS productWeightG");
        assertThat(sql).doesNotContain("pvs.effective_source_id");
        assertThat(sql).doesNotContain("source_type = 'warehouse'");
    }

    @Test
    void appReceiptSpecStatusReadsWarehouseSourceOnly() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "listReceiptRows",
                Long.class,
                Collection.class,
                String.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("warehouseSpec.source_type = 'warehouse'");
        assertThat(sql).contains("CASE WHEN warehouseSpec.product_length_cm IS NULL");
        assertThat(sql).doesNotContain("pvs.effective_source_id");
        assertThat(sql).doesNotContain("COALESCE(pvss.product_length_cm");
    }

    @Test
    void persistedShippingApplicationsQueryCurrentWarehouseSpecs() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "listShippingBatchSources",
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("warehouseSpec.source_type = 'warehouse'");
        assertThat(sql).contains("warehouseSpec.product_length_cm AS productLengthCm");
        assertThat(sql).doesNotContain("source.product_length_cm AS productLengthCm");
        assertThat(sql).doesNotContain("pvs.effective_source_id");
    }

    @Test
    void newShippingApplicationsDoNotPersistProductSpecSnapshots() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "insertShippingBatchSource",
                WarehouseDispatchRecords.ShippingBatchSourceRecord.class,
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Insert.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).doesNotContain("product_length_cm");
        assertThat(sql).doesNotContain("product_width_cm");
        assertThat(sql).doesNotContain("product_height_cm");
        assertThat(sql).doesNotContain("product_weight_g");
    }

    @Test
    void dispatchPlanDetailsReadCurrentWarehouseSpecsWithoutPersistingStatusSnapshot() throws Exception {
        Method readMethod = WarehouseDispatchMapper.class.getMethod(
                "listDispatchPlanLines",
                Long.class
        );
        String readSql = String.join(" ", readMethod.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
        assertThat(readSql).contains("warehouseSpec.source_type = 'warehouse'");
        assertThat(readSql).contains("CASE WHEN warehouseSpec.product_length_cm IS NULL");
        assertThat(readSql).doesNotContain("line.spec_status AS specStatus");

        Method insertMethod = WarehouseDispatchMapper.class.getMethod(
                "insertDispatchPlanLine",
                WarehouseDispatchRecords.DispatchPlanLineRecord.class,
                Long.class
        );
        String insertSql = String.join(" ", insertMethod.getAnnotation(Insert.class).value())
                .replaceAll("\\s+", " ");
        assertThat(insertSql).doesNotContain("spec_status");
        assertThat(insertSql).doesNotContain("#{row.specStatus}");
    }

    @Test
    void outboundOrderPackingGateCountsUnconfirmedOrUnsubmittedQuoteSources() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "countBlockingOutboundOrderLogisticsQuotes",
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("FROM warehouse_outbound_order_line_source source");
        assertThat(sql).contains("LEFT JOIN procurement_purchase_order_logistics_quote_line quote");
        assertThat(sql).contains("quote.purchase_order_item_site_id = source.purchase_order_item_site_id");
        assertThat(sql).contains("quote.id IS NULL");
        assertThat(sql).contains("quote.quote_status != 'CONFIRMED'");
        assertThat(sql).contains("quote.shipping_submit_status != 'SUBMITTED'");
    }

    @Test
    void outboundOrderLineSourcesExposeStoreScopeForAppPackingIdentity() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "listOutboundOrderLineSources",
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("balance.logical_store_id AS logicalStoreId");
        assertThat(sql).contains("COALESCE(batch_source.source_store_code, balance.source_store_code) AS sourceStoreCode");
        assertThat(sql).contains("COALESCE(batch_source.source_store_name, balance.source_store_name) AS sourceStoreName");
        assertThat(sql).contains("LEFT JOIN warehouse_shipping_batch_source batch_source");
        assertThat(sql).contains("LEFT JOIN procurement_fulfillment_balance balance");
    }

    @Test
    void receiptOrdersOnlyExposeSubmittedShippingOrders() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "listReceiptRows",
                Long.class,
                Collection.class,
                String.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("FROM procurement_shipping_order so");
        assertThat(sql).contains("so.shipping_submit_status IN ('SUBMITTED', 'PARTIAL_SUBMITTED')");
        assertThat(sql).doesNotContain("so.status IN ('DRAFT', 'SUBMITTED')");
    }

    @Test
    void shippingBatchStatusFollowsOutboundExecutionProgress() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "listShippingBatches",
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("pending_order.status != 'SHIPPED'");
        assertThat(sql).contains("unpacked_order.status NOT IN ('PACKED', 'SHIPPED')");
        assertThat(sql).contains("packing_order.status = 'PACKING'");
        assertThat(sql).contains("THEN 'SHIPPED'");
        assertThat(sql).contains("THEN 'PACKED'");
        assertThat(sql).contains("THEN 'PACKING'");
    }

    @Test
    void shippingBatchListIncludesPackingExecutionSummary() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "listShippingBatches",
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("COUNT(*) AS optionCount");
        assertThat(sql).contains("COUNT(list.id) AS packingListCount");
        assertThat(sql).contains("SUM(list.box_count)");
        assertThat(sql).contains("SUM(list.packed_quantity)");
        assertThat(sql).contains("SUM(list.gross_weight_kg)");
        assertThat(sql).contains("SUM(list.volume_cbm)");
    }

    @Test
    void packingBoxReplacementPhysicallyDeletesDraftRowsBeforeReinsert() throws Exception {
        Method deleteItems = WarehouseDispatchMapper.class.getMethod(
                "deletePackingBoxItems",
                Long.class,
                Long.class
        );
        Method deleteBoxes = WarehouseDispatchMapper.class.getMethod(
                "deletePackingBoxes",
                Long.class,
                Long.class
        );

        Delete deleteItemsSql = deleteItems.getAnnotation(Delete.class);
        Delete deleteBoxesSql = deleteBoxes.getAnnotation(Delete.class);
        assertThat(deleteItemsSql).isNotNull();
        assertThat(deleteBoxesSql).isNotNull();

        String itemSql = String.join(" ", deleteItemsSql.value())
                .replaceAll("\\s+", " ");
        String boxSql = String.join(" ", deleteBoxesSql.value())
                .replaceAll("\\s+", " ");

        assertThat(itemSql).contains("DELETE FROM warehouse_packing_box_item");
        assertThat(itemSql).doesNotContain("is_deleted = b'1'");
        assertThat(boxSql).contains("DELETE FROM warehouse_packing_box");
        assertThat(boxSql).doesNotContain("is_deleted = b'1'");
    }

    private Method findMethod(String name) {
        return List.of(WarehouseDispatchMapper.class.getMethods()).stream()
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

}
