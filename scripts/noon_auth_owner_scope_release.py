#!/usr/bin/env python3
"""Plan or apply release of a completed owner-scoped Noon auth fence."""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
import stat
from pathlib import Path

from noon_auth_owner_scope_release_artifact import load_release_provenance
from noon_auth_owner_scope_release_sql import build_release_sql
from schema_migrations.mysql_client import MySqlClient

LOCK_NAME = "nuono:noon-auth-owner-scope-release"


def _canonical(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True,
                      separators=(",", ":")).encode()


def _sha(value: object) -> str:
    return hashlib.sha256(_canonical(value)).hexdigest()


def _snapshot_sql(manifest_id: int, source_id: int, scoped_id: int) -> str:
    return f"""
SELECT JSON_OBJECT(
  'activeManifestCount',(SELECT COUNT(*) FROM noon_auth_owner_scope_manifest active
    WHERE active.identity_key=manifest.identity_key AND active.status='ACTIVE'),
  'activeRecoveryCount',(SELECT COUNT(*) FROM noon_auth_identity_recovery active
    WHERE active.identity_key=manifest.identity_key AND active.active_identity_slot IS NOT NULL),
  'releaseAuditCount',(SELECT COUNT(*) FROM noon_auth_owner_scope_audit audit
    WHERE audit.manifest_id=manifest.id AND audit.action='SCOPE_RELEASED'),
  'manifestSelectedItemCount',(SELECT COUNT(*) FROM noon_auth_owner_scope_manifest_item frozen
    WHERE frozen.manifest_id=manifest.id AND frozen.selected_for_scope=b'1'),
  'manifestRemainingItemCount',(SELECT COUNT(*) FROM noon_auth_owner_scope_manifest_item frozen
    WHERE frozen.manifest_id=manifest.id AND frozen.selected_for_scope=b'0'),
  'manifestItemDriftCount',(SELECT COUNT(*) FROM noon_auth_owner_scope_manifest_item frozen
    LEFT JOIN noon_auth_identity_recovery_item item ON item.id=frozen.source_item_id
      AND item.recovery_id=IF(frozen.selected_for_scope=b'1',{scoped_id},{source_id})
    WHERE frozen.manifest_id=manifest.id AND (item.id IS NULL
      OR item.owner_user_id<>frozen.owner_user_id
      OR BINARY item.project_code<>BINARY frozen.project_code
      OR NOT(item.store_code<=>frozen.store_code) OR NOT(item.site_code<=>frozen.site_code)
      OR NOT(item.source_task_id<=>frozen.source_task_id)
      OR NOT(item.source_domain<=>frozen.source_domain)
      OR NOT(item.source_checkpoint<=>frozen.source_checkpoint)
      OR item.resume_policy<>frozen.resume_policy
      OR item.expected_auth_version<>frozen.expected_auth_version
      OR (frozen.selected_for_scope=b'0' AND item.status<>frozen.item_status)
      OR (frozen.selected_for_scope=b'1' AND item.status NOT IN ('RECOVERED','STALE')))),
  'sourceSendLedgerCount',(SELECT COUNT(*) FROM noon_auth_identity_send_ledger ledger
    WHERE ledger.recovery_id=source.id),
  'manifest',JSON_OBJECT('id',manifest.id,'ownerUserId',manifest.owner_user_id,
    'identityKey',manifest.identity_key,'predecessorRecoveryId',manifest.predecessor_recovery_id,
    'sourceRecoveryId',manifest.source_recovery_id,'scopedRecoveryId',manifest.scoped_recovery_id,
    'manifestSha256',manifest.manifest_sha256,'itemCount',manifest.item_count,
    'projectCount',manifest.project_count,'sourceRemainingItemCount',manifest.source_remaining_item_count,
    'sourceRemainingProjectCount',manifest.source_remaining_project_count,
    'status',manifest.status,'versionNo',manifest.version_no),
  'predecessor',JSON_OBJECT('id',predecessor.id,'identityKey',predecessor.identity_key,
    'status',predecessor.status,'versionNo',predecessor.version_no,
    'leaseFree',predecessor.lease_token IS NULL AND
      (predecessor.lease_until IS NULL OR predecessor.lease_until<=UTC_TIMESTAMP(3))),
  'source',JSON_OBJECT('id',source.id,'predecessorRecoveryId',source.predecessor_recovery_id,
    'identityKey',source.identity_key,'status',source.status,'versionNo',source.version_no,
    'scopeOwnerUserId',source.scope_owner_user_id,'generationNo',source.generation_no,
    'sendBudgetEpoch',source.send_budget_epoch,'sendAttemptCount',source.send_attempt_count,
    'leaseFree',source.lease_token IS NULL AND
      (source.lease_until IS NULL OR source.lease_until<=UTC_TIMESTAMP(3))),
  'scoped',JSON_OBJECT('id',scoped.id,'predecessorRecoveryId',scoped.predecessor_recovery_id,
    'identityKey',scoped.identity_key,'status',scoped.status,'versionNo',scoped.version_no,
    'scopeOwnerUserId',scoped.scope_owner_user_id,'sendAttemptCount',scoped.send_attempt_count,
    'leaseFree',scoped.lease_token IS NULL AND
      (scoped.lease_until IS NULL OR scoped.lease_until<=UTC_TIMESTAMP(3))),
  'sourceItems',(SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT(
    'id',ordered.id,'ownerUserId',ordered.owner_user_id,'projectCode',ordered.project_code,
    'storeCode',ordered.store_code,'siteCode',ordered.site_code,'sourceTaskId',ordered.source_task_id,
    'sourceDomain',ordered.source_domain,'sourceCheckpoint',ordered.source_checkpoint,
    'resumePolicy',ordered.resume_policy,'expectedAuthVersion',ordered.expected_auth_version,
    'status',ordered.status)),JSON_ARRAY()) FROM
      (SELECT * FROM noon_auth_identity_recovery_item WHERE recovery_id={source_id} ORDER BY id) ordered),
  'scopedItems',(SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT(
    'id',ordered.id,'ownerUserId',ordered.owner_user_id,'projectCode',ordered.project_code,
    'storeCode',ordered.store_code,'siteCode',ordered.site_code,'sourceTaskId',ordered.source_task_id,
    'sourceDomain',ordered.source_domain,'sourceCheckpoint',ordered.source_checkpoint,
    'resumePolicy',ordered.resume_policy,'expectedAuthVersion',ordered.expected_auth_version,
    'status',ordered.status)),JSON_ARRAY()) FROM
      (SELECT * FROM noon_auth_identity_recovery_item WHERE recovery_id={scoped_id} ORDER BY id) ordered),
  'sourceProjectStates',(SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT(
    'ownerUserId',ordered.owner_user_id,'projectCode',ordered.project_code,'status',ordered.status,
    'activeRecoveryId',ordered.active_recovery_id,'authVersion',ordered.auth_version)),JSON_ARRAY()) FROM
      (SELECT state.owner_user_id,state.project_code,state.status,state.active_recovery_id,state.auth_version
       FROM noon_project_auth_state state JOIN
        (SELECT DISTINCT owner_user_id,project_code FROM noon_auth_identity_recovery_item
          WHERE recovery_id={source_id}) project
       ON project.owner_user_id=state.owner_user_id AND BINARY project.project_code=BINARY state.project_code
       ORDER BY state.owner_user_id,state.project_code) ordered),
  'scopedProjectStates',(SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT(
    'ownerUserId',ordered.owner_user_id,'projectCode',ordered.project_code,'status',ordered.status,
    'activeRecoveryId',ordered.active_recovery_id,'authVersion',ordered.auth_version)),JSON_ARRAY()) FROM
      (SELECT state.owner_user_id,state.project_code,state.status,state.active_recovery_id,state.auth_version
       FROM noon_project_auth_state state JOIN
        (SELECT DISTINCT owner_user_id,project_code FROM noon_auth_identity_recovery_item
          WHERE recovery_id={scoped_id}) project
       ON project.owner_user_id=state.owner_user_id AND BINARY project.project_code=BINARY state.project_code
       ORDER BY state.owner_user_id,state.project_code) ordered)
) FROM noon_auth_owner_scope_manifest manifest
JOIN noon_auth_identity_recovery predecessor ON predecessor.id=manifest.predecessor_recovery_id
JOIN noon_auth_identity_recovery source ON source.id=manifest.source_recovery_id
JOIN noon_auth_identity_recovery scoped ON scoped.id=manifest.scoped_recovery_id
WHERE manifest.id={manifest_id};
"""


