"""Transactional SQL for one owner-scoped historical Noon auth takeover."""
from __future__ import annotations

import json


def _text(value: object) -> str:
    if not isinstance(value, str) or not value or "\x00" in value:
        raise ValueError("expected non-empty text")
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def _signal(name: str, predicate: str) -> str:
    return (f"SET @owner_takeover_guard=IF(({predicate}),'DO 0','SIGNAL SQLSTATE ''45000'' "
            f"SET MESSAGE_TEXT=''{name}'');PREPARE owner_takeover_stmt FROM @owner_takeover_guard;"
            "EXECUTE owner_takeover_stmt;DEALLOCATE PREPARE owner_takeover_stmt;")


def build_apply_sql(manifest: dict, actor: str) -> str:
    predecessor = manifest["predecessor"]
    source = manifest["source"]
    owner = manifest["ownerUserId"]
    items = manifest["items"]
    remaining = manifest["remainingItems"]
    tasks = manifest["tasks"]
    projects = manifest["projects"]
    identity = manifest["identityKey"]
    item_ids = ",".join(str(item["id"]) for item in items)
    all_item_ids = ",".join(str(item["id"]) for item in items + remaining)
    task_ids = ",".join(str(task["id"]) for task in tasks)
    plan_ids = ",".join(str(plan) for plan in manifest["planIds"])
    project_predicates = " OR ".join(
        "(owner_user_id={owner} AND BINARY project_code=BINARY {project} "
        "AND status='REAUTH_REQUIRED' AND active_recovery_id={source_id} "
        "AND auth_version={version})".format(
            owner=owner, project=_text(project["projectCode"]), source_id=source["id"],
            version=project["authVersion"],
        ) for project in projects
    )
    task_predicates = " OR ".join(
        "(task.id={id} AND task.status='BLOCKED_AUTH' AND task.auth_recovery_id={source_id} "
        "AND task.plan_id={plan_id} AND task.owner_user_id={owner} AND task.data_domain={domain} "
        "AND task.checkpoint_cursor IS NULL AND task.next_resume_position IS NULL "
        "AND task.last_safe_response_summary IS NULL AND COALESCE(task.processed_item_count,0)=0 "
        "AND COALESCE(task.request_count,0)=0 AND task.finished_at IS NULL AND task.is_deleted=b'0')".format(
            id=task["id"], source_id=source["id"], plan_id=task["planId"], owner=owner,
            domain=_text(task["domain"]),
        ) for task in tasks
    )
    source_item_guard = " AND ".join((
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery_item WHERE recovery_id={source['id']} AND owner_user_id={owner})={len(items)}",
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery_item WHERE recovery_id={source['id']} AND owner_user_id<>{owner})={len(remaining)}",
        f"(SELECT COUNT(DISTINCT project_code) FROM noon_auth_identity_recovery_item WHERE recovery_id={source['id']} AND owner_user_id<>{owner})={manifest['remainingProjectCount']}",
    ))
    guards = " AND ".join((
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery WHERE id={predecessor['id']} AND identity_key={_text(identity)} AND status='MANUAL_HOLD' AND failure_code='IDENTITY_AUTH_FAILED' AND version_no={predecessor['versionNo']} AND generation_no={predecessor['generationNo']} AND send_budget_epoch={predecessor['sendBudgetEpoch']} AND send_attempt_count=0 AND first_send_at IS NULL AND second_send_at IS NULL AND lease_token IS NULL)=1",
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery WHERE id={source['id']} AND predecessor_recovery_id={predecessor['id']} AND identity_key={_text(identity)} AND status='WAITING_PREDECESSOR' AND scope_owner_user_id IS NULL AND version_no={source['versionNo']} AND generation_no={source['generationNo']} AND send_budget_epoch={source['sendBudgetEpoch']} AND send_attempt_count=0 AND first_send_at IS NULL AND second_send_at IS NULL AND lease_token IS NULL)=1",
        f"(SELECT COUNT(*) FROM noon_auth_identity_send_ledger WHERE recovery_id IN ({predecessor['id']},{source['id']}))=0",
        source_item_guard,
        f"(SELECT COUNT(*) FROM noon_project_auth_state state WHERE {project_predicates})={len(projects)}",
        f"(SELECT COUNT(*) FROM noon_pull_task task JOIN noon_pull_plan plan ON plan.id=task.plan_id WHERE ({task_predicates}) AND plan.enabled=b'1' AND plan.paused=b'0')={len(tasks)}",
        f"(SELECT COUNT(*) FROM noon_http_call_log WHERE path='/_svc/mp-partner-identity/public/user/credential/generate')={manifest['providerGenerateCount']}",
        f"(SELECT COALESCE(MAX(id),0) FROM noon_http_call_log WHERE path='/_svc/mp-partner-identity/public/user/credential/generate')={manifest['providerGenerateMaxId']}",
    ))
    reason = "authorized owner-only login recovery " + manifest["manifestKey"]
    return "\n".join((
        "SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;START TRANSACTION;",
        f"SELECT id FROM noon_auth_identity_recovery WHERE id IN ({predecessor['id']},{source['id']}) ORDER BY id FOR UPDATE;",
        f"SELECT id FROM noon_auth_identity_recovery_item WHERE id IN ({all_item_ids}) ORDER BY id FOR UPDATE;",
        f"SELECT owner_user_id,project_code FROM noon_project_auth_state state WHERE {project_predicates} ORDER BY owner_user_id,project_code FOR UPDATE;",
        f"SELECT id FROM noon_pull_task WHERE id IN ({task_ids}) ORDER BY id FOR UPDATE;",
        f"SELECT id FROM noon_pull_plan WHERE id IN ({plan_ids}) ORDER BY id FOR UPDATE;",
        _signal("NOON_AUTH_OWNER_TAKEOVER_SCOPE_DRIFT", guards),
        "UPDATE noon_pull_plan SET paused=b'1',pause_reason=" + _text(reason)
        + f",gmt_updated=UTC_TIMESTAMP(3) WHERE id IN ({plan_ids}) AND enabled=b'1' AND paused=b'0';",
        _signal("NOON_AUTH_OWNER_TAKEOVER_PLAN_PAUSE_CAS_FAILED", f"ROW_COUNT()={len(manifest['planIds'])}"),
        "UPDATE noon_pull_task SET status='CANCELLED',finished_at=UTC_TIMESTAMP(3),gmt_updated=UTC_TIMESTAMP(3) "
        + f"WHERE id IN ({task_ids}) AND status='BLOCKED_AUTH' AND auth_recovery_id={source['id']};",
        _signal("NOON_AUTH_OWNER_TAKEOVER_TASK_CANCEL_CAS_FAILED", f"ROW_COUNT()={len(tasks)}"),
        "UPDATE noon_auth_identity_recovery SET status='FAILED_FINAL',failure_code='SUPERSEDED_BY_OWNER_SCOPE_TAKEOVER',"
        "diagnostic_summary='historical mixed owner hold retired for an authorized owner-only login recovery',"
        "completed_at=UTC_TIMESTAMP(3),lease_owner=NULL,lease_token=NULL,lease_until=NULL,version_no=version_no+1,gmt_updated=UTC_TIMESTAMP(3) "
        + f"WHERE id={predecessor['id']} AND status='MANUAL_HOLD' AND version_no={predecessor['versionNo']};",
        _signal("NOON_AUTH_OWNER_TAKEOVER_PREDECESSOR_CAS_FAILED", "ROW_COUNT()=1"),
        "INSERT INTO noon_auth_identity_recovery (predecessor_recovery_id,identity_key,status,scope_owner_user_id,generation_no,send_budget_epoch,send_attempt_count,coalesce_until,next_attempt_at,version_no,config_fingerprint,diagnostic_summary,requested_at,gmt_create,gmt_updated) "
        + f"SELECT NULL,identity_key,'COALESCING',NULL,0,0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),0,config_fingerprint,{_text(reason)},UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3) FROM noon_auth_identity_recovery WHERE id={source['id']};",
        "SET @owner_takeover_recovery_id=LAST_INSERT_ID();",
        _signal("NOON_AUTH_OWNER_TAKEOVER_INSERT_FAILED", "@owner_takeover_recovery_id>0"),
        f"UPDATE noon_auth_identity_recovery_item SET recovery_id=@owner_takeover_recovery_id,gmt_updated=UTC_TIMESTAMP(3) WHERE recovery_id={source['id']} AND owner_user_id={owner} AND id IN ({item_ids});",
        _signal("NOON_AUTH_OWNER_TAKEOVER_ITEM_MOVE_CAS_FAILED", f"ROW_COUNT()={len(items)}"),
        "UPDATE noon_project_auth_state SET active_recovery_id=@owner_takeover_recovery_id,gmt_updated=UTC_TIMESTAMP(3) WHERE " + project_predicates + ";",
        _signal("NOON_AUTH_OWNER_TAKEOVER_PROJECT_MOVE_CAS_FAILED", f"ROW_COUNT()={len(projects)}"),
        "COMMIT;SELECT @owner_takeover_recovery_id;",
    )) + "\n"


__all__ = ["build_apply_sql"]
