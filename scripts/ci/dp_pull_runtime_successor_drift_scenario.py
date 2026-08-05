from __future__ import annotations

from schema_migrations.mysql_support import MySqlExecutionError


EXTRA_TARGETS = {
    244: "dp_pull_report_stage_row",
    245: "dp_pull_snapshot_effective_item",
    246: "dp_pull_advertising_current_head",
    247: "dp_pull_schedule_rotation",
    248: "dp_pull_dp08_member_set_item",
}


def verify_additive_successor_shape(
    test_case, database, order, migration
) -> None:
    table = EXTRA_TARGETS[order]
    column = f"ci_dp_{order}_nullable_note"
    index = f"idx_ci_dp_{order}_nullable_note"
    database.client.execute(
        f"ALTER TABLE `{table}` ADD COLUMN `{column}` VARCHAR(32) NULL, "
        f"ADD KEY `{index}` (`{column}`) VISIBLE;"
    )
    exact, live = migration[1:]
    test_case.assertEqual("0", database.client.execute_readonly(exact))
    test_case.assertEqual("1", database.client.execute_readonly(live))
    database.client.execute(
        f"ALTER TABLE `{table}` DROP INDEX `{index}`, "
        f"DROP COLUMN `{column}`;"
    )
    _assert_contract(test_case, database, exact, live)


def verify_half_migration_fails_closed(
    test_case, database, migrations
) -> None:
    init, exact, live = migrations[245]
    database.client.execute(
        "ALTER TABLE dp_pull_snapshot_stage_item "
        "DROP INDEX idx_dp_snapshot_item_canonical, "
        "DROP COLUMN absence_reconciliation_safe;"
    )
    with test_case.assertRaises(MySqlExecutionError) as caught:
        database.client.execute(init)
    test_case.assertEqual(3819, caught.exception.error_code)
    _assert_fail_closed(test_case, database, exact)
    _assert_fail_closed(test_case, database, live)
    database.client.execute(
        "DROP TEMPORARY TABLE IF EXISTS nuono_dp245_item_shape_guard;"
        "ALTER TABLE dp_pull_snapshot_stage_item "
        "DROP COLUMN validated_identity_candidate;"
    )
    database.client.execute(init)
    _assert_contract(test_case, database, exact, live)


def verify_missing_table_fails_closed(
    test_case, database, migrations
) -> None:
    exact, live = migrations[247][1:]
    database.client.execute("DROP TABLE dp_pull_schedule_source_scope;")
    _assert_fail_closed(test_case, database, exact)
    _assert_fail_closed(test_case, database, live)
    database.client.execute(migrations[247][0])
    _assert_contract(test_case, database, exact, live)
    database.client.execute(migrations[248][0])
    test_case.assertEqual("0", database.client.execute_readonly(exact))
    test_case.assertEqual("1", database.client.execute_readonly(live))
    _assert_contract(test_case, database, *migrations[248][1:])


def verify_constraint_drift_fails_closed(
    test_case, database, migrations
) -> None:
    init, exact, live = migrations[248]
    database.client.execute(
        "ALTER TABLE dp_pull_task "
        "DROP CHECK chk_dp_task_dp08_handle_size, "
        "ADD CONSTRAINT chk_dp_task_dp08_handle_size CHECK ("
        "operation_code NOT IN ('DP08A','DP08B') "
        "OR OCTET_LENGTH(scope_payload) BETWEEN 1 AND 5000);"
    )
    _assert_fail_closed(test_case, database, exact)
    _assert_fail_closed(test_case, database, live)
    database.client.execute(
        "ALTER TABLE dp_pull_task DROP CHECK chk_dp_task_dp08_handle_size;"
    )
    database.client.execute(init)
    _assert_contract(test_case, database, exact, live)


def _assert_fail_closed(test_case, database, sql) -> None:
    try:
        result = database.client.execute_readonly(sql)
    except MySqlExecutionError as error:
        test_case.assertIn(error.error_code, (1054, 1146))
        return
    test_case.assertEqual("0", result)


def _assert_contract(test_case, database, exact, live) -> None:
    test_case.assertEqual("1", database.client.execute_readonly(exact))
    test_case.assertEqual("1", database.client.execute_readonly(live))
