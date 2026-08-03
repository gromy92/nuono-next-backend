"""Audited, checksum-bound clock classification overrides."""
from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .policy import (
    CURRENT_CLOCK,
    LEGACY_CLOCK,
    LEGACY_CONTRACT,
    LIST_V1_CONTRACT,
)
from .secure_files import SecureFileError, require_private_regular_file, sha256_file


class OverrideError(RuntimeError):
    pass


@dataclass(frozen=True)
class ClockOverrides:
    file_sha256: str
    reason: str
    approved_by: str
    snapshot: dict[int, str]
    rank: dict[int, str]
    event_contract: dict[int, str]

    def manifest_value(self) -> dict[str, Any]:
        return {
            "schema_version": 1,
            "file_sha256": self.file_sha256,
            "reason": self.reason,
            "approved_by": self.approved_by,
            "snapshot": {str(key): self.snapshot[key] for key in sorted(self.snapshot)},
            "rank": {str(key): self.rank[key] for key in sorted(self.rank)},
            "event_contract": {
                str(key): self.event_contract[key]
                for key in sorted(self.event_contract)
            },
        }


def load_clock_overrides(path: Path, expected_sha256: str) -> ClockOverrides:
    if not re.fullmatch(r"[0-9a-f]{64}", expected_sha256):
        raise OverrideError("override SHA-256 must be lowercase hexadecimal")
    try:
        source = require_private_regular_file(path)
    except SecureFileError as error:
        raise OverrideError(str(error)) from error
    if sha256_file(source) != expected_sha256:
        raise OverrideError("clock override SHA-256 mismatch")
    try:
        payload = json.loads(source.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise OverrideError("clock override file is not valid UTF-8 JSON") from error
    if not isinstance(payload, dict) or payload.get("schema_version") != 1:
        raise OverrideError("clock override schema_version must be 1")
    reason = _required_text(payload, "reason")
    approved_by = _required_text(payload, "approved_by")
    snapshot = _classification_map(payload.get("snapshot"), "snapshot")
    rank = _classification_map(payload.get("rank"), "rank")
    event_contract = _classification_map(
        payload.get("event_contract"),
        "event_contract",
        allowed={LEGACY_CONTRACT, LIST_V1_CONTRACT},
    )
    if not snapshot and not rank and not event_contract:
        raise OverrideError("clock override file must classify at least one row")
    return ClockOverrides(
        expected_sha256,
        reason,
        approved_by,
        snapshot,
        rank,
        event_contract,
    )


def _required_text(payload: dict[str, Any], key: str) -> str:
    value = payload.get(key)
    if not isinstance(value, str) or not value.strip():
        raise OverrideError(f"clock override {key} must be non-blank text")
    return value.strip()


def _classification_map(
    value: Any,
    label: str,
    *,
    allowed: set[str] | None = None,
) -> dict[int, str]:
    if not isinstance(value, dict):
        raise OverrideError(f"clock override {label} must be an object")
    result: dict[int, str] = {}
    for raw_id, classification in value.items():
        if not isinstance(raw_id, str) or not raw_id.isdigit() or int(raw_id) <= 0:
            raise OverrideError(f"clock override {label} IDs must be positive strings")
        if classification not in (allowed or {LEGACY_CLOCK, CURRENT_CLOCK}):
            raise OverrideError(
                f"clock override {label}/{raw_id} has invalid classification"
            )
        result[int(raw_id)] = classification
    return result
