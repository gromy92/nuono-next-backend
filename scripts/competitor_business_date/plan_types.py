"""Value objects emitted by the correction planner."""
from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Iterable


@dataclass(frozen=True)
class RowChange:
    group_kind: str
    group_key: str
    table_name: str
    primary_key: str
    action: str
    pre: dict[str, Any] | None
    post: dict[str, Any]


@dataclass(frozen=True)
class CorrectionPlan:
    metadata: dict[str, Any]
    changes: tuple[RowChange, ...]
    summary: dict[str, int]

    def changes_for_group(self, kind: str, key: str) -> tuple[RowChange, ...]:
        return tuple(
            change
            for change in self.changes
            if change.group_kind == kind and change.group_key == key
        )

    @property
    def group_keys(self) -> tuple[tuple[str, str], ...]:
        return tuple(
            dict.fromkeys(
                (change.group_kind, change.group_key)
                for change in self.changes
            )
        )


def count_changes(changes: Iterable[RowChange]) -> dict[str, int]:
    result: dict[str, int] = {}
    for change in changes:
        key = f"{change.table_name}:{change.action}"
        result[key] = result.get(key, 0) + 1
    return dict(sorted(result.items()))
