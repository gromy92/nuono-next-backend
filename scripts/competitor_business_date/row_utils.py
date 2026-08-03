"""Strict row parsing helpers shared by correction planners."""
from __future__ import annotations

import json
from datetime import date, datetime
from decimal import Decimal
from typing import Any, Iterable


class RowContractError(ValueError):
    pass


def require_columns(row: dict[str, Any], columns: Iterable[str], table: str) -> None:
    missing = sorted(set(columns) - row.keys())
    if missing:
        raise RowContractError(f"{table} row is missing columns: {', '.join(missing)}")


def parse_datetime(value: Any, field: str) -> datetime:
    if isinstance(value, datetime):
        return value
    if not isinstance(value, str) or not value.strip():
        raise RowContractError(f"{field} must be a non-null DATETIME string")
    normalized = value.strip().replace("T", " ")
    try:
        return datetime.fromisoformat(normalized)
    except ValueError as error:
        raise RowContractError(f"{field} has an invalid DATETIME: {value!r}") from error


def parse_optional_datetime(value: Any, field: str) -> datetime | None:
    return None if value is None else parse_datetime(value, field)


def parse_date(value: Any, field: str) -> date:
    if isinstance(value, date) and not isinstance(value, datetime):
        return value
    if not isinstance(value, str) or not value.strip():
        raise RowContractError(f"{field} must be a non-null DATE string")
    try:
        return date.fromisoformat(value.strip())
    except ValueError as error:
        raise RowContractError(f"{field} has an invalid DATE: {value!r}") from error


def format_datetime(value: datetime) -> str:
    return value.isoformat(sep=" ", timespec="microseconds").rstrip("0").rstrip(".")


def format_date(value: date) -> str:
    return value.isoformat()


def as_int(value: Any, field: str) -> int:
    if isinstance(value, bool):
        return int(value)
    try:
        return int(value)
    except (TypeError, ValueError) as error:
        raise RowContractError(f"{field} must be an integer") from error


def as_bit(value: Any, field: str) -> bool:
    normalized = as_int(value, field)
    if normalized not in {0, 1}:
        raise RowContractError(f"{field} must be 0 or 1")
    return bool(normalized)


def json_scalar(value: Any) -> str:
    if isinstance(value, Decimal):
        if not value.is_finite():
            raise RowContractError("event decimal must be finite")
        return format(value, "f")
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))
