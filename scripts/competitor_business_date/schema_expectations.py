"""Golden column/index expectations for the correction's exact schema."""
from __future__ import annotations

from .persistent_fence import FENCE_TABLE
from .table_contracts import TABLE_CONTRACTS


TABLE_COMMENT = {FENCE_TABLE: "competitor correction writer fence v1"}

INDEXES = {
    "operations_competitor_product_snapshot": {
        "PRIMARY": (False, ("id",)),
        "uk_ops_comp_snapshot_active_daily": (
            False,
            (
                "watch_product_id",
                "subject_type",
                "noon_product_code",
                "active_fact_date",
            ),
        ),
        "idx_ops_comp_snapshot_watch_date": (
            True,
            ("watch_product_id", "fact_date"),
        ),
        "idx_ops_comp_snapshot_product_date": (
            True,
            ("watch_product_id", "competitor_product_id", "fact_date"),
        ),
        "idx_ops_comp_snapshot_code_date": (
            True,
            ("site_code", "noon_product_code", "fact_date"),
        ),
        "idx_ops_comp_snapshot_task": (True, ("source_task_id",)),
        "idx_ops_comp_snapshot_run": (True, ("source_run_id",)),
    },
    "operations_competitor_product_change_event": {
        "PRIMARY": (False, ("id",)),
        "idx_ops_comp_change_watch_date": (
            True,
            ("watch_product_id", "fact_date"),
        ),
        "idx_ops_comp_change_product_date": (
            True,
            ("watch_product_id", "noon_product_code", "fact_date"),
        ),
        "idx_ops_comp_change_field_date": (
            True,
            ("field_key", "fact_date"),
        ),
        "idx_ops_comp_change_snapshot": (True, ("snapshot_id",)),
    },
    "operations_competitor_rank_fact": {
        "PRIMARY": (False, ("id",)),
        "uk_ops_comp_rank_fact_run_product_channel": (
            False,
            (
                "keyword_run_id",
                "tracked_product_type",
                "noon_product_code",
                "rank_channel",
            ),
        ),
        "idx_ops_comp_rank_fact_history": (
            True,
            ("watch_product_id", "keyword_id", "noon_product_code", "fact_time"),
        ),
        "idx_ops_comp_rank_fact_date": (
            True,
            ("watch_product_id", "fact_date"),
        ),
        "idx_ops_comp_rank_fact_search": (
            True,
            ("search_run_id", "keyword_run_id"),
        ),
    },
    "operations_competitor_keyword_run": {
        "PRIMARY": (False, ("id",)),
        "idx_ops_comp_keyword_run_search": (
            True,
            ("search_run_id", "keyword_id"),
        ),
        "idx_ops_comp_keyword_run_keyword": (True, ("keyword_id", "id")),
        "idx_ops_comp_keyword_run_provider": (
            True,
            ("provider_status", "gmt_updated"),
        ),
    },
    "operations_competitor_analysis_id_sequence": {
        "PRIMARY": (False, ("sequence_name",)),
    },
    FENCE_TABLE: {
        "PRIMARY": (False, ("fence_name",)),
    },
}

DEFAULTS = {
    ("operations_competitor_product_snapshot", "is_deleted"): "0",
    ("operations_competitor_product_change_event", "severity"): "INFO",
    ("operations_competitor_product_change_event", "is_deleted"): "0",
    ("operations_competitor_rank_fact", "rank_channel"): "ORGANIC",
    ("operations_competitor_rank_fact", "is_sponsored"): "0",
    ("operations_competitor_rank_fact", "is_deleted"): "0",
    ("operations_competitor_keyword_run", "provider_status"): "FAILED",
    ("operations_competitor_keyword_run", "result_count"): "0",
    ("operations_competitor_keyword_run", "is_deleted"): "0",
    (FENCE_TABLE, "generation"): "0",
    (FENCE_TABLE, "fence_status"): "OPEN",
}

FENCE_COLUMNS = (
    ("fence_name", "varchar(64)", False, "text"),
    ("generation", "bigint unsigned", False, "int"),
    ("fence_status", "varchar(16)", False, "text"),
    ("operation_run_id", "varchar(128)", True, "text"),
    ("activated_by", "varchar(128)", True, "text"),
    ("activated_at", "datetime", True, "datetime"),
    ("reopened_by", "varchar(128)", True, "text"),
    ("reopened_at", "datetime", True, "datetime"),
    ("gmt_create", "datetime", False, "datetime"),
    ("gmt_updated", "datetime", False, "datetime"),
)


def columns() -> dict[str, tuple[tuple[str, str, bool, str], ...]]:
    result = {
        table: tuple(
            (
                column.name,
                column.sql_type.lower(),
                column.nullable,
                column.kind,
            )
            for column in contract.columns
        )
        for table, contract in TABLE_CONTRACTS.items()
    }
    result[FENCE_TABLE] = FENCE_COLUMNS
    return result
