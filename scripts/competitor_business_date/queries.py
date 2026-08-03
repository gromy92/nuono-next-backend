"""Fixed, read-only MySQL 8 extracts for the competitor date correction.

Every template emits one base64-encoded JSON object per source row.  There is
no caller-controlled SQL, identifier, or predicate in this module.
"""
from __future__ import annotations

from types import MappingProxyType

from .mysql_cli import encoded_json_select
from .schema_query import SERVER_SCHEMA_FINGERPRINT_SQL


SNAPSHOT_CUTOFF = 358244
RANK_CUTOFF = 1001946
WRITER_CUTOVER = "2026-07-28 20:00:50"
LIST_ONLY_RUNTIME = "2026-07-29 16:28:40"

_SNAPSHOT_TABLE = "operations_competitor_product_snapshot"
_EVENT_TABLE = "operations_competitor_product_change_event"
_RANK_TABLE = "operations_competitor_rank_fact"
_KEYWORD_RUN_TABLE = "operations_competitor_keyword_run"
_SEQUENCE_TABLE = "operations_competitor_analysis_id_sequence"

_SNAPSHOT_COLUMNS = (
    ("id", "raw"), ("owner_user_id", "raw"), ("watch_product_id", "raw"),
    ("competitor_product_id", "raw"), ("subject_type", "raw"),
    ("site_code", "raw"), ("noon_product_code", "raw"), ("code_type", "raw"),
    ("fact_date", "char"), ("captured_at", "char"), ("source_task_id", "raw"),
    ("source_run_id", "raw"), ("detail_url", "raw"), ("title_en", "raw"),
    ("title_ar", "raw"), ("brand", "raw"), ("seller_name", "raw"),
    ("price_amount", "char"), ("currency_code", "raw"), ("rating", "char"),
    ("review_count", "raw"), ("main_image_url_raw", "raw"),
    ("main_image_url_normalized", "raw"), ("main_image_asset_key", "raw"),
    ("supermall_enabled", "bit"), ("sold_recently_text", "raw"),
    ("logistics_tags_json", "json"), ("badges_json", "json"),
    ("availability_status", "raw"), ("snapshot_hash", "raw"),
    ("raw_detail_json", "json"), ("is_deleted", "bit"), ("created_by", "raw"),
    ("updated_by", "raw"), ("gmt_create", "char"), ("gmt_updated", "char"),
)
_EVENT_COLUMNS = (
    ("id", "raw"), ("snapshot_id", "raw"), ("previous_snapshot_id", "raw"),
    ("owner_user_id", "raw"), ("watch_product_id", "raw"),
    ("competitor_product_id", "raw"), ("subject_type", "raw"),
    ("site_code", "raw"), ("noon_product_code", "raw"), ("fact_date", "char"),
    ("field_key", "raw"), ("field_label", "raw"), ("change_type", "raw"),
    ("old_value_json", "json"), ("new_value_json", "json"),
    ("severity", "raw"), ("is_deleted", "bit"), ("created_by", "raw"),
    ("updated_by", "raw"), ("gmt_create", "char"), ("gmt_updated", "char"),
)
_RANK_COLUMNS = (
    ("id", "raw"), ("watch_product_id", "raw"), ("keyword_id", "raw"),
    ("keyword_run_id", "raw"), ("search_run_id", "raw"), ("fact_time", "char"),
    ("fact_date", "char"), ("tracked_product_type", "raw"),
    ("rank_channel", "raw"), ("noon_product_code", "raw"),
    ("rank_status", "raw"), ("rank_no", "raw"), ("scan_depth", "raw"),
    ("is_sponsored", "bit"), ("price_amount", "char"),
    ("currency_code", "raw"), ("rating", "char"), ("review_count", "raw"),
    ("source_result_id", "raw"), ("is_deleted", "bit"), ("created_by", "raw"),
    ("updated_by", "raw"), ("gmt_create", "char"), ("gmt_updated", "char"),
)
_KEYWORD_RUN_COLUMNS = (
    ("id", "raw"), ("search_run_id", "raw"), ("keyword_id", "raw"),
    ("keyword_snapshot", "raw"), ("locale_snapshot", "raw"),
    ("provider_status", "raw"), ("result_count", "raw"),
    ("requested_result_limit", "raw"), ("source_url", "raw"),
    ("parser_version", "raw"), ("provider_http_status", "raw"),
    ("response_hash", "raw"), ("captured_at", "char"), ("error_code", "raw"),
    ("error_message", "raw"), ("started_at", "char"), ("finished_at", "char"),
    ("is_deleted", "bit"), ("created_by", "raw"), ("updated_by", "raw"),
    ("gmt_create", "char"), ("gmt_updated", "char"),
)


