#!/usr/bin/env python3
"""Plan, apply, or roll back an exact owner-scoped Noon auth successor."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import stat
from pathlib import Path

from noon_auth_owner_scope_split_sql import build_apply_sql, build_rollback_sql
from schema_migrations.mysql_client import MySqlClient

LOCK_NAME = "nuono:noon-auth-owner-scope-split"


def _canonical(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()


def _sha(value: object) -> str:
    return hashlib.sha256(_canonical(value)).hexdigest()


def _item_payload(item: dict) -> dict:
    keys = (
        "id", "ownerUserId", "projectCode", "storeCode", "siteCode", "sourceTaskId",
        "sourceDomain", "sourceCheckpoint", "resumePolicy", "expectedAuthVersion", "status",
    )
    return {key: item.get(key) for key in keys}


def build_manifest(snapshot: dict, owner: int, projects: tuple[str, ...], actor: str) -> dict:
    if not isinstance(owner, int) or owner <= 0 or not actor.strip():
        raise ValueError("owner and actor are required")
    predecessor = snapshot["predecessor"]
    source = snapshot["source"]
    identity = snapshot["identityKey"]
    if predecessor["id"] != source["predecessorRecoveryId"]:
        raise ValueError("source predecessor drifted")
    if predecessor["status"] not in {"MANUAL_HOLD", "COALESCING", "WAITING_COOLDOWN"}:
        raise ValueError("predecessor is not a safely paused active recovery")
    if source["status"] != "WAITING_PREDECESSOR":
        raise ValueError("source recovery is not waiting for its predecessor")
    if predecessor.get("identityKey", identity) != identity or source.get("identityKey", identity) != identity:
        raise ValueError("recovery identity drifted")
    if not predecessor.get("leaseFree", True) or not source.get("leaseFree", True):
        raise ValueError("recovery lease is active")
    if snapshot.get("activeManifestCount", 0) != 0:
        raise ValueError("an owner-scoped manifest is already active")
    manifest_sequence = int(snapshot.get("manifestSequence", 0)) + 1
    items = sorted((_item_payload(item) for item in snapshot["items"]), key=lambda item: item["id"])
    selected = [item for item in items if item["ownerUserId"] == owner]
    remaining = [item for item in items if item["ownerUserId"] != owner]
    requested = tuple(sorted(set(project.strip() for project in projects if project.strip())))
    actual = tuple(sorted({item["projectCode"] for item in selected}))
    if not selected or requested != actual:
        raise ValueError("requested projects must equal the complete owner scope in the source recovery")
    states = {
        (state["ownerUserId"], state["projectCode"]): state
        for state in snapshot["projectStates"]
    }
    for item in selected:
        state = states.get((owner, item["projectCode"]))
        if (state is None or state["activeRecoveryId"] != source["id"]
                or state["authVersion"] != item["expectedAuthVersion"]):
            raise ValueError("owner project state does not match the source recovery manifest")
        item["itemSha256"] = _sha(item)
    for item in remaining:
        item["itemSha256"] = _sha(item)
    source_frozen = dict(source)
    source_frozen.update({
        "remainingItemCount": len(remaining),
        "remainingProjectCount": len({item["projectCode"] for item in remaining}),
        "remainingSha256": _sha(remaining),
    })
    normalized_snapshot = dict(snapshot)
    normalized_snapshot["items"] = items
    normalized_snapshot["projectStates"] = sorted(
        snapshot["projectStates"], key=lambda state: (state["ownerUserId"], state["projectCode"])
    )
    pre_state_sha = _sha(normalized_snapshot)
    core = {
        "ownerUserId": owner,
        "identityKey": identity,
        "predecessor": predecessor,
        "source": source_frozen,
        "items": selected,
        "remainingItems": remaining,
    }
    manifest_sha = _sha(core)
    return {
        "manifestVersion": 1,
        "manifestKey": f"owner{owner}-predecessor{predecessor['id']}-source{source['id']}-m{manifest_sequence}-{manifest_sha[:12]}",
        "manifestSha256": manifest_sha,
        "preStateSha256": pre_state_sha,
        "createdBy": actor.strip(),
        **core,
    }


def _snapshot_sql(predecessor: int, source: int) -> str:
    return f"""
