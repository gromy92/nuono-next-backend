from __future__ import annotations

from schema_migrations.mysql_support import MySqlExecutionError


def verify_shipping_batch_idempotency_migration(test_case, database, migrations):
    migration = next(
        item for item in migrations
        if item.key == "235_warehouse_shipping_batch_request_idempotency.sql"
    )
    test_case.assertTrue(database.postcheck(migration))
    database.run_script(migration)
    database.run_script(migration)
    test_case.assertEqual(
        "1",
        database.client.execute(
            "SELECT COUNT(*) FROM warehouse_shipping_batch "
            "WHERE id=1 AND client_request_id IS NULL "
            "AND request_fingerprint IS NULL;"
        ),
    )
    database.client.execute(
        "INSERT INTO warehouse_shipping_batch (id,owner_user_id,batch_no) "
        "VALUES (2,307,'WB-2'),(3,307,'WB-3');"
        "INSERT INTO warehouse_shipping_batch "
        "(id,owner_user_id,client_request_id,request_fingerprint,batch_no) "
        "VALUES (4,307,'Request-A',REPEAT('a',64),'WB-4');"
    )
    duplicate = (
        "INSERT INTO warehouse_shipping_batch "
        "(id,owner_user_id,client_request_id,request_fingerprint,batch_no) "
        "VALUES (5,307,'Request-A',REPEAT('b',64),'WB-5');"
    )
    with test_case.assertRaises(MySqlExecutionError) as caught:
        database.client.execute(duplicate)
    test_case.assertEqual(1062, caught.exception.error_code)
    database.client.execute(
        "INSERT INTO warehouse_shipping_batch "
        "(id,owner_user_id,client_request_id,request_fingerprint,batch_no) "
        "VALUES (6,409,'Request-A',REPEAT('b',64),'WB-6');"
        "ALTER TABLE warehouse_shipping_batch "
        "MODIFY COLUMN client_request_id VARCHAR(100) CHARACTER SET utf8mb4 "
        "COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL;"
    )
    test_case.assertFalse(database.postcheck(migration))
    with test_case.assertRaises(MySqlExecutionError) as caught:
        database.run_script(migration)
    test_case.assertEqual(3819, caught.exception.error_code)
    database.client.execute(
        "ALTER TABLE warehouse_shipping_batch "
        "MODIFY COLUMN client_request_id VARCHAR(100) CHARACTER SET utf8mb4 "
        "COLLATE utf8mb4_bin NULL DEFAULT NULL;"
        "INSERT INTO warehouse_shipping_batch "
        "(id,owner_user_id,client_request_id,request_fingerprint,batch_no) "
        "VALUES (7,307,'request-a',REPEAT('c',64),'WB-7');"
    )
    test_case.assertTrue(database.postcheck(migration))
