"""Closed validators for nested manifest metadata values."""
from __future__ import annotations

import re
from typing import Any


class MetadataValueError(ValueError):
    pass


def validate_release_artifact(
    value: Any,
    operation_bundle: dict[str, Any],
) -> None:
    required = {
        "manifest_sha256",
        "repository",
        "commit",
        "workflow",
        "run_id",
        "run_attempt",
        "artifact_name",
        "operation_bundle_sha256",
    }
    if (
        not isinstance(value, dict)
        or set(value) != required
        or not _sha(value.get("manifest_sha256"))
        or not re.fullmatch(r"[0-9a-f]{40}", str(value.get("commit", "")))
        or not _positive_int(value.get("run_id"))
        or not _positive_int(value.get("run_attempt"))
        or any(
            not isinstance(value.get(key), str) or not value[key]
            for key in ("repository", "workflow", "artifact_name")
        )
        or value.get("operation_bundle_sha256") != operation_bundle.get("sha256")
    ):
        raise MetadataValueError("release artifact provenance is invalid")


def validate_clock_overrides(value: Any) -> None:
    if (
        not isinstance(value, dict)
        or set(value)
        != {
            "schema_version",
            "file_sha256",
            "reason",
            "approved_by",
            "snapshot",
            "rank",
            "event_contract",
        }
        or value.get("schema_version") != 1
        or not _sha(value.get("file_sha256"))
        or not isinstance(value.get("reason"), str)
        or not value["reason"].strip()
        or not isinstance(value.get("approved_by"), str)
        or not value["approved_by"].strip()
    ):
        raise MetadataValueError("clock override metadata is invalid")
    for key, allowed in (
        ("snapshot", {"legacy", "current"}),
        ("rank", {"legacy", "current"}),
        ("event_contract", {"legacy", "list_v1"}),
    ):
        mapping = value.get(key)
        if (
            not isinstance(mapping, dict)
            or any(
                not str(row_id).isdigit() or classification not in allowed
                for row_id, classification in mapping.items()
            )
        ):
            raise MetadataValueError(f"{key} override metadata is invalid")


def _positive_int(value: Any) -> bool:
    return isinstance(value, int) and not isinstance(value, bool) and value > 0


def _sha(value: Any) -> bool:
    return isinstance(value, str) and bool(re.fullmatch(r"[0-9a-f]{64}", value))
