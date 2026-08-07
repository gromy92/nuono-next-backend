"""Verify the frozen release bundle that owns the manual-hold retry bytes."""
from __future__ import annotations

import hashlib
import json
import os
import re
import stat
from pathlib import Path, PurePosixPath

OPERATION_NAME = "noon_auth_manual_hold_retry"
ENTRYPOINT = "scripts/noon_auth_manual_hold_retry.py"
EXPECTED_FILES = frozenset({
    ENTRYPOINT,
    "scripts/noon_auth_manual_hold_retry_artifact.py",
    "scripts/noon_auth_manual_hold_retry_sql.py",
    "scripts/schema_migrations/__init__.py",
    "scripts/schema_migrations/model.py",
    "scripts/schema_migrations/mysql_client.py",
    "scripts/schema_migrations/mysql_support.py",
})


class ReleaseArtifactError(RuntimeError):
    pass


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _regular(path: Path) -> Path:
    path = Path(path)
    if path.is_symlink():
        raise ReleaseArtifactError("release artifact file must not be a symlink")
    info = path.stat()
    if not stat.S_ISREG(info.st_mode) or info.st_uid != os.geteuid():
        raise ReleaseArtifactError("release artifact file must be owner-controlled")
    return path.resolve()


def _canonical(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True,
                      separators=(",", ":")).encode()


def load_release_provenance(path: Path, expected_sha256: str) -> dict:
    manifest_path = _regular(path)
    if not re.fullmatch(r"[0-9a-f]{64}", expected_sha256):
        raise ReleaseArtifactError("release manifest SHA-256 is invalid")
    if _sha256(manifest_path) != expected_sha256:
        raise ReleaseArtifactError("release manifest SHA-256 mismatch")
    value = json.loads(manifest_path.read_text(encoding="utf-8"))
    if (value.get("schema_version") != 1 or value.get("component") != "backend"
            or value.get("ref") != "refs/heads/master" or value.get("event") != "push"
            or value.get("deployable") is not True
            or not re.fullmatch(r"[0-9a-f]{40}", str(value.get("commit", "")))):
        raise ReleaseArtifactError("release manifest is not a deployable master artifact")
    matches = [bundle for bundle in value.get("operation_bundles", [])
               if isinstance(bundle, dict) and bundle.get("name") == OPERATION_NAME]
    if len(matches) != 1 or matches[0].get("entrypoint") != ENTRYPOINT:
        raise ReleaseArtifactError("release manifest has no unique retry bundle")
    bundle = matches[0]
    descriptors = bundle.get("files")
    if (bundle.get("schema_version") != 1 or not isinstance(descriptors, list)
            or {item.get("path") for item in descriptors if isinstance(item, dict)} != EXPECTED_FILES):
        raise ReleaseArtifactError("release retry bundle file set differs")
    root = manifest_path.parent
    for item in descriptors:
        relative = PurePosixPath(item["path"])
        if relative.is_absolute() or any(part in {"", ".", ".."} for part in relative.parts):
            raise ReleaseArtifactError("release retry bundle path is unsafe")
        frozen = _regular(root.joinpath(*relative.parts))
        if frozen.stat().st_size != item.get("size") or _sha256(frozen) != item.get("sha256"):
            raise ReleaseArtifactError("release retry bundle bytes differ")
    ordered = sorted(descriptors, key=lambda item: item["path"])
    digest = hashlib.sha256(_canonical(ordered)).hexdigest()
    if ordered != descriptors or digest != bundle.get("sha256"):
        raise ReleaseArtifactError("release retry bundle identity differs")
    return {
        "manifestSha256": expected_sha256,
        "commit": value["commit"],
        "runId": value["run_id"],
        "artifactName": value["artifact_name"],
        "operationBundleSha256": digest,
    }


__all__ = ["load_release_provenance", "ReleaseArtifactError"]
