package com.nuono.next.officialwarehouse;

import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AsnListSyncView;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.Collection;

public interface OfficialWarehouseAsnNumberSyncer {

    AsnListSyncView syncNoonAsnNumbers(
            BusinessAccessContext access,
            String storeCode,
            String siteCode,
            Collection<String> asnNumbers,
            boolean dryRun
    );
}
