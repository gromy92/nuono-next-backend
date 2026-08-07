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
    details = json.dumps({"manifestKey": manifest["manifestKey"], "recoveryId": recovery_id,
                          "operation": "MANUAL_HOLD_RETRY_REBASE",
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
        f"(SELECT COUNT(*) FROM noon_auth_identity_recovery_item WHERE id={int(item['id'])} "
        f"AND recovery_id={recovery_id} AND owner_user_id={owner} "
        f"AND BINARY project_code=BINARY {_text(item['projectCode'])} AND source_task_id IS NULL "
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
        _signal("NOON_AUTH_RETRY_SCOPE_DRIFT", guards),
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


__all__ = ["build_apply_sql"]
