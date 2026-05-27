package com.nuono.next.noonpull;

import java.util.List;

public interface NoonProductionSchedulerEnablementRepository {
    NoonProductionSchedulerEnablementRecord save(NoonProductionSchedulerEnablementRecord record);

    List<NoonProductionSchedulerEnablementRecord> listRecent(int limit);
}
