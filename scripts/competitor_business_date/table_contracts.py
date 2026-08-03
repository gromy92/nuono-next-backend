"""Closed MySQL row contracts used by correction CAS batches."""
from __future__ import annotations

import json
import re
from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal, InvalidOperation
from types import MappingProxyType
from typing import Any, Mapping


@dataclass(frozen=True)
class Column:
    name: str
    sql_type: str
    kind: str
    nullable: bool = True
    generated: bool = False


@dataclass(frozen=True)
class TableContract:
    name: str
    primary_key: str
    temp_token: str
    columns: tuple[Column, ...]

    @property
    def row_columns(self) -> tuple[Column, ...]:
        """Columns captured in PRE/POST rows; generated values are derived."""

        return tuple(column for column in self.columns if not column.generated)

    @property
    def row_column_names(self) -> frozenset[str]:
        return frozenset(column.name for column in self.row_columns)

    def column(self, name: str) -> Column:
        for column in self.columns:
            if column.name == name:
                return column
        raise KeyError(name)


def _c(
    name: str,
    sql_type: str,
    kind: str,
    nullable: bool = True,
    generated: bool = False,
) -> Column:
    return Column(name, sql_type, kind, nullable, generated)


def _contract(
    name: str,
    primary_key: str,
    temp_token: str,
    columns: tuple[Column, ...],
) -> TableContract:
    return TableContract(name, primary_key, temp_token, columns)


SNAPSHOT = _contract(
    "operations_competitor_product_snapshot",
    "id",
    "snapshot",
    (
        _c("id", "BIGINT", "int", False),
        _c("owner_user_id", "BIGINT", "int", False),
        _c("watch_product_id", "BIGINT", "int", False),
        _c("competitor_product_id", "BIGINT", "int"),
        _c("subject_type", "VARCHAR(32)", "text", False),
        _c("site_code", "VARCHAR(32)", "text", False),
        _c("noon_product_code", "VARCHAR(80)", "text", False),
        _c("code_type", "VARCHAR(32)", "text", False),
        _c("fact_date", "DATE", "date", False),
        _c("active_fact_date", "DATE", "date", True, True),
        _c("captured_at", "DATETIME", "datetime", False),
        _c("source_task_id", "BIGINT", "int"),
        _c("source_run_id", "BIGINT", "int"),
        _c("detail_url", "VARCHAR(1000)", "text"),
        _c("title_en", "VARCHAR(500)", "text"),
        _c("title_ar", "VARCHAR(500)", "text"),
        _c("brand", "VARCHAR(200)", "text"),
        _c("seller_name", "VARCHAR(255)", "text"),
        _c("price_amount", "DECIMAL(18,4)", "decimal"),
        _c("currency_code", "VARCHAR(16)", "text"),
        _c("rating", "DECIMAL(4,2)", "decimal"),
        _c("review_count", "INT", "int"),
        _c("main_image_url_raw", "VARCHAR(1000)", "text"),
        _c("main_image_url_normalized", "VARCHAR(1000)", "text"),
        _c("main_image_asset_key", "VARCHAR(255)", "text"),
        _c("supermall_enabled", "BIT(1)", "bit"),
        _c("sold_recently_text", "VARCHAR(255)", "text"),
        _c("logistics_tags_json", "JSON", "json"),
        _c("badges_json", "JSON", "json"),
        _c("availability_status", "VARCHAR(64)", "text"),
        _c("snapshot_hash", "CHAR(64)", "text", False),
        _c("raw_detail_json", "JSON", "json"),
        _c("is_deleted", "BIT(1)", "bit", False),
        _c("created_by", "BIGINT", "int"),
        _c("updated_by", "BIGINT", "int"),
        _c("gmt_create", "DATETIME", "datetime", False),
        _c("gmt_updated", "DATETIME", "datetime", False),
    ),
)

