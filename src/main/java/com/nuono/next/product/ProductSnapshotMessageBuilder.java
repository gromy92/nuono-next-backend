package com.nuono.next.product;

import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.Collections;
import java.util.List;

class ProductSnapshotMessageBuilder {

    String buildLoadedMessage(
            StoreSyncStoreRecord store,
            String storeCode,
            List<String> missingOperationalKeys,
            boolean degraded,
            int projectSiteCount
    ) {
        String displayName = store != null && store.getProjectName() != null
                ? store.getProjectName()
                : storeCode;
        if (degraded) {
            List<String> missingKeys = missingOperationalKeys == null
                    ? Collections.emptyList()
                    : missingOperationalKeys;
            return "已读取 "
                    + displayName
                    + " 的 Noon 商品详情，但当前索引还缺少 "
                    + String.join(" / ", missingKeys)
                    + "，站点经营数据先按降级模式展示。";
        }
        return "已读取 "
                + displayName
                + " 的真实 Noon 商品主档快照，并汇总 "
                + projectSiteCount
                + " 个站点经营面。";
    }
}
