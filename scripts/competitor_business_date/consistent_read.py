"""Demultiplex fixed read queries from one MySQL consistent snapshot."""
from __future__ import annotations

import base64
import json
import re
from collections.abc import Iterator, Sequence
from typing import Any

from .mysql_cli import MysqlCli


class ConsistentReadError(RuntimeError):
    pass


def build_consistent_read_script(
    datasets: Sequence[tuple[str, str]],
) -> str:
    names = [name for name, _ in datasets]
    if len(names) != len(set(names)) or any(
        not re.fullmatch(r"[a-z][a-z0-9_]*", name) for name in names
    ):
        raise ConsistentReadError("dataset names must be unique safe identifiers")
    lines = [
        "SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;",
        "START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY;",
    ]
    for name, query in datasets:
        normalized = query.strip()
        if not normalized.endswith(";") or normalized.count(";") != 1:
            raise ConsistentReadError(f"dataset {name} must be one SQL statement")
        lines.append(_control_sql(name, "start"))
        lines.append(normalized)
        lines.append(_control_sql(name, "end"))
    lines.append("COMMIT;")
    return "\n".join(lines) + "\n"


class ConsistentDatasetReader:
    def __init__(
        self,
        mysql: MysqlCli,
        datasets: Sequence[tuple[str, str]],
        *,
        timeout_seconds: int,
    ):
        self.names = tuple(name for name, _ in datasets)
        self.rows = iter(
            mysql.query_json_objects(
                build_consistent_read_script(datasets),
                timeout_seconds=timeout_seconds,
            )
        )
        self.position = 0

    def read(self, expected_name: str) -> Iterator[dict[str, Any]]:
        if self.position >= len(self.names) or self.names[self.position] != expected_name:
            raise ConsistentReadError(f"unexpected dataset request: {expected_name}")
        start = next(self.rows, None)
        if start != _control(expected_name, "start"):
            raise ConsistentReadError(f"missing start marker for {expected_name}")
        for row in self.rows:
            if row == _control(expected_name, "end"):
                self.position += 1
                return
            if "__dataset__" in row or "__phase__" in row:
                raise ConsistentReadError(
                    f"unexpected control marker inside {expected_name}"
                )
            yield row
        raise ConsistentReadError(f"missing end marker for {expected_name}")

    def finish(self) -> None:
        if self.position != len(self.names):
            raise ConsistentReadError("not all consistent datasets were consumed")
        if next(self.rows, None) is not None:
            raise ConsistentReadError("unexpected rows after consistent datasets")


def _control(name: str, phase: str) -> dict[str, str]:
    return {"__dataset__": name, "__phase__": phase}


def _control_sql(name: str, phase: str) -> str:
    payload = json.dumps(
        _control(name, phase),
        separators=(",", ":"),
        sort_keys=True,
    ).encode("utf-8")
    encoded = base64.b64encode(payload).decode("ascii")
    return f"SELECT '{encoded}';"
