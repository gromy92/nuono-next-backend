from __future__ import annotations

from schema_migrations.mysql_database import MySqlMigrationDatabase
from schema_migrations.mysql_support import MySqlExecutionError, sql_literal


TABLE = "product_forwarder_transport_eligibility"
ANCHOR = "product_forwarder_eligibility_scope_anchor"
FIRST_ID = 379900
LAST_ID = 379999
ANCHOR_STATE_SQL = (
    "SET SESSION group_concat_max_len=16777216;SELECT COUNT(*),COALESCE(SHA2("
    "GROUP_CONCAT(CAST(JSON_ARRAY(owner_user_id,"
    "logical_store_id,partner_sku_normalized,DATE_FORMAT(gmt_create,"
    "'%Y-%m-%d %H:%i:%s'),DATE_FORMAT(gmt_updated,'%Y-%m-%d %H:%i:%s')) AS CHAR) "
    "ORDER BY owner_user_id,logical_store_id,partner_sku_normalized SEPARATOR '\\n'),256),"
    "'<EMPTY>') FROM product_forwarder_eligibility_scope_anchor;"
)


def verify_forwarder_eligibility_binary_guards(test_case, database, migration):
    _verify_anchor_trigger_rejected(test_case, database, migration)
    _verify_missing_and_unnormalized_identity(test_case, database)
    expected_slots = _verify_stable_identity_and_slots(test_case, database)
    database.run_script(migration)
    test_case.assertTrue(database.postcheck(migration))
    test_case.assertEqual(
        "5", database.client.execute(f"SELECT COUNT(*) FROM {ANCHOR};")
    )
    _verify_anchor_contention(test_case, database)
    anchor_state = database.client.execute(ANCHOR_STATE_SQL)
    database.client.execute(
        f"DELETE FROM {TABLE} WHERE id BETWEEN {FIRST_ID} AND {LAST_ID};"
    )
    database.run_script(migration)
    test_case.assertTrue(database.postcheck(migration))
    test_case.assertEqual(anchor_state, database.client.execute(ANCHOR_STATE_SQL))
    test_case.assertEqual(
        "0\t5", database.client.execute(
            f"SELECT (SELECT COUNT(*) FROM {TABLE} WHERE id BETWEEN {FIRST_ID} "
            f"AND {LAST_ID}),(SELECT COUNT(*) FROM {ANCHOR});"
        )
    )
    test_case.assertEqual(5, len(set(expected_slots)))
    _verify_snapshot_guards(test_case, database)


def _verify_anchor_trigger_rejected(test_case, database, migration):
    test_case.assertTrue(database.postcheck(migration))
    database.client.execute(
        f"CREATE TRIGGER trg_ci_pfea_extra BEFORE INSERT ON {ANCHOR} FOR EACH ROW "
        "SET NEW.gmt_updated=NEW.gmt_updated;"
    )
    state_sql = (
        f"SELECT (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema="
        f"DATABASE() AND table_name='{ANCHOR}'),(SELECT COUNT(*) FROM {ANCHOR}),"
        "(SELECT CONCAT(COUNT(*),':',COALESCE(HEX(MAX(action_statement)),'-')) FROM "
        "information_schema.triggers WHERE trigger_schema=DATABASE() AND "
        "trigger_name='trg_ci_pfea_extra');"
    )
    try:
        before = database.client.execute(state_sql)
        with test_case.assertRaises(MySqlExecutionError) as caught:
            database.run_script(migration)
        test_case.assertEqual(3819, caught.exception.error_code)
        test_case.assertEqual(before, database.client.execute(state_sql))
    finally:
        database.client.execute("DROP TRIGGER IF EXISTS trg_ci_pfea_extra;")
    test_case.assertTrue(database.postcheck(migration))


def _verify_missing_and_unnormalized_identity(test_case, database):
    for offset, field in enumerate(("owner", "store", "partner", "site", "forwarder", "mode")):
        _assert_rejected(
            test_case, database, _insert_sql(FIRST_ID + offset, **{field: None}), 1048
        )
    invalid = (
        {"owner": 0}, {"store": 0}, {"partner": ""}, {"site": ""},
        {"forwarder": ""}, {"mode": ""}, {"partner": "sku|#1"},
        {"partner": " SKU|#1"}, {"partner": "SKU|#1 "}, {"site": "sa"},
        {"site": "SA "}, {"forwarder": "et"}, {"forwarder": "ET "},
        {"mode": "sea"}, {"mode": "AIR "}, {"mode": "RAIL"},
        {"status": "unsupported"}, {"version": 0},
    )
    for offset, overrides in enumerate(invalid, 10):
        _assert_rejected(
            test_case, database, _insert_sql(FIRST_ID + offset, **overrides), 3819
        )


