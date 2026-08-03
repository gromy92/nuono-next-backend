"""Private, no-follow file primitives for correction evidence."""
from __future__ import annotations

import hashlib
import os
import shutil
import stat
from pathlib import Path


class SecureFileError(RuntimeError):
    pass


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def fsync_file(path: Path) -> None:
    descriptor = os.open(Path(path), os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def require_private_regular_file(path: Path) -> Path:
    candidate = Path(path)
    if candidate.is_symlink():
        raise SecureFileError(f"file must not be a symbolic link: {candidate}")
    try:
        info = candidate.stat()
    except FileNotFoundError as error:
        raise SecureFileError(f"file does not exist: {candidate}") from error
    if not stat.S_ISREG(info.st_mode):
        raise SecureFileError(f"file must be regular: {candidate}")
    if hasattr(os, "getuid") and info.st_uid != os.getuid():
        raise SecureFileError(f"file must be owned by the current user: {candidate}")
    if stat.S_IMODE(info.st_mode) & 0o077:
        raise SecureFileError(
            f"file permissions must deny group/other access: {candidate}"
        )
    return candidate.resolve()


def create_exclusive(path: Path) -> Path:
    candidate = Path(path)
    if candidate.is_symlink():
        raise SecureFileError(f"file target must not be a symbolic link: {candidate}")
    if candidate.exists():
        raise SecureFileError(f"file target already exists: {candidate}")
    candidate.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    flags = os.O_CREAT | os.O_EXCL | os.O_WRONLY
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    descriptor = os.open(candidate, flags, 0o600)
    os.close(descriptor)
    fsync_directory(candidate.parent)
    return candidate.resolve()


def copy_exact_private(source: Path, destination: Path, expected_sha256: str) -> str:
    source_path = require_private_regular_file(source)
    if sha256_file(source_path) != expected_sha256:
        raise SecureFileError("source SHA-256 mismatch before backup")
    destination_path = Path(destination)
    if source_path == destination_path.resolve():
        raise SecureFileError("backup must be a separate file")
    created = create_exclusive(destination_path)
    with source_path.open("rb") as source_handle, created.open("r+b") as target:
        shutil.copyfileobj(source_handle, target, 1024 * 1024)
        target.flush()
        os.fsync(target.fileno())
    os.chmod(created, 0o400)
    fsync_directory(created.parent)
    actual = sha256_file(created)
    if actual != expected_sha256:
        raise SecureFileError("backup SHA-256 mismatch")
    return actual


def fsync_directory(path: Path) -> None:
    descriptor = os.open(Path(path), os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)
