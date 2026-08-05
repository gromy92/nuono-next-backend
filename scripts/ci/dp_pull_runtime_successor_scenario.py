from __future__ import annotations

import json

from ci.dp_pull_runtime_authority_scenario import (
    run_243_authority_scenario,
    run_245_authority_scenario,
)
from ci.dp_pull_runtime_successor_drift_scenario import (
    verify_additive_successor_shape,
    verify_constraint_drift_fails_closed,
    verify_half_migration_fails_closed,
    verify_missing_table_fails_closed,
)
from ci.dp_pull_runtime_successor_fixture import prepare_successor_fixture
from ci.release_schema_mysql_postcheck_diagnostics import failing_predicate_indexes


SUCCESSOR_KEYS = {
    244: "244_dp_pull_report_bounded_apply.sql",
    245: "245_dp_pull_snapshot_bounded_apply.sql",
    246: "246_dp_pull_advertising_generation.sql",
    247: "247_dp_pull_schedule_core.sql",
    248: "248_dp_pull_dp08_member_retention.sql",
}
SUCCESSOR_TABLES = (
    "dp_pull_dp08_task_member_progress",
    "dp_pull_dp08_member_set_item",
    "dp_pull_dp08_member_set",
    "dp_pull_schedule_dp08_member_stage_item",
    "dp_pull_schedule_dp08_member_stage_head",
    "dp_pull_schedule_source_scope",
    "dp_pull_schedule_source_epoch",
    "dp_pull_schedule_manifest_seal",
    "dp_pull_schedule_epoch_sequence",
    "dp_pull_schedule_rotation",
    "dp_pull_advertising_current_head",
    "dp_pull_advertising_query_fact",
    "dp_pull_advertising_campaign_fact",
    "dp_pull_advertising_generation",
    "dp_pull_snapshot_current_head",
    "dp_pull_snapshot_effective_item",
    "dp_pull_snapshot_apply_progress",
    "dp_pull_snapshot_verify_page",
    "dp_pull_snapshot_fingerprint_count",
    "dp_pull_report_stage_row",
    "dp_pull_report_stage",
    "dp_pull_report_artifact_chunk",
)
SUCCESSOR_VIEWS = (
    "official_warehouse_effective_inventory_snapshot_line",
    "official_warehouse_current_inventory_snapshot_line_raw",
    "noon_ad_effective_query_fact",
    "noon_ad_effective_campaign_fact",
    "noon_ad_effective_report_batch",
    "dp_pull_advertising_sealed_current_generation",
)


def run_successor_schema_scenario(
    test_case, database, resources, exact_243, live_243, dp08_task_id
) -> None:
    run_243_authority_scenario(
        test_case, database, exact_243, live_243, dp08_task_id
    )
    fixture = prepare_successor_fixture(database, resources)
    test_case.addCleanup(_cleanup_environment, database, fixture)
    migrations = _load_migrations(resources)
    for order in SUCCESSOR_KEYS:
        if order == 244:
            _assert_244_preflight(test_case, database, migrations[order][0], "absent")
        database.client.execute(migrations[order][0])
        _assert_contract(test_case, database, *migrations[order][1:])
        if order == 244:
            _assert_244_preflight(test_case, database, migrations[order][0], "present")
        database.client.execute(migrations[order][0])
        _assert_contract(test_case, database, *migrations[order][1:])
        verify_additive_successor_shape(
            test_case, database, order, migrations[order]
        )
    verify_half_migration_fails_closed(test_case, database, migrations)
    verify_missing_table_fails_closed(test_case, database, migrations)
    verify_constraint_drift_fails_closed(test_case, database, migrations)
    for order in (244, 245, 246, 248):
        _assert_contract(test_case, database, *migrations[order][1:])
    test_case.assertEqual(
        "0", database.client.execute_readonly(migrations[247][1])
    )
    test_case.assertEqual(
        "1", database.client.execute_readonly(migrations[247][2])
    )
    run_245_authority_scenario(
        test_case, database, *migrations[245][1:], dp08_task_id
    )


def _load_migrations(resources):
    migrations = {}
    for order, key in SUCCESSOR_KEYS.items():
        migrations[order] = tuple(
            (resources / directory / key).read_text(encoding="utf-8")
            for directory in ("init", "postcheck", "livecheck")
        )
    return migrations


def _assert_contract(test_case, database, exact, live) -> None:
    exact_result = database.client.execute_readonly(exact)
    test_case.assertEqual(
        "1",
        exact_result,
        None if exact_result == "1" else
        "false successor exact predicates: " + ",".join(
            failing_predicate_indexes(database, exact)
        ),
    )
    test_case.assertEqual("1", database.client.execute_readonly(live))


def _assert_244_preflight(test_case, database, migration, expected) -> None:
    prefix = migration.split(
        "DROP TEMPORARY TABLE IF EXISTS nuono_dp244_shape_guard;", 1
    )[0]
    diagnostic = database.client.execute(
        prefix + "SELECT JSON_OBJECT("
        "'all_absent',@dp244_all_absent,'all_present',@dp244_all_present,"
        "'column_name_count',@dp244_column_name_count,"
        "'column_shape_count',@dp244_column_shape_count,"
        "'storage_check_count',@dp244_storage_check_count,"
        "'progress_check_count',@dp244_progress_check_count,"
        "'storage_clause',@dp244_storage_clause,'old_storage',@dp244_old_storage,"
        "'new_storage',@dp244_new_storage,'progress_clause',@dp244_progress_clause,"
        "'new_progress',@dp244_new_progress);"
    )
    state = json.loads(diagnostic)
    test_case.assertEqual(1, state[f"all_{expected}"], diagnostic)


def _cleanup_environment(database, fixture) -> None:
    drop_successor_objects(database)
    fixture.cleanup(database)


def drop_successor_objects(database) -> None:
    database.client.execute(
        "SET FOREIGN_KEY_CHECKS=0;"
        + "".join(f"DROP VIEW IF EXISTS `{view}`;" for view in SUCCESSOR_VIEWS)
        + "".join(
            f"DROP TABLE IF EXISTS `{table}`;" for table in SUCCESSOR_TABLES
        )
        + "SET FOREIGN_KEY_CHECKS=1;"
    )
