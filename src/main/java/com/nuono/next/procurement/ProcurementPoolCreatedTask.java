package com.nuono.next.procurement;

import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.PoolItemLockRow;

final class ProcurementPoolCreatedTask {

    final Long poolItemId;
    final Long taskId;
    final PoolItemLockRow itemLock;

    ProcurementPoolCreatedTask(Long poolItemId, Long taskId, PoolItemLockRow itemLock) {
        this.poolItemId = poolItemId;
        this.taskId = taskId;
        this.itemLock = itemLock;
    }
}
