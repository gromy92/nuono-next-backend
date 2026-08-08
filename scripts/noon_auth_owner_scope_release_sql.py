"""SQL builder for releasing a completed Noon owner-scoped recovery fence."""
from __future__ import annotations

import json


def _text(value: object) -> str:
    if not isinstance(value, str) or not value or "\x00" in value:
        raise ValueError("expected non-empty manifest text")
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def _nullable(value: object) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, int):
        return str(_number(value))
    return _text(value)


def _number(value: object) -> int:
    if not isinstance(value, int) or value < 0:
        raise ValueError("expected non-negative manifest number")
    return value


def _signal(name: str, predicate: str) -> str:
    return "\n".join((
        f"SET @scope_release_guard_sql=IF(({predicate}),'DO 0',",
        f"  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT=''{name}''');",
        "PREPARE scope_release_guard_stmt FROM @scope_release_guard_sql;",
        "EXECUTE scope_release_guard_stmt;",
        "DEALLOCATE PREPARE scope_release_guard_stmt;",
    ))


def _item_exact(item: dict, recovery_id: int) -> str:
    fields = (
        f"item.id={_number(item['id'])}",
        f"item.recovery_id={recovery_id}",
        f"item.owner_user_id={_number(item['ownerUserId'])}",
        f"BINARY item.project_code=BINARY {_text(item['projectCode'])}",
        f"item.store_code<=>{_nullable(item.get('storeCode'))}",
        f"item.site_code<=>{_nullable(item.get('siteCode'))}",
        f"item.source_task_id<=>{_nullable(item.get('sourceTaskId'))}",
        f"item.source_domain<=>{_nullable(item.get('sourceDomain'))}",
        f"item.source_checkpoint<=>{_nullable(item.get('sourceCheckpoint'))}",
        f"item.resume_policy={_text(item['resumePolicy'])}",
        f"item.expected_auth_version={_number(item['expectedAuthVersion'])}",
        f"item.status={_text(item['status'])}",
    )
    return "(" + " AND ".join(fields) + ")"


def _state_exact(state: dict) -> str:
    fields = (
        f"state.owner_user_id={_number(state['ownerUserId'])}",
        f"BINARY state.project_code=BINARY {_text(state['projectCode'])}",
        f"state.status={_text(state['status'])}",
        f"state.active_recovery_id<=>{_nullable(state.get('activeRecoveryId'))}",
        f"state.auth_version={_number(state['authVersion'])}",
    )
    return "(" + " AND ".join(fields) + ")"


def _validate(manifest: dict) -> None:
    if manifest.get("schemaVersion") != 1:
        raise ValueError("unsupported owner-scope release manifest")
    for key in ("manifestKey", "manifestSha256", "preStateSha256", "afterStateSha256"):
        _text(manifest[key])
    snapshot = manifest["snapshot"]
    scope = snapshot["manifest"]
    predecessor = snapshot["predecessor"]
    source = snapshot["source"]
    scoped = snapshot["scoped"]
    if (scope["predecessorRecoveryId"] != predecessor["id"]
            or scope["sourceRecoveryId"] != source["id"]
            or scope["scopedRecoveryId"] != scoped["id"]):
        raise ValueError("owner-scope recovery ids drifted")
    if (scope["status"] != "ACTIVE" or predecessor["status"] != "COMPLETED"
            or source["status"] != "WAITING_PREDECESSOR" or scoped["status"] != "COMPLETED"):
        raise ValueError("owner-scope recoveries are not ready for release")
    if (source["sendAttemptCount"] != 0 or source["generationNo"] != 0
            or snapshot["sourceSendLedgerCount"] != 0):
        raise ValueError("source recovery is no longer send-free")
    if (not predecessor["leaseFree"] or not source["leaseFree"] or not scoped["leaseFree"]):
        raise ValueError("owner-scope recovery lease is active")
    if snapshot["activeRecoveryCount"] != 0 or snapshot["activeManifestCount"] != 1:
        raise ValueError("shared identity slots are not ready for release")
    if snapshot["releaseAuditCount"] != 0:
        raise ValueError("owner-scope manifest was already released")
    if (snapshot["manifestSelectedItemCount"] != scope["itemCount"]
            or snapshot["manifestRemainingItemCount"] != scope["sourceRemainingItemCount"]
            or snapshot["manifestItemDriftCount"] != 0):
        raise ValueError("owner-scope frozen item manifest drifted")
    source_items = snapshot["sourceItems"]
    scoped_items = snapshot["scopedItems"]
    if (len(source_items) != scope["sourceRemainingItemCount"]
            or len({item["projectCode"] for item in source_items}) != scope["sourceRemainingProjectCount"]
            or any(item["ownerUserId"] == scope["ownerUserId"] or item["status"] != "PENDING"
                   for item in source_items)):
        raise ValueError("source recovery items are not the frozen non-scope remainder")
    if (len(scoped_items) != scope["itemCount"]
            or len({item["projectCode"] for item in scoped_items}) != scope["projectCount"]
            or any(item["ownerUserId"] != scope["ownerUserId"]
                   or item["status"] not in {"RECOVERED", "STALE"} for item in scoped_items)):
        raise ValueError("scoped recovery did not drain its frozen items")
    if (len(snapshot["sourceProjectStates"]) != scope["sourceRemainingProjectCount"]
            or any(state["status"] != "REAUTH_REQUIRED"
                   or state["activeRecoveryId"] != source["id"]
                   for state in snapshot["sourceProjectStates"])):
        raise ValueError("source project states are not waiting on the source recovery")
    source_versions = {}
    for item in source_items:
        source_versions.setdefault(item["projectCode"], set()).add(item["expectedAuthVersion"])
    state_projects = {state["projectCode"] for state in snapshot["sourceProjectStates"]}
    if (state_projects != set(source_versions)
            or any(len(versions) != 1 for versions in source_versions.values())
            or any(state["authVersion"] != next(iter(source_versions[state["projectCode"]]))
                   for state in snapshot["sourceProjectStates"])):
        raise ValueError("source project auth versions drifted")
    if (len(snapshot["scopedProjectStates"]) != scope["projectCount"]
            or any(state["status"] != "HEALTHY" or state["activeRecoveryId"] is not None
                   for state in snapshot["scopedProjectStates"])):
        raise ValueError("scoped project states are not healthy")


