"""Transactional SQL for an explicitly authorized remaining OTP retry."""
from __future__ import annotations


def _text(value: str) -> str:
    if not isinstance(value, str) or not value or "\x00" in value:
        raise ValueError("expected non-empty text")
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def _signal(name: str, predicate: str) -> str:
    return (f"SET @retry_guard=IF(({predicate}),'DO 0','SIGNAL SQLSTATE ''45000'' "
            f"SET MESSAGE_TEXT=''{name}''');PREPARE retry_stmt FROM @retry_guard;"
            "EXECUTE retry_stmt;DEALLOCATE PREPARE retry_stmt;")


def _task_predicate(task: dict) -> str:
    return "(" + " AND ".join((
        f"item.id={int(task['itemId'])}", f"item.recovery_id={int(task['recoveryId'])}",
        f"item.source_task_id={int(task['taskId'])}", f"item.status={_text(task['itemStatus'])}",
        f"item.source_checkpoint={_text(task['sourceCheckpoint'])}", f"item.resume_policy={_text(task['resumePolicy'])}",
        f"task.id={int(task['taskId'])}", "task.status='BLOCKED_AUTH'",
        f"task.auth_recovery_id={int(task['authRecoveryId'])}",
        f"task.owner_user_id={int(task['ownerUserId'])}",
        f"task.store_code={_text(task['storeCode'])}", f"task.site_code={_text(task['siteCode'])}",
        f"task.data_domain={_text(task['domain'])}", f"task.trigger_mode={_text(task['triggerMode'])}",
        f"task.pull_type={_text(task['pullType'])}", f"task.retry_action={_text(task['retryAction'])}",
        "task.checkpoint_cursor IS NULL", "task.next_resume_position IS NULL",
        "task.last_safe_response_summary IS NULL", "COALESCE(task.processed_item_count,0)=0",
        "COALESCE(task.request_count,0)=0", "task.finished_at IS NULL", "task.is_deleted=b'0'",
        f"plan.id={int(task['planId'])}", "plan.enabled=b'1'", "plan.paused=b'0'",
    )) + ")"


