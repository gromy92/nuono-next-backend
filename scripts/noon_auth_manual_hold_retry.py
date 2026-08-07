#!/usr/bin/env python3
"""Plan/apply one exact, audited Noon manual-hold binding retry."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import stat
from pathlib import Path

from noon_auth_manual_hold_retry_artifact import load_release_provenance
from noon_auth_manual_hold_retry_sql import build_apply_sql
from schema_migrations.mysql_client import MySqlClient

LOCK_NAME = "nuono:noon-auth-manual-hold-retry"


def _canonical(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()


def _sha(value: object) -> str:
    return hashlib.sha256(_canonical(value)).hexdigest()


def build_manifest(snapshot: dict, recovery: int, owner: int, project: str,
                   cooldown_seconds: int, actor: str, provenance: dict) -> dict:
    target = snapshot["recovery"]
    item = snapshot["item"]
    state = snapshot["projectState"]
    scope = snapshot["ownerScope"]
    if recovery <= 0 or owner <= 0 or not project.strip() or not actor.strip():
        raise ValueError("recovery, owner, project and actor are required")
    if not 60 <= cooldown_seconds <= 3600:
        raise ValueError("cooldown must preserve the governed 60-3600 second range")
    if (target["id"] != recovery or target["status"] != "MANUAL_HOLD"
            or target["failureCode"] != "SEND_RESULT_UNKNOWN"
            or target["sendAttemptCount"] != 2 or target["generationNo"] != 2
            or not target["leaseFree"]):
        raise ValueError("recovery is not the exact exhausted unknown-send manual hold")
    if (item["recoveryId"] != recovery or item["ownerUserId"] != owner
            or item["projectCode"] != project or item["sourceTaskId"] is not None
            or item["status"] != "PENDING"):
        raise ValueError("manual-hold source-less item scope drifted")
    if (state["ownerUserId"] != owner or state["projectCode"] != project
            or state["activeRecoveryId"] != recovery or state["status"] != "MANUAL_HOLD"
            or state["authVersion"] != item["expectedAuthVersion"]):
        raise ValueError("manual-hold project fence drifted")
    if (scope["status"] != "ACTIVE" or scope["ownerUserId"] != owner
            or scope["predecessorRecoveryId"] != recovery
            or scope["scopedRecoveryStatus"] != "WAITING_PREDECESSOR"
            or scope["scopedSendAttemptCount"] != 0):
        raise ValueError("owner-scoped successor fence is not active and send-free")
    if snapshot["ledgerCount"] != 2 or snapshot["incidentGenerateCount"] != 0:
        raise ValueError("old two intents are not proven provider-generate-free")
    expected_after = {
        "recoveryId": recovery, "status": "WAITING_COOLDOWN",
        "sendBudgetEpoch": target["sendBudgetEpoch"] + 1, "generationNo": 0,
        "sendAttemptCount": 0, "versionNo": target["versionNo"] + 1,
        "projectStatus": "REAUTH_REQUIRED", "authVersion": state["authVersion"] + 1,
        "itemStatus": "PENDING", "itemExpectedAuthVersion": state["authVersion"] + 1,
    }
    core = {
        "releaseProvenance": provenance,
        "recovery": target,
        "item": item,
        "projectState": state,
        "ownerScope": scope,
        "ledgerCount": snapshot["ledgerCount"],
        "ledgerFirstAt": snapshot["ledgerFirstAt"],
        "ledgerLastAt": snapshot["ledgerLastAt"],
        "incidentGenerateCount": snapshot["incidentGenerateCount"],
        "providerGenerateCount": snapshot["providerGenerateCount"],
        "providerGenerateMaxId": snapshot["providerGenerateMaxId"],
        "providerGenerateLastAt": snapshot["providerGenerateLastAt"],
        "cooldownSeconds": cooldown_seconds,
        "expectedAfter": expected_after,
    }
    digest = _sha(core)
    return {
        "manifestVersion": 1,
        "manifestKey": f"recovery{recovery}-owner{owner}-{project}-retry-{digest[:12]}",
        "manifestSha256": digest,
        "createdBy": actor.strip(),
        **core,
    }


def _snapshot_sql(recovery: int, owner: int, project: str) -> str:
    project_sql = project.replace("'", "''")
    path = "/_svc/mp-partner-identity/public/user/credential/generate"
    return f"""