def _value(alias: str, column: str, kind: str) -> str:
    reference = f"`{alias}`.`{column}`"
    if kind == "bit":
        return f"CAST({reference} AS UNSIGNED)"
    if kind == "json":
        return f"CAST({reference} AS CHAR CHARACTER SET utf8mb4)"
    if kind == "char":
        return f"CAST({reference} AS CHAR)"
    return reference


def _row_json(alias: str, columns: tuple[tuple[str, str], ...], *extra: tuple[str, str]) -> str:
    pairs = [(name, _value(alias, name, kind)) for name, kind in columns]
    pairs.extend(extra)
    return "JSON_OBJECT(" + ", ".join(f"'{name}', {value}" for name, value in pairs) + ")"


_AFFECTED_KEYS = f"""
affected_keys AS (
    SELECT DISTINCT watch_product_id, subject_type, noon_product_code
    FROM `{_SNAPSHOT_TABLE}`
    WHERE id <= {SNAPSHOT_CUTOFF} AND is_deleted = b'0'
)"""

AMBIGUITY_AUDIT_SQL = f"""
WITH {_AFFECTED_KEYS},
audit_rows AS (
    SELECT 'SNAPSHOT_WITHIN_CUTOFF_UPDATED_AFTER_CUTOVER' AS audit_kind,
           '{_SNAPSHOT_TABLE}' AS table_name, id,
           captured_at AS business_time, fact_date, gmt_create, gmt_updated
    FROM `{_SNAPSHOT_TABLE}`
    WHERE id <= {SNAPSHOT_CUTOFF} AND is_deleted = b'0'
      AND gmt_updated > '{WRITER_CUTOVER}'
    UNION ALL
    SELECT 'SNAPSHOT_OUTSIDE_CUTOFF_REQUIRES_CLOCK_REVIEW', '{_SNAPSHOT_TABLE}',
           id, captured_at, fact_date, gmt_create, gmt_updated
    FROM `{_SNAPSHOT_TABLE}`
    WHERE id > {SNAPSHOT_CUTOFF} AND is_deleted = b'0'
      AND (gmt_create <= '{WRITER_CUTOVER}' OR captured_at < '{WRITER_CUTOVER}')
    UNION ALL
    SELECT 'RANK_WITHIN_CUTOFF_UPDATED_AFTER_CUTOVER', '{_RANK_TABLE}',
           id, fact_time, fact_date, gmt_create, gmt_updated
    FROM `{_RANK_TABLE}`
    WHERE id <= {RANK_CUTOFF} AND is_deleted = b'0'
      AND gmt_updated > '{WRITER_CUTOVER}'
    UNION ALL
    SELECT 'RANK_OUTSIDE_CUTOFF_REQUIRES_CLOCK_REVIEW', '{_RANK_TABLE}',
           id, fact_time, fact_date, gmt_create, gmt_updated
    FROM `{_RANK_TABLE}`
    WHERE id > {RANK_CUTOFF} AND is_deleted = b'0'
      AND (gmt_create <= '{WRITER_CUTOVER}' OR fact_time < '{WRITER_CUTOVER}')
    UNION ALL
    SELECT 'SNAPSHOT_EVENT_CONTRACT_SPANS_LIST_V1_CUTOVER',
           '{_SNAPSHOT_TABLE}', s.id, s.captured_at, s.fact_date,
           s.gmt_create, s.gmt_updated
    FROM `{_SNAPSHOT_TABLE}` s
    JOIN affected_keys k
      ON k.watch_product_id = s.watch_product_id
     AND k.subject_type = s.subject_type
     AND k.noon_product_code = s.noon_product_code
    WHERE s.is_deleted = b'0'
      AND s.gmt_create < '{LIST_ONLY_RUNTIME}'
      AND s.gmt_updated >= '{LIST_ONLY_RUNTIME}'
)
""" + encoded_json_select(
    "JSON_OBJECT('audit_kind', audit_kind, 'table_name', table_name, 'id', id, "
    "'business_time', CAST(business_time AS CHAR), "
    "'fact_date', CAST(fact_date AS CHAR), 'gmt_create', CAST(gmt_create AS CHAR), "
    "'gmt_updated', CAST(gmt_updated AS CHAR))",
    "FROM audit_rows ORDER BY table_name, id, audit_kind",
)

