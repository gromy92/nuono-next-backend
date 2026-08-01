from __future__ import annotations

from schema_migrations.mysql_database import MySqlMigrationDatabase
from schema_migrations.mysql_support import MySqlExecutionError


ELIGIBILITY = "product_forwarder_transport_eligibility"
ANCHOR = "product_forwarder_eligibility_scope_anchor"
FACT_TABLES = (
    "forwarder_quote_version",
    "forwarder_quote_service_line",
    "forwarder_quote_cargo_category",
    "forwarder_quote_base_price",
    "forwarder_quote_transport_fee",
    "forwarder_quote_route_template",
    "forwarder_quote_route_template_segment",
    "forwarder_quote_numeric_adjustment",
    "forwarder_quote_numeric_adjustment_log",
    "procurement_shipping_order_line",
    "product_management_id_sequence",
)


def verify_forwarder_atomic_guards(test_case, database, migration):
    _verify_observational_rerun(test_case, database, migration)
    _verify_target_hash_drift(test_case, database, migration)
    _verify_old_window_drift(test_case, database, migration)
    _verify_extra_legacy_trigger(test_case, database, migration)
    _verify_snapshot_shape_drift(test_case, database, migration)
    _verify_anchor_only_pair(test_case, database, migration)
    _verify_scope_lock_contention_and_disconnect(test_case, database)
    _verify_snapshot_mdl_blocks_ddl(test_case, database)
    _verify_sequence_tracks_rule_max(test_case, database, migration)


def _verify_observational_rerun(test_case, database, migration):
    database.client.execute(
        "UPDATE forwarder_quote_route_template SET gmt_updated='2026-07-31 01:02:03' "
        "WHERE route_code='YT-SAU-SEA-FBN-RUH';"
        "UPDATE forwarder_quote_route_template_segment SET "
        "gmt_updated='2026-07-31 01:02:03' WHERE route_code='YT-SAU-SEA-FBN-RUH' "
        "AND segment_role='HEADHAUL';"
        "UPDATE product_management_id_sequence SET gmt_updated='2026-07-31 01:02:03' "
        "WHERE sequence_name='product_forwarder_transport_eligibility';"
    )
    before = _full_state(database)
    database.run_script(migration)
    test_case.assertTrue(database.postcheck(migration))
    test_case.assertEqual(before, _full_state(database))


def _verify_target_hash_drift(test_case, database, migration):
    row_id, original = database.client.execute(
        "SELECT id,match_priority FROM forwarder_quote_cargo_category "
        "WHERE service_code='YT-SAU-SEA-FBN-RUH-20260728' ORDER BY id LIMIT 1;"
    ).split("\t")
    database.client.execute(
        "UPDATE forwarder_quote_cargo_category SET match_priority="
        f"{int(original) + 1000} WHERE id={row_id};"
    )
    try:
        _assert_failed_unchanged(test_case, database, migration)
    finally:
        database.client.execute(
            "UPDATE forwarder_quote_cargo_category SET match_priority="
            f"{int(original)} WHERE id={row_id};"
        )
    test_case.assertTrue(database.postcheck(migration))


def _verify_old_window_drift(test_case, database, migration):
    database.client.execute(
        "UPDATE forwarder_quote_version SET effective_to=NULL "
        "WHERE version_no='YT-SAU-UNDATED-001';"
    )
    try:
        _assert_failed_unchanged(test_case, database, migration)
    finally:
        database.client.execute(
            "UPDATE forwarder_quote_version SET effective_to='2026-07-27' "
            "WHERE version_no='YT-SAU-UNDATED-001';"
        )
    test_case.assertTrue(database.postcheck(migration))


def _verify_extra_legacy_trigger(test_case, database, migration):
    name = "trg_fq_numeric_adjustment_ci_extra"
    database.client.execute(
        f"CREATE TRIGGER {name} BEFORE INSERT ON forwarder_quote_numeric_adjustment "
        "FOR EACH ROW FOLLOWS trg_fq_numeric_adjustment_retired_bi "
        "SET NEW.reason=NEW.reason;"
    )
    try:
        test_case.assertEqual(
            "2",
            database.client.execute(
                "SELECT action_order FROM information_schema.triggers "
                f"WHERE trigger_schema=DATABASE() AND trigger_name='{name}';"
            ),
        )
        _assert_failed_unchanged(test_case, database, migration)
    finally:
        database.client.execute(f"DROP TRIGGER IF EXISTS {name};")
    test_case.assertTrue(database.postcheck(migration))