def load_snapshot(client: MySqlClient, manifest_id: int) -> dict:
    identity_raw = client.execute_readonly(
        "SELECT JSON_OBJECT('sourceRecoveryId',source_recovery_id,"
        "'scopedRecoveryId',scoped_recovery_id) FROM noon_auth_owner_scope_manifest "
        f"WHERE id={manifest_id};"
    ).strip()
    if not identity_raw or "\n" in identity_raw:
        raise ValueError("owner-scope release identity did not return exactly one JSON document")
    identity = json.loads(identity_raw)
    raw = client.execute_readonly(_snapshot_sql(
        manifest_id, int(identity["sourceRecoveryId"]), int(identity["scopedRecoveryId"])
    )).strip()
    if not raw or "\n" in raw:
        raise ValueError("owner-scope release snapshot did not return exactly one JSON document")
    return json.loads(raw)


def build_manifest(snapshot: dict, actor: str, release_provenance: dict) -> dict:
    if not actor.strip():
        raise ValueError("actor is required")
    pre_state_sha = _sha(snapshot)
    core = {"snapshot": snapshot, "releaseProvenance": release_provenance,
            "createdBy": actor.strip()}
    manifest_sha = _sha(core)
    result = {
        "schemaVersion": 1,
        "manifestKey": f"owner-scope-release-m{snapshot['manifest']['id']}-v{snapshot['manifest']['versionNo']}-{manifest_sha[:12]}",
        "manifestSha256": manifest_sha,
        "preStateSha256": pre_state_sha,
        **core,
    }
    after = copy.deepcopy(snapshot)
    after["manifest"]["status"] = "RELEASED"
    after["manifest"]["versionNo"] += 1
    after["releaseAuditCount"] += 1
    result["afterStateSha256"] = _sha(after)
    build_release_sql(result, actor)
    return result


