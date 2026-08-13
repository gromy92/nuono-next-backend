#!/usr/bin/env python3
"""Freeze and take over one mixed historical Noon auth recovery for one owner."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import stat
from pathlib import Path

from noon_auth_owner_scope_takeover_artifact import load_release_provenance
from noon_auth_owner_scope_takeover_sql import build_apply_sql
from schema_migrations.mysql_client import MySqlClient

LOCK_NAME = "nuono:noon-auth-owner-scope-takeover"


def _canonical(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()


def _sha(value: object) -> str:
    return hashlib.sha256(_canonical(value)).hexdigest()


def _item(item: dict) -> dict:
    fields = ("id", "ownerUserId", "projectCode", "storeCode", "siteCode", "sourceTaskId",
              "sourceDomain", "sourceCheckpoint", "resumePolicy", "expectedAuthVersion", "status")
    result = {field: item.get(field) for field in fields}
    result["itemSha256"] = _sha(result)
    return result


def _snapshot_sql(predecessor: int, source: int, owner: int) -> str:
    return f"""
SELECT JSON_OBJECT(
 'identityKey',source.identity_key,
 'activeManifestCount',(SELECT COUNT(*) FROM noon_auth_owner_scope_manifest
   WHERE identity_key=source.identity_key AND status='ACTIVE'),
 'predecessor',JSON_OBJECT('id',predecessor.id,'versionNo',predecessor.version_no,
   'status',predecessor.status,'failureCode',predecessor.failure_code,
   'generationNo',predecessor.generation_no,'sendBudgetEpoch',predecessor.send_budget_epoch,
   'sendAttemptCount',predecessor.send_attempt_count,'firstSendAt',predecessor.first_send_at,
   'secondSendAt',predecessor.second_send_at,'leaseFree',predecessor.lease_token IS NULL),
 'source',JSON_OBJECT('id',source.id,'predecessorRecoveryId',source.predecessor_recovery_id,
   'versionNo',source.version_no,'status',source.status,'generationNo',source.generation_no,
   'sendBudgetEpoch',source.send_budget_epoch,'sendAttemptCount',source.send_attempt_count,
   'firstSendAt',source.first_send_at,'secondSendAt',source.second_send_at,
   'leaseFree',source.lease_token IS NULL),
 'items',(SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT('id',ordered.id,
   'ownerUserId',ordered.owner_user_id,'projectCode',ordered.project_code,
   'storeCode',ordered.store_code,'siteCode',ordered.site_code,'sourceTaskId',ordered.source_task_id,
   'sourceDomain',ordered.source_domain,'sourceCheckpoint',ordered.source_checkpoint,
   'resumePolicy',ordered.resume_policy,'expectedAuthVersion',ordered.expected_auth_version,
   'status',ordered.status)),JSON_ARRAY()) FROM
   (SELECT * FROM noon_auth_identity_recovery_item WHERE recovery_id={source} ORDER BY id) ordered),
 'projectStates',(SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT('ownerUserId',ordered.owner_user_id,
   'projectCode',ordered.project_code,'status',ordered.status,'activeRecoveryId',ordered.active_recovery_id,
   'authVersion',ordered.auth_version)),JSON_ARRAY()) FROM
   (SELECT state.owner_user_id,state.project_code,state.status,state.active_recovery_id,state.auth_version
    FROM noon_project_auth_state state JOIN (SELECT DISTINCT owner_user_id,project_code
      FROM noon_auth_identity_recovery_item WHERE recovery_id={source}) item
      ON item.owner_user_id=state.owner_user_id AND BINARY item.project_code=BINARY state.project_code
    ORDER BY state.owner_user_id,state.project_code) ordered),
 'tasks',(SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT('id',ordered.id,'planId',ordered.plan_id,
   'ownerUserId',ordered.owner_user_id,'status',ordered.status,'authRecoveryId',ordered.auth_recovery_id,
   'domain',ordered.data_domain,'pullType',ordered.pull_type,'triggerMode',ordered.trigger_mode,
   'retryAction',ordered.retry_action,'checkpointCursor',ordered.checkpoint_cursor,
   'nextResumePosition',ordered.next_resume_position,'lastSafeResponse',ordered.last_safe_response_summary,
   'processedItemCount',ordered.processed_item_count,'requestCount',ordered.request_count,
   'finishedAt',ordered.finished_at,'isDeleted',ordered.is_deleted,'planEnabled',ordered.plan_enabled,
   'planPaused',ordered.plan_paused)),JSON_ARRAY()) FROM
   (SELECT task.id,task.plan_id,task.owner_user_id,task.status,task.auth_recovery_id,task.data_domain,
    task.pull_type,task.trigger_mode,task.retry_action,task.checkpoint_cursor,task.next_resume_position,
    task.last_safe_response_summary,task.processed_item_count,task.request_count,task.finished_at,task.is_deleted,
    plan.enabled AS plan_enabled,plan.paused AS plan_paused
    FROM noon_pull_task task JOIN noon_pull_plan plan ON plan.id=task.plan_id
    WHERE task.auth_recovery_id={source} AND EXISTS (SELECT 1 FROM noon_auth_identity_recovery_item item
      WHERE item.recovery_id={source} AND item.owner_user_id={owner} AND item.source_task_id=task.id)
    ORDER BY task.id) ordered),
 'providerGenerateCount',(SELECT COUNT(*) FROM noon_http_call_log
   WHERE path='/_svc/mp-partner-identity/public/user/credential/generate'),
 'providerGenerateMaxId',(SELECT COALESCE(MAX(id),0) FROM noon_http_call_log
   WHERE path='/_svc/mp-partner-identity/public/user/credential/generate')
) FROM noon_auth_identity_recovery source JOIN noon_auth_identity_recovery predecessor
 ON predecessor.id={predecessor} WHERE source.id={source} AND source.predecessor_recovery_id=predecessor.id;