def build_apply_sql(manifest: dict, actor: str) -> str:
    recovery = manifest["recovery"]
    successor = manifest["successor"]
    tasks = manifest["tasks"]
    projects = manifest["projects"]
    task_ids = ",".join(str(item["taskId"]) for item in tasks)
    plan_ids = ",".join(str(item) for item in manifest["planIds"])
    project_locks = ",".join(
        f"({int(item['ownerUserId'])},{_text(item['projectCode'])})" for item in projects
    )
    task_predicates = " OR ".join(_task_predicate(item) for item in tasks)
    path = "/_svc/mp-partner-identity/public/user/credential/generate"
    project_guard = " AND ".join(
        f"(SELECT COUNT(*) FROM noon_project_auth_state WHERE owner_user_id={int(item['ownerUserId'])} "
        f"AND BINARY project_code=BINARY {_text(item['projectCode'])} AND status='MANUAL_HOLD' "
        f"AND active_recovery_id={int(recovery['id'])} AND auth_version={int(item['authVersion'])} "
        f"AND identity_key={_text(item['identityKey'])})=1" for item in projects
    )
    guards = " AND ".join((
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery WHERE id={int(recovery['id'])} "
        "AND status='MANUAL_HOLD' AND failure_code='IDENTITY_AUTH_FAILED' "
        f"AND version_no={int(recovery['versionNo'])} AND generation_no=1 "
        f"AND send_budget_epoch={int(recovery['sendBudgetEpoch'])} AND send_attempt_count=1 "
        "AND first_send_at IS NOT NULL AND second_send_at IS NULL AND lease_token IS NULL)=1",
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery WHERE id={int(successor['id'])} "
        f"AND predecessor_recovery_id={int(recovery['id'])} AND status='WAITING_PREDECESSOR' "
        f"AND version_no={int(successor['versionNo'])} AND generation_no=0 AND send_attempt_count=0 "
        "AND first_send_at IS NULL AND second_send_at IS NULL AND lease_token IS NULL)=1",
        f"(SELECT COUNT(*) FROM noon_auth_identity_send_ledger WHERE recovery_id={int(recovery['id'])})=1",
        f"(SELECT COUNT(*) FROM noon_http_call_log WHERE path={_text(path)} "
        f"AND occurred_at BETWEEN {_text(recovery['firstSendAt'])}-INTERVAL 1 MINUTE "
        f"AND {_text(recovery['firstSendAt'])}+INTERVAL 10 MINUTE)=0",
        f"(SELECT COUNT(*) FROM noon_http_call_log WHERE path={_text(path)})={int(manifest['providerGenerateCount'])}",
        f"(SELECT COALESCE(MAX(id),0) FROM noon_http_call_log WHERE path={_text(path)})={int(manifest['providerGenerateMaxId'])}",
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery_item WHERE recovery_id IN ({int(recovery['id'])},{int(successor['id'])}) "
        f"AND source_task_id IS NOT NULL)={len(tasks)}",
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery_item item JOIN noon_pull_task task ON task.id=item.source_task_id "
        f"JOIN noon_pull_plan plan ON plan.id=task.plan_id WHERE {task_predicates})={len(tasks)}",
        project_guard,
    ))
    pause_reason = "authorized identity retry " + manifest["manifestKey"]
    return "\n".join((
        "SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;START TRANSACTION;",
        f"SELECT id FROM noon_auth_identity_recovery WHERE id IN ({int(recovery['id'])},{int(successor['id'])}) ORDER BY id FOR UPDATE;",
        f"SELECT id FROM noon_auth_identity_recovery_item WHERE recovery_id IN ({int(recovery['id'])},{int(successor['id'])}) ORDER BY id FOR UPDATE;",
        f"SELECT id FROM noon_pull_task WHERE id IN ({task_ids}) ORDER BY id FOR UPDATE;",
        f"SELECT id FROM noon_pull_plan WHERE id IN ({plan_ids}) ORDER BY id FOR UPDATE;",
        f"SELECT owner_user_id FROM noon_project_auth_state WHERE (owner_user_id,project_code) IN ({project_locks}) FOR UPDATE;",
        _signal("NOON_AUTH_IDENTITY_FAILED_RETRY_SCOPE_DRIFT", guards),
        "UPDATE noon_pull_plan SET paused=b'1',pause_reason=" + _text(pause_reason)
        + ",gmt_updated=UTC_TIMESTAMP(3) WHERE id IN (" + plan_ids + ") AND enabled=b'1' AND paused=b'0';",
        _signal("NOON_AUTH_IDENTITY_FAILED_RETRY_PLAN_PAUSE_CAS_FAILED", f"ROW_COUNT()={len(manifest['planIds'])}"),
        "UPDATE noon_pull_task SET status='CANCELLED',finished_at=UTC_TIMESTAMP(3),gmt_updated=UTC_TIMESTAMP(3) "
        f"WHERE id IN ({task_ids}) AND status='BLOCKED_AUTH';",
        _signal("NOON_AUTH_IDENTITY_FAILED_RETRY_TASK_CANCEL_CAS_FAILED", f"ROW_COUNT()={len(tasks)}"),
        "UPDATE noon_auth_identity_recovery SET status='WAITING_COOLDOWN',next_attempt_at=UTC_TIMESTAMP(3)+INTERVAL "
        + str(int(manifest["cooldownSeconds"])) + " SECOND,failure_code=NULL,diagnostic_summary="
        + _text("authorized retry " + manifest["manifestSha256"])
        + ",lease_owner=NULL,lease_token=NULL,lease_until=NULL,version_no=version_no+1,gmt_updated=UTC_TIMESTAMP(3) "
        f"WHERE id={int(recovery['id'])} AND version_no={int(recovery['versionNo'])};",
        _signal("NOON_AUTH_IDENTITY_FAILED_RETRY_RECOVERY_CAS_FAILED", "ROW_COUNT()=1"),
        "UPDATE noon_project_auth_state SET status='REAUTH_REQUIRED',manual_hold_reason=NULL,last_failure_code=NULL,"
        "last_failure_task_id=NULL,last_failure_at=NULL,gmt_updated=UTC_TIMESTAMP(3) "
        f"WHERE active_recovery_id={int(recovery['id'])} AND status='MANUAL_HOLD';",
        _signal("NOON_AUTH_IDENTITY_FAILED_RETRY_PROJECT_CAS_FAILED", f"ROW_COUNT()={len(projects)}"),
        "COMMIT;",
    )) + "\n"


__all__ = ["build_apply_sql"]
