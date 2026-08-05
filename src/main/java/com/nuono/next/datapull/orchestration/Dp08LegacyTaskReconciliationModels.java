package com.nuono.next.datapull.orchestration;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** Internal immutable values used while reconciling one active DP08 snapshot. */
final class Dp08LegacyTaskReconciliationModels {
    private Dp08LegacyTaskReconciliationModels() {
    }

    static final class ActiveTask {
        private final long id;
        private final String taskType;
        private final String status;
        private final ObjectNode payload;
        private final Long ownerUserId;
        private final String storeCode;
        private final String siteCode;
        private final String naturalKey;

        ActiveTask(
                long id,
                String taskType,
                String status,
                ObjectNode payload,
                Long ownerUserId,
                String storeCode,
                String siteCode,
                String naturalKey
        ) {
            this.id = id;
            this.taskType = taskType;
            this.status = status;
            this.payload = payload;
            this.ownerUserId = ownerUserId;
            this.storeCode = storeCode;
            this.siteCode = siteCode;
            this.naturalKey = naturalKey;
        }

        long id() { return id; }
        String taskType() { return taskType; }
        String status() { return status; }
        ObjectNode payload() { return payload; }
        Long ownerUserId() { return ownerUserId; }
        String storeCode() { return storeCode; }
        String siteCode() { return siteCode; }
        String naturalKey() { return naturalKey; }
    }

    static final class ActiveRun {
        private final long id;
        private final long taskId;
        private final long watchProductId;
        private final String status;
        private final String triggerMode;

        ActiveRun(
                long id,
                long taskId,
                long watchProductId,
                String status,
                String triggerMode
        ) {
            this.id = id;
            this.taskId = taskId;
            this.watchProductId = watchProductId;
            this.status = status;
            this.triggerMode = triggerMode;
        }

        long id() { return id; }
        long taskId() { return taskId; }
        long watchProductId() { return watchProductId; }
        String status() { return status; }
        String triggerMode() { return triggerMode; }
    }
}
