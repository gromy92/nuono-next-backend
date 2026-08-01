#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from schema_migrations.catalog import (  # noqa: E402
    CATALOG_PATH,
    load_catalog,
)
from schema_migrations.artifact import runner_descriptors  # noqa: E402
from schema_migrations.core import MigrationError  # noqa: E402


class ArtifactError(RuntimeError):
    pass


MANAGED_MIGRATIONS = (
    "182_product_barcode_psku_identity.sql",
    "189_product_barcode_store_identity_repair.sql",
    "190_noon_shared_email_auth_recovery.sql",
    "204_product_listing_workflow_attempt_claim.sql",
    "205_product_listing_reauthentication_attempt.sql",
    "206_product_barcode_store_uniqueness.sql",
)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def required_env(name: str, env: dict[str, str]) -> str:
    value = env.get(name, "").strip()
    if not value:
        raise ArtifactError(f"missing required environment variable: {name}")
    return value


def select_runnable_jar(target_dir: Path) -> Path:
    candidates = sorted(
        path
        for path in target_dir.glob("nuono-next-backend-*.jar")
        if path.is_file()
        and not path.name.endswith(("-sources.jar", "-javadoc.jar"))
    )
    if len(candidates) != 1:
        rendered = ", ".join(path.name for path in candidates) or "none"
        raise ArtifactError(f"expected exactly one runnable backend Jar, found: {rendered}")
    return candidates[0]


def build_manifest(
    artifact_path: Path,
    artifact_name: str,
    env: dict[str, str],
    migration_dir: Path | None = None,
) -> dict[str, object]:
    commit = required_env("GITHUB_SHA", env)
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ArtifactError("GITHUB_SHA must be a full lowercase commit SHA")
    event = required_env("GITHUB_EVENT_NAME", env)
    ref = required_env("GITHUB_REF", env)
    repository = required_env("GITHUB_REPOSITORY", env)
    deployable = event == "push" and ref == "refs/heads/master"
    if not deployable:
        raise ArtifactError("release artifacts may only be produced by a push to master")
    manifest: dict[str, object] = {
        "schema_version": 1,
        "component": "backend",
        "repository": repository,
        "commit": commit,
        "ref": ref,
        "event": event,
        "workflow": required_env("GITHUB_WORKFLOW", env),
        "run_id": int(required_env("GITHUB_RUN_ID", env)),
        "run_attempt": int(required_env("GITHUB_RUN_ATTEMPT", env)),
        "artifact_name": artifact_name,
        "deployable": True,
        "files": [
            {
                "path": artifact_path.name,
                "sha256": sha256_file(artifact_path),
                "size": artifact_path.stat().st_size,
            }
        ],
    }
    if migration_dir is not None:
        descriptors: list[dict[str, object]] = []
        for name in MANAGED_MIGRATIONS:
            path = migration_dir / name
            if not path.is_file():
                raise ArtifactError(f"managed migration is missing: {path}")
            descriptors.append(
                {
                    "path": name,
                    "sha256": sha256_file(path),
                    "size": path.stat().st_size,
                }
            )
        manifest["migrations"] = descriptors
        resource_root = migration_dir.parents[1]
        try:
            forward_migrations = load_catalog(resource_root)
        except MigrationError as error:
            raise ArtifactError(f"invalid forward migration catalog: {error}") from error
        catalog_file = resource_root.joinpath(*CATALOG_PATH.parts)
        manifest["migration_catalog"] = {
            "path": CATALOG_PATH.as_posix(),
            "sha256": sha256_file(catalog_file),
            "size": catalog_file.stat().st_size,
        }
        manifest["forward_migrations"] = [
            {
                "order": migration.order,
                "migration_key": migration.key,
                "kind": migration.kind,
                "script_path": migration.script_path.as_posix(),
                "script_sha256": migration.checksum,
                "script_size": len(migration.script_bytes),
                "postcheck_path": migration.postcheck_path.as_posix(),
                "postcheck_sha256": migration.postcheck_checksum,
                "postcheck_size": len(migration.postcheck_bytes),
            }
            for migration in forward_migrations
        ]
        try:
            manifest["migration_runner"] = runner_descriptors(SCRIPT_DIR)
        except MigrationError as error:
            raise ArtifactError(f"invalid migration runner bundle: {error}") from error
    return manifest


def package_release_artifact(
    target_dir: Path,
    output_dir: Path,
    artifact_name: str,
    env: dict[str, str],
    migration_dir: Path | None = None,
) -> Path:
    jar = select_runnable_jar(target_dir)
    output_dir.mkdir(parents=True, exist_ok=False)
    artifact_path = output_dir / "nuono-next-backend.jar"
    shutil.copyfile(jar, artifact_path)
    manifest = build_manifest(artifact_path, artifact_name, env, migration_dir)
    manifest_path = output_dir / "release-manifest.json"
    manifest_path.write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return manifest_path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Package the deployable backend CI artifact.")
    parser.add_argument("--target-dir", default="target")
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--migration-dir", default="src/main/resources/db/init")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    env = dict(os.environ)
    artifact_name = required_env("RELEASE_ARTIFACT_NAME", env)
    manifest_path = package_release_artifact(
        Path(args.target_dir).resolve(),
        Path(args.output_dir).resolve(),
        artifact_name,
        env,
        Path(args.migration_dir).resolve(),
    )
    print(manifest_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
