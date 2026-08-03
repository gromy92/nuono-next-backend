"""Rows and state probes for the complete MySQL correction fixture."""
from __future__ import annotations

from typing import Any

from competitor_business_date.mysql_cli import MysqlCli, encoded_json_select
from competitor_business_date.table_contracts import (
    CHANGE_EVENT,
    KEYWORD_RUN,
    RANK_FACT,
    SNAPSHOT,
    TableContract,
    typed_literal,
)


CORRECTION_TIME = "2026-07-31 12:00:00"
ACTOR_USER_ID = 99
SEQUENCE_NAME = "operations_competitor_product_change_event"


def correction_state(mysql: MysqlCli) -> dict[str, Any]:
    return mysql.query_one_json(
        encoded_json_select(
            "JSON_OBJECT("
            "'snapshot_1_date', DATE_FORMAT(s1.fact_date, '%Y-%m-%d'), "
            "'snapshot_1_deleted', CAST(s1.is_deleted AS UNSIGNED), "
            "'snapshot_2_date', DATE_FORMAT(s2.fact_date, '%Y-%m-%d'), "
            "'snapshot_2_deleted', CAST(s2.is_deleted AS UNSIGNED), "
            "'event_count', (SELECT COUNT(*) FROM "
            "`operations_competitor_product_change_event` WHERE id = 270001), "
            "'sequence_next', seq.next_id, "
            "'keyword_captured', DATE_FORMAT(kr.captured_at, '%Y-%m-%d %H:%i:%s'), "
            "'rank_time', DATE_FORMAT(r.fact_time, '%Y-%m-%d %H:%i:%s'), "
            "'rank_date', DATE_FORMAT(r.fact_date, '%Y-%m-%d'))",
            "FROM `operations_competitor_product_snapshot` s1 "
            "JOIN `operations_competitor_product_snapshot` s2 ON s2.id = 2 "
            "JOIN `operations_competitor_analysis_id_sequence` seq "
            f"ON seq.sequence_name = '{SEQUENCE_NAME}' "
            "JOIN `operations_competitor_keyword_run` kr ON kr.id = 700 "
            "JOIN `operations_competitor_rank_fact` r ON r.id = 900000 "
            "WHERE s1.id = 1",
        )
    )


def snapshot_changes():
    common = {
        "owner_user_id": 1,
        "subject_type": "SELF",
        "site_code": "sa",
        "code_type": "SKU",
        "snapshot_hash": "a" * 64,
    }
    first = _row(
        SNAPSHOT,
        **common,
        id=1,
        watch_product_id=500,
        noon_product_code="CAS-CI",
        fact_date="2026-07-28",
        captured_at="2026-07-28 16:30:00",
    )
    second = _row(
        SNAPSHOT,
        **common,
        id=2,
        watch_product_id=500,
        noon_product_code="CAS-CI",
        fact_date="2026-07-29",
        captured_at="2026-07-29 00:15:00",
    )
    first_post = {
        **first,
        "fact_date": "2026-07-29",
        "captured_at": "2026-07-29 00:30:00",
        "is_deleted": 1,
        "updated_by": ACTOR_USER_ID,
        "gmt_updated": CORRECTION_TIME,
    }
    second_post = {
        **second,
        "updated_by": ACTOR_USER_ID,
        "gmt_updated": CORRECTION_TIME,
    }
    return (first, second), (first_post, second_post)


def rank_changes():
    keyword = _row(
        KEYWORD_RUN,
        id=700,
        search_run_id=600,
        keyword_id=800,
        keyword_snapshot="laundry basket",
        provider_status="SUCCESS",
        captured_at="2026-07-27 18:30:00",
    )
    rank = _row(
        RANK_FACT,
        id=900000,
        watch_product_id=500,
        keyword_id=800,
        keyword_run_id=700,
        search_run_id=600,
        fact_time="2026-07-27 18:30:00",
        fact_date="2026-07-27",
        tracked_product_type="SELF",
        rank_channel="ORGANIC",
        noon_product_code="NSELF0001",
        rank_status="RANKED",
        rank_no=1,
        scan_depth=200,
        is_sponsored=0,
    )
    keyword_post = {
        **keyword,
        "captured_at": "2026-07-28 02:30:00",
        "updated_by": ACTOR_USER_ID,
        "gmt_updated": CORRECTION_TIME,
    }
    rank_post = {
        **rank,
        "fact_time": "2026-07-28 02:30:00",
        "fact_date": "2026-07-28",
        "updated_by": ACTOR_USER_ID,
        "gmt_updated": CORRECTION_TIME,
    }
    return keyword, keyword_post, rank, rank_post


def event_row(row_id: int, snapshot: dict[str, Any]) -> dict[str, Any]:
    return _row(
        CHANGE_EVENT,
        id=row_id,
        snapshot_id=snapshot["id"],
        previous_snapshot_id=1,
        owner_user_id=snapshot["owner_user_id"],
        watch_product_id=snapshot["watch_product_id"],
        subject_type="SELF",
        site_code="sa",
        noon_product_code="CAS-CI",
        fact_date=snapshot["fact_date"],
        field_key="price_amount",
        field_label="Price",
        change_type="VALUE_CHANGED",
        old_value_json="1",
        new_value_json="2",
        severity="INFO",
        created_by=ACTOR_USER_ID,
        updated_by=ACTOR_USER_ID,
        gmt_create=snapshot["gmt_create"],
        gmt_updated=CORRECTION_TIME,
    )


def insert_row(
    mysql: MysqlCli,
    contract: TableContract,
    row: dict[str, Any],
) -> None:
    columns = contract.row_columns
    mysql.run_script(
        f"INSERT INTO `{contract.name}` "
        f"({', '.join(f'`{column.name}`' for column in columns)}) VALUES "
        f"({', '.join(typed_literal(row[column.name], column) for column in columns)});\n"
    )


def event_sequence_state(mysql: MysqlCli) -> dict[str, Any]:
    return mysql.query_one_json(
        encoded_json_select(
            "JSON_OBJECT('sequence_name', s.sequence_name, 'next_id', s.next_id, "
            "'max_event_id', COALESCE(MAX(e.id), 0), "
            "'gmt_create', DATE_FORMAT(s.gmt_create, '%Y-%m-%d %H:%i:%s'), "
            "'gmt_updated', DATE_FORMAT(s.gmt_updated, '%Y-%m-%d %H:%i:%s'))",
            "FROM `operations_competitor_analysis_id_sequence` s "
            "LEFT JOIN `operations_competitor_product_change_event` e ON 1 = 1 "
            f"WHERE s.sequence_name = '{SEQUENCE_NAME}' "
            "GROUP BY s.sequence_name, s.next_id, s.gmt_create, s.gmt_updated",
        )
    )


def _row(contract: TableContract, **values: Any) -> dict[str, Any]:
    defaults = {
        "text": "fixture",
        "date": "2026-07-28",
        "datetime": "2026-07-28 00:00:00",
        "json": "{}",
        "decimal": "1.00",
        "int": 1,
        "bit": 0,
    }
    row = {
        column.name: None if column.nullable else defaults[column.kind]
        for column in contract.row_columns
    }
    row.update(
        {
            "is_deleted": 0,
            "created_by": 1,
            "updated_by": 1,
            "gmt_create": "2026-07-28 00:00:00",
            "gmt_updated": "2026-07-28 00:00:00",
            **values,
        }
    )
    return row