def _verify_snapshot_shape_drift(test_case, database, migration):
    database.client.execute(
        "ALTER TABLE procurement_shipping_order_line DROP CHECK "
        "chk_shipping_line_eligibility_snapshot;"
    )
    try:
        _assert_failed_unchanged(test_case, database, migration)
    finally:
        database.client.execute(
            "ALTER TABLE procurement_shipping_order_line ADD CONSTRAINT "
            "chk_shipping_line_eligibility_snapshot CHECK "
            "(eligibility_status_snapshot IS NULL OR "
            "CAST(eligibility_status_snapshot AS BINARY) IN "
            "(CAST('SUPPORTED' AS BINARY),CAST('INQUIRY_REQUIRED' AS BINARY),"
            "CAST('UNSUPPORTED' AS BINARY)));"
        )
    test_case.assertTrue(database.postcheck(migration))


def _verify_anchor_only_pair(test_case, database, migration):
    database.client.execute(f"DROP TABLE {ELIGIBILITY};")
    before = _full_state(database, include_eligibility=False)
    with test_case.assertRaises(MySqlExecutionError) as caught:
        database.run_script(migration)
    test_case.assertEqual(3819, caught.exception.error_code)
    test_case.assertEqual(before, _full_state(database, include_eligibility=False))
    database.client.execute(f"DROP TABLE {ANCHOR};")
    database.run_script(migration)
    test_case.assertTrue(database.postcheck(migration))


def _verify_scope_lock_contention_and_disconnect(test_case, database):
    holder = _sibling_database(database)
    test_case.addCleanup(holder.close)
    holder.client.acquire_lock("nuono:ci:forwarder-scope-pair-holder", 1)
    holder.client.execute(
        f"LOCK TABLES {ANCHOR} WRITE,{ELIGIBILITY} WRITE;"
    )
    with test_case.assertRaises(MySqlExecutionError) as caught:
        database.client.execute(
            "SET SESSION lock_wait_timeout=1;"
            f"LOCK TABLES {ANCHOR} WRITE,{ELIGIBILITY} WRITE;",
            timeout_seconds=5,
        )
    test_case.assertEqual(1205, caught.exception.error_code)
    holder.close()
    test_case.assertEqual(
        "0\t0",
        database.client.execute(
            "SET SESSION lock_wait_timeout=1;"
            f"LOCK TABLES {ANCHOR} WRITE,{ELIGIBILITY} WRITE;"
            f"SELECT (SELECT COUNT(*) FROM {ANCHOR}),"
            f"(SELECT COUNT(*) FROM {ELIGIBILITY});UNLOCK TABLES;"
        ),
    )


def _verify_snapshot_mdl_blocks_ddl(test_case, database):
    holder = _sibling_database(database)
    test_case.addCleanup(holder.close)
    holder.client.acquire_lock("nuono:ci:forwarder-snapshot-mdl-holder", 1)
    holder.client.execute(
        "START TRANSACTION;SELECT id FROM procurement_shipping_order_line "
        "WHERE id=-1 FOR UPDATE;"
    )
    with test_case.assertRaises(MySqlExecutionError) as caught:
        database.client.execute(
            "SET SESSION lock_wait_timeout=1;"
            "ALTER TABLE procurement_shipping_order_line ALTER COLUMN "
            "eligibility_status_snapshot SET DEFAULT NULL;",
            timeout_seconds=5,
        )
    test_case.assertEqual(1205, caught.exception.error_code)
    holder.close()