SELECT JSON_OBJECT(
 'recovery',JSON_OBJECT('id',r.id,'identityKey',r.identity_key,'status',r.status,
  'failureCode',r.failure_code,'versionNo',r.version_no,'generationNo',r.generation_no,
  'sendBudgetEpoch',r.send_budget_epoch,'sendAttemptCount',r.send_attempt_count,
  'firstSendAt',r.first_send_at,'secondSendAt',r.second_send_at,
  'configFingerprint',r.config_fingerprint,
  'leaseFree',r.lease_token IS NULL AND (r.lease_until IS NULL OR r.lease_until<=UTC_TIMESTAMP(3))),
 'item',JSON_OBJECT('id',i.id,'recoveryId',i.recovery_id,'ownerUserId',i.owner_user_id,
  'projectCode',i.project_code,'sourceTaskId',i.source_task_id,'status',i.status,
  'expectedAuthVersion',i.expected_auth_version),
 'projectState',JSON_OBJECT('ownerUserId',s.owner_user_id,'projectCode',s.project_code,
  'status',s.status,'activeRecoveryId',s.active_recovery_id,'authVersion',s.auth_version,
  'identityKey',s.identity_key,'bindingFingerprint',s.binding_fingerprint,
  'configFingerprint',s.config_fingerprint),
 'ownerScope',(SELECT JSON_OBJECT('id',m.id,'status',m.status,'ownerUserId',m.owner_user_id,
  'predecessorRecoveryId',m.predecessor_recovery_id,'sourceRecoveryId',m.source_recovery_id,
  'scopedRecoveryId',m.scoped_recovery_id,'scopedRecoveryStatus',sr.status,
  'scopedSendAttemptCount',sr.send_attempt_count) FROM noon_auth_owner_scope_manifest m
  JOIN noon_auth_identity_recovery sr ON sr.id=m.scoped_recovery_id
  WHERE m.predecessor_recovery_id=r.id AND m.status='ACTIVE'),
 'ledgerCount',(SELECT COUNT(*) FROM noon_auth_identity_send_ledger l WHERE l.recovery_id=r.id),
 'ledgerFirstAt',(SELECT MIN(send_intent_at) FROM noon_auth_identity_send_ledger l WHERE l.recovery_id=r.id),
 'ledgerLastAt',(SELECT MAX(send_intent_at) FROM noon_auth_identity_send_ledger l WHERE l.recovery_id=r.id),
 'incidentGenerateCount',(SELECT COUNT(*) FROM noon_http_call_log h WHERE h.path='{path}'
  AND h.occurred_at BETWEEN r.first_send_at-INTERVAL 1 MINUTE AND r.second_send_at+INTERVAL 10 MINUTE),
 'providerGenerateCount',(SELECT COUNT(*) FROM noon_http_call_log h WHERE h.path='{path}'),
 'providerGenerateMaxId',(SELECT COALESCE(MAX(id),0) FROM noon_http_call_log h WHERE h.path='{path}'),
 'providerGenerateLastAt',(SELECT MAX(occurred_at) FROM noon_http_call_log h WHERE h.path='{path}')
) FROM noon_auth_identity_recovery r
JOIN noon_auth_identity_recovery_item i ON i.recovery_id=r.id AND i.owner_user_id={owner}
 AND BINARY i.project_code=BINARY '{project_sql}' AND i.source_task_id IS NULL
JOIN noon_project_auth_state s ON s.owner_user_id=i.owner_user_id
 AND BINARY s.project_code=BINARY i.project_code
WHERE r.id={recovery};
"""


def load_snapshot(client: MySqlClient, recovery: int, owner: int, project: str) -> dict:
    raw = client.execute_readonly(_snapshot_sql(recovery, owner, project)).strip()
    if not raw or "\n" in raw:
        raise ValueError("manual-hold retry snapshot must return exactly one document")
    return json.loads(raw)


def _write_new(path: Path, payload: dict) -> None:
    descriptor = os.open(os.path.abspath(path), os.O_WRONLY | os.O_CREAT | os.O_EXCL
                         | getattr(os, "O_NOFOLLOW", 0), 0o600)
    with os.fdopen(descriptor, "wb") as output:
        output.write(_canonical(payload) + b"\n")


def _load(path: Path) -> dict:
    metadata = path.lstat()
    if (not stat.S_ISREG(metadata.st_mode) or stat.S_ISLNK(metadata.st_mode)
            or metadata.st_uid != os.geteuid() or stat.S_IMODE(metadata.st_mode) != 0o600):
        raise ValueError("retry manifest must be an owner-only 0600 regular file")
    value = json.loads(path.read_text(encoding="utf-8"))
    core = {key: value[key] for key in (
        "releaseProvenance", "recovery", "item", "projectState", "ownerScope",
        "ledgerCount", "ledgerFirstAt",
        "ledgerLastAt", "incidentGenerateCount", "providerGenerateCount",
        "providerGenerateMaxId", "providerGenerateLastAt", "cooldownSeconds", "expectedAfter")}
    if _sha(core) != value["manifestSha256"]:
        raise ValueError("retry manifest checksum mismatch")
    return value


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("plan", "apply"))
    for name in ("defaults-file", "schema", "host", "actor"):
        parser.add_argument(f"--{name}", required=True)
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--recovery", type=int)
    parser.add_argument("--owner", type=int)
    parser.add_argument("--project")
    parser.add_argument("--cooldown-seconds", type=int, default=60)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--release-manifest", type=Path, required=True)
    parser.add_argument("--release-manifest-sha256", required=True)
    args = parser.parse_args()
    provenance = load_release_provenance(
        args.release_manifest, args.release_manifest_sha256
    )
    client = MySqlClient(Path(args.defaults_file), expected_schema=args.schema,
                         expected_host=args.host, expected_port=args.port)
    try:
        client.acquire_lock(LOCK_NAME, 0)
        if args.action == "plan":
            if None in (args.recovery, args.owner) or not args.project or not args.output:
                raise ValueError("plan requires recovery, owner, project and output")
            value = build_manifest(load_snapshot(client, args.recovery, args.owner, args.project),
                                   args.recovery, args.owner, args.project,
                                   args.cooldown_seconds, args.actor, provenance)
            _write_new(args.output, value)
            result = {"result": "PLANNED", "manifest": str(args.output),
                      "manifestSha256": value["manifestSha256"]}
        else:
            if not args.manifest:
                raise ValueError("apply requires --manifest")
            value = _load(args.manifest)
            current = build_manifest(load_snapshot(client, value["recovery"]["id"],
                                                     value["item"]["ownerUserId"],
                                                     value["item"]["projectCode"]),
                                     value["recovery"]["id"], value["item"]["ownerUserId"],
                                     value["item"]["projectCode"], value["cooldownSeconds"],
                                     value["createdBy"], provenance)
            if current["manifestSha256"] != value["manifestSha256"]:
                raise ValueError("production state changed after retry manifest freeze")
            client.execute(build_apply_sql(value, args.actor))
            result = {"result": "APPLIED", "recoveryId": value["recovery"]["id"]}
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
        return 0
    finally:
        client.release_lock(LOCK_NAME)
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
