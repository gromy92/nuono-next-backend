"""SQL builders for the governed Noon owner-scoped recovery split."""
from __future__ import annotations

import json
from collections import defaultdict


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
        f"SET @dp251_guard_sql=IF(({predicate}),'DO 0',",
        f"  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT=''{name}''');",
        "PREPARE dp251_guard_stmt FROM @dp251_guard_sql;",
        "EXECUTE dp251_guard_stmt;",
        "DEALLOCATE PREPARE dp251_guard_stmt;",
    ))


def _project_versions(manifest: dict) -> dict[str, int]:
    versions: dict[str, set[int]] = defaultdict(set)
    for item in manifest["items"]:
        versions[item["projectCode"]].add(_number(item["expectedAuthVersion"]))
    if not versions or any(len(values) != 1 for values in versions.values()):
        raise ValueError("every frozen project must have one auth version")
    return {project: next(iter(values)) for project, values in versions.items()}


def _validate(manifest: dict) -> None:
    for key in ("manifestKey", "manifestSha256", "preStateSha256", "identityKey"):
        _text(manifest[key])
    _number(manifest["ownerUserId"])
    for recovery_key in ("predecessor", "source"):
        _number(manifest[recovery_key]["id"])
        _number(manifest[recovery_key]["versionNo"])
    if not manifest.get("items"):
        raise ValueError("owner-scoped manifest cannot be empty")
    all_items = manifest["items"] + manifest.get("remainingItems", [])
    if len({item["id"] for item in all_items}) != len(all_items):
        raise ValueError("owner-scoped manifest contains duplicate item ids")
    if len(manifest.get("remainingItems", [])) != manifest["source"]["remainingItemCount"]:
        raise ValueError("non-scope item count does not match its frozen source")
    _project_versions(manifest)