def _verify_sequence_tracks_rule_max(test_case, database, migration):
    database.client.execute(
        f"INSERT INTO {ELIGIBILITY} (id,owner_user_id,logical_store_id,partner_sku,"
        "site_code,forwarder_code,transport_mode,eligibility_status,effective_from) "
        "VALUES (480001,307,108065,'ATOMIC-GUARD','SA','ET','AIR','UNSUPPORTED',"
        "'2026-08-01');"
    )
    database.run_script(migration)
    test_case.assertTrue(database.postcheck(migration))
    test_case.assertGreaterEqual(
        int(database.client.execute(
            "SELECT next_id FROM product_management_id_sequence WHERE "
            "sequence_name='product_forwarder_transport_eligibility';"
        )),
        480001,
    )
    database.client.execute(f"DELETE FROM {ELIGIBILITY} WHERE id=480001;")
    before = database.client.execute(
        "SELECT next_id,DATE_FORMAT(gmt_updated,'%Y-%m-%d %H:%i:%s') FROM "
        "product_management_id_sequence WHERE "
        "sequence_name='product_forwarder_transport_eligibility';"
    )
    database.run_script(migration)
    test_case.assertTrue(database.postcheck(migration))
    test_case.assertEqual(
        before,
        database.client.execute(
            "SELECT next_id,DATE_FORMAT(gmt_updated,'%Y-%m-%d %H:%i:%s') FROM "
            "product_management_id_sequence WHERE "
            "sequence_name='product_forwarder_transport_eligibility';"
        ),
    )


def _assert_failed_unchanged(test_case, database, migration):
    before = _full_state(database)
    with test_case.assertRaises(MySqlExecutionError) as caught:
        database.run_script(migration)
    test_case.assertEqual(3819, caught.exception.error_code)
    test_case.assertEqual(before, _full_state(database))


def _full_state(database, include_eligibility=True):
    tables = (*FACT_TABLES, ANCHOR)
    if include_eligibility:
        tables = (*tables, ELIGIBILITY)
    table_list = ",".join(tables)
    quoted = ",".join(f"'{table}'" for table in (*FACT_TABLES, ANCHOR, ELIGIBILITY))
    return database.client.execute(
        "SET SESSION group_concat_max_len=16777216;"
        f"CHECKSUM TABLE {table_list};"
        "SELECT COALESCE(SHA2(GROUP_CONCAT(CONCAT_WS('|',table_name,ordinal_position,"
        "column_name,column_type,is_nullable,COALESCE(column_default,'<NULL>'),extra,"
        "COALESCE(generation_expression,''),COALESCE(collation_name,'-')) "
        "ORDER BY table_name,ordinal_position SEPARATOR '\\n'),256),'<EMPTY>') "
        "FROM information_schema.columns WHERE table_schema=DATABASE() "
        f"AND table_name IN ({quoted});"
        "SELECT COALESCE(SHA2(GROUP_CONCAT(CONCAT_WS('|',tc.table_name,"
        "tc.constraint_name,tc.constraint_type,tc.enforced,COALESCE(cc.check_clause,'')) "
        "ORDER BY tc.table_name,tc.constraint_name SEPARATOR '\\n'),256),'<EMPTY>') "
        "FROM information_schema.table_constraints tc LEFT JOIN "
        "information_schema.check_constraints cc ON cc.constraint_schema="
        "tc.constraint_schema AND cc.constraint_name=tc.constraint_name WHERE "
        f"tc.constraint_schema=DATABASE() AND tc.table_name IN ({quoted});"
        "SELECT COALESCE(SHA2(GROUP_CONCAT(CONCAT_WS('|',table_name,index_name,"
        "non_unique,seq_in_index,column_name,index_type,visible,COALESCE(sub_part,'-')) "
        "ORDER BY table_name,index_name,seq_in_index SEPARATOR '\\n'),256),'<EMPTY>') "
        "FROM information_schema.statistics WHERE table_schema=DATABASE() "
        f"AND table_name IN ({quoted});"
        "SELECT COALESCE(SHA2(GROUP_CONCAT(CONCAT_WS('|',trigger_name,event_object_table,"
        "event_manipulation,action_timing,action_orientation,action_order,action_statement) "
        "ORDER BY trigger_name SEPARATOR '\\n'),256),'<EMPTY>') FROM "
        "information_schema.triggers WHERE trigger_schema=DATABASE() AND "
        "event_object_table IN ('forwarder_quote_numeric_adjustment',"
        "'forwarder_quote_numeric_adjustment_log');"
    )


def _sibling_database(database):
    client = database.client
    return MySqlMigrationDatabase(
        client.source_defaults_file,
        expected_schema=client.expected_schema,
        expected_host=client.expected_host,
        expected_port=client.expected_port,
        execution_timeout_seconds=10,
    )