SELECT JSON_OBJECT(
  'identityKey',source.identity_key,
  'activeManifestCount',(SELECT COUNT(*) FROM noon_auth_owner_scope_manifest WHERE identity_key=source.identity_key AND status='ACTIVE'),
  'manifestSequence',(SELECT COUNT(*) FROM noon_auth_owner_scope_manifest WHERE identity_key=source.identity_key),
  'predecessor',JSON_OBJECT('id',predecessor.id,'identityKey',predecessor.identity_key,
    'versionNo',predecessor.version_no,'status',predecessor.status,
    'leaseFree',predecessor.lease_token IS NULL AND (predecessor.lease_until IS NULL OR predecessor.lease_until<=UTC_TIMESTAMP(3))),
  'source',JSON_OBJECT('id',source.id,'predecessorRecoveryId',source.predecessor_recovery_id,
    'identityKey',source.identity_key,'versionNo',source.version_no,'status',source.status,
    'generationNo',source.generation_no,'sendBudgetEpoch',source.send_budget_epoch,
    'sendAttemptCount',source.send_attempt_count,'configFingerprint',source.config_fingerprint,
    'leaseFree',source.lease_token IS NULL AND (source.lease_until IS NULL OR source.lease_until<=UTC_TIMESTAMP(3))),
  'items',(SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT(
    'id',ordered.id,'ownerUserId',ordered.owner_user_id,'projectCode',ordered.project_code,
    'storeCode',ordered.store_code,'siteCode',ordered.site_code,'sourceTaskId',ordered.source_task_id,
    'sourceDomain',ordered.source_domain,'sourceCheckpoint',ordered.source_checkpoint,
    'resumePolicy',ordered.resume_policy,'expectedAuthVersion',ordered.expected_auth_version,
    'status',ordered.status)),JSON_ARRAY()) FROM
      (SELECT * FROM noon_auth_identity_recovery_item WHERE recovery_id={source} ORDER BY id) ordered),
  'projectStates',(SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT(
    'ownerUserId',ordered.owner_user_id,'projectCode',ordered.project_code,
    'activeRecoveryId',ordered.active_recovery_id,'authVersion',ordered.auth_version)),JSON_ARRAY()) FROM
      (SELECT state.* FROM noon_project_auth_state state
       JOIN noon_auth_identity_recovery_item item ON item.owner_user_id=state.owner_user_id
        AND BINARY item.project_code=BINARY state.project_code AND item.recovery_id={source}
       GROUP BY state.owner_user_id,state.project_code ORDER BY state.owner_user_id,state.project_code) ordered)
) FROM noon_auth_identity_recovery source
JOIN noon_auth_identity_recovery predecessor ON predecessor.id={predecessor}
WHERE source.id={source} AND source.predecessor_recovery_id=predecessor.id;
"""


def load_snapshot(client: MySqlClient, predecessor: int, source: int) -> dict:
    raw = client.execute_readonly(_snapshot_sql(predecessor, source)).strip()
    if not raw or "\n" in raw:
        raise ValueError("owner-scope snapshot did not return exactly one JSON document")
    return json.loads(raw)


def _write_new(path: Path, payload: dict) -> None:
    path = Path(os.path.abspath(os.fspath(path)))
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0), 0o600)
    with os.fdopen(descriptor, "wb") as output:
        output.write(_canonical(payload) + b"\n")


def _load_manifest(path: Path) -> dict:
    metadata = path.lstat()
    if (not stat.S_ISREG(metadata.st_mode) or stat.S_ISLNK(metadata.st_mode)
            or metadata.st_uid != os.geteuid() or stat.S_IMODE(metadata.st_mode) != 0o600):
        raise ValueError("manifest must be an owner-only 0600 regular file")
    manifest = json.loads(path.read_text(encoding="utf-8"))
    rebuilt = dict(manifest)
    frozen_hash = rebuilt.pop("manifestSha256")
    rebuilt.pop("manifestVersion", None)
    rebuilt.pop("manifestKey", None)
    rebuilt.pop("preStateSha256", None)
    rebuilt.pop("createdBy", None)
    if _sha(rebuilt) != frozen_hash:
        raise ValueError("manifest checksum does not match its immutable payload")
    return manifest


def _client(args: argparse.Namespace) -> MySqlClient:
    return MySqlClient(Path(args.defaults_file), expected_schema=args.schema,
                       expected_host=args.host, expected_port=args.port)


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("plan", "apply", "rollback"))
    parser.add_argument("--defaults-file", required=True)
    parser.add_argument("--schema", required=True)
    parser.add_argument("--host", required=True)
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--actor", required=True)
    parser.add_argument("--predecessor", type=int)
    parser.add_argument("--source", type=int)
    parser.add_argument("--owner", type=int)
    parser.add_argument("--project", action="append", default=[])
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--output", type=Path)
    return parser


def main() -> int:
    args = _parser().parse_args()
    client = _client(args)
    try:
        client.acquire_lock(LOCK_NAME, 0)
        if args.action == "plan":
            if None in (args.predecessor, args.source, args.owner) or not args.output:
                raise ValueError("plan requires predecessor, source, owner, projects, and output")
            manifest = build_manifest(load_snapshot(client, args.predecessor, args.source),
                                      args.owner, tuple(args.project), args.actor)
            _write_new(args.output, manifest)
            result = {"result": "PLANNED", "manifest": str(args.output),
                      "manifestSha256": manifest["manifestSha256"],
                      "itemCount": len(manifest["items"])}
        else:
            if not args.manifest:
                raise ValueError("apply and rollback require --manifest")
            manifest = _load_manifest(args.manifest)
            if args.action == "apply":
                current = build_manifest(
                    load_snapshot(client, manifest["predecessor"]["id"], manifest["source"]["id"]),
                    manifest["ownerUserId"], tuple(item["projectCode"] for item in manifest["items"]),
                    manifest["createdBy"],
                )
                if current["preStateSha256"] != manifest["preStateSha256"]:
                    raise ValueError("production state changed after manifest freeze")
                output = client.execute(build_apply_sql(manifest, args.actor)).strip().splitlines()
                result = {"result": "APPLIED", "databaseResult": output[-1] if output else None}
            else:
                client.execute(build_rollback_sql(manifest, args.actor))
                result = {"result": "ROLLED_BACK"}
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
        return 0
    finally:
        client.release_lock(LOCK_NAME)
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