"""


def load_snapshot(client: MySqlClient, predecessor: int, source: int, owner: int) -> dict:
    raw = client.execute_readonly(_snapshot_sql(predecessor, source, owner))
    decoder = json.JSONDecoder()
    document = raw.lstrip()
    if not document:
        raise ValueError("owner takeover snapshot must return exactly one document")
    value, end = decoder.raw_decode(document)
    if document[end:].strip() or not isinstance(value, dict):
        raise ValueError("owner takeover snapshot must return exactly one document")
    return value


def build_manifest(snapshot: dict, owner: int, projects: tuple[str, ...], actor: str,
                   provenance: dict) -> dict:
    predecessor, source = snapshot["predecessor"], snapshot["source"]
    if not isinstance(owner, int) or owner <= 0 or not actor.strip():
        raise ValueError("owner and actor are required")
    if (predecessor["status"] != "MANUAL_HOLD" or predecessor["failureCode"] != "IDENTITY_AUTH_FAILED"
            or predecessor["generationNo"] != 0 or predecessor["sendAttemptCount"] != 0
            or predecessor["firstSendAt"] is not None or predecessor["secondSendAt"] is not None
            or not predecessor["leaseFree"]):
        raise ValueError("predecessor is not the exact unsent identity hold")
    if (source["status"] != "WAITING_PREDECESSOR" or source["predecessorRecoveryId"] != predecessor["id"]
            or source["generationNo"] != 0 or source["sendAttemptCount"] != 0
            or source["firstSendAt"] is not None or source["secondSendAt"] is not None
            or not source["leaseFree"] or snapshot["activeManifestCount"] != 0):
        raise ValueError("source is not the exact unsent mixed successor")
    items = sorted((_item(item) for item in snapshot["items"]), key=lambda item: item["id"])
    selected = [item for item in items if item["ownerUserId"] == owner]
    remaining = [item for item in items if item["ownerUserId"] != owner]
    requested = tuple(sorted(set(project.strip() for project in projects if project.strip())))
    if (not selected or tuple(sorted({item["projectCode"] for item in selected})) != requested
            or any(item["status"] != "PENDING" for item in items)):
        raise ValueError("requested projects must equal the complete pending owner scope")
    states = {(state["ownerUserId"], state["projectCode"]): state for state in snapshot["projectStates"]}
    selected_states = []
    for project in requested:
        state = states.get((owner, project))
        versions = {item["expectedAuthVersion"] for item in selected if item["projectCode"] == project}
        if (state is None or len(versions) != 1 or state["status"] != "REAUTH_REQUIRED"
                or state["activeRecoveryId"] != source["id"] or state["authVersion"] != next(iter(versions))):
            raise ValueError("selected project state differs from its source recovery item")
        selected_states.append(state)
    if len(states) != len({(item["ownerUserId"], item["projectCode"]) for item in items}):
        raise ValueError("source recovery has missing or duplicate project state")
    selected_task_ids = {item["sourceTaskId"] for item in selected if item["sourceTaskId"] is not None}
    if any(item["sourceTaskId"] is None and item["resumePolicy"] != "NONE" for item in selected):
        raise ValueError("source-less selected item is not a binding-only recovery")
    tasks = sorted(snapshot["tasks"], key=lambda task: task["id"])
    if {task["id"] for task in tasks} != selected_task_ids or len(selected_task_ids) != len(tasks):
        raise ValueError("task scope differs from selected owner recovery items")
    for task in tasks:
        if (task["ownerUserId"] != owner or task["status"] != "BLOCKED_AUTH"
                or task["authRecoveryId"] != source["id"] or not task["domain"]
                or task["checkpointCursor"] is not None or task["nextResumePosition"] is not None
                or task["lastSafeResponse"] is not None or task["processedItemCount"] is not None
                or task["requestCount"] is not None or task["finishedAt"] is not None
                or task["isDeleted"] != "base64:type16:AA==" or task["planEnabled"] != "base64:type16:AQ=="
                or task["planPaused"] != "base64:type16:AA=="):
            raise ValueError("selected task is not a zero-progress blocked pull")
    core = {"ownerUserId": owner, "identityKey": snapshot["identityKey"], "predecessor": predecessor,
            "source": source, "items": selected, "remainingItems": remaining,
            "projects": selected_states, "tasks": tasks, "planIds": sorted(task["planId"] for task in tasks),
            "remainingProjectCount": len({item["projectCode"] for item in remaining}),
            "remainingSha256": _sha(remaining), "providerGenerateCount": snapshot["providerGenerateCount"],
            "providerGenerateMaxId": snapshot["providerGenerateMaxId"], "releaseProvenance": provenance}
    digest = _sha(core)
    return {"manifestVersion": 1, "manifestKey": f"owner{owner}-takeover-{predecessor['id']}-{source['id']}-{digest[:12]}",
            "manifestSha256": digest, "preStateSha256": _sha(snapshot), "createdBy": actor.strip(), **core}


def _write_new(path: Path, value: dict) -> None:
    descriptor = os.open(os.path.abspath(path), os.O_WRONLY | os.O_CREAT | os.O_EXCL
                         | getattr(os, "O_NOFOLLOW", 0), 0o600)
    with os.fdopen(descriptor, "wb") as output:
        output.write(_canonical(value) + b"\n")


def _load(path: Path) -> dict:
    info = path.lstat()
    if path.is_symlink() or not stat.S_ISREG(info.st_mode) or info.st_uid != os.geteuid() or stat.S_IMODE(info.st_mode) != 0o600:
        raise ValueError("takeover manifest must be an owner-only 0600 regular file")
    value = json.loads(path.read_text(encoding="utf-8"))
    core = {key: value[key] for key in ("ownerUserId", "identityKey", "predecessor", "source", "items",
            "remainingItems", "projects", "tasks", "planIds", "remainingProjectCount", "remainingSha256",
            "providerGenerateCount", "providerGenerateMaxId", "releaseProvenance")}
    if _sha(core) != value.get("manifestSha256") or value.get("manifestVersion") != 1:
        raise ValueError("takeover manifest checksum mismatch")
    if value.get("manifestKey") != f"owner{value['ownerUserId']}-takeover-{value['predecessor']['id']}-{value['source']['id']}-{value['manifestSha256'][:12]}":
        raise ValueError("takeover manifest identity mismatch")
    return value


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("plan", "apply"))
    for name in ("defaults-file", "schema", "host", "actor", "release-manifest", "release-manifest-sha256"):
        parser.add_argument(f"--{name}", required=True)
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--predecessor", type=int)
    parser.add_argument("--source", type=int)
    parser.add_argument("--owner", type=int)
    parser.add_argument("--project", action="append", default=[])
    parser.add_argument("--output", type=Path)
    parser.add_argument("--manifest", type=Path)
    args = parser.parse_args()
    provenance = load_release_provenance(Path(args.release_manifest), args.release_manifest_sha256)
    client = MySqlClient(Path(args.defaults_file), expected_schema=args.schema, expected_host=args.host, expected_port=args.port)
    try:
        client.acquire_lock(LOCK_NAME, 0)
        if args.action == "plan":
            if None in (args.predecessor, args.source, args.owner) or not args.output:
                raise ValueError("plan requires predecessor, source, owner and output")
            value = build_manifest(load_snapshot(client, args.predecessor, args.source, args.owner), args.owner,
                                   tuple(args.project), args.actor, provenance)
            _write_new(args.output, value)
            result = {"result": "PLANNED", "manifest": str(args.output), "manifestSha256": value["manifestSha256"]}
        else:
            if not args.manifest:
                raise ValueError("apply requires manifest")
            value = _load(args.manifest)
            current = build_manifest(load_snapshot(client, value["predecessor"]["id"], value["source"]["id"], value["ownerUserId"]),
                                     value["ownerUserId"], tuple(project["projectCode"] for project in value["projects"]),
                                     value["createdBy"], provenance)
            if current["manifestSha256"] != value["manifestSha256"]:
                raise ValueError("production state changed after manifest freeze")
            output = client.execute(build_apply_sql(value, args.actor)).strip().splitlines()
            result = {"result": "APPLIED", "databaseResult": output[-1] if output else None}
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
        return 0
    finally:
        client.release_lock(LOCK_NAME)
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
