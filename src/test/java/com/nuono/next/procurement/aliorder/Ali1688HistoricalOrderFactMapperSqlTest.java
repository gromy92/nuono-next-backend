package com.nuono.next.procurement.aliorder;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.Ali1688Dp10FactLookupMapper;
import com.nuono.next.infrastructure.mapper.Ali1688HistoricalOrderMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class Ali1688HistoricalOrderFactMapperSqlTest {

    @Test
    void sharedProviderUpsertsPreserveManualDeletionSemantics() throws Exception {
        Method order = method("upsertOrder", Ali1688HistoricalOrderRow.class);
        Method item = method("upsertOrderItem", Ali1688HistoricalOrderItemRow.class);
        Method logistics = method(
                "upsertOrderLogistics",
                Ali1688HistoricalOrderLogisticsRow.class
        );

        assertThat(sql(order)).doesNotContain("is_deleted = b'0'");
        assertThat(sql(item)).doesNotContain("is_deleted = b'0'");
        assertThat(sql(logistics)).doesNotContain("is_deleted = b'0'");
    }

    @Test
    void dp10LocksProviderOrderAcrossReauthorizationIncludingManualTombstones() {
        String header = selectSql(lookup("selectCanonicalOrderHeadersForUpdate"));

        assertThat(header)
                .contains("oh.authorization_id AS authorizationId")
                .contains("oh.order_natural_key AS orderNaturalKey")
                .contains("auth.provider_code AS providerCode")
                .contains("auth.provider_account_id AS providerAccountId")
                .contains("oh.provider_order_no AS providerOrderNo")
                .contains("oh.is_deleted AS deleted")
                .contains("LEFT JOIN procurement_ali1688_order_authorization auth")
                .contains("auth.id = oh.authorization_id")
                .contains("auth.owner_user_id = oh.owner_user_id")
                .contains("oh.owner_user_id = #{ownerUserId}")
                .contains("auth.provider_code IN ('ALI1688_OPEN_API',")
                .contains("'ALI1688_EXCEL_LOCAL', 'ALI1688_EXCEL_UPLOAD')")
                .contains("BINARY oh.provider_order_no = BINARY #{providerOrderNo}")
                .contains("BINARY oh.order_natural_key = BINARY #{orderNaturalKey}")
                .contains("oh.superseded_by_order_id IS NULL")
                .contains("THEN 0 ELSE 1 END, oh.id ASC")
                .contains("LIMIT 2 FOR UPDATE")
                .doesNotContain("BINARY auth.provider_account_id = BINARY #{providerAccountId}")
                .doesNotContain("oh.is_deleted = b'0'");
    }

    @Test
    void dp10AloneRebindsStableIdentityAndRevivesReturnedChildren() {
        String item = updateSql(lookup("activateCanonicalItemIdentity"));
        String logistics = updateSql(lookup("activateCanonicalLogisticsIdentity"));

        assertThat(item)
                .contains("item_natural_key = #{naturalKey}")
                .contains("is_deleted = b'0'")
                .contains("WHERE id = #{itemId} AND order_id = #{orderId}");
        assertThat(logistics)
                .contains("logistics_natural_key = #{naturalKey}")
                .contains("is_deleted = b'0'")
                .contains("WHERE id = #{logisticsId}")
                .contains("item_id = #{itemId}");
    }

    @Test
    void providerUpsertsReturnCanonicalIdsAndRepairChildReferences() throws Exception {
        Method order = method("upsertOrder", Ali1688HistoricalOrderRow.class);
        Method item = method("upsertOrderItem", Ali1688HistoricalOrderItemRow.class);
        Method logistics = method(
                "upsertOrderLogistics",
                Ali1688HistoricalOrderLogisticsRow.class
        );

        assertThat(sql(order))
                .contains("LAST_INSERT_ID(#{id})")
                .contains("id = LAST_INSERT_ID(id)");
        assertThat(sql(item))
                .contains("LAST_INSERT_ID(#{id})")
                .contains("id = LAST_INSERT_ID(id)")
                .contains("order_id = VALUES(order_id)");
        assertThat(sql(logistics))
                .contains("LAST_INSERT_ID(#{id})")
                .contains("id = LAST_INSERT_ID(id)")
                .contains("order_id = VALUES(order_id)")
                .contains("item_id = VALUES(item_id)");
        assertThat(selectKey(order)).containsExactly("SELECT LAST_INSERT_ID()");
        assertThat(selectKey(item)).containsExactly("SELECT LAST_INSERT_ID()");
        assertThat(selectKey(logistics)).containsExactly("SELECT LAST_INSERT_ID()");
    }

    @Test
    void finalizationIsExactTaskFenceAndStageLocatorGuarded() {
        String guard = selectSql(lookup("countDp10ChildFinalizeFence"));
        String retireItems = updateSql(
                lookup("softRetireDp10ItemsMissingFromAuthoritativeSet"));
        String retireLogistics = updateSql(
                lookup("softRetireDp10LogisticsMissingFromAuthoritativeSet"));

        assertThat(guard)
                .contains("task.id = #{task.id}")
                .contains("task.operation_code = 'DP10'")
                .contains("task.state = 'RUNNING'")
                .contains("task.lease_owner = BINARY #{task.leaseOwner}")
                .contains("task.lease_until > UTC_TIMESTAMP(6)")
                .contains("task.fence_epoch = #{task.fenceEpoch}")
                .contains("stage.generation_no = #{slice.generationNo}")
                .contains("stage.scan_pass = 2")
                .contains("stage.partition_name = BINARY #{slice.partition}")
                .contains("stage.page_no = #{slice.pageNo}")
                .contains("stage.item_ordinal = #{slice.itemOrdinal}")
                .contains("stage.provider_order_no = BINARY #{slice.order.providerOrderNo}")
                .contains("stage.verification_state = 'VERIFIED'")
                .contains("stage.apply_state = 'READY'")
                .contains("stage.apply_item_cursor = #{slice.itemCursor}");
        assertThat(retireItems)
                .contains("item.item_natural_key NOT IN")
                .contains("AND EXISTS (")
                .contains("stage.apply_item_cursor = #{slice.itemCursor}");
        assertThat(retireLogistics)
                .contains("logistics.logistics_natural_key NOT IN")
                .contains("AND EXISTS (")
                .contains("stage.apply_item_cursor = #{slice.itemCursor}");
    }

    @Test
    void legacyReuseMatchesStableTupleAndNeverUsesGlobalOrderOffset() {
        String lookup = selectSql(lookup("selectCanonicalItemIdByStableTuple"));

        assertThat(lookup)
                .contains("offer_id")
                .contains("sku_id")
                .contains("product_code")
                .contains("single_product_code")
                .contains("LIMIT #{occurrenceOffset}, 1")
                .doesNotContain("LIMIT #{offset}, 1");
    }

    private Method method(String name, Class<?> parameter) throws Exception {
        return Ali1688HistoricalOrderMapper.class.getMethod(name, parameter);
    }

    private String sql(Method method) {
        return String.join(" ", method.getAnnotation(Insert.class).value());
    }

    private Method lookup(String name) {
        return Arrays.stream(Ali1688Dp10FactLookupMapper.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private String selectSql(Method method) {
        return String.join(" ", method.getAnnotation(Select.class).value());
    }

    private String updateSql(Method method) {
        return String.join(" ", method.getAnnotation(Update.class).value());
    }

    private String[] selectKey(Method method) {
        return method.getAnnotation(SelectKey.class).statement();
    }
}
