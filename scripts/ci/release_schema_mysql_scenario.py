from __future__ import annotations

from pathlib import PurePosixPath

from schema_migrations.catalog import sha256_bytes
from schema_migrations.core import Migration, MigrationError
from schema_migrations.mysql_database import MySqlMigrationDatabase
from schema_migrations.mysql_support import MySqlExecutionError


def prepare_current_release_fixture(database):
    database.client.execute(
        "CREATE TABLE procurement_fulfillment_balance ("
        "id BIGINT NOT NULL,"
        "planned_quantity INT NOT NULL DEFAULT 0,"
        "confirmed_quantity INT NOT NULL DEFAULT 0,"
        "abnormal_quantity INT NOT NULL DEFAULT 0,"
        "reserved_quantity INT NOT NULL DEFAULT 0,"
        "logistics_handoff_quantity INT NOT NULL DEFAULT 0,"
        "available_quantity INT NOT NULL DEFAULT 0,"
        "PRIMARY KEY (id)"
        ") ENGINE=InnoDB;"
        "INSERT INTO procurement_fulfillment_balance VALUES "
        "(1, 10, 5, 0, -1, 0, 6),"
        "(2, 10, 8, 1, 1, 1, 6);"
        "CREATE TABLE procurement_dispatch_plan ("
        "id BIGINT NOT NULL,"
        "owner_user_id BIGINT NOT NULL,"
        "PRIMARY KEY (id)"
        ") ENGINE=InnoDB;"
        "CREATE TABLE procurement_fulfillment_confirmation ("
        "id BIGINT NOT NULL,"
        "owner_user_id BIGINT NOT NULL,"
        "PRIMARY KEY (id)"
        ") ENGINE=InnoDB;"
        "INSERT INTO procurement_dispatch_plan "
        "(id, owner_user_id) VALUES (1, 307);"
        "INSERT INTO procurement_fulfillment_confirmation "
        "(id, owner_user_id) VALUES (1, 307);"
        "CREATE TABLE warehouse_shipping_batch ("
        "id BIGINT NOT NULL,"
        "owner_user_id BIGINT NOT NULL,"
        "batch_no VARCHAR(80) NOT NULL,"
        "is_deleted BIT(1) NOT NULL DEFAULT b'0',"
        "PRIMARY KEY (id),"
        "UNIQUE KEY uk_warehouse_shipping_batch_no (batch_no)"
        ") ENGINE=InnoDB;"
        "INSERT INTO warehouse_shipping_batch (id,owner_user_id,batch_no) "
        "VALUES (1,307,'WB-1');"
        "CREATE TABLE warehouse_packing_box_item ("
        "id BIGINT NOT NULL,"
        "packing_list_id BIGINT NOT NULL,"
        "is_deleted BIT(1) NOT NULL DEFAULT b'0',"
        "PRIMARY KEY (id)"
        ") ENGINE=InnoDB;"
        "CREATE TABLE official_warehouse_asn ("
        "id BIGINT NOT NULL,"
        "owner_user_id BIGINT NOT NULL,"
        "store_code VARCHAR(100) NOT NULL,"
        "site_code VARCHAR(20) NOT NULL,"
        "is_deleted BIT(1) NOT NULL DEFAULT b'0',"
        "PRIMARY KEY (id)"
        ") ENGINE=InnoDB;"
        "CREATE TABLE official_warehouse_appointment ("
        "id BIGINT NOT NULL,"
        "asn_id BIGINT NOT NULL,"
        "owner_user_id BIGINT NOT NULL,"
        "store_code VARCHAR(100) NOT NULL,"
        "site_code VARCHAR(20) NOT NULL,"
        "project_code VARCHAR(100) NOT NULL,"
        "partner_id VARCHAR(80) NOT NULL,"
        "noon_asn_nr VARCHAR(120) NOT NULL,"
        "status VARCHAR(40) NOT NULL DEFAULT 'PENDING',"
        "attempt_count INT NOT NULL DEFAULT 0,"
        "is_deleted BIT(1) DEFAULT b'0',"
        "PRIMARY KEY (id)"
        ") ENGINE=InnoDB;"
    )


def verify_applied_schema(
    test_case,
    database,
    history,
    integrity,
    request_idempotency,
    packing_index,
):
    for migration in (integrity, request_idempotency, packing_index):
        test_case.assertTrue(database.postcheck(migration))
        database.run_script(migration)
        database.run_script(migration)
        test_case.assertTrue(database.postcheck(migration))

    test_case.assertEqual(
        "2",
        database.client.execute(
            "SELECT ("
            "(SELECT COUNT(*) FROM procurement_dispatch_plan "
            " WHERE client_request_id IS NULL AND request_fingerprint IS NULL)"
            "+"
            "(SELECT COUNT(*) FROM procurement_fulfillment_confirmation "
            " WHERE client_request_id IS NULL AND request_fingerprint IS NULL)"
            ");"
        ),
    )
    database.client.execute(
        "INSERT INTO procurement_dispatch_plan "
        "(id, owner_user_id, client_request_id, request_fingerprint) "
        "VALUES (2, 307, 'dispatch-request-1', REPEAT('a', 64)),"
        "       (3, 308, 'dispatch-request-1', REPEAT('b', 64));"
        "INSERT INTO procurement_fulfillment_confirmation "
        "(id, owner_user_id, client_request_id, request_fingerprint) "
        "VALUES (2, 307, 'receipt-request-1', REPEAT('c', 64)),"
        "       (3, 308, 'receipt-request-1', REPEAT('d', 64));"
    )
    _assert_mysql_rejects(
        test_case,
        database,
        "INSERT INTO procurement_dispatch_plan "
        "(id, owner_user_id, client_request_id, request_fingerprint) "
        "VALUES (4, 307, 'dispatch-request-1', REPEAT('e', 64));",
        1062,
    )
    _assert_mysql_rejects(
        test_case,
        database,
        "INSERT INTO procurement_fulfillment_confirmation "
        "(id, owner_user_id, client_request_id, request_fingerprint) "
        "VALUES (4, 307, 'receipt-request-1', REPEAT('f', 64));",
        1062,
    )
    _verify_packing_index(test_case, database, packing_index)
    _verify_fulfillment_balance_constraints(test_case, database)
    _verify_history_postcheck(test_case, database, history)


