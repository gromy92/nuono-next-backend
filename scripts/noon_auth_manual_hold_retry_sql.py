"""Transactional SQL for the exact governed Noon manual-hold retry."""
from __future__ import annotations

import json
import hashlib


def _text(value: object) -> str:
    if not isinstance(value, str) or not value or "\x00" in value:
        raise ValueError("expected non-empty text")
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def _nullable(value: object) -> str:
    return "NULL" if value is None else _text(value)


def _signal(name: str, predicate: str) -> str:
    return (f"SET @retry_guard=IF(({predicate}),'DO 0','SIGNAL SQLSTATE ''45000'' "
            f"SET MESSAGE_TEXT=''{name}''');PREPARE retry_stmt FROM @retry_guard;"
            "EXECUTE retry_stmt;DEALLOCATE PREPARE retry_stmt;")


def _state_sha(value: dict) -> str:
    encoded = json.dumps(value, ensure_ascii=False, sort_keys=True,
                         separators=(",", ":")).encode()
    return hashlib.sha256(encoded).hexdigest()


def _task_predicate(task: dict) -> str:
    return "(" + " AND ".join((
        f"item.id={int(task['itemId'])}",
        f"item.recovery_id={int(task['recoveryId'])}",
        f"item.owner_user_id={int(task['ownerUserId'])}",
        f"BINARY item.project_code=BINARY {_text(task['projectCode'])}",
        f"item.source_task_id={int(task['sourceTaskId'])}",
        f"item.source_domain={_text(task['sourceDomain'])}",
        "item.source_checkpoint='PERSISTED_TASK_STATE'",
        "item.resume_policy='AUTO_RESUME'", "item.status='PENDING'",
        f"task.id={int(task['taskId'])}", f"task.owner_user_id={int(task['taskOwnerUserId'])}",
        f"task.store_code={_text(task['taskStoreCode'])}",
        f"task.site_code={_text(task['taskSiteCode'])}",
        f"task.data_domain={_text(task['dataDomain'])}", "task.status='BLOCKED_AUTH'",
        f"task.auth_recovery_id={int(task['authRecoveryId'])}",
        "task.trigger_mode='SCHEDULED_DAILY'", "task.pull_type='REPORT'",
        f"task.target_identity={_text(task['targetIdentity'])}",
        "task.retry_action='WAIT_FOR_AUTH'", "task.checkpoint_cursor IS NULL",
        "task.next_resume_position IS NULL", "task.last_safe_response_summary IS NULL",
        "COALESCE(task.processed_item_count,0)=0", "COALESCE(task.request_count,0)=0",
        "task.finished_at IS NULL", "task.is_deleted=b'0'",
        f"plan.id={int(task['planId'])}", "plan.enabled=b'1'", "plan.paused=b'0'",
    )) + ")"