SNAPSHOT_CHAIN_SQL = f"WITH {_AFFECTED_KEYS}\n" + encoded_json_select(
    _row_json("s", _SNAPSHOT_COLUMNS),
    f"""FROM `{_SNAPSHOT_TABLE}` s
JOIN affected_keys k
  ON k.watch_product_id = s.watch_product_id
 AND k.subject_type = s.subject_type
 AND k.noon_product_code = s.noon_product_code
WHERE s.is_deleted = b'0'
ORDER BY s.watch_product_id, s.subject_type, s.noon_product_code,
         s.captured_at, s.id""",
)

CHANGE_EVENT_CHAIN_SQL = f"WITH {_AFFECTED_KEYS}\n" + encoded_json_select(
    _row_json("e", _EVENT_COLUMNS),
    f"""FROM `{_EVENT_TABLE}` e
JOIN affected_keys k
  ON k.watch_product_id = e.watch_product_id
 AND k.subject_type = e.subject_type
 AND k.noon_product_code = e.noon_product_code
ORDER BY e.watch_product_id, e.subject_type, e.noon_product_code,
         e.fact_date, e.gmt_create, e.id""",
)

RANK_FACT_ROWS_SQL = f"""
WITH affected_keyword_runs AS (
    SELECT DISTINCT keyword_run_id
    FROM `{_RANK_TABLE}`
    WHERE id <= {RANK_CUTOFF} AND is_deleted = b'0'
)
""" + encoded_json_select(
    _row_json("r", _RANK_COLUMNS),
    f"""FROM `{_RANK_TABLE}` r
JOIN affected_keyword_runs scope ON scope.keyword_run_id = r.keyword_run_id
WHERE r.is_deleted = b'0'
ORDER BY r.id""",
)

KEYWORD_RUN_ROWS_SQL = f"""
WITH legacy_keyword_runs AS (
    SELECT DISTINCT keyword_run_id
    FROM `{_RANK_TABLE}`
    WHERE id <= {RANK_CUTOFF} AND is_deleted = b'0'
)
""" + encoded_json_select(
    _row_json("kr", _KEYWORD_RUN_COLUMNS),
    f"""FROM `{_KEYWORD_RUN_TABLE}` kr
JOIN legacy_keyword_runs scope ON scope.keyword_run_id = kr.id
ORDER BY kr.id""",
)

EVENT_SEQUENCE_SQL = encoded_json_select(
    "JSON_OBJECT("
    "'sequence_name', seq.sequence_name, 'next_id', seq.next_id, "
    "'gmt_create', CAST(seq.gmt_create AS CHAR), "
    "'gmt_updated', CAST(seq.gmt_updated AS CHAR), "
    "'max_event_id', (SELECT COALESCE(MAX(id), 0) "
    f"FROM `{_EVENT_TABLE}`))",
    f"""FROM `{_SEQUENCE_TABLE}` seq
WHERE seq.sequence_name = 'operations_competitor_product_change_event'
ORDER BY seq.sequence_name""",
)

EVENT_CONTRACT_BOUNDARY_AUDIT_SQL = f"WITH {_AFFECTED_KEYS}\n" + encoded_json_select(
    _row_json(
        "e", _EVENT_COLUMNS,
        ("contract_boundary_bucket", f"""CASE
          WHEN e.gmt_create < '{WRITER_CUTOVER}' THEN 'BEFORE_WRITER_CUTOVER_REVIEW'
          WHEN e.gmt_create < '{LIST_ONLY_RUNTIME}' THEN 'AMBIGUOUS_TRANSITION_WINDOW'
          ELSE 'AT_OR_AFTER_CONFIRMED_LIST_ONLY_RUNTIME' END"""),
        ("writer_cutover", f"'{WRITER_CUTOVER}'"),
        ("earliest_confirmed_list_only_runtime", f"'{LIST_ONLY_RUNTIME}'"),
        ("requires_manual_contract_assignment", "1"),
    ),
    f"""FROM `{_EVENT_TABLE}` e
JOIN affected_keys k
  ON k.watch_product_id = e.watch_product_id
 AND k.subject_type = e.subject_type
 AND k.noon_product_code = e.noon_product_code
ORDER BY e.gmt_create, e.id""",
)

