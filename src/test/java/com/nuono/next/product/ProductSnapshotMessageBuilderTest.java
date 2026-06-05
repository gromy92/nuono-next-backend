package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductSnapshotMessageBuilderTest {

    private final ProductSnapshotMessageBuilder builder = new ProductSnapshotMessageBuilder();

    @Test
    void shouldBuildDegradedLoadedMessageWithMissingOperationalKeys() {
        StoreSyncStoreRecord store = new StoreSyncStoreRecord();
        store.setProjectName("canman");

        String message = builder.buildLoadedMessage(
                store,
                "STR245027-NSA",
                List.of("partnerSku", "pskuCode"),
                true,
                2
        );

        assertEquals(
                "已读取 canman 的 Noon 商品详情，但当前索引还缺少 partnerSku / pskuCode，站点经营数据先按降级模式展示。",
                message
        );
    }

    @Test
    void shouldBuildReadyLoadedMessageAndFallbackToStoreCodeWhenProjectNameIsNull() {
        StoreSyncStoreRecord store = new StoreSyncStoreRecord();

        String message = builder.buildLoadedMessage(
                store,
                "STR245027-NAE",
                List.of(),
                false,
                3
        );

        assertEquals(
                "已读取 STR245027-NAE 的真实 Noon 商品主档快照，并汇总 3 个站点经营面。",
                message
        );
    }
}