CHANGE_EVENT = _contract(
    "operations_competitor_product_change_event",
    "id",
    "change_event",
    (
        _c("id", "BIGINT", "int", False),
        _c("snapshot_id", "BIGINT", "int", False),
        _c("previous_snapshot_id", "BIGINT", "int"),
        _c("owner_user_id", "BIGINT", "int", False),
        _c("watch_product_id", "BIGINT", "int", False),
        _c("competitor_product_id", "BIGINT", "int"),
        _c("subject_type", "VARCHAR(32)", "text", False),
        _c("site_code", "VARCHAR(32)", "text", False),
        _c("noon_product_code", "VARCHAR(80)", "text", False),
        _c("fact_date", "DATE", "date", False),
        _c("field_key", "VARCHAR(64)", "text", False),
        _c("field_label", "VARCHAR(64)", "text", False),
        _c("change_type", "VARCHAR(32)", "text", False),
        _c("old_value_json", "JSON", "json"),
        _c("new_value_json", "JSON", "json"),
        _c("severity", "VARCHAR(16)", "text", False),
        _c("is_deleted", "BIT(1)", "bit", False),
        _c("created_by", "BIGINT", "int"),
        _c("updated_by", "BIGINT", "int"),
        _c("gmt_create", "DATETIME", "datetime", False),
        _c("gmt_updated", "DATETIME", "datetime", False),
    ),
)

RANK_FACT = _contract(
    "operations_competitor_rank_fact",
    "id",
    "rank_fact",
    (
        _c("id", "BIGINT", "int", False),
        _c("watch_product_id", "BIGINT", "int", False),
        _c("keyword_id", "BIGINT", "int", False),
        _c("keyword_run_id", "BIGINT", "int", False),
        _c("search_run_id", "BIGINT", "int", False),
        _c("fact_time", "DATETIME", "datetime", False),
        _c("fact_date", "DATE", "date", False),
        _c("tracked_product_type", "VARCHAR(32)", "text", False),
        _c("rank_channel", "VARCHAR(32)", "text", False),
        _c("noon_product_code", "VARCHAR(80)", "text", False),
        _c("rank_status", "VARCHAR(32)", "text", False),
        _c("rank_no", "INT", "int"),
        _c("scan_depth", "INT", "int"),
        _c("is_sponsored", "BIT(1)", "bit", False),
        _c("price_amount", "DECIMAL(14,2)", "decimal"),
        _c("currency_code", "VARCHAR(16)", "text"),
        _c("rating", "DECIMAL(4,2)", "decimal"),
        _c("review_count", "INT", "int"),
        _c("source_result_id", "BIGINT", "int"),
        _c("is_deleted", "BIT(1)", "bit", False),
        _c("created_by", "BIGINT", "int"),
        _c("updated_by", "BIGINT", "int"),
        _c("gmt_create", "DATETIME", "datetime", False),
        _c("gmt_updated", "DATETIME", "datetime", False),
    ),
)

KEYWORD_RUN = _contract(
    "operations_competitor_keyword_run",
    "id",
    "keyword_run",
    (
        _c("id", "BIGINT", "int", False),
        _c("search_run_id", "BIGINT", "int", False),
        _c("keyword_id", "BIGINT", "int", False),
        _c("keyword_snapshot", "VARCHAR(255)", "text", False),
        _c("locale_snapshot", "VARCHAR(32)", "text"),
        _c("provider_status", "VARCHAR(32)", "text", False),
        _c("result_count", "INT", "int", False),
        _c("requested_result_limit", "INT", "int"),
        _c("source_url", "VARCHAR(1000)", "text"),
        _c("parser_version", "VARCHAR(80)", "text"),
        _c("provider_http_status", "INT", "int"),
        _c("response_hash", "VARCHAR(128)", "text"),
        _c("captured_at", "DATETIME", "datetime"),
        _c("error_code", "VARCHAR(128)", "text"),
        _c("error_message", "VARCHAR(1024)", "text"),
        _c("started_at", "DATETIME", "datetime"),
        _c("finished_at", "DATETIME", "datetime"),
        _c("is_deleted", "BIT(1)", "bit", False),
        _c("created_by", "BIGINT", "int"),
        _c("updated_by", "BIGINT", "int"),
        _c("gmt_create", "DATETIME", "datetime", False),
        _c("gmt_updated", "DATETIME", "datetime", False),
    ),
)