PREFLIGHT_COUNT_SUMMARY_SQL = f"""
WITH {_AFFECTED_KEYS},
legacy_ranks AS (
    SELECT id, keyword_run_id FROM `{_RANK_TABLE}`
    WHERE id <= {RANK_CUTOFF} AND is_deleted = b'0'
),
metrics AS (
    SELECT 'legacy_snapshot_candidates' metric, COUNT(*) row_count
    FROM `{_SNAPSHOT_TABLE}`
    WHERE id <= {SNAPSHOT_CUTOFF} AND is_deleted = b'0'
    UNION ALL SELECT 'affected_business_keys', COUNT(*) FROM affected_keys
    UNION ALL SELECT 'affected_active_snapshot_chain_rows', COUNT(*)
    FROM `{_SNAPSHOT_TABLE}` s JOIN affected_keys k
      ON k.watch_product_id=s.watch_product_id AND k.subject_type=s.subject_type
     AND k.noon_product_code=s.noon_product_code WHERE s.is_deleted=b'0'
    UNION ALL SELECT 'affected_change_event_rows', COUNT(*)
    FROM `{_EVENT_TABLE}` e JOIN affected_keys k
      ON k.watch_product_id=e.watch_product_id AND k.subject_type=e.subject_type
     AND k.noon_product_code=e.noon_product_code
    UNION ALL SELECT 'legacy_rank_candidates', COUNT(*) FROM legacy_ranks
    UNION ALL SELECT 'affected_rank_scope_rows', COUNT(*)
    FROM `{_RANK_TABLE}` r
    JOIN (SELECT DISTINCT keyword_run_id FROM legacy_ranks) scope
      ON scope.keyword_run_id = r.keyword_run_id
    WHERE r.is_deleted = b'0'
    UNION ALL SELECT 'rank_cross_cutoff_same_run', COUNT(*)
    FROM `{_RANK_TABLE}` r
    JOIN (SELECT DISTINCT keyword_run_id FROM legacy_ranks) scope
      ON scope.keyword_run_id = r.keyword_run_id
    WHERE r.is_deleted = b'0' AND r.id > {RANK_CUTOFF}
    UNION ALL SELECT 'legacy_distinct_keyword_runs', COUNT(DISTINCT keyword_run_id)
    FROM legacy_ranks
    UNION ALL SELECT 'snapshot_within_cutoff_updated_after_cutover', COUNT(*)
    FROM `{_SNAPSHOT_TABLE}` WHERE id <= {SNAPSHOT_CUTOFF} AND is_deleted=b'0'
      AND gmt_updated > '{WRITER_CUTOVER}'
    UNION ALL SELECT 'snapshot_outside_cutoff_requires_clock_review', COUNT(*)
    FROM `{_SNAPSHOT_TABLE}` WHERE id > {SNAPSHOT_CUTOFF} AND is_deleted=b'0'
      AND (gmt_create <= '{WRITER_CUTOVER}' OR captured_at < '{WRITER_CUTOVER}')
    UNION ALL SELECT 'rank_within_cutoff_updated_after_cutover', COUNT(*)
    FROM `{_RANK_TABLE}` WHERE id <= {RANK_CUTOFF} AND is_deleted=b'0'
      AND gmt_updated > '{WRITER_CUTOVER}'
    UNION ALL SELECT 'rank_outside_cutoff_requires_clock_review', COUNT(*)
    FROM `{_RANK_TABLE}` WHERE id > {RANK_CUTOFF} AND is_deleted=b'0'
      AND (gmt_create <= '{WRITER_CUTOVER}' OR fact_time < '{WRITER_CUTOVER}')
    UNION ALL SELECT 'snapshot_event_contract_spans_list_v1_cutover', COUNT(*)
    FROM `{_SNAPSHOT_TABLE}` s JOIN affected_keys k
      ON k.watch_product_id=s.watch_product_id AND k.subject_type=s.subject_type
     AND k.noon_product_code=s.noon_product_code
    WHERE s.is_deleted=b'0' AND s.gmt_create < '{LIST_ONLY_RUNTIME}'
      AND s.gmt_updated >= '{LIST_ONLY_RUNTIME}'
)
""" + encoded_json_select(
    "JSON_OBJECT('metric', metric, 'row_count', row_count)",
    "FROM metrics ORDER BY metric",
)

READ_ONLY_QUERIES = MappingProxyType({
    "server_schema_fingerprint": SERVER_SCHEMA_FINGERPRINT_SQL,
    "ambiguity_audit": AMBIGUITY_AUDIT_SQL,
    "snapshot_chain": SNAPSHOT_CHAIN_SQL,
    "change_event_chain": CHANGE_EVENT_CHAIN_SQL,
    "rank_fact_rows": RANK_FACT_ROWS_SQL,
    "keyword_run_rows": KEYWORD_RUN_ROWS_SQL,
    "event_sequence": EVENT_SEQUENCE_SQL,
    "event_contract_boundary_audit": EVENT_CONTRACT_BOUNDARY_AUDIT_SQL,
    "preflight_count_summary": PREFLIGHT_COUNT_SUMMARY_SQL,
})
