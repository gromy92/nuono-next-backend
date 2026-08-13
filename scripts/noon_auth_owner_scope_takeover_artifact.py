"""Verify the frozen operation bundle for an owner-only historical auth takeover."""
from __future__ import annotations

import hashlib
import json
import os
import re
import stat
from pathlib import Path, PurePosixPath

OPERATION_NAME = "noon_auth_owner_scope_takeover"
ENTRYPOINT = "scripts/noon_auth_owner_scope_takeover.py"
EXPECTED_FILES = frozenset({
    ENTRYPOINT,
    "scripts/noon_auth_owner_scope_takeover_artifact.py",
    "scripts/noon_auth_owner_scope_takeover_sql.py",
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
    info = path.lstat()
    if path.is_symlink() or not stat.S_ISREG(info.st_mode) or info.st_uid != os.geteuid():
        raise ReleaseArtifactError("release artifact file must be owner-controlled")
    return path.resolve()


def load_release_provenance(path: Path, expected_sha256: str) -> dict:
    manifest = _regular(Path(path))
    if not re.fullmatch(r"[0-9a-f]{64}", expected_sha256) or _sha256(manifest) != expected_sha256:
        raise ReleaseArtifactError("release manifest SHA-256 mismatch")
    value = json.loads(manifest.read_text(encoding="utf-8"))
    if (value.get("schema_version") != 1 or value.get("component") != "backend"
            or value.get("ref") != "refs/heads/master" or value.get("event") != "push"
            or value.get("deployable") is not True
            or not re.fullmatch(r"[0-9a-f]{40}", str(value.get("commit", "")))):
        raise ReleaseArtifactError("release manifest is not a deployable master artifact")
    bundles = [item for item in value.get("operation_bundles", [])
               if isinstance(item, dict) and item.get("name") == OPERATION_NAME]
    if len(bundles) != 1 or bundles[0].get("entrypoint") != ENTRYPOINT:
        raise ReleaseArtifactError("release manifest has no unique owner takeover bundle")
    bundle = bundles[0]
    files = bundle.get("files")
    if (bundle.get("schema_version") != 1 or not isinstance(files, list)
            or {item.get("path") for item in files if isinstance(item, dict)} != EXPECTED_FILES):
        raise ReleaseArtifactError("release takeover bundle file set differs")
    for item in files:
        relative = PurePosixPath(item["path"])
        if relative.is_absolute() or any(part in {"", ".", ".."} for part in relative.parts):
            raise ReleaseArtifactError("release takeover bundle path is unsafe")
        frozen = _regular(manifest.parent.joinpath(*relative.parts))
        if frozen.stat().st_size != item.get("size") or _sha256(frozen) != item.get("sha256"):
            raise ReleaseArtifactError("release takeover bundle bytes differ")
    ordered = sorted(files, key=lambda item: item["path"])
    digest = hashlib.sha256(json.dumps(ordered, ensure_ascii=False, sort_keys=True,
                                       separators=(",", ":")).encode()).hexdigest()
    if files != ordered or bundle.get("sha256") != digest:
        raise ReleaseArtifactError("release takeover bundle identity differs")
    return {"manifestSha256": expected_sha256, "commit": value["commit"],
            "runId": value["run_id"], "artifactName": value["artifact_name"],
            "operationBundleSha256": digest}


__all__ = ["ReleaseArtifactError", "load_release_provenance"]
