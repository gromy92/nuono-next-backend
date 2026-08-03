#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
from pathlib import Path, PurePosixPath


class ArtifactError(RuntimeError):
    pass


MANAGED_MIGRATIONS = (
    "182_product_barcode_psku_identity.sql",
    "189_product_barcode_store_identity_repair.sql",
    "190_noon_shared_email_auth_recovery.sql",
    "204_product_listing_workflow_attempt_claim.sql",
    "205_product_listing_reauthentication_attempt.sql",
    "206_product_barcode_store_uniqueness.sql",
    "240_operations_competitor_snapshot_active_uniqueness.sql",
    "241_operations_competitor_correction_writer_fence.sql",
)

COMPETITOR_OPERATION_NAME = "competitor_business_date_correction"
COMPETITOR_OPERATION_ENTRYPOINT = "scripts/competitor_business_date_correction.py"
COMPETITOR_OPERATION_MIGRATIONS = (
    "240_operations_competitor_snapshot_active_uniqueness.sql",
    "241_operations_competitor_correction_writer_fence.sql",
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


def canonical_json(value: object) -> str:
    return json.dumps(
        value,
        ensure_ascii=False,
        separators=(",", ":"),
        sort_keys=True,
    )


def build_competitor_operation_identity(
    source_root: Path,
) -> dict[str, object]:
    root = source_root.resolve()
    package_dir = root / "scripts/competitor_business_date"
    paths = sorted(package_dir.glob("*.py"))
    paths.append(root / COMPETITOR_OPERATION_ENTRYPOINT)
    paths.extend(
        root / "src/main/resources/db/init" / name
        for name in COMPETITOR_OPERATION_MIGRATIONS
    )
    if not paths or any(
        not path.is_file() or path.is_symlink()
        for path in paths
    ):
        raise ArtifactError("competitor correction operation bundle is incomplete")
    descriptors = sorted(
        (
            {
                "path": path.relative_to(root).as_posix(),
                "sha256": sha256_file(path),
                "size": path.stat().st_size,
            }
            for path in paths
        ),
        key=lambda item: item["path"],
    )
    digest = hashlib.sha256(
        canonical_json(descriptors).encode("utf-8")
    ).hexdigest()
    return {
        "schema_version": 1,
        "sha256": digest,
        "files": descriptors,
    }


def freeze_competitor_operation_bundle(
    source_root: Path,
    output_root: Path,
) -> dict[str, object]:
    identity = build_competitor_operation_identity(source_root)
    for item in identity["files"]:
        relative = PurePosixPath(str(item["path"]))
        if relative.is_absolute() or any(
            part in {"", ".", ".."}
            for part in relative.parts
        ):
            raise ArtifactError(
                f"unsafe competitor operation bundle path: {relative}"
            )
        source = source_root.resolve().joinpath(*relative.parts)
        destination = output_root.resolve().joinpath(*relative.parts)
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
        if (
            destination.is_symlink()
            or not destination.is_file()
            or destination.stat().st_size != item["size"]
            or sha256_file(destination) != item["sha256"]
        ):
            raise ArtifactError(
                f"frozen competitor operation file differs: {relative}"
            )
    return {
        "name": COMPETITOR_OPERATION_NAME,
        "entrypoint": COMPETITOR_OPERATION_ENTRYPOINT,
        **identity,
    }


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
    operation_bundles: list[dict[str, object]] | None = None,
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
    if operation_bundles is not None:
        manifest["operation_bundles"] = operation_bundles
    return manifest


def package_release_artifact(
    target_dir: Path,
    output_dir: Path,
    artifact_name: str,
    env: dict[str, str],
    migration_dir: Path | None = None,
    source_root: Path | None = None,
) -> Path:
    jar = select_runnable_jar(target_dir)
    output_dir.mkdir(parents=True, exist_ok=False)
    artifact_path = output_dir / "nuono-next-backend.jar"
    shutil.copyfile(jar, artifact_path)
    operation_bundle = freeze_competitor_operation_bundle(
        source_root or Path(__file__).resolve().parents[2],
        output_dir,
    )
    manifest = build_manifest(
        artifact_path,
        artifact_name,
        env,
        migration_dir,
        [operation_bundle],
    )
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
    parser.add_argument("--source-root", default=".")
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
        Path(args.source_root).resolve(),
    )
    print(manifest_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