def verify_lock_contention(test_case, defaults_file, expected_schema):
    databases = [
        MySqlMigrationDatabase(
            defaults_file,
            expected_schema=expected_schema,
            expected_host="127.0.0.1",
            expected_port=3306,
        )
        for _ in range(2)
    ]
    for database in databases:
        test_case.addCleanup(database.close)
    first, second = databases
    first.acquire_lock(1)
    try:
        with test_case.assertRaisesRegex(MigrationError, "not acquired"):
            second.acquire_lock(0)
    finally:
        second.release_lock()
        first.release_lock()


def build_probe_migration(root, order, key, script, postcheck):
    script_file = root / key
    postcheck_file = root / ("postcheck_" + key)
    script_file.write_text(script, encoding="utf-8")
    postcheck_file.write_text(postcheck, encoding="utf-8")
    return Migration(
        order,
        key,
        "AUTO_ADDITIVE",
        PurePosixPath("db/init") / key,
        PurePosixPath("db/postcheck") / key,
        sha256_bytes(script.encode("utf-8")),
        sha256_bytes(postcheck.encode("utf-8")),
        script.encode("utf-8"),
        postcheck.encode("utf-8"),
        script_file,
        postcheck_file,
    )


def _verify_packing_index(test_case, database, packing_index):
    database.client.execute(
        "ALTER TABLE warehouse_packing_box_item "
        "DROP INDEX idx_packing_box_item_list, "
        "ADD KEY idx_packing_box_item_list (is_deleted, packing_list_id);"
    )
    test_case.assertFalse(database.postcheck(packing_index))
    with test_case.assertRaises(MySqlExecutionError) as caught:
        database.run_script(packing_index)
    test_case.assertEqual(3819, caught.exception.error_code)
    database.client.execute(
        "ALTER TABLE warehouse_packing_box_item "
        "DROP INDEX idx_packing_box_item_list;"
    )
    database.run_script(packing_index)
    test_case.assertTrue(database.postcheck(packing_index))


def _verify_fulfillment_balance_constraints(test_case, database):
    database.client.execute(
        "INSERT INTO procurement_fulfillment_balance "
        "(id, planned_quantity, confirmed_quantity, abnormal_quantity, "
        "reserved_quantity, logistics_handoff_quantity, available_quantity) "
        "VALUES (11, 10, 10, 2, 3, 1, 4);"
    )
    _assert_mysql_rejects(
        test_case,
        database,
        "INSERT INTO procurement_fulfillment_balance "
        "(id, planned_quantity, confirmed_quantity, abnormal_quantity, "
        "reserved_quantity, logistics_handoff_quantity, available_quantity) "
        "VALUES (12, 10, 5, 0, -1, 0, 6);",
        3819,
    )
    _assert_mysql_rejects(
        test_case,
        database,
        "INSERT INTO procurement_fulfillment_balance "
        "(id, planned_quantity, confirmed_quantity, abnormal_quantity, "
        "reserved_quantity, logistics_handoff_quantity, available_quantity) "
        "VALUES (13, 10, 8, 1, 1, 1, 6);",
        3819,
    )
    test_case.assertEqual(
        "2",
        database.client.execute(
            "SELECT COUNT(*) FROM procurement_fulfillment_balance;"
        ),
    )


def _verify_history_postcheck(test_case, database, history):
    database.client.execute(
        "ALTER TABLE nuono_schema_migration "
        "MODIFY COLUMN gmt_updated DATETIME(6) NOT NULL "
        "DEFAULT CURRENT_TIMESTAMP(6);"
    )
    test_case.assertFalse(database.postcheck(history))
    database.client.execute(
        "ALTER TABLE nuono_schema_migration "
        "MODIFY COLUMN gmt_updated DATETIME(6) NOT NULL "
        "DEFAULT CURRENT_TIMESTAMP(6) "
        "ON UPDATE CURRENT_TIMESTAMP(6);"
    )
    test_case.assertTrue(database.postcheck(history))


def _assert_mysql_rejects(test_case, database, sql, error_code):
    with test_case.assertRaises(MySqlExecutionError) as caught:
        database.client.execute(sql)
    test_case.assertEqual(error_code, caught.exception.error_code)
