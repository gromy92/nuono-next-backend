"""Transactional SQL for one owner-only terminal drain of a historical Noon recovery."""
from __future__ import annotations


def _text(value: object) -> str:
    if not isinstance(value, str) or not value or "\x00" in value:
        raise ValueError("expected non-empty text")
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def _signal(name: str, predicate: str) -> str:
    relation = "__nuono_owner_takeover_guard_" + name.lower()
    return (f"SET @owner_takeover_guard=IF(({predicate}),'DO 0','SELECT 1 FROM `{relation}`');"
            "PREPARE owner_takeover_stmt FROM @owner_takeover_guard;"
            "EXECUTE owner_takeover_stmt;DEALLOCATE PREPARE owner_takeover_stmt;")


def _item_guard(item: dict, source_id: int) -> str:
    def nullable(column: str, value: object) -> str:
        if value is None:
            rendered = "NULL"
        elif isinstance(value, int):
            rendered = str(value)
        else:
            rendered = _text(value)
        return f"{column} <=> {rendered}"
    return "(SELECT COUNT(*) FROM noon_auth_identity_recovery_item WHERE id={id} AND recovery_id={recovery} AND owner_user_id={owner} AND BINARY project_code=BINARY {project} AND {store} AND {site} AND {task} AND {domain} AND {checkpoint} AND resume_policy={policy} AND expected_auth_version={version} AND status={status})=1".format(
        id=item["id"], recovery=source_id, owner=item["ownerUserId"], project=_text(item["projectCode"]),
        store=nullable("store_code", item["storeCode"]), site=nullable("site_code", item["siteCode"]),
        task=nullable("source_task_id", item["sourceTaskId"]), domain=nullable("source_domain", item["sourceDomain"]),
        checkpoint=nullable("source_checkpoint", item["sourceCheckpoint"]), policy=_text(item["resumePolicy"]),
        version=item["expectedAuthVersion"], status=_text(item["status"]))


