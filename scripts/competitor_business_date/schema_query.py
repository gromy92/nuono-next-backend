"""Exact server/schema fingerprint query for correction planning and apply."""
from __future__ import annotations

from .mysql_cli import encoded_json_select


TABLES = (
    "operations_competitor_product_snapshot",
    "operations_competitor_product_change_event",
    "operations_competitor_rank_fact",
    "operations_competitor_keyword_run",
    "operations_competitor_analysis_id_sequence",
    "operations_competitor_correction_writer_fence",
)


SERVER_SCHEMA_FINGERPRINT_SQL = f"""
WITH fingerprint_rows AS (
    SELECT 0 group_order, 'server' record_type, '' table_name,
           '' object_name, 0 ordinal_position,
           JSON_OBJECT(
             'database', DATABASE(), 'version', VERSION(),
             'version_comment', @@version_comment,
             'server_uuid', @@server_uuid, 'hostname', @@hostname,
             'port', @@port, 'max_allowed_packet', @@max_allowed_packet,
             'global_time_zone', @@GLOBAL.time_zone,
             'session_time_zone', @@SESSION.time_zone,
             'system_time_zone', @@system_time_zone,
             'sql_mode', @@SESSION.sql_mode,
             'transaction_isolation', @@SESSION.transaction_isolation
           ) payload
    UNION ALL
    SELECT 1, 'table', t.table_name, '', 0,
           JSON_OBJECT('engine', t.engine, 'row_format', t.row_format,
                       'table_collation', t.table_collation,
                       'table_comment', t.table_comment)
    FROM INFORMATION_SCHEMA.TABLES t
    WHERE t.table_schema = DATABASE() AND t.table_name IN {repr(TABLES)}
    UNION ALL
    SELECT 2, 'column', c.table_name, c.column_name, c.ordinal_position,
           JSON_OBJECT('column_type', c.column_type, 'nullable', c.is_nullable,
                       'default', c.column_default, 'extra', c.extra,
                       'generation_expression', c.generation_expression,
                       'charset', c.character_set_name,
                       'collation', c.collation_name)
    FROM INFORMATION_SCHEMA.COLUMNS c
    WHERE c.table_schema = DATABASE() AND c.table_name IN {repr(TABLES)}
    UNION ALL
    SELECT 3, 'index', s.table_name, s.index_name, s.seq_in_index,
           JSON_OBJECT('non_unique', s.non_unique,
                       'column_name', s.column_name, 'sub_part', s.sub_part,
                       'index_type', s.index_type, 'visible', s.is_visible,
                       'expression', s.expression, 'collation', s.collation)
    FROM INFORMATION_SCHEMA.STATISTICS s
    WHERE s.table_schema = DATABASE() AND s.table_name IN {repr(TABLES)}
    UNION ALL
    SELECT 4, 'constraint', tc.table_name, tc.constraint_name, 0,
           JSON_OBJECT('constraint_type', tc.constraint_type,
                       'enforced', tc.enforced)
    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
    WHERE tc.constraint_schema = DATABASE() AND tc.table_name IN {repr(TABLES)}
    UNION ALL
    SELECT 5, 'key_usage', k.table_name, k.constraint_name, k.ordinal_position,
           JSON_OBJECT('column_name', k.column_name,
                       'referenced_table_schema', k.referenced_table_schema,
                       'referenced_table_name', k.referenced_table_name,
                       'referenced_column_name', k.referenced_column_name)
    FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE k
    WHERE k.constraint_schema = DATABASE() AND k.table_name IN {repr(TABLES)}
    UNION ALL
    SELECT 6, 'check', tc.table_name, tc.constraint_name, 0,
           JSON_OBJECT('check_clause', cc.check_clause)
    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
    JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc
      ON cc.constraint_schema = tc.constraint_schema
     AND cc.constraint_name = tc.constraint_name
    WHERE tc.constraint_schema = DATABASE() AND tc.table_name IN {repr(TABLES)}
    UNION ALL
    SELECT 7, 'trigger', tr.event_object_table, tr.trigger_name, 0,
           JSON_OBJECT('event', tr.event_manipulation,
                       'timing', tr.action_timing,
                       'statement', tr.action_statement)
    FROM INFORMATION_SCHEMA.TRIGGERS tr
    WHERE tr.trigger_schema = DATABASE()
      AND tr.event_object_table IN {repr(TABLES)}
)
""" + encoded_json_select(
    "JSON_OBJECT('record_type', record_type, 'table_name', table_name, "
    "'object_name', object_name, 'ordinal_position', ordinal_position, "
    "'payload_json', CAST(payload AS CHAR CHARACTER SET utf8mb4))",
    "FROM fingerprint_rows "
    "ORDER BY group_order, table_name, object_name, ordinal_position",
)