def _write_new(path: Path, payload: dict) -> None:
    path = Path(os.path.abspath(os.fspath(path)))
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL
                         | getattr(os, "O_NOFOLLOW", 0), 0o600)
    with os.fdopen(descriptor, "wb") as output:
        output.write(_canonical(payload) + b"\n")


def _load_manifest(path: Path) -> dict:
    metadata = path.lstat()
    if (not stat.S_ISREG(metadata.st_mode) or stat.S_ISLNK(metadata.st_mode)
            or metadata.st_uid != os.geteuid() or stat.S_IMODE(metadata.st_mode) != 0o600):
        raise ValueError("manifest must be an owner-only 0600 regular file")
    manifest = json.loads(path.read_text(encoding="utf-8"))
    core = {"snapshot": manifest["snapshot"],
            "releaseProvenance": manifest["releaseProvenance"],
            "createdBy": manifest["createdBy"]}
    if _sha(core) != manifest.get("manifestSha256"):
        raise ValueError("manifest checksum does not match its immutable payload")
    if _sha(manifest["snapshot"]) != manifest.get("preStateSha256"):
        raise ValueError("manifest pre-state checksum differs")
    rebuilt = build_manifest(manifest["snapshot"], manifest["createdBy"],
                             manifest["releaseProvenance"])
    for key in ("manifestKey", "manifestSha256", "preStateSha256", "afterStateSha256"):
        if rebuilt[key] != manifest.get(key):
            raise ValueError("manifest derived identity differs")
    build_release_sql(manifest, manifest["createdBy"])
    return manifest


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("plan", "apply"))
    parser.add_argument("--defaults-file", required=True)
    parser.add_argument("--schema", required=True)
    parser.add_argument("--host", required=True)
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--actor", required=True)
    parser.add_argument("--release-manifest", type=Path, required=True)
    parser.add_argument("--release-manifest-sha256", required=True)
    parser.add_argument("--manifest-id", type=int)
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--output", type=Path)
    return parser


def main() -> int:
    args = _parser().parse_args()
    provenance = load_release_provenance(
        args.release_manifest, args.release_manifest_sha256
    )
    client = MySqlClient(Path(args.defaults_file), expected_schema=args.schema,
                         expected_host=args.host, expected_port=args.port)
    try:
        client.acquire_lock(LOCK_NAME, 0)
        if args.action == "plan":
            if args.manifest_id is None or not args.output:
                raise ValueError("plan requires manifest id and output")
            manifest = build_manifest(load_snapshot(client, args.manifest_id),
                                      args.actor, provenance)
            _write_new(args.output, manifest)
            result = {"result": "PLANNED", "manifest": str(args.output),
                      "manifestSha256": manifest["manifestSha256"],
                      "sourceRecoveryId": manifest["snapshot"]["source"]["id"]}
        else:
            if not args.manifest:
                raise ValueError("apply requires --manifest")
            manifest = _load_manifest(args.manifest)
            if manifest["releaseProvenance"] != provenance:
                raise ValueError("release provenance differs from the frozen plan")
            current = load_snapshot(client, manifest["snapshot"]["manifest"]["id"])
            if _sha(current) != manifest["preStateSha256"]:
                raise ValueError("production state changed after manifest freeze")
            output = client.execute(build_release_sql(manifest, args.actor)).strip().splitlines()
            result = {"result": "APPLIED", "databaseResult": output[-1] if output else None}
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
        return 0
    finally:
        client.release_lock(LOCK_NAME)
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