def build_apply_sql(manifest: dict, actor: str) -> str:
    predecessor, source = manifest["predecessor"], manifest["source"]
    owner, identity = manifest["ownerUserId"], manifest["identityKey"]
    selected, settled, remaining = manifest["items"], manifest["settledItems"], manifest["remainingItems"]
    tasks, projects, bindings = manifest["tasks"], manifest["projects"], manifest["bindingItems"]
    selected_ids = ",".join(str(item["id"]) for item in selected)
    settled_ids = ",".join(str(item["id"]) for item in settled)
    all_item_ids = ",".join(str(item["id"]) for item in selected + settled + remaining)
    task_ids = ",".join(str(task["id"]) for task in tasks)
    project_predicates = " OR ".join(
        "(owner_user_id={owner} AND BINARY project_code=BINARY {project} AND status='REAUTH_REQUIRED' "
        "AND active_recovery_id={source} AND auth_version={version})".format(
            owner=owner, project=_text(project["projectCode"]), source=source["id"], version=project["authVersion"])
        for project in projects)
    settled_states = {}
    for item in settled:
        settled_states[item["projectCode"]] = item["expectedAuthVersion"]
    settled_predicates = " OR ".join(
        "(owner_user_id={owner} AND BINARY project_code=BINARY {project} AND status='HEALTHY' "
        "AND active_recovery_id IS NULL AND auth_version>{version})".format(
            owner=owner, project=_text(project), version=version)
        for project, version in sorted(settled_states.items())) or "0"
    state_predicates = " OR ".join(filter(None, (project_predicates, settled_predicates)))
    task_predicates = " OR ".join(
        "(task.id={id} AND task.status='BLOCKED_AUTH' AND task.auth_recovery_id={source} "
        "AND task.plan_id={plan} AND task.owner_user_id={owner} AND task.data_domain={domain} "
        "AND task.checkpoint_cursor IS NULL AND task.next_resume_position IS NULL "
        "AND task.last_safe_response_summary IS NULL AND COALESCE(task.processed_item_count,0)=0 "
        "AND COALESCE(task.request_count,0)=0 AND task.finished_at IS NULL AND task.is_deleted=0)".format(
            id=task["id"], source=source["id"], plan=task["planId"], owner=owner, domain=_text(task["domain"]))
        for task in tasks) or "0"
    binding_values = ",".join(
        "(@owner_takeover_recovery_id,{owner},{project},{store},{site},NULL,'STORE_BINDING','PROJECT_BINDING','NONE',{version},'PENDING',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))".format(
            owner=owner, project=_text(item["projectCode"]), store=_text(item["storeCode"]),
            site=_text(item["siteCode"]), version=item["expectedAuthVersion"])
        for item in bindings)
    guards = " AND ".join((
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery WHERE id={predecessor['id']} AND identity_key={_text(identity)} AND status='COMPLETED' AND version_no={predecessor['versionNo']} AND generation_no=1 AND send_budget_epoch={predecessor['sendBudgetEpoch']} AND send_attempt_count=1 AND first_send_at IS NOT NULL AND second_send_at IS NULL AND lease_token IS NULL)=1",
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery WHERE id={source['id']} AND predecessor_recovery_id={predecessor['id']} AND identity_key={_text(identity)} AND status='WAITING_PREDECESSOR' AND scope_owner_user_id IS NULL AND version_no={source['versionNo']} AND generation_no=0 AND send_budget_epoch=0 AND send_attempt_count=0 AND first_send_at IS NULL AND second_send_at IS NULL AND lease_token IS NULL)=1",
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery WHERE identity_key={_text(identity)} AND active_identity_slot={_text(identity)})=0",
        f"(SELECT COUNT(*) FROM noon_auth_owner_scope_manifest WHERE identity_key={_text(identity)} AND status='ACTIVE')=0",
        *(_item_guard(item, source["id"]) for item in selected + settled + remaining),
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery_item WHERE recovery_id={source['id']} AND owner_user_id={owner})={len(selected) + len(settled)}",
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery_item WHERE recovery_id={source['id']} AND owner_user_id<>{owner})={len(remaining)}",
        f"(SELECT COUNT(DISTINCT project_code) FROM noon_auth_identity_recovery_item WHERE recovery_id={source['id']} AND owner_user_id<>{owner})={manifest['remainingProjectCount']}",
        f"(SELECT COUNT(*) FROM noon_project_auth_state state WHERE {project_predicates})={len(projects)}",
        f"(SELECT COUNT(*) FROM noon_project_auth_state state WHERE {settled_predicates})={len(settled_states)}",
        f"(SELECT COUNT(*) FROM noon_pull_task task JOIN noon_pull_plan plan ON plan.id=task.plan_id WHERE ({task_predicates}) AND plan.enabled=1 AND plan.paused=0)={len(tasks)}",
        f"(SELECT COUNT(*) FROM noon_http_call_log WHERE path='/_svc/mp-partner-identity/public/user/credential/generate')={manifest['providerGenerateCount']}",
        f"(SELECT COALESCE(MAX(id),0) FROM noon_http_call_log WHERE path='/_svc/mp-partner-identity/public/user/credential/generate')={manifest['providerGenerateMaxId']}",
    ))
    reason = "authorized owner-only terminal login recovery " + manifest["manifestKey"]
    statements = [
        "SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;START TRANSACTION;",
        f"SELECT id FROM noon_auth_identity_recovery WHERE id IN ({predecessor['id']},{source['id']}) ORDER BY id FOR UPDATE;",
        f"SELECT id FROM noon_auth_identity_recovery_item WHERE id IN ({all_item_ids}) ORDER BY id FOR UPDATE;",
        f"SELECT owner_user_id,project_code FROM noon_project_auth_state state WHERE {state_predicates} ORDER BY owner_user_id,project_code FOR UPDATE;",
        _signal("NOON_AUTH_OWNER_TERMINAL_DRAIN_SCOPE_DRIFT", guards),
        "INSERT INTO noon_auth_identity_recovery (predecessor_recovery_id,identity_key,status,scope_owner_user_id,generation_no,send_budget_epoch,send_attempt_count,coalesce_until,next_attempt_at,version_no,config_fingerprint,diagnostic_summary,requested_at,gmt_create,gmt_updated) "
        + f"SELECT NULL,identity_key,'COALESCING',NULL,0,0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),0,config_fingerprint,{_text(reason)},UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3) FROM noon_auth_identity_recovery WHERE id={source['id']};",
        "SET @owner_takeover_recovery_id=LAST_INSERT_ID();",
        _signal("NOON_AUTH_OWNER_TERMINAL_DRAIN_INSERT_FAILED", "@owner_takeover_recovery_id>0"),
        "INSERT INTO noon_auth_identity_recovery_item (recovery_id,owner_user_id,project_code,store_code,site_code,source_task_id,source_domain,source_checkpoint,resume_policy,expected_auth_version,status,gmt_create,gmt_updated) VALUES " + binding_values + ";",
        _signal("NOON_AUTH_OWNER_TERMINAL_DRAIN_BINDING_INSERT_FAILED", f"ROW_COUNT()={len(bindings)}"),
        "UPDATE noon_project_auth_state SET active_recovery_id=@owner_takeover_recovery_id,auth_version=auth_version+1,gmt_updated=UTC_TIMESTAMP(3) WHERE " + project_predicates + ";",
        _signal("NOON_AUTH_OWNER_TERMINAL_DRAIN_PROJECT_MOVE_CAS_FAILED", f"ROW_COUNT()={len(projects)}"),
        "UPDATE noon_auth_identity_recovery_item SET status='SKIPPED',failure_code='LEGACY_OWNER_SCOPE_REQUEUED',diagnostic_summary=" + _text(reason) + ",gmt_updated=UTC_TIMESTAMP(3) "
        + f"WHERE recovery_id={source['id']} AND id IN ({selected_ids}) AND status='PENDING';",
        _signal("NOON_AUTH_OWNER_TERMINAL_DRAIN_SELECTED_ITEM_CAS_FAILED", f"ROW_COUNT()={len(selected)}"),
    ]
    if tasks:
        statements.insert(4, f"SELECT id FROM noon_pull_task WHERE id IN ({task_ids}) ORDER BY id FOR UPDATE;")
        statements[6:6] = (
            "UPDATE noon_pull_task SET status='CANCELLED',finished_at=UTC_TIMESTAMP(3),gmt_updated=UTC_TIMESTAMP(3) "
            + f"WHERE id IN ({task_ids}) AND status='BLOCKED_AUTH' AND auth_recovery_id={source['id']};",
            _signal("NOON_AUTH_OWNER_TERMINAL_DRAIN_TASK_CANCEL_CAS_FAILED", f"ROW_COUNT()={len(tasks)}"),
        )
    if settled:
        statements.extend((
            "UPDATE noon_auth_identity_recovery_item SET status='RECOVERED',failure_code=NULL,diagnostic_summary='already healthy before terminal owner drain',recovered_at=UTC_TIMESTAMP(3),gmt_updated=UTC_TIMESTAMP(3) "
            + f"WHERE recovery_id={source['id']} AND id IN ({settled_ids}) AND status='PENDING';",
            _signal("NOON_AUTH_OWNER_TERMINAL_DRAIN_SETTLED_ITEM_CAS_FAILED", f"ROW_COUNT()={len(settled)}"),
        ))
    statements.extend(("COMMIT;SELECT @owner_takeover_recovery_id;",))
    return "\n".join(statements) + "\n"


__all__ = ["build_apply_sql"]
