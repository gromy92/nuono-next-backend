package com.nuono.next.officialwarehouse;

import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AsnListSyncView;
import com.nuono.next.permission.access.BusinessAccessContext;

/**
 * Reconstructible ASN-list operation used by both the request thread and the durable pull worker.
 */
public interface OfficialWarehouseAsnListTaskExecutor {
    AsnListSyncView syncNoonAsnListForTask(
            BusinessAccessContext access,
            String storeCode,
            String siteCode
    );
}
