package com.nuono.next.infrastructure.mapper;

interface NoonAuthRecoveryMapperColumns {
    String RECOVERY_COLUMNS = ""
            + "id, predecessor_recovery_id AS predecessorRecoveryId, identity_key AS identityKey, "
            + "status, generation_no AS generationNo, "
            + "send_attempt_count AS sendAttemptCount, first_send_at AS firstSendAt, "
            + "second_send_at AS secondSendAt, coalesce_until AS coalesceUntil, "
            + "next_attempt_at AS nextAttemptAt, lease_owner AS leaseOwner, lease_token AS leaseToken, "
            + "lease_until AS leaseUntil, version_no AS versionNo, config_fingerprint AS configFingerprint, "
            + "last_mail_uid_hash AS lastMailUidHash, last_message_id_hash AS lastMessageIdHash, "
            + "failure_code AS failureCode, diagnostic_summary AS diagnosticSummary, "
            + "requested_at AS requestedAt, started_at AS startedAt, completed_at AS completedAt, "
            + "gmt_create AS createdAt, gmt_updated AS updatedAt";

    String ITEM_COLUMNS = ""
            + "id, recovery_id AS recoveryId, owner_user_id AS ownerUserId, project_code AS projectCode, "
            + "store_code AS storeCode, site_code AS siteCode, source_task_id AS sourceTaskId, "
            + "source_domain AS sourceDomain, source_checkpoint AS sourceCheckpoint, "
            + "resume_policy AS resumePolicy, expected_auth_version AS expectedAuthVersion, status, "
            + "failure_code AS failureCode, diagnostic_summary AS diagnosticSummary, "
            + "recovered_at AS recoveredAt, gmt_create AS createdAt, gmt_updated AS updatedAt";

    String PROJECT_STATE_COLUMNS = ""
            + "owner_user_id AS ownerUserId, project_code AS projectCode, identity_key AS identityKey, "
            + "status, active_recovery_id AS activeRecoveryId, auth_version AS authVersion, "
            + "binding_fingerprint AS bindingFingerprint, config_fingerprint AS configFingerprint, "
            + "last_failure_code AS lastFailureCode, last_failure_task_id AS lastFailureTaskId, "
            + "last_failure_at AS lastFailureAt, last_success_at AS lastSuccessAt, "
            + "manual_hold_reason AS manualHoldReason, gmt_create AS createdAt, gmt_updated AS updatedAt";
}
