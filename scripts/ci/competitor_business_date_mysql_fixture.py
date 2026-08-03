"""Small disposable MySQL schema for correction integration checks."""
from __future__ import annotations

from typing import Any

from competitor_business_date.mysql_cli import MysqlCli, encoded_json_select
from competitor_business_date.table_contracts import (
    SNAPSHOT,
    typed_literal,
)

from ci.competitor_business_date_mysql_manifest import (
    ACTOR_USER_ID,
    CORRECTION_TIME,
    FENCE_GENERATION,
    RELEASE_PROVENANCE,
    RUN_ID,
    correction_state,
    seed_and_build_manifest,
)
from ci.competitor_business_date_mysql_schema import (
    FENCE_TABLE,
    create_contract_tables,
    drop_contract_tables,
    run_migrations,
)

build_manifest = seed_and_build_manifest


class FixtureError(RuntimeError):
    pass


def assert_empty_schema(mysql: MysqlCli) -> None:
    row = mysql.query_one_json(
        encoded_json_select(
            "JSON_OBJECT('table_count', COUNT(*))",
            "FROM information_schema.tables "
            "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'",
        )
    )
    if int(row["table_count"]) != 0:
        raise FixtureError("MySQL fixture schema must start empty")


def snapshot_row(
    row_id: int,
    *,
    watch_product_id: int,
    noon_product_code: str,
    fact_date: str = "2026-07-28",
    captured_at: str = "2026-07-28 16:30:00",
    is_deleted: int = 0,
) -> dict[str, Any]:
    row: dict[str, Any] = {}
    for column in SNAPSHOT.row_columns:
        if column.kind == "text":
            row[column.name] = "fixture"
        elif column.kind == "date":
            row[column.name] = fact_date
        elif column.kind == "datetime":
            row[column.name] = captured_at
        elif column.kind == "json":
            row[column.name] = "{}"
        elif column.kind == "decimal":
            row[column.name] = "1.00"
        elif column.kind == "int":
            row[column.name] = 1
        elif column.kind == "bit":
            row[column.name] = 0
        else:
            raise FixtureError(f"unsupported fixture column kind: {column.kind}")
    row.update(
        {
            "id": row_id,
            "owner_user_id": 1,
            "watch_product_id": watch_product_id,
            "subject_type": "SELF",
            "site_code": "sa",
            "noon_product_code": noon_product_code,
            "code_type": "SKU",
            "fact_date": fact_date,
            "captured_at": captured_at,
            "snapshot_hash": "a" * 64,
            "is_deleted": is_deleted,
            "created_by": 1,
            "updated_by": 1,
            "gmt_create": "2026-07-28 16:30:00",
            "gmt_updated": "2026-07-28 16:30:00",
        }
    )
    return row


def insert_snapshot(mysql: MysqlCli, row: dict[str, Any]) -> None:
    columns = SNAPSHOT.row_columns
    mysql.run_script(
        f"INSERT INTO `{SNAPSHOT.name}` "
        f"({', '.join(f'`{column.name}`' for column in columns)}) VALUES "
        f"({', '.join(typed_literal(row[column.name], column) for column in columns)});\n"
    )


def snapshot_state(mysql: MysqlCli, row_id: int) -> dict[str, Any]:
    return mysql.query_one_json(
        encoded_json_select(
            "JSON_OBJECT("
            "'fact_date', DATE_FORMAT(`fact_date`, '%Y-%m-%d'), "
            "'captured_at', DATE_FORMAT(`captured_at`, '%Y-%m-%d %H:%i:%s'), "
            "'title_en', `title_en`)",
            f"FROM `{SNAPSHOT.name}` WHERE `id` = {row_id}",
        )
    )

