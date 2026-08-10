#!/usr/bin/env python3
"""Plan/apply one explicitly authorized remaining OTP retry without resetting budget."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import stat
from pathlib import Path

from noon_auth_identity_failed_retry_artifact import load_release_provenance
from noon_auth_identity_failed_retry_sql import build_apply_sql
from schema_migrations.mysql_client import MySqlClient

LOCK_NAME = "nuono:noon-auth-identity-failed-retry"
ALLOWED_DOMAINS = frozenset({"SALES", "FINANCE_TRANSACTION", "ORDER", "NOON_ADVERTISING"})


def _canonical(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()


def _sha(value: object) -> str:
    return hashlib.sha256(_canonical(value)).hexdigest()


def _sql(recovery: int, successor: int) -> str:
    path = "/_svc/mp-partner-identity/public/user/credential/generate"
    return f"""
SELECT JSON_OBJECT(
 'recovery',JSON_OBJECT('id',r.id,'identityKey',r.identity_key,'status',r.status,
  'failureCode',r.failure_code,'versionNo',r.version_no,'generationNo',r.generation_no,
  'sendBudgetEpoch',r.send_budget_epoch,'sendAttemptCount',r.send_attempt_count,
  'firstSendAt',r.first_send_at,'secondSendAt',r.second_send_at,
  'configFingerprint',r.config_fingerprint,'leaseFree',r.lease_token IS NULL
 ),
 'successor',JSON_OBJECT('id',s.id,'identityKey',s.identity_key,'status',s.status,
  'predecessorRecoveryId',s.predecessor_recovery_id,'versionNo',s.version_no,
  'generationNo',s.generation_no,'sendAttemptCount',s.send_attempt_count,
  'firstSendAt',s.first_send_at,'secondSendAt',s.second_send_at,'leaseFree',s.lease_token IS NULL),
 'projects',(SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT('ownerUserId',state.owner_user_id,
  'projectCode',state.project_code,'authVersion',state.auth_version,'identityKey',state.identity_key)
  ORDER BY state.owner_user_id,state.project_code),JSON_ARRAY()) FROM noon_project_auth_state state
  WHERE state.active_recovery_id=r.id),
 'tasks',(SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT('itemId',i.id,'recoveryId',i.recovery_id,
  'ownerUserId',i.owner_user_id,'projectCode',i.project_code,'taskId',t.id,
  'authRecoveryId',t.auth_recovery_id,'storeCode',t.store_code,'siteCode',t.site_code,
  'domain',t.data_domain,'planId',p.id) ORDER BY t.id),JSON_ARRAY())
  FROM noon_auth_identity_recovery_item i JOIN noon_pull_task t ON t.id=i.source_task_id
  JOIN noon_pull_plan p ON p.id=t.plan_id WHERE i.recovery_id IN (r.id,s.id)),
 'ledgerCount',(SELECT COUNT(*) FROM noon_auth_identity_send_ledger l WHERE l.recovery_id=r.id),
 'incidentGenerateCount',(SELECT COUNT(*) FROM noon_http_call_log h WHERE h.path='{path}'
  AND h.occurred_at BETWEEN r.first_send_at-INTERVAL 1 MINUTE AND r.first_send_at+INTERVAL 10 MINUTE),
 'providerGenerateCount',(SELECT COUNT(*) FROM noon_http_call_log h WHERE h.path='{path}'),
 'providerGenerateMaxId',(SELECT COALESCE(MAX(id),0) FROM noon_http_call_log h WHERE h.path='{path}')
) FROM noon_auth_identity_recovery r JOIN noon_auth_identity_recovery s ON s.id={successor}
WHERE r.id={recovery};
"""


def _load_snapshot(client: MySqlClient, recovery: int, successor: int) -> dict:
    raw = client.execute_readonly(_sql(recovery, successor))
    decoder = json.JSONDecoder()
    document = raw.lstrip()
    if not document:
        raise ValueError("identity-failed retry snapshot must return exactly one document")
    value, end = decoder.raw_decode(document)
    if document[end:].strip():
        raise ValueError("identity-failed retry snapshot must return exactly one document")
    if not isinstance(value, dict):
        raise ValueError("identity-failed retry snapshot must be an object")
    return value


def build_manifest(snapshot: dict, recovery: int, successor: int, cooldown: int,
                   actor: str, provenance: dict) -> dict:
    target = snapshot["recovery"]
    follow = snapshot["successor"]
    projects = snapshot["projects"]
    tasks = snapshot["tasks"]
    if not 60 <= cooldown <= 3600 or not actor.strip():
        raise ValueError("cooldown or actor is invalid")
    if (target["id"] != recovery or target["status"] != "MANUAL_HOLD"
            or target["failureCode"] != "IDENTITY_AUTH_FAILED"
            or target["generationNo"] != 1 or target["sendAttemptCount"] != 1
            or not target["firstSendAt"] or target["secondSendAt"] is not None
            or not target["leaseFree"]):
        raise ValueError("recovery is not the exact one-remaining-send identity failure")
    if (follow["id"] != successor or follow["identityKey"] != target["identityKey"]
            or follow["status"] != "WAITING_PREDECESSOR"
            or follow["predecessorRecoveryId"] != recovery or follow["generationNo"] != 0
            or follow["sendAttemptCount"] != 0 or follow["firstSendAt"] is not None
            or follow["secondSendAt"] is not None or not follow["leaseFree"]):
        raise ValueError("successor is not the exact unsent fenced successor")
    if snapshot["ledgerCount"] != 1 or snapshot["incidentGenerateCount"] != 0:
        raise ValueError("initial intent is not proven generate-free")
    if not projects or not tasks:
        raise ValueError("recovery scope is empty")
    task_ids = [task["taskId"] for task in tasks]
    if len(task_ids) != len(set(task_ids)):
        raise ValueError("recovery scope has duplicate tasks")
    for task in tasks:
        if (task["recoveryId"] not in {recovery, successor} or task["authRecoveryId"] != task["recoveryId"]
                or task["domain"] not in ALLOWED_DOMAINS or not task["storeCode"]
                or not task["siteCode"] or task["taskId"] <= 0 or task["planId"] <= 0):
            raise ValueError("recovery scope contains an unsafe task")
    project_keys = {(item["ownerUserId"], item["projectCode"]) for item in projects}
    target_task_keys = {(item["ownerUserId"], item["projectCode"])
                        for item in tasks if item["recoveryId"] == recovery}
    if target_task_keys != project_keys:
        raise ValueError("manual-hold project scope does not match task scope")
    core = {"releaseProvenance": provenance, "recovery": target, "successor": follow,
            "projects": projects, "tasks": tasks, "planIds": sorted({item["planId"] for item in tasks}),
            "ledgerCount": snapshot["ledgerCount"], "incidentGenerateCount": snapshot["incidentGenerateCount"],
            "providerGenerateCount": snapshot["providerGenerateCount"],
            "providerGenerateMaxId": snapshot["providerGenerateMaxId"], "cooldownSeconds": cooldown}
    digest = _sha(core)
    return {"manifestVersion": 1, "manifestKey": f"recovery{recovery}-identity-failed-{digest[:12]}",
            "manifestSha256": digest, "createdBy": actor.strip(), **core}


def _write_new(path: Path, value: dict) -> None:
    descriptor = os.open(os.path.abspath(path), os.O_WRONLY | os.O_CREAT | os.O_EXCL
                         | getattr(os, "O_NOFOLLOW", 0), 0o600)
    with os.fdopen(descriptor, "wb") as output:
        output.write(_canonical(value) + b"\n")


def _load(path: Path) -> dict:
    info = path.lstat()
    if (path.is_symlink() or not stat.S_ISREG(info.st_mode) or info.st_uid != os.geteuid()
            or stat.S_IMODE(info.st_mode) != 0o600):
        raise ValueError("retry manifest must be an owner-only 0600 regular file")
    value = json.loads(path.read_text(encoding="utf-8"))
    core = {key: value[key] for key in ("releaseProvenance", "recovery", "successor", "projects",
            "tasks", "planIds", "ledgerCount", "incidentGenerateCount", "providerGenerateCount",
            "providerGenerateMaxId", "cooldownSeconds")}
    if _sha(core) != value.get("manifestSha256"):
        raise ValueError("retry manifest checksum mismatch")
    expected_key = (f"recovery{value['recovery']['id']}-identity-failed-"
                    f"{value['manifestSha256'][:12]}")
    if value.get("manifestVersion") != 1 or value.get("manifestKey") != expected_key:
        raise ValueError("retry manifest identity mismatch")
    return value


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("plan", "apply"))
    for name in ("defaults-file", "schema", "host", "actor", "release-manifest", "release-manifest-sha256"):
        parser.add_argument(f"--{name}", required=True)
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--recovery", type=int)
    parser.add_argument("--successor", type=int)
    parser.add_argument("--cooldown-seconds", type=int, default=60)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--manifest", type=Path)
    args = parser.parse_args()
    provenance = load_release_provenance(Path(args.release_manifest), args.release_manifest_sha256)
    client = MySqlClient(Path(args.defaults_file), expected_schema=args.schema,
                         expected_host=args.host, expected_port=args.port)
    try:
        client.acquire_lock(LOCK_NAME, 0)
        if args.action == "plan":
            if not args.recovery or not args.successor or not args.output:
                raise ValueError("plan requires recovery, successor and output")
            value = build_manifest(_load_snapshot(client, args.recovery, args.successor), args.recovery,
                                   args.successor, args.cooldown_seconds, args.actor, provenance)
            _write_new(args.output, value)
            result = {"result": "PLANNED", "manifest": str(args.output), "manifestSha256": value["manifestSha256"]}
        else:
            if not args.manifest:
                raise ValueError("apply requires manifest")
            value = _load(args.manifest)
            current = build_manifest(_load_snapshot(client, value["recovery"]["id"], value["successor"]["id"]),
                                     value["recovery"]["id"], value["successor"]["id"],
                                     value["cooldownSeconds"], value["createdBy"], provenance)
            if current["manifestSha256"] != value["manifestSha256"]:
                raise ValueError("production state changed after manifest freeze")
            client.execute(build_apply_sql(value, args.actor))
            result = {"result": "APPLIED", "recoveryId": value["recovery"]["id"]}
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
        return 0
    finally:
        client.release_lock(LOCK_NAME)
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