def _verify_stable_identity_and_slots(test_case, database):
    base_slot = _slot(307, 108065, "SKU|#1", "SA", "ET", "AIR")
    database.client.execute(_insert_sql(379950, variant=7001))
    test_case.assertEqual(base_slot, _active_slot(database, 379950))
    _assert_rejected(
        test_case, database, _insert_sql(379951, variant=7002), 1062
    )
    database.client.execute(
        f"UPDATE {TABLE} SET effective_to='2026-08-02' WHERE id=379950;"
        + _insert_sql(379952, variant=None, version=2, effective_from="2026-08-03")
    )
    test_case.assertEqual(
        f"<NULL>\t7001\n{base_slot}\t<NULL>",
        database.client.execute(
            f"SELECT COALESCE(active_scope_slot,'<NULL>'),COALESCE(CAST("
            f"product_variant_id AS CHAR),'<NULL>') FROM {TABLE} WHERE id IN "
            "(379950,379952) ORDER BY id;"
        ),
    )

    store_slot = _slot(307, 108066, "SKU|#1", "SA", "ET", "AIR")
    prefix_slot = _slot(307, 108065, "SKU|#10", "SA", "ET", "AIR")
    database.client.execute(
        _insert_sql(379953, store=108066, partner="SKU|#1")
        + _insert_sql(379954, partner="SKU|#10")
    )
    test_case.assertEqual(store_slot, _active_slot(database, 379953))
    test_case.assertEqual(prefix_slot, _active_slot(database, 379954))
    test_case.assertNotEqual(base_slot, prefix_slot)

    cjk = ("货号#|一", "中东", "承运|#商", "AIR")
    cjk_slot = _slot(307, 108067, *cjk)
    database.client.execute(
        _insert_sql(
            379955, store=108067, partner=cjk[0], site=cjk[1],
            forwarder=cjk[2], mode=cjk[3]
        )
    )
    test_case.assertEqual(cjk_slot, _active_slot(database, 379955))

    maximum = ("🧰" * 100, "仓" * 20, "运" * 80, "SEA")
    maximum_id = 9223372036854775807
    maximum_slot = _slot(maximum_id, maximum_id, *maximum)
    database.client.execute(
        _insert_sql(
            379956, owner=maximum_id, store=maximum_id, partner=maximum[0],
            site=maximum[1], forwarder=maximum[2], mode=maximum[3]
        )
    )
    test_case.assertEqual(maximum_slot, _active_slot(database, 379956))
    test_case.assertEqual(
        f"400\t60\t240\t{len(maximum_slot.encode('utf-8'))}\t{len(maximum_slot)}",
        database.client.execute(
            f"SELECT OCTET_LENGTH(partner_sku),OCTET_LENGTH(site_code),"
            f"OCTET_LENGTH(forwarder_code),OCTET_LENGTH(active_scope_slot),"
            f"CHAR_LENGTH(active_scope_slot) FROM {TABLE} WHERE id=379956;"
        ),
    )
    return base_slot, store_slot, prefix_slot, cjk_slot, maximum_slot


def _verify_anchor_contention(test_case, database):
    first, second = _sibling_database(database), _sibling_database(database)
    test_case.addCleanup(first.close)
    test_case.addCleanup(second.close)
    lock_name = "nuono:ci:forwarder-anchor-order"
    ordered_rows = (
        "SELECT owner_user_id,logical_store_id,partner_sku_normalized "
        f"FROM {ANCHOR} WHERE owner_user_id=307 AND logical_store_id=108065 "
        "AND partner_sku_normalized IN ('SKU|#1','SKU|#10') ORDER BY "
        "owner_user_id,logical_store_id,partner_sku_normalized FOR UPDATE;"
    )
    first.client.acquire_lock(lock_name, 1)
    try:
        test_case.assertEqual(
            "307\t108065\tSKU|#1\n307\t108065\tSKU|#10",
            first.client.execute("START TRANSACTION;" + ordered_rows),
        )
        with test_case.assertRaises(MySqlExecutionError) as caught:
            second.client.execute(
                "SET SESSION innodb_lock_wait_timeout=1;START TRANSACTION;"
                + ordered_rows, timeout_seconds=5
            )
        test_case.assertEqual(1205, caught.exception.error_code)
        test_case.assertEqual(
            "货号#|一", second.client.execute(
                "START TRANSACTION;SELECT partner_sku_normalized FROM "
                f"{ANCHOR} WHERE owner_user_id=307 AND logical_store_id=108067 "
                f"AND partner_sku_normalized={sql_literal('货号#|一')} FOR UPDATE;ROLLBACK;"
            )
        )
    finally:
        try:
            first.client.execute("ROLLBACK;")
        finally:
            first.client.release_lock(lock_name)


def _verify_snapshot_guards(test_case, database):
    for value in ("unsupported", "SUPPORTED "):
        _assert_rejected(
            test_case, database,
            "UPDATE procurement_shipping_order_line "
            f"SET eligibility_status_snapshot='{value}' WHERE id=1;", 3819
        )


def _insert_sql(row_id, *, owner=307, store=108065, partner="SKU|#1", site="SA",
                forwarder="ET", mode="AIR", status="UNSUPPORTED", variant=None,
                version=1, effective_from="2026-08-01"):
    values = (row_id, owner, variant, store, partner, site, forwarder, mode, status)
    encoded = ",".join(_sql_value(value) for value in values)
    return (
        f"INSERT INTO {TABLE} (id,owner_user_id,product_variant_id,logical_store_id,"
        "partner_sku,site_code,forwarder_code,transport_mode,eligibility_status,"
        f"effective_from,version) VALUES ({encoded},{sql_literal(effective_from)},{version});"
    )


def _sql_value(value):
    if value is None:
        return "NULL"
    return str(value) if isinstance(value, int) else sql_literal(value)


def _slot(owner, store, partner, site, forwarder, mode):
    values = (str(owner), str(store), partner.strip().upper(), site.strip().upper(),
              forwarder.strip().upper(), mode.strip().upper())
    return "".join(f"{len(value.encode('utf-8'))}#{value}" for value in values)


def _active_slot(database, row_id):
    return database.client.execute(
        f"SELECT active_scope_slot FROM {TABLE} WHERE id={row_id};"
    )


def _assert_rejected(test_case, database, statement, error_code):
    with test_case.assertRaises(MySqlExecutionError) as caught:
        database.client.execute(statement)
    test_case.assertEqual(error_code, caught.exception.error_code)


def _sibling_database(database):
    client = database.client
    return MySqlMigrationDatabase(
        client.source_defaults_file, expected_schema=client.expected_schema,
        expected_host=client.expected_host, expected_port=client.expected_port,
        execution_timeout_seconds=10,
    )
