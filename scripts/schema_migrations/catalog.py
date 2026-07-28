from __future__ import annotations

import csv
import hashlib
import re
import zipfile
from pathlib import Path, PurePosixPath
from typing import Mapping

from schema_migrations.core import Migration, MigrationError

CATALOG_PATH = PurePosixPath("db/init/release-migrations.tsv")
CATALOG_COLUMNS = (
    "order",
    "migration_key",
    "kind",
    "script_path",
    "postcheck_path",
)
MIGRATION_KINDS = frozenset({"BOOTSTRAP", "AUTO_ADDITIVE", "MANAGED"})
MIGRATION_NAME = re.compile(r"^(?P<order>[0-9]{3})_[a-z0-9_]+\.sql$")
JAR_RESOURCE_PREFIX = "BOOT-INF/classes/"


def sha256_bytes(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_catalog(
    resource_root: Path,
    catalog_path: PurePosixPath = CATALOG_PATH,
) -> tuple[Migration, ...]:
    root = resource_root.resolve()
    catalog_file = _resolve_resource(root, catalog_path)
    try:
        catalog_bytes = catalog_file.read_bytes()
    except OSError as error:
        raise MigrationError(f"cannot read migration catalog: {catalog_file}") from error

    def read_resource(path: PurePosixPath) -> tuple[bytes, Path | None]:
        resource = _resolve_resource(root, path)
        try:
            return resource.read_bytes(), resource
        except OSError as error:
            raise MigrationError(f"missing migration resource: {resource}") from error

    return _load_catalog(catalog_bytes, read_resource)


def load_catalog_from_jar(staged_jar: Path) -> tuple[Migration, ...]:
    try:
        with zipfile.ZipFile(staged_jar) as archive:
            return load_catalog_from_archive(archive)
    except (OSError, zipfile.BadZipFile) as error:
        raise MigrationError(f"cannot inspect staged Jar: {staged_jar}") from error


def load_catalog_from_archive(
    archive: zipfile.ZipFile,
) -> tuple[Migration, ...]:
    names = archive.namelist()
    catalog_bytes = _read_exact_entry(archive, names, CATALOG_PATH)

    def read_resource(path: PurePosixPath) -> tuple[bytes, Path | None]:
        return _read_exact_entry(archive, names, path), None

    return _load_catalog(catalog_bytes, read_resource)


def _load_catalog(catalog_bytes, read_resource) -> tuple[Migration, ...]:
    try:
        text = catalog_bytes.decode("utf-8")
        reader = csv.DictReader(text.splitlines(), delimiter="\t")
        fieldnames = tuple(reader.fieldnames or ())
        rows = list(reader)
    except (UnicodeError, csv.Error) as error:
        raise MigrationError("cannot decode migration catalog as UTF-8 TSV") from error
    if fieldnames != CATALOG_COLUMNS:
        raise MigrationError(
            "migration catalog columns must be: " + "\t".join(CATALOG_COLUMNS)
        )
    if not rows:
        raise MigrationError("migration catalog must contain at least one row")

    migrations = tuple(
        _migration_from_row(row, read_resource)
        for row in rows
    )
    keys = [migration.key for migration in migrations]
    orders = [migration.order for migration in migrations]
    if len(keys) != len(set(keys)):
        raise MigrationError("migration catalog contains duplicate migration_key")
    if len(orders) != len(set(orders)):
        raise MigrationError("migration catalog contains duplicate order")
    if list(migrations) != sorted(migrations, key=lambda item: (item.order, item.key)):
        raise MigrationError("migration catalog rows must be in stable order")
    bootstraps = [item for item in migrations if item.kind == "BOOTSTRAP"]
    if len(bootstraps) != 1 or migrations[0] != bootstraps[0]:
        raise MigrationError("migration catalog must start with exactly one BOOTSTRAP")
    return migrations


def _migration_from_row(
    row: Mapping[str, str | None],
    read_resource,
) -> Migration:
    raw_order = _required_cell(row, "order")
    try:
        order = int(raw_order)
    except ValueError as error:
        raise MigrationError(f"invalid migration order: {raw_order!r}") from error
    key = _required_cell(row, "migration_key")
    match = MIGRATION_NAME.fullmatch(key)
    if match is None or int(match.group("order")) != order:
        raise MigrationError(
            f"{key or '<empty>'}: filename prefix must equal the catalog order"
        )
    kind = _required_cell(row, "kind")
    if kind not in MIGRATION_KINDS:
        raise MigrationError(f"{key}: unsupported migration kind {kind!r}")
    script_path = _relative_path(_required_cell(row, "script_path"))
    postcheck_path = _relative_path(_required_cell(row, "postcheck_path"))
    if key != script_path.name:
        raise MigrationError("migration_key must equal the exact script filename")
    if key != postcheck_path.name:
        raise MigrationError(f"{key}: postcheck filename must equal migration_key")
    script_bytes, script_file = read_resource(script_path)
    postcheck_bytes, postcheck_file = read_resource(postcheck_path)
    _decode_sql(key, "script", script_bytes)
    _decode_sql(key, "postcheck", postcheck_bytes)
    return Migration(
        order=order,
        key=key,
        kind=kind,
        script_path=script_path,
        postcheck_path=postcheck_path,
        checksum=sha256_bytes(script_bytes),
        postcheck_checksum=sha256_bytes(postcheck_bytes),
        script_bytes=script_bytes,
        postcheck_bytes=postcheck_bytes,
        script_file=script_file,
        postcheck_file=postcheck_file,
    )


def _required_cell(row: Mapping[str, str | None], column: str) -> str:
    value = row.get(column)
    if value is None or not value.strip():
        raise MigrationError(f"migration catalog row has empty {column}")
    return value.strip()


def _relative_path(value: str) -> PurePosixPath:
    path = PurePosixPath(value)
    if path.is_absolute() or ".." in path.parts or "." in path.parts:
        raise MigrationError(f"unsafe migration resource path: {value!r}")
    return path


def _resolve_resource(root: Path, path: PurePosixPath) -> Path:
    resolved = root.joinpath(*path.parts).resolve()
    if root != resolved and root not in resolved.parents:
        raise MigrationError(f"migration resource escapes root: {path}")
    return resolved


def _read_exact_entry(
    archive: zipfile.ZipFile,
    names: list[str],
    path: PurePosixPath,
) -> bytes:
    entry = JAR_RESOURCE_PREFIX + path.as_posix()
    if names.count(entry) != 1:
        raise MigrationError(f"staged Jar must contain exactly one {entry}")
    return archive.read(entry)


def _decode_sql(key: str, label: str, content: bytes) -> str:
    try:
        return content.decode("utf-8")
    except UnicodeError as error:
        raise MigrationError(f"{key}: {label} must be valid UTF-8") from error