ID_SEQUENCE = _contract(
    "operations_competitor_analysis_id_sequence",
    "sequence_name",
    "analysis_id_sequence",
    (
        _c("sequence_name", "VARCHAR(100)", "text", False),
        _c("next_id", "BIGINT", "int", False),
        _c("gmt_create", "DATETIME", "datetime", False),
        _c("gmt_updated", "DATETIME", "datetime", False),
    ),
)

TABLE_CONTRACTS: Mapping[str, TableContract] = MappingProxyType(
    {
        contract.name: contract
        for contract in (SNAPSHOT, CHANGE_EVENT, RANK_FACT, KEYWORD_RUN, ID_SEQUENCE)
    }
)


class ContractValueError(ValueError):
    pass


def mysql_text_literal(value: str) -> str:
    """Encode untrusted text without placing it verbatim in generated SQL."""

    return f"CONVERT(X'{value.encode('utf-8').hex().upper()}' USING utf8mb4)"


def typed_literal(value: Any, column: Column) -> str:
    """Validate one manifest value and return an explicit MySQL literal."""

    if value is None:
        if not column.nullable:
            raise ContractValueError(f"{column.name} must not be NULL")
        return "NULL"
    if column.kind == "text":
        if not isinstance(value, str):
            raise ContractValueError(f"{column.name} must be text")
        match = re.fullmatch(r"(?:VAR)?CHAR\((\d+)\)", column.sql_type)
        if match and len(value) > int(match.group(1)):
            raise ContractValueError(f"{column.name} exceeds {column.sql_type}")
        return mysql_text_literal(value)
    if column.kind in {"date", "datetime"}:
        if not isinstance(value, str):
            raise ContractValueError(f"{column.name} must be an ISO string")
        format_ = "%Y-%m-%d" if column.kind == "date" else "%Y-%m-%d %H:%M:%S"
        try:
            datetime.strptime(value, format_)
        except ValueError as error:
            raise ContractValueError(
                f"{column.name} is not a valid {column.kind}"
            ) from error
        return f"CAST({_ascii_literal(value)} AS {column.sql_type})"
    if column.kind == "json":
        if not isinstance(value, str):
            raise ContractValueError(f"{column.name} must be raw JSON text")
        try:
            json.loads(value)
        except (json.JSONDecodeError, ValueError) as error:
            raise ContractValueError(f"{column.name} is not valid JSON") from error
        return f"CAST({mysql_text_literal(value)} AS JSON)"
    if column.kind == "int":
        if isinstance(value, bool) or not isinstance(value, int):
            raise ContractValueError(f"{column.name} must be an integer")
        bits = 32 if column.sql_type == "INT" else 64
        if not -(2 ** (bits - 1)) <= value < 2 ** (bits - 1):
            raise ContractValueError(
                f"{column.name} exceeds signed {bits}-bit range"
            )
        return f"CAST({_ascii_literal(str(value))} AS SIGNED)"
    if column.kind == "bit":
        if not isinstance(value, (bool, int)) or value not in {0, 1}:
            raise ContractValueError(f"{column.name} must be a one-bit value")
        return f"CAST({_ascii_literal('1' if bool(value) else '0')} AS UNSIGNED)"
    if column.kind == "decimal":
        if isinstance(value, bool) or not isinstance(value, (str, int)):
            raise ContractValueError(
                f"{column.name} must be a decimal string or integer"
            )
        try:
            decimal = Decimal(str(value))
        except InvalidOperation as error:
            raise ContractValueError(f"{column.name} is not a valid decimal") from error
        if not decimal.is_finite():
            raise ContractValueError(f"{column.name} must be finite")
        precision, scale = map(int, re.findall(r"\d+", column.sql_type))
        integral = max(decimal.adjusted() + 1, 0) if decimal else 0
        fractional = max(-decimal.as_tuple().exponent, 0)
        if integral > precision - scale or fractional > scale:
            raise ContractValueError(f"{column.name} exceeds {column.sql_type}")
        return f"CAST({_ascii_literal(str(value))} AS {column.sql_type})"
    raise ContractValueError(f"unsupported column kind: {column.kind}")


def _ascii_literal(value: str) -> str:
    return f"CONVERT(X'{value.encode('ascii').hex().upper()}' USING ascii)"