def build_apply_sql(manifest: dict, actor: str) -> str:
    recovery = manifest["recovery"]
    item = manifest["item"]
    state = manifest["projectState"]
    scope = manifest["ownerScope"]
    recovery_id = int(recovery["id"])
    owner = int(item["ownerUserId"])
    cooldown = int(manifest["cooldownSeconds"])
    next_version = int(state["authVersion"]) + 1
    path = "/_svc/mp-partner-identity/public/user/credential/generate"
    task_ids = ",".join(str(task_id) for task_id in manifest["cancelledTaskIds"])
    plan_ids = ",".join(str(plan_id) for plan_id in manifest["pausedPlanIds"])
    pause_reason = "managed auth recovery " + manifest["manifestKey"]
    task_predicates = " OR ".join(_task_predicate(task) for task in manifest["taskItems"])
    details = json.dumps({"manifestKey": manifest["manifestKey"], "recoveryId": recovery_id,
                          "operation": "MANUAL_HOLD_RETRY_REBASE",
                          "cancelledTaskIds": manifest["cancelledTaskIds"],
                          "pausedPlanIds": manifest["pausedPlanIds"],
                          "releaseProvenance": manifest["releaseProvenance"]},
                         separators=(",", ":"))
    guards = " AND ".join((
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery WHERE id={recovery_id} "
        f"AND status='MANUAL_HOLD' AND failure_code='SEND_RESULT_UNKNOWN' "
        f"AND version_no={int(recovery['versionNo'])} AND generation_no=2 "
        f"AND send_budget_epoch={int(recovery['sendBudgetEpoch'])} AND send_attempt_count=2 "
        "AND first_send_at IS NOT NULL AND second_send_at IS NOT NULL AND lease_token IS NULL)=1",
        f"(SELECT COUNT(*) FROM noon_auth_identity_send_ledger WHERE recovery_id={recovery_id})=2",
        f"(SELECT COUNT(*) FROM noon_http_call_log WHERE path={_text(path)} "
        f"AND occurred_at BETWEEN {_text(recovery['firstSendAt'])}-INTERVAL 1 MINUTE "
        f"AND {_text(recovery['secondSendAt'])}+INTERVAL 10 MINUTE)=0",
        f"(SELECT COUNT(*) FROM noon_http_call_log WHERE path={_text(path)})={int(manifest['providerGenerateCount'])}",
        f"(SELECT COALESCE(MAX(id),0) FROM noon_http_call_log WHERE path={_text(path)})={int(manifest['providerGenerateMaxId'])}",
        f"(SELECT COUNT(*) FROM noon_auth_owner_scope_manifest m JOIN noon_auth_identity_recovery sr "
        f"ON sr.id=m.scoped_recovery_id WHERE m.id={int(scope['id'])} AND m.status='ACTIVE' "
        f"AND m.owner_user_id={owner} AND m.predecessor_recovery_id={recovery_id} "
        f"AND sr.id={int(scope['scopedRecoveryId'])} AND sr.status='WAITING_PREDECESSOR' "
        "AND sr.send_attempt_count=0 AND sr.lease_token IS NULL)=1",
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery_item item "
        "JOIN noon_pull_task task ON task.id=item.source_task_id "
        "JOIN noon_pull_plan plan ON plan.id=task.plan_id "
        f"WHERE {task_predicates})={len(manifest['taskItems'])}",
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery_item item "
        f"WHERE item.owner_user_id={owner} AND item.source_task_id IS NOT NULL "
        f"AND item.recovery_id IN ({recovery_id},{int(scope['scopedRecoveryId'])}))="
        f"{int(manifest['sourceTaskItemCount'])}",
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery_item WHERE id={int(item['id'])} "
        f"AND recovery_id={recovery_id} AND owner_user_id={owner} "
        f"AND BINARY project_code=BINARY {_text(item['projectCode'])} "
        f"AND source_task_id={int(item['sourceTaskId'])} "
        f"AND source_domain={_text(item['sourceDomain'])} "
        f"AND source_checkpoint={_text(item['sourceCheckpoint'])} "
        f"AND resume_policy={_text(item['resumePolicy'])} "
        f"AND status='PENDING' AND expected_auth_version={int(item['expectedAuthVersion'])})=1",
        f"(SELECT COUNT(*) FROM noon_project_auth_state WHERE owner_user_id={owner} "
        f"AND BINARY project_code=BINARY {_text(item['projectCode'])} AND status='MANUAL_HOLD' "
        f"AND active_recovery_id={recovery_id} AND auth_version={int(state['authVersion'])} "
        f"AND identity_key={_text(state['identityKey'])} "
        f"AND binding_fingerprint<=>{_nullable(state.get('bindingFingerprint'))} "
        f"AND config_fingerprint<=>{_nullable(state.get('configFingerprint'))})=1",
    ))
    return "\n".join((
        "SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;START TRANSACTION;",
        f"SELECT id FROM noon_auth_identity_recovery WHERE id={recovery_id} FOR UPDATE;",
        f"SELECT id FROM noon_auth_identity_recovery_item WHERE id={int(item['id'])} FOR UPDATE;",
        f"SELECT owner_user_id FROM noon_project_auth_state WHERE owner_user_id={owner} "
        f"AND BINARY project_code=BINARY {_text(item['projectCode'])} FOR UPDATE;",
        f"SELECT id FROM noon_auth_owner_scope_manifest WHERE id={int(scope['id'])} FOR UPDATE;",
        f"SELECT id FROM noon_pull_task WHERE id IN ({task_ids}) ORDER BY id FOR UPDATE;",
        f"SELECT id FROM noon_pull_plan WHERE id IN ({plan_ids}) ORDER BY id FOR UPDATE;",
        _signal("NOON_AUTH_RETRY_SCOPE_DRIFT", guards),
        "UPDATE noon_pull_plan SET paused=b'1',"
        f"pause_reason={_text(pause_reason)},gmt_updated=UTC_TIMESTAMP(3) "
        f"WHERE id IN ({plan_ids}) AND enabled=b'1' AND paused=b'0';",
        _signal("NOON_AUTH_RETRY_PLAN_PAUSE_CAS_FAILED",
                f"ROW_COUNT()={len(manifest['pausedPlanIds'])}"),
        "UPDATE noon_pull_task SET status='CANCELLED',finished_at=UTC_TIMESTAMP(3),"
        f"gmt_updated=UTC_TIMESTAMP(3) WHERE id IN ({task_ids}) AND status='BLOCKED_AUTH';",
        _signal("NOON_AUTH_RETRY_TASK_CANCEL_CAS_FAILED",
                f"ROW_COUNT()={len(manifest['taskItems'])}"),
        "UPDATE noon_auth_identity_recovery SET status='WAITING_COOLDOWN',"
        f"config_fingerprint={_text(recovery['configFingerprint'])},"
        f"next_attempt_at=UTC_TIMESTAMP(3)+INTERVAL {cooldown} SECOND,"
        "send_budget_epoch=send_budget_epoch+1,generation_no=0,send_attempt_count=0,"
        "first_send_at=NULL,second_send_at=NULL,failure_code=NULL,diagnostic_summary=NULL,"
        "lease_owner=NULL,lease_token=NULL,lease_until=NULL,version_no=version_no+1,"
        f"gmt_updated=UTC_TIMESTAMP(3) WHERE id={recovery_id} AND version_no={int(recovery['versionNo'])};",
        _signal("NOON_AUTH_RETRY_RECOVERY_CAS_FAILED", "ROW_COUNT()=1"),
        "UPDATE noon_project_auth_state SET status='REAUTH_REQUIRED',auth_version=auth_version+1,"
        "config_fingerprint=" + _text(recovery["configFingerprint"]) + ",last_failure_code=NULL,"
        "last_failure_task_id=NULL,last_failure_at=NULL,manual_hold_reason=NULL,gmt_updated=UTC_TIMESTAMP(3) "
        f"WHERE owner_user_id={owner} AND BINARY project_code=BINARY {_text(item['projectCode'])} "
        f"AND active_recovery_id={recovery_id} AND auth_version={int(state['authVersion'])};",
        _signal("NOON_AUTH_RETRY_PROJECT_CAS_FAILED", "ROW_COUNT()=1"),
        "UPDATE noon_auth_identity_recovery_item SET status='PENDING',"
        f"expected_auth_version={next_version},failure_code=NULL,diagnostic_summary=NULL,"
        f"recovered_at=NULL,gmt_updated=UTC_TIMESTAMP(3) WHERE id={int(item['id'])} "
        f"AND recovery_id={recovery_id} AND expected_auth_version={int(item['expectedAuthVersion'])};",
        _signal("NOON_AUTH_RETRY_ITEM_CAS_FAILED", "ROW_COUNT()=1"),
        "INSERT INTO noon_auth_owner_scope_audit "
        "(manifest_id,action,actor,before_state_sha256,after_state_sha256,details_json,gmt_create) VALUES ("
        f"{int(scope['id'])},'PREDECESSOR_RETRY_REBASE',{_text(actor)},"
        f"{_text(manifest['manifestSha256'])},{_text(_state_sha(manifest['expectedAfter']))},"
        f"CAST({_text(details)} AS JSON),UTC_TIMESTAMP(3));",
        "COMMIT;",
    )) + "\n"


