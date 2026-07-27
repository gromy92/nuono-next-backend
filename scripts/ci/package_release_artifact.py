#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
from pathlib import Path


class ArtifactError(RuntimeError):
    pass


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
    return {
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


def package_release_artifact(
    target_dir: Path,
    output_dir: Path,
    artifact_name: str,
    env: dict[str, str],
) -> Path:
    jar = select_runnable_jar(target_dir)
    output_dir.mkdir(parents=True, exist_ok=False)
    artifact_path = output_dir / "nuono-next-backend.jar"
    shutil.copyfile(jar, artifact_path)
    manifest = build_manifest(artifact_path, artifact_name, env)
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
    )
    print(manifest_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
