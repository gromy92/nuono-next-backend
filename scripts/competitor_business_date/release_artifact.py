"""Verify the CI release manifest that owns the correction runner bytes."""
from __future__ import annotations

import json
import os
import re
import stat
from pathlib import Path
from typing import Any

from .bundle_identity import operation_bundle_identity
from .secure_files import sha256_file


OPERATION_NAME = "competitor_business_date_correction"
ENTRYPOINT = "scripts/competitor_business_date_correction.py"


class ReleaseArtifactError(RuntimeError):
    pass


def load_release_provenance(
    path: Path,
    expected_sha256: str,
) -> dict[str, Any]:
    source = _regular_file(path)
    if not re.fullmatch(r"[0-9a-f]{64}", expected_sha256):
        raise ReleaseArtifactError("release manifest SHA-256 is invalid")
    if sha256_file(source) != expected_sha256:
        raise ReleaseArtifactError("release manifest SHA-256 mismatch")
    try:
        value = json.loads(source.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ReleaseArtifactError("release manifest is unreadable") from error
    if not isinstance(value, dict):
        raise ReleaseArtifactError("release manifest must be an object")
    _validate_release(value)
    bundles = value.get("operation_bundles")
    matches = [
        item
        for item in bundles
        if isinstance(item, dict) and item.get("name") == OPERATION_NAME
    ]
    if len(matches) != 1:
        raise ReleaseArtifactError("release manifest has no unique correction bundle")
    bundle = matches[0]
    identity = {
        key: bundle.get(key) for key in ("schema_version", "sha256", "files")
    }
    if bundle.get("entrypoint") != ENTRYPOINT:
        raise ReleaseArtifactError("release correction entrypoint differs")
    if identity != operation_bundle_identity():
        raise ReleaseArtifactError("live correction bytes differ from frozen artifact")
    return {
        "manifest_sha256": expected_sha256,
        "repository": value["repository"],
        "commit": value["commit"],
        "workflow": value["workflow"],
        "run_id": value["run_id"],
        "run_attempt": value["run_attempt"],
        "artifact_name": value["artifact_name"],
        "operation_bundle_sha256": bundle["sha256"],
    }


def _validate_release(value: dict[str, Any]) -> None:
    if (
        value.get("schema_version") != 1
        or value.get("component") != "backend"
        or value.get("ref") != "refs/heads/master"
        or value.get("event") != "push"
        or value.get("deployable") is not True
        or not re.fullmatch(r"[0-9a-f]{40}", str(value.get("commit", "")))
        or not isinstance(value.get("repository"), str)
        or not value["repository"]
        or not isinstance(value.get("workflow"), str)
        or not value["workflow"]
        or not _positive_int(value.get("run_id"))
        or not _positive_int(value.get("run_attempt"))
        or not isinstance(value.get("artifact_name"), str)
        or not value["artifact_name"]
        or not isinstance(value.get("operation_bundles"), list)
    ):
        raise ReleaseArtifactError("release manifest is not a deployable master artifact")


def _regular_file(path: Path) -> Path:
    candidate = Path(path)
    if candidate.is_symlink():
        raise ReleaseArtifactError("release manifest must not be a symbolic link")
    try:
        info = candidate.stat()
    except FileNotFoundError as error:
        raise ReleaseArtifactError("release manifest does not exist") from error
    if not stat.S_ISREG(info.st_mode):
        raise ReleaseArtifactError("release manifest must be a regular file")
    if hasattr(os, "getuid") and info.st_uid != os.getuid():
        raise ReleaseArtifactError("release manifest must be owner-controlled")
    return candidate.resolve()


def _positive_int(value: Any) -> bool:
    return isinstance(value, int) and not isinstance(value, bool) and value > 0
