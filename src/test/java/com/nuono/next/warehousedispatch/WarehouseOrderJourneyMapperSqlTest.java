package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.WarehouseOrderJourneyMapper;
import java.lang.reflect.Method;
import java.util.Collection;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class WarehouseOrderJourneyMapperSqlTest {

    @Test
    void journeyUsesStableItemSiteIdentityAndKeepsEveryBatchStatus() throws Exception {
        Method method = WarehouseOrderJourneyMapper.class.getMethod(
                "listWarehouseOrderJourneys",
                Long.class,
                Collection.class
        );
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains(
                "batch_source.purchase_order_item_site_id = shipping_line.purchase_order_item_site_id"
        );
        assertThat(sql).contains("shipping_order.owner_user_id = #{ownerUserId}");
        assertThat(sql).contains("batch.owner_user_id = #{ownerUserId}");
        assertThat(sql).doesNotContain("batch.status IN");
        assertThat(sql).doesNotContain("shipping_order.status IN");
    }
}
