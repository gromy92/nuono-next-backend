from __future__ import annotations

import io
import json
import os
import stat
import zipfile
from pathlib import Path, PurePosixPath
from typing import Sequence

from schema_migrations.catalog import (
    CATALOG_PATH,
    JAR_RESOURCE_PREFIX,
    load_catalog_from_archive,
    sha256_bytes,
)
from schema_migrations.core import Migration, MigrationError

RUNNER_RESOURCE_ROOT = PurePosixPath("release/schema-migrations")
MAX_STAGED_JAR_BYTES = 512 * 1024 * 1024
RUNNER_RELATIVE_PATHS = (
    PurePosixPath("release_schema_migrations.py"),
    PurePosixPath("schema_migrations/__init__.py"),
    PurePosixPath("schema_migrations/artifact.py"),
    PurePosixPath("schema_migrations/catalog.py"),
    PurePosixPath("schema_migrations/core.py"),
    PurePosixPath("schema_migrations/model.py"),
    PurePosixPath("schema_migrations/mysql_client.py"),
    PurePosixPath("schema_migrations/mysql_database.py"),
    PurePosixPath("schema_migrations/mysql_history.py"),
    PurePosixPath("schema_migrations/mysql_history_sql.py"),
    PurePosixPath("schema_migrations/mysql_support.py"),
    PurePosixPath("schema_migrations/runner.py"),
    PurePosixPath("schema_migrations/state.py"),
)


def runner_descriptors(runner_root: Path) -> list[dict[str, object]]:
    descriptors = []
    for relative in RUNNER_RELATIVE_PATHS:
        path = runner_root.joinpath(*relative.parts)
        if not path.is_file():
            raise MigrationError(f"migration runner source is missing: {path}")
        content = path.read_bytes()
        descriptors.append(
            {
                "path": (RUNNER_RESOURCE_ROOT / relative).as_posix(),
                "sha256": sha256_bytes(content),
                "size": len(content),
            }
        )
    return descriptors


def verify_release_inputs(
    manifest_path: Path,
    staged_jar: Path,
    expected_commit: str,
    runner_root: Path,
) -> tuple[Migration, ...]:
    manifest = _load_manifest(manifest_path)
    _expect(manifest, "schema_version", 1)
    _expect(manifest, "component", "backend")
    _expect(manifest, "repository", "gromy92/nuono-next-backend")
    _expect(manifest, "commit", expected_commit)
    _expect(manifest, "event", "push")
    _expect(manifest, "ref", "refs/heads/master")
    _expect(manifest, "deployable", True)
    jar_bytes = _read_frozen_jar(staged_jar)
    _verify_jar_descriptor(manifest, staged_jar.name, jar_bytes)
    with _open_frozen_jar(staged_jar, jar_bytes) as archive:
        migrations = load_catalog_from_archive(archive)
        _verify_catalog_descriptor(manifest, archive)
        _verify_forward_descriptors(manifest, migrations)
        _verify_runner(manifest, archive, runner_root)
    return migrations


def verify_governed_jar(
    staged_jar: Path,
    expected_jar_sha256: str,
    runner_root: Path,
) -> tuple[Migration, ...]:
    jar_bytes = _read_frozen_jar(staged_jar)
    if expected_jar_sha256 != sha256_bytes(jar_bytes):
        raise MigrationError("staged Jar checksum does not match governed cutover")
    with _open_frozen_jar(staged_jar, jar_bytes) as archive:
        migrations = load_catalog_from_archive(archive)
        _verify_runner_entries(
            archive,
            runner_root,
            runner_descriptors(runner_root),
        )
    return migrations


def _load_manifest(path: Path) -> dict[str, object]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise MigrationError(f"cannot read release manifest: {path}") from error
    if not isinstance(value, dict):
        raise MigrationError("release manifest must be a JSON object")
    return value


def _verify_jar_descriptor(
    manifest: dict[str, object],
    staged_jar_name: str,
    jar_bytes: bytes,
) -> None:
    files = manifest.get("files")
    if not isinstance(files, list) or len(files) != 1:
        raise MigrationError("release manifest must bind exactly one Jar")
    descriptor = files[0]
    if not isinstance(descriptor, dict):
        raise MigrationError("release Jar descriptor must be an object")
    if descriptor.get("path") != staged_jar_name:
        raise MigrationError("staged Jar name does not match release manifest")
    if descriptor.get("sha256") != sha256_bytes(jar_bytes):
        raise MigrationError("staged Jar checksum does not match release manifest")
    if descriptor.get("size") != len(jar_bytes):
        raise MigrationError("staged Jar size does not match release manifest")