def _item_exact(item: dict) -> str:
    fields = (
        f"item.id={_number(item['id'])}",
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


def build_apply_sql(manifest: dict, actor: str) -> str:
    _validate(manifest)
    actor_sql = _text(actor)
    owner = _number(manifest["ownerUserId"])
    predecessor = manifest["predecessor"]
    source = manifest["source"]
    items = manifest["items"]
    remaining_items = manifest.get("remainingItems", [])
    projects = _project_versions(manifest)
    item_ids = ",".join(str(_number(item["id"])) for item in items)
    item_predicate = " OR ".join(_item_exact(item) for item in items)
    remaining_predicate = " OR ".join(_item_exact(item) for item in remaining_items) or "FALSE"
    lines = [
        "SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;",
        "START TRANSACTION;",
        f"SELECT id FROM noon_auth_identity_recovery WHERE id IN ({predecessor['id']},{source['id']}) ORDER BY id FOR UPDATE;",
        f"SELECT id FROM noon_auth_identity_recovery_item WHERE recovery_id={source['id']} ORDER BY id FOR UPDATE;",
        f"SELECT owner_user_id,project_code FROM noon_project_auth_state WHERE active_recovery_id={source['id']} ORDER BY owner_user_id,project_code FOR UPDATE;",
        _signal("DP251_RECOVERY_CAS_DRIFT", " AND ".join((
            f"(SELECT COUNT(*) FROM noon_auth_identity_recovery WHERE id={predecessor['id']} AND version_no={predecessor['versionNo']} AND status={_text(predecessor['status'])} AND identity_key={_text(manifest['identityKey'])} AND lease_token IS NULL)=1",
            f"(SELECT COUNT(*) FROM noon_auth_identity_recovery WHERE id={source['id']} AND predecessor_recovery_id={predecessor['id']} AND version_no={source['versionNo']} AND status='WAITING_PREDECESSOR' AND identity_key={_text(manifest['identityKey'])} AND scope_owner_user_id IS NULL AND generation_no={source['generationNo']} AND send_budget_epoch={source['sendBudgetEpoch']} AND send_attempt_count={source['sendAttemptCount']} AND lease_token IS NULL)=1",
            f"(SELECT COUNT(*) FROM noon_auth_owner_scope_manifest WHERE identity_key={_text(manifest['identityKey'])} AND status='ACTIVE')=0",
        ))),
        _signal("DP251_ITEM_MANIFEST_DRIFT", " AND ".join((
            f"(SELECT COUNT(*) FROM noon_auth_identity_recovery_item item WHERE item.recovery_id={source['id']} AND item.owner_user_id={owner} AND ({item_predicate}))={len(items)}",
            f"(SELECT COUNT(*) FROM noon_auth_identity_recovery_item WHERE recovery_id={source['id']} AND owner_user_id={owner})={len(items)}",
            f"(SELECT COUNT(*) FROM noon_auth_identity_recovery_item WHERE recovery_id={source['id']} AND owner_user_id<>{owner})={source['remainingItemCount']}",
            f"(SELECT COUNT(DISTINCT project_code) FROM noon_auth_identity_recovery_item WHERE recovery_id={source['id']} AND owner_user_id<>{owner})={source['remainingProjectCount']}",
            f"(SELECT COUNT(*) FROM noon_auth_identity_recovery_item item WHERE item.recovery_id={source['id']} AND item.owner_user_id<>{owner} AND ({remaining_predicate}))={len(remaining_items)}",
        ))),
        "INSERT INTO noon_auth_identity_recovery (predecessor_recovery_id,identity_key,status,scope_owner_user_id,generation_no,send_budget_epoch,send_attempt_count,coalesce_until,next_attempt_at,version_no,config_fingerprint,requested_at,gmt_create,gmt_updated)",
        f"SELECT {predecessor['id']},identity_key,'WAITING_PREDECESSOR',{owner},0,0,0,coalesce_until,next_attempt_at,0,config_fingerprint,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3) FROM noon_auth_identity_recovery WHERE id={source['id']};",
        "SET @dp251_scoped_insert_count=ROW_COUNT();",
        "SET @dp251_scoped_recovery_id=LAST_INSERT_ID();",
        _signal("DP251_SCOPED_INSERT_FAILED", "@dp251_scoped_insert_count=1 AND @dp251_scoped_recovery_id>0"),
        "INSERT INTO noon_auth_owner_scope_manifest (manifest_key,owner_user_id,identity_key,predecessor_recovery_id,source_recovery_id,scoped_recovery_id,predecessor_recovery_version,source_recovery_version,source_generation_no,source_send_budget_epoch,source_send_attempt_count,source_remaining_item_count,source_remaining_project_count,source_remaining_sha256,manifest_sha256,item_count,project_count,status,created_by,gmt_create,gmt_updated) VALUES ("
        f"{_text(manifest['manifestKey'])},{owner},{_text(manifest['identityKey'])},{predecessor['id']},{source['id']},@dp251_scoped_recovery_id,{predecessor['versionNo']},{source['versionNo']},{source['generationNo']},{source['sendBudgetEpoch']},{source['sendAttemptCount']},{source['remainingItemCount']},{source['remainingProjectCount']},{_text(source['remainingSha256'])},{_text(manifest['manifestSha256'])},{len(items)},{len(projects)},'ACTIVE',{actor_sql},UTC_TIMESTAMP(3),UTC_TIMESTAMP(3));",
        "SET @dp251_manifest_id=LAST_INSERT_ID();",
    ]
    values = []
    for item, selected in [(item, 1) for item in items] + [(item, 0) for item in remaining_items]:
        values.append("(" + ",".join((
            "@dp251_manifest_id", str(item["id"]), str(selected), str(item.get("ownerUserId", owner)), _text(item["projectCode"]),
            _nullable(item.get("storeCode")), _nullable(item.get("siteCode")),
            "NULL" if item.get("sourceTaskId") is None else str(item["sourceTaskId"]),
            _nullable(item.get("sourceDomain")), _nullable(item.get("sourceCheckpoint")),
            _text(item["resumePolicy"]), str(item["expectedAuthVersion"]), _text(item["status"]),
            _text(item["itemSha256"]), "UTC_TIMESTAMP(3)",
        )) + ")")
    lines.extend((
        "INSERT INTO noon_auth_owner_scope_manifest_item (manifest_id,source_item_id,selected_for_scope,owner_user_id,project_code,store_code,site_code,source_task_id,source_domain,source_checkpoint,resume_policy,expected_auth_version,item_status,item_sha256,gmt_create) VALUES\n  " + ",\n  ".join(values) + ";",
        _signal("DP251_MANIFEST_ITEM_INSERT_FAILED", f"ROW_COUNT()={len(items) + len(remaining_items)}"),
        f"UPDATE noon_auth_identity_recovery_item SET recovery_id=@dp251_scoped_recovery_id,gmt_updated=UTC_TIMESTAMP(3) WHERE recovery_id={source['id']} AND owner_user_id={owner} AND id IN ({item_ids});",
        _signal("DP251_ITEM_MOVE_CAS_FAILED", f"ROW_COUNT()={len(items)}"),
    ))
    for project, version in sorted(projects.items()):
        lines.extend((
            f"UPDATE noon_project_auth_state SET active_recovery_id=@dp251_scoped_recovery_id,gmt_updated=UTC_TIMESTAMP(3) WHERE owner_user_id={owner} AND BINARY project_code=BINARY {_text(project)} AND active_recovery_id={source['id']} AND auth_version={version} AND status IN ('REAUTH_REQUIRED','RECOVERING','MANUAL_HOLD');",
            _signal("DP251_PROJECT_MOVE_CAS_FAILED", "ROW_COUNT()=1"),
        ))
    details = json.dumps({"manifestKey": manifest["manifestKey"], "operation": "SPLIT"}, separators=(",", ":"))
    lines.extend((
        "INSERT INTO noon_auth_owner_scope_audit (manifest_id,action,actor,before_state_sha256,after_state_sha256,details_json,gmt_create) VALUES ("
        f"@dp251_manifest_id,'SPLIT',{actor_sql},{_text(manifest['preStateSha256'])},{_text(manifest['manifestSha256'])},CAST({_text(details)} AS JSON),UTC_TIMESTAMP(3));",
        "COMMIT;",
        "SELECT @dp251_manifest_id,@dp251_scoped_recovery_id;",
    ))
    return "\n".join(lines) + "\n"


def build_rollback_sql(manifest: dict, actor: str) -> str:
    _validate(manifest)
    owner = manifest["ownerUserId"]
    source_id = manifest["source"]["id"]
    projects = _project_versions(manifest)
    actor_sql = _text(actor)
    lines = [
        "SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;",
        "START TRANSACTION;",
        f"SELECT id,scoped_recovery_id FROM noon_auth_owner_scope_manifest WHERE manifest_key={_text(manifest['manifestKey'])} FOR UPDATE;",
        f"SET @dp251_manifest_id=(SELECT id FROM noon_auth_owner_scope_manifest WHERE manifest_key={_text(manifest['manifestKey'])} AND status='ACTIVE');",
        "SET @dp251_scoped_recovery_id=(SELECT scoped_recovery_id FROM noon_auth_owner_scope_manifest WHERE id=@dp251_manifest_id);",
        _signal("DP251_ROLLBACK_NOT_SAFE", " AND ".join((
            "@dp251_manifest_id IS NOT NULL",
            "(SELECT COUNT(*) FROM noon_auth_identity_recovery WHERE id=@dp251_scoped_recovery_id AND status='WAITING_PREDECESSOR' AND generation_no=0 AND send_attempt_count=0 AND first_send_at IS NULL AND second_send_at IS NULL AND lease_token IS NULL)=1",
            "(SELECT COUNT(*) FROM noon_auth_identity_send_ledger WHERE recovery_id=@dp251_scoped_recovery_id)=0",
        ))),
        f"UPDATE noon_auth_identity_recovery_item SET recovery_id={source_id},gmt_updated=UTC_TIMESTAMP(3) WHERE recovery_id=@dp251_scoped_recovery_id AND owner_user_id={owner};",
        _signal("DP251_ROLLBACK_ITEM_CAS_FAILED", f"ROW_COUNT()={len(manifest['items'])}"),
    ]
    for project, version in sorted(projects.items()):
        lines.extend((
            f"UPDATE noon_project_auth_state SET active_recovery_id={source_id},gmt_updated=UTC_TIMESTAMP(3) WHERE owner_user_id={owner} AND BINARY project_code=BINARY {_text(project)} AND active_recovery_id=@dp251_scoped_recovery_id AND auth_version={version};",
            _signal("DP251_ROLLBACK_PROJECT_CAS_FAILED", "ROW_COUNT()=1"),
        ))
    details = json.dumps({"manifestKey": manifest["manifestKey"], "operation": "ROLLBACK"}, separators=(",", ":"))
    lines.extend((
        "UPDATE noon_auth_identity_recovery SET status='CANCELLED',failure_code='OWNER_SCOPE_ROLLED_BACK',completed_at=UTC_TIMESTAMP(3),version_no=version_no+1,gmt_updated=UTC_TIMESTAMP(3) WHERE id=@dp251_scoped_recovery_id AND status='WAITING_PREDECESSOR' AND send_attempt_count=0 AND lease_token IS NULL;",
        _signal("DP251_ROLLBACK_RECOVERY_CAS_FAILED", "ROW_COUNT()=1"),
        "UPDATE noon_auth_owner_scope_manifest SET status='ROLLED_BACK',status_reason='governed pre-send rollback',version_no=version_no+1,gmt_updated=UTC_TIMESTAMP(3) WHERE id=@dp251_manifest_id AND status='ACTIVE';",
        _signal("DP251_ROLLBACK_MANIFEST_CAS_FAILED", "ROW_COUNT()=1"),
        "INSERT INTO noon_auth_owner_scope_audit (manifest_id,action,actor,before_state_sha256,after_state_sha256,details_json,gmt_create) VALUES ("
        f"@dp251_manifest_id,'ROLLBACK',{actor_sql},{_text(manifest['manifestSha256'])},{_text(manifest['preStateSha256'])},CAST({_text(details)} AS JSON),UTC_TIMESTAMP(3));",
        "COMMIT;",
    ))
    return "\n".join(lines) + "\n"


__all__ = ["build_apply_sql", "build_rollback_sql"]