def build_release_sql(manifest: dict, actor: str) -> str:
    _validate(manifest)
    if not actor.strip():
        raise ValueError("actor is required")
    snapshot = manifest["snapshot"]
    scope = snapshot["manifest"]
    predecessor = snapshot["predecessor"]
    source = snapshot["source"]
    scoped = snapshot["scoped"]
    source_items = snapshot["sourceItems"]
    scoped_items = snapshot["scopedItems"]
    source_states = snapshot["sourceProjectStates"]
    scoped_states = snapshot["scopedProjectStates"]
    all_item_ids = ",".join(str(item["id"]) for item in source_items + scoped_items)
    all_projects = source_states + scoped_states
    project_predicate = " OR ".join(_state_exact(state) for state in all_projects)
    source_item_predicate = " OR ".join(_item_exact(item, source["id"]) for item in source_items)
    scoped_item_predicate = " OR ".join(_item_exact(item, scoped["id"]) for item in scoped_items)
    reason = "governed completed owner-scope release " + manifest["manifestKey"]
    details = json.dumps({
        "manifestKey": manifest["manifestKey"],
        "operation": "SCOPE_RELEASED",
        "sourceRecoveryId": source["id"],
        "scopedRecoveryId": scoped["id"],
        "releaseProvenance": manifest["releaseProvenance"],
    }, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    recovery_guard = " AND ".join((
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery WHERE id={predecessor['id']} AND status='COMPLETED' AND version_no={predecessor['versionNo']} AND identity_key={_text(scope['identityKey'])} AND lease_token IS NULL)=1",
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery WHERE id={source['id']} AND predecessor_recovery_id={predecessor['id']} AND status='WAITING_PREDECESSOR' AND version_no={source['versionNo']} AND identity_key={_text(scope['identityKey'])} AND scope_owner_user_id IS NULL AND generation_no=0 AND send_budget_epoch={source['sendBudgetEpoch']} AND send_attempt_count=0 AND lease_token IS NULL)=1",
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery WHERE id={scoped['id']} AND predecessor_recovery_id={predecessor['id']} AND status='COMPLETED' AND version_no={scoped['versionNo']} AND identity_key={_text(scope['identityKey'])} AND scope_owner_user_id={scope['ownerUserId']} AND lease_token IS NULL)=1",
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery WHERE identity_key={_text(scope['identityKey'])} AND active_identity_slot IS NOT NULL)=0",
        f"(SELECT COUNT(*) FROM noon_auth_identity_send_ledger WHERE recovery_id={source['id']})=0",
    ))
    scope_guard = " AND ".join((
        f"(SELECT COUNT(*) FROM noon_auth_owner_scope_manifest WHERE id={scope['id']} AND status='ACTIVE' AND version_no={scope['versionNo']} AND owner_user_id={scope['ownerUserId']} AND identity_key={_text(scope['identityKey'])} AND predecessor_recovery_id={predecessor['id']} AND source_recovery_id={source['id']} AND scoped_recovery_id={scoped['id']} AND manifest_sha256={_text(scope['manifestSha256'])} AND item_count={scope['itemCount']} AND project_count={scope['projectCount']} AND source_remaining_item_count={scope['sourceRemainingItemCount']} AND source_remaining_project_count={scope['sourceRemainingProjectCount']})=1",
        f"(SELECT COUNT(*) FROM noon_auth_owner_scope_manifest WHERE identity_key={_text(scope['identityKey'])} AND status='ACTIVE')=1",
        f"(SELECT COUNT(*) FROM noon_auth_owner_scope_audit WHERE manifest_id={scope['id']} AND action='SCOPE_RELEASED')=0",
        f"(SELECT COUNT(*) FROM noon_auth_owner_scope_manifest_item WHERE manifest_id={scope['id']} AND selected_for_scope=b'1')={scope['itemCount']}",
        f"(SELECT COUNT(*) FROM noon_auth_owner_scope_manifest_item WHERE manifest_id={scope['id']} AND selected_for_scope=b'0')={scope['sourceRemainingItemCount']}",
        "NOT EXISTS (SELECT 1 FROM noon_auth_owner_scope_manifest_item frozen "
        "LEFT JOIN noon_auth_identity_recovery_item item ON item.id=frozen.source_item_id "
        f"AND item.recovery_id=IF(frozen.selected_for_scope=b'1',{scoped['id']},{source['id']}) "
        f"WHERE frozen.manifest_id={scope['id']} AND (item.id IS NULL "
        "OR item.owner_user_id<>frozen.owner_user_id "
        "OR BINARY item.project_code<>BINARY frozen.project_code "
        "OR NOT(item.store_code<=>frozen.store_code) OR NOT(item.site_code<=>frozen.site_code) "
        "OR NOT(item.source_task_id<=>frozen.source_task_id) "
        "OR NOT(item.source_domain<=>frozen.source_domain) "
        "OR NOT(item.source_checkpoint<=>frozen.source_checkpoint) "
        "OR item.resume_policy<>frozen.resume_policy "
        "OR item.expected_auth_version<>frozen.expected_auth_version "
        "OR (frozen.selected_for_scope=b'0' AND item.status<>frozen.item_status) "
        "OR (frozen.selected_for_scope=b'1' AND item.status NOT IN ('RECOVERED','STALE'))))",
    ))
    item_guard = " AND ".join((
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery_item item WHERE item.recovery_id={source['id']} AND ({source_item_predicate}))={len(source_items)}",
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery_item WHERE recovery_id={source['id']})={len(source_items)}",
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery_item item WHERE item.recovery_id={scoped['id']} AND ({scoped_item_predicate}))={len(scoped_items)}",
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery_item WHERE recovery_id={scoped['id']})={len(scoped_items)}",
        f"(SELECT COUNT(*) FROM noon_project_auth_state state WHERE ({project_predicate}))={len(all_projects)}",
    ))
    return "\n".join((
        "SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;",
        "START TRANSACTION;",
        f"SELECT id FROM noon_auth_owner_scope_manifest WHERE id={scope['id']} FOR UPDATE;",
        f"SELECT id FROM noon_auth_identity_recovery WHERE id IN ({predecessor['id']},{source['id']},{scoped['id']}) ORDER BY id FOR UPDATE;",
        f"SELECT id FROM noon_auth_identity_recovery_item WHERE id IN ({all_item_ids}) ORDER BY id FOR UPDATE;",
        f"SELECT state.owner_user_id,state.project_code FROM noon_project_auth_state state WHERE ({project_predicate}) ORDER BY state.owner_user_id,state.project_code FOR UPDATE;",
        _signal("NOON_AUTH_SCOPE_RELEASE_RECOVERY_DRIFT", recovery_guard),
        _signal("NOON_AUTH_SCOPE_RELEASE_MANIFEST_DRIFT", scope_guard),
        _signal("NOON_AUTH_SCOPE_RELEASE_ITEM_DRIFT", item_guard),
        "UPDATE noon_auth_owner_scope_manifest SET status='RELEASED',"
        f"status_reason={_text(reason)},version_no=version_no+1,gmt_updated=UTC_TIMESTAMP(3) "
        f"WHERE id={scope['id']} AND status='ACTIVE' AND version_no={scope['versionNo']};",
        _signal("NOON_AUTH_SCOPE_RELEASE_CAS_FAILED", "ROW_COUNT()=1"),
        "INSERT INTO noon_auth_owner_scope_audit "
        "(manifest_id,action,actor,before_state_sha256,after_state_sha256,details_json,gmt_create) VALUES ("
        f"{scope['id']},'SCOPE_RELEASED',{_text(actor.strip())},{_text(manifest['preStateSha256'])},"
        f"{_text(manifest['afterStateSha256'])},CAST({_text(details)} AS JSON),UTC_TIMESTAMP(3));",
        "COMMIT;",
        f"SELECT JSON_OBJECT('manifestId',{scope['id']},'manifestStatus',(SELECT status FROM noon_auth_owner_scope_manifest WHERE id={scope['id']}),'sourceRecoveryId',{source['id']},'sourceStatus',(SELECT status FROM noon_auth_identity_recovery WHERE id={source['id']}));",
    )) + "\n"


__all__ = ["build_release_sql"]