def _verify_catalog_descriptor(
    manifest: dict[str, object],
    archive: zipfile.ZipFile,
) -> None:
    descriptor = manifest.get("migration_catalog")
    if not isinstance(descriptor, dict):
        raise MigrationError("release manifest migration catalog is missing")
    path = CATALOG_PATH.as_posix()
    if descriptor.get("path") != path:
        raise MigrationError("release manifest migration catalog path is invalid")
    content = _read_entry(archive, CATALOG_PATH)
    if descriptor.get("sha256") != sha256_bytes(content):
        raise MigrationError("migration catalog checksum does not match manifest")
    if descriptor.get("size") != len(content):
        raise MigrationError("migration catalog size does not match manifest")


def _verify_forward_descriptors(
    manifest: dict[str, object],
    migrations: Sequence[Migration],
) -> None:
    descriptors = manifest.get("forward_migrations")
    if not isinstance(descriptors, list) or len(descriptors) != len(migrations):
        raise MigrationError("release manifest forward migration count is invalid")
    for migration, descriptor in zip(migrations, descriptors):
        expected = {
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
        if descriptor != expected:
            raise MigrationError(
                f"{migration.key}: release manifest descriptor mismatch"
            )


def _verify_runner(
    manifest: dict[str, object],
    archive: zipfile.ZipFile,
    runner_root: Path,
) -> None:
    expected = runner_descriptors(runner_root)
    if manifest.get("migration_runner") != expected:
        raise MigrationError("release manifest migration runner descriptor mismatch")
    _verify_runner_entries(archive, runner_root, expected)


def _verify_runner_entries(
    archive: zipfile.ZipFile,
    runner_root: Path,
    descriptors: Sequence[dict[str, object]],
) -> None:
    names = archive.namelist()
    for relative, descriptor in zip(RUNNER_RELATIVE_PATHS, descriptors):
        resource = RUNNER_RESOURCE_ROOT / relative
        entry = JAR_RESOURCE_PREFIX + resource.as_posix()
        if names.count(entry) != 1:
            raise MigrationError(f"staged Jar must contain exactly one {entry}")
        packaged = archive.read(entry)
        source = runner_root.joinpath(*relative.parts).read_bytes()
        if packaged != source:
            raise MigrationError(f"staged Jar migration runner differs: {relative}")
        if (
            descriptor.get("sha256") != sha256_bytes(packaged)
            or descriptor.get("size") != len(packaged)
        ):
            raise MigrationError(f"migration runner descriptor differs: {relative}")


def _read_entry(archive: zipfile.ZipFile, path: PurePosixPath) -> bytes:
    entry = JAR_RESOURCE_PREFIX + path.as_posix()
    if archive.namelist().count(entry) != 1:
        raise MigrationError(f"staged Jar must contain exactly one {entry}")
    return archive.read(entry)


def _read_frozen_jar(path: Path) -> bytes:
    try:
        descriptor = os.open(
            path,
            os.O_RDONLY
            | getattr(os, "O_CLOEXEC", 0)
            | getattr(os, "O_NOFOLLOW", 0),
        )
        with os.fdopen(descriptor, "rb") as source:
            metadata = os.fstat(source.fileno())
            if not stat.S_ISREG(metadata.st_mode):
                raise MigrationError("staged Jar must be a regular file")
            if metadata.st_size > MAX_STAGED_JAR_BYTES:
                raise MigrationError("staged Jar is unexpectedly large")
            content = source.read(MAX_STAGED_JAR_BYTES + 1)
            after = os.fstat(source.fileno())
    except OSError as error:
        raise MigrationError(f"cannot freeze staged Jar: {path}") from error
    if len(content) > MAX_STAGED_JAR_BYTES:
        raise MigrationError("staged Jar is unexpectedly large")
    if (
        len(content) != metadata.st_size
        or (metadata.st_dev, metadata.st_ino, metadata.st_size)
        != (after.st_dev, after.st_ino, after.st_size)
    ):
        raise MigrationError("staged Jar changed while it was being frozen")
    return content


def _open_frozen_jar(path: Path, content: bytes) -> zipfile.ZipFile:
    try:
        return zipfile.ZipFile(io.BytesIO(content))
    except zipfile.BadZipFile as error:
        raise MigrationError(f"cannot inspect staged Jar: {path}") from error


def _expect(manifest: dict[str, object], field: str, expected: object) -> None:
    if manifest.get(field) != expected:
        raise MigrationError(
            f"release manifest {field} mismatch: expected {expected!r}"
        )