def build_finalize_sql(manifest: dict, actor: str) -> str:
    scope = manifest["ownerScope"]
    owner = int(manifest["item"]["ownerUserId"])
    recovery_id = int(manifest["recovery"]["id"])
    scoped_id = int(scope["scopedRecoveryId"])
    task_ids = ",".join(str(task_id) for task_id in manifest["cancelledTaskIds"])
    plan_ids = ",".join(str(plan_id) for plan_id in manifest["pausedPlanIds"])
    projects = sorted({task["projectCode"] for task in manifest["taskItems"]})
    project_values = ",".join(_text(project) for project in projects)
    pause_reason = "managed auth recovery " + manifest["manifestKey"]
    details = json.dumps({"manifestKey": manifest["manifestKey"],
                          "operation": "SOURCE_PLANS_UNPAUSED",
                          "planIds": manifest["pausedPlanIds"]}, separators=(",", ":"))
    guards = " AND ".join((
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery WHERE id IN ({recovery_id},{scoped_id}) AND status='COMPLETED' AND lease_token IS NULL)=2",
        f"(SELECT COUNT(*) FROM noon_project_auth_state WHERE owner_user_id={owner} AND project_code IN ({project_values}) AND status='HEALTHY' AND active_recovery_id IS NULL)={len(projects)}",
        f"(SELECT COUNT(*) FROM noon_pull_task WHERE id IN ({task_ids}) AND status='CANCELLED' AND finished_at IS NOT NULL)={len(manifest['cancelledTaskIds'])}",
        f"(SELECT COUNT(*) FROM noon_pull_plan WHERE id IN ({plan_ids}) AND enabled=b'1' AND paused=b'1' AND pause_reason={_text(pause_reason)})={len(manifest['pausedPlanIds'])}",
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery WHERE id={int(scope['sourceRecoveryId'])} AND status='WAITING_PREDECESSOR' AND send_attempt_count=0)=1",
    ))
    return "\n".join((
        "SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;START TRANSACTION;",
        f"SELECT id FROM noon_pull_plan WHERE id IN ({plan_ids}) ORDER BY id FOR UPDATE;",
        _signal("NOON_AUTH_RETRY_FINALIZE_DRIFT", guards),
        "UPDATE noon_pull_plan SET paused=b'0',pause_reason=NULL,gmt_updated=UTC_TIMESTAMP(3) "
        f"WHERE id IN ({plan_ids}) AND enabled=b'1' AND paused=b'1' "
        f"AND pause_reason={_text(pause_reason)};",
        _signal("NOON_AUTH_RETRY_PLAN_UNPAUSE_CAS_FAILED",
                f"ROW_COUNT()={len(manifest['pausedPlanIds'])}"),
        "INSERT INTO noon_auth_owner_scope_audit "
        "(manifest_id,action,actor,before_state_sha256,after_state_sha256,details_json,gmt_create) VALUES ("
        f"{int(scope['id'])},'SOURCE_PLANS_UNPAUSED',{_text(actor)},"
        f"{_text(_state_sha(manifest['expectedAfter']))},{_text(manifest['manifestSha256'])},"
        f"CAST({_text(details)} AS JSON),UTC_TIMESTAMP(3));",
        "COMMIT;",
    )) + "\n"


__all__ = ["build_apply_sql", "build_finalize_sql"]
