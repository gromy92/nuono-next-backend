from __future__ import annotations

from schema_migrations.mysql_support import MySqlExecutionError


def verify_shipping_batch_dispatch_plan_uniqueness_migration(
        test_case,
        database,
        migrations,
        runner,
):
    migration = next(
        item for item in migrations
        if item.key == "236_warehouse_shipping_batch_dispatch_plan_uniqueness.sql"
    )
    test_case.assertTrue(database.postcheck(migration))
    database.run_script(migration)
    database.run_script(migration)

    database.client.execute(
        "INSERT INTO procurement_dispatch_plan "
        "(id,owner_user_id,is_deleted) VALUES "
        "(10,307,b'0'),(11,307,b'0'),(12,307,b'1');"
        "INSERT INTO warehouse_shipping_batch "
        "(id,owner_user_id,dispatch_plan_id,batch_no) "
        "VALUES (20,307,10,'WB-LINK-20');"
    )
    duplicate_active = (
        "INSERT INTO warehouse_shipping_batch "
        "(id,owner_user_id,dispatch_plan_id,batch_no) "
        "VALUES (21,307,10,'WB-LINK-21');"
    )
    with test_case.assertRaises(MySqlExecutionError) as caught:
        database.client.execute(duplicate_active)
    test_case.assertEqual(1062, caught.exception.error_code)
    database.client.execute(
        "UPDATE warehouse_shipping_batch SET is_deleted=b'1' WHERE id=20;"
        + duplicate_active
    )
    database.client.execute(
        "INSERT INTO procurement_dispatch_plan "
        "(id,owner_user_id,is_deleted) VALUES (14,307,b'0'),(15,307,b'0');"
        "INSERT INTO warehouse_shipping_batch "
        "(id,owner_user_id,dispatch_plan_id,batch_no,status,is_deleted) VALUES "
        "(27,307,NULL,'WB-NULL-27','DRAFT',b'0'),"
        "(28,307,NULL,'WB-NULL-28','OUTBOUND_CREATED',b'0'),"
        "(29,307,14,'WB-HISTORY-29','DRAFT',b'1'),"
        "(30,307,14,'WB-HISTORY-30','SHIPPED',b'1'),"
        "(31,307,15,'WB-FUTURE-31','FUTURE_STATE',b'0');"
    )
    with test_case.assertRaises(MySqlExecutionError) as caught:
        database.client.execute(
            "INSERT INTO warehouse_shipping_batch "
            "(id,owner_user_id,dispatch_plan_id,batch_no,status) "
            "VALUES (32,307,15,'WB-FUTURE-32','SHIPPED');"
        )
    test_case.assertEqual(1062, caught.exception.error_code)
    test_case.assertTrue(database.postcheck(migration))

    _assert_data_guard_rejects(
        test_case,
        database,
        migration,
        "INSERT INTO warehouse_shipping_batch "
        "(id,owner_user_id,dispatch_plan_id,batch_no) "
        "VALUES (22,409,11,'WB-OWNER-MISMATCH');",
        "DELETE FROM warehouse_shipping_batch WHERE id=22;",
    )
    _assert_data_guard_rejects(
        test_case,
        database,
        migration,
        "INSERT INTO warehouse_shipping_batch "
        "(id,owner_user_id,dispatch_plan_id,batch_no) "
        "VALUES (23,307,999,'WB-ORPHAN');",
        "DELETE FROM warehouse_shipping_batch WHERE id=23;",
    )
    _assert_data_guard_rejects(
        test_case,
        database,
        migration,
        "INSERT INTO warehouse_shipping_batch "
        "(id,owner_user_id,dispatch_plan_id,batch_no) "
        "VALUES (24,307,12,'WB-DELETED-PARENT');",
        "DELETE FROM warehouse_shipping_batch WHERE id=24;",
    )

    database.client.execute(
        "ALTER TABLE warehouse_shipping_batch "
        "DROP INDEX uk_shipping_batch_active_dispatch_plan;"
    )
    test_case.assertFalse(database.postcheck(migration))
    with test_case.assertRaises(MySqlExecutionError) as caught:
        database.run_script(migration)
    test_case.assertEqual(3819, caught.exception.error_code)
    database.client.execute(
        "ALTER TABLE warehouse_shipping_batch "
        "ADD UNIQUE KEY uk_shipping_batch_active_dispatch_plan "
        "(active_dispatch_plan_id);"
    )
    test_case.assertTrue(database.postcheck(migration))

    database.client.execute(
        "ALTER TABLE warehouse_shipping_batch "
        "DROP INDEX idx_shipping_batch_dispatch_plan,"
        "ADD KEY idx_shipping_batch_dispatch_plan "
        "(dispatch_plan_id,gmt_updated,is_deleted);"
    )
    test_case.assertFalse(database.postcheck(migration))
    with test_case.assertRaises(MySqlExecutionError) as caught:
        database.run_script(migration)
    test_case.assertEqual(3819, caught.exception.error_code)
    database.client.execute(
        "ALTER TABLE warehouse_shipping_batch "
        "DROP INDEX idx_shipping_batch_dispatch_plan,"
        "ADD KEY idx_shipping_batch_dispatch_plan "
        "(dispatch_plan_id,is_deleted,gmt_updated);"
    )
    test_case.assertTrue(database.postcheck(migration))

    database.client.execute(
        "ALTER TABLE procurement_dispatch_plan "
        "DROP PRIMARY KEY,"
        "ADD UNIQUE KEY uk_dispatch_plan_id_drift (id);"
    )
    test_case.assertFalse(database.postcheck(migration))
    with test_case.assertRaises(MySqlExecutionError) as caught:
        database.run_script(migration)
    test_case.assertEqual(3819, caught.exception.error_code)
    database.client.execute(
        "ALTER TABLE procurement_dispatch_plan "
        "DROP INDEX uk_dispatch_plan_id_drift,"
        "ADD PRIMARY KEY (id);"
    )
    test_case.assertTrue(database.postcheck(migration))

    database.client.execute(
        "ALTER TABLE warehouse_shipping_batch "
        "DROP INDEX uk_shipping_batch_active_dispatch_plan,"
        "DROP COLUMN active_dispatch_plan_id;"
    )
    test_case.assertFalse(database.postcheck(migration))
    database.client.execute(
        "INSERT INTO procurement_dispatch_plan "
        "(id,owner_user_id,is_deleted) VALUES (13,307,b'0');"
        "INSERT INTO warehouse_shipping_batch "
        "(id,owner_user_id,dispatch_plan_id,batch_no) VALUES "
        "(25,307,13,'WB-DUPLICATE-25'),"
        "(26,307,13,'WB-DUPLICATE-26');"
    )
    with test_case.assertRaises(MySqlExecutionError) as caught:
        database.run_script(migration)
    test_case.assertEqual(3819, caught.exception.error_code)
    database.client.execute(
        "DELETE FROM warehouse_shipping_batch WHERE id=26;"
    )
    database.client.execute(
        "UPDATE nuono_schema_migration h "
        "JOIN nuono_schema_migration_attempt a "
        "ON a.migration_key=h.migration_key "
        "AND a.attempt_no=h.attempt_no "
        "SET h.state='FAILED', a.state='FAILED' "
        f"WHERE h.migration_key='{migration.key}';"
    )
    test_case.assertEqual(
        "RERUN_APPLIED",
        runner.repair_forward(
            migration.key,
            rerun=True,
            approved_managed=[migration.key],
        ),
    )
    test_case.assertTrue(database.postcheck(migration))


def _assert_data_guard_rejects(
        test_case,
        database,
        migration,
        insert_sql,
        cleanup_sql,
):
    database.client.execute(insert_sql)
    test_case.assertFalse(database.postcheck(migration))
    with test_case.assertRaises(MySqlExecutionError) as caught:
        database.run_script(migration)
    test_case.assertEqual(3819, caught.exception.error_code)
    database.client.execute(cleanup_sql)
    test_case.assertTrue(database.postcheck(migration))
