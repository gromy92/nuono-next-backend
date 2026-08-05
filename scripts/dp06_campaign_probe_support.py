"""Small file/time helpers for the DP-06 release probe."""
from __future__ import annotations

import datetime as dt
import hashlib
import json
import os
import stat
from pathlib import Path


class ProbeFailure(RuntimeError):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


def require_private_file(path: Path) -> None:
    try:
        metadata = path.lstat()
    except OSError as failure:
        raise ProbeFailure("DP06_CAPTURE_FILE_INVALID") from failure
    if path.is_symlink() or not stat.S_ISREG(metadata.st_mode) \
            or stat.S_IMODE(metadata.st_mode) != 0o600:
        raise ProbeFailure("DP06_CAPTURE_FILE_INVALID")


def read_json(path: Path, code: str) -> dict[str, object]:
    try:
        value = json.loads(path.read_bytes())
    except (OSError, ValueError) as failure:
        raise ProbeFailure(code) from failure
    if not isinstance(value, dict):
        raise ProbeFailure(code)
    return value


def write_new(path: Path, payload: dict[str, object]) -> None:
    encoded = canonical(payload) + b"\n"
    try:
        descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, "wb") as output:
            output.write(encoded)
    except OSError as failure:
        raise ProbeFailure("DP06_EVIDENCE_WRITE_FAILED") from failure


def canonical(value: object) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":")).encode()


def sha256(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def parse_instant(value: object, code: str) -> dt.datetime:
    try:
        parsed = dt.datetime.fromisoformat(str(value).replace("Z", "+00:00"))
        return utc(parsed)
    except (TypeError, ValueError) as failure:
        raise ProbeFailure(code) from failure


def utc(value: dt.datetime) -> dt.datetime:
    if value.tzinfo is None:
        raise ProbeFailure("DP06_TIMEZONE_REQUIRED")
    return value.astimezone(dt.timezone.utc)


def instant(value: dt.datetime) -> str:
    return utc(value).isoformat().replace("+00:00", "Z")
