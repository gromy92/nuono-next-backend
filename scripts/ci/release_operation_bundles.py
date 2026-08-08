"""Freeze governed operational entrypoints into the backend release artifact."""
from __future__ import annotations

import hashlib
import json
import shutil
from pathlib import Path, PurePosixPath

COMPETITOR_OPERATION_NAME = "competitor_business_date_correction"
COMPETITOR_OPERATION_ENTRYPOINT = "scripts/competitor_business_date_correction.py"
COMPETITOR_OPERATION_MIGRATIONS = (
    "240_operations_competitor_snapshot_active_uniqueness.sql",
    "241_operations_competitor_correction_writer_fence.sql",
)
NOON_RETRY_OPERATION_NAME = "noon_auth_manual_hold_retry"
NOON_RETRY_OPERATION_ENTRYPOINT = "scripts/noon_auth_manual_hold_retry.py"
NOON_RETRY_OPERATION_FILES = (
    NOON_RETRY_OPERATION_ENTRYPOINT,
    "scripts/noon_auth_manual_hold_retry_artifact.py",
    "scripts/noon_auth_manual_hold_retry_sql.py",
    "scripts/noon_auth_retry_task_scope.py",
    "scripts/schema_migrations/__init__.py",
    "scripts/schema_migrations/model.py",
    "scripts/schema_migrations/mysql_client.py",
    "scripts/schema_migrations/mysql_support.py",
)
NOON_SCOPE_RELEASE_OPERATION_NAME = "noon_auth_owner_scope_release"
NOON_SCOPE_RELEASE_OPERATION_ENTRYPOINT = "scripts/noon_auth_owner_scope_release.py"
NOON_SCOPE_RELEASE_OPERATION_FILES = (
    NOON_SCOPE_RELEASE_OPERATION_ENTRYPOINT,
    "scripts/noon_auth_owner_scope_release_artifact.py",
    "scripts/noon_auth_owner_scope_release_sql.py",
    "scripts/schema_migrations/__init__.py",
    "scripts/schema_migrations/model.py",
    "scripts/schema_migrations/mysql_client.py",
    "scripts/schema_migrations/mysql_support.py",
)


class OperationBundleError(RuntimeError):
    pass


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _identity(root: Path, paths: list[Path]) -> dict:
    if not paths or any(not path.is_file() or path.is_symlink() for path in paths):
        raise OperationBundleError("operation bundle is incomplete")
    descriptors = sorted(({
        "path": path.relative_to(root).as_posix(),
        "sha256": _sha256(path),
        "size": path.stat().st_size,
    } for path in paths), key=lambda item: item["path"])
    canonical = json.dumps(descriptors, ensure_ascii=False, sort_keys=True,
                           separators=(",", ":")).encode()
    return {"schema_version": 1, "sha256": hashlib.sha256(canonical).hexdigest(),
            "files": descriptors}


def _freeze(root: Path, output: Path, name: str, entrypoint: str,
            paths: list[Path]) -> dict:
    identity = _identity(root, paths)
    for item in identity["files"]:
        relative = PurePosixPath(item["path"])
        if relative.is_absolute() or any(part in {"", ".", ".."} for part in relative.parts):
            raise OperationBundleError(f"unsafe operation bundle path: {relative}")
        source = root.joinpath(*relative.parts)
        destination = output.joinpath(*relative.parts)
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
        if (destination.is_symlink() or not destination.is_file()
                or destination.stat().st_size != item["size"]
                or _sha256(destination) != item["sha256"]):
            raise OperationBundleError(f"frozen operation file differs: {relative}")
    return {"name": name, "entrypoint": entrypoint, **identity}


def freeze_operation_bundles(source_root: Path, output_root: Path) -> list[dict]:
    root = source_root.resolve()
    output = output_root.resolve()
    competitor_paths = sorted((root / "scripts/competitor_business_date").glob("*.py"))
    competitor_paths.append(root / COMPETITOR_OPERATION_ENTRYPOINT)
    competitor_paths.extend(root / "src/main/resources/db/init" / name
                            for name in COMPETITOR_OPERATION_MIGRATIONS)
    retry_paths = [root / name for name in NOON_RETRY_OPERATION_FILES]
    scope_release_paths = [root / name for name in NOON_SCOPE_RELEASE_OPERATION_FILES]
    return [
        _freeze(root, output, COMPETITOR_OPERATION_NAME,
                COMPETITOR_OPERATION_ENTRYPOINT, competitor_paths),
        _freeze(root, output, NOON_RETRY_OPERATION_NAME,
                NOON_RETRY_OPERATION_ENTRYPOINT, retry_paths),
        _freeze(root, output, NOON_SCOPE_RELEASE_OPERATION_NAME,
                NOON_SCOPE_RELEASE_OPERATION_ENTRYPOINT, scope_release_paths),
    ]


__all__ = [
    "COMPETITOR_OPERATION_MIGRATIONS", "COMPETITOR_OPERATION_NAME",
    "COMPETITOR_OPERATION_ENTRYPOINT", "NOON_RETRY_OPERATION_FILES",
    "NOON_RETRY_OPERATION_NAME", "NOON_RETRY_OPERATION_ENTRYPOINT",
    "NOON_SCOPE_RELEASE_OPERATION_FILES", "NOON_SCOPE_RELEASE_OPERATION_NAME",
    "NOON_SCOPE_RELEASE_OPERATION_ENTRYPOINT",
    "OperationBundleError", "freeze_operation_bundles",
]
