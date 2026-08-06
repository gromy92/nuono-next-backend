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
    250: "250_dp_pull_advertising_campaign_pagination.sql",
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
        if order <= 248:
            verify_additive_successor_shape(
                test_case, database, order, migrations[order]
            )
    verify_half_migration_fails_closed(test_case, database, migrations)
    verify_missing_table_fails_closed(test_case, database, migrations)
    verify_constraint_drift_fails_closed(test_case, database, migrations)
    for order in (244, 245, 246, 248):
        _assert_live(test_case, database, migrations[order][2])
    _assert_contract(test_case, database, *migrations[250][1:])
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
    detail = ""
    if exact_result != "1" and (
        "expected_check AS" in exact or "required_check AS" in exact
    ):
        detail = "; check drift: " + _check_drift(database, exact)
    if exact_result != "1" and "expected_view AS" in exact:
        detail += "; view drift: " + _view_drift(database, exact)
    test_case.assertEqual(
        "1",
        exact_result,
        None if exact_result == "1" else
        "false successor exact predicates: " + ",".join(
            failing_predicate_indexes(database, exact)
        ) + detail,
    )
    test_case.assertEqual("1", database.client.execute_readonly(live))


def _assert_live(test_case, database, live) -> None:
    test_case.assertEqual("1", database.client.execute_readonly(live))


def _check_drift(database, exact) -> str:
    select_start = exact.rfind("SELECT IF(")
    if select_start < 0:
        return "outer-select-missing"
    database.client.execute("SET SESSION group_concat_max_len=65535;")
    prefix = exact[:select_start]
    if "required_pattern" in exact:
        return database.client.execute_readonly(
            prefix
            + "SELECT GROUP_CONCAT(CONCAT(e.table_name,'.',e.constraint_name,"
            "':',e.required_pattern,'/',COALESCE(HEX(a.check_clause),'missing')) "
            "ORDER BY e.table_name,e.constraint_name SEPARATOR '|') "
            "FROM required_check e LEFT JOIN actual_check a "
            "USING(table_name,constraint_name) WHERE a.table_name IS NULL "
            "OR a.enforced<>'YES' OR a.check_clause NOT REGEXP e.required_pattern;"
        )
    hash_column = (
        "clause_sha256" if "clause_sha256" in exact
        else "clause_hash" if "clause_hash" in exact
        else None
    )
    if hash_column is None:
        return "named-check-contract-has-no-expression-hash"
    raw_first = database.client.execute_readonly(
        prefix
        + "SELECT CONCAT(e.table_name,'.',e.constraint_name,':',"
        "HEX(cc.check_clause)) FROM expected_check e "
        "LEFT JOIN actual_check a USING(table_name,constraint_name) "
        "LEFT JOIN information_schema.check_constraints cc "
        "ON cc.constraint_schema=DATABASE() "
        "AND cc.constraint_name=e.constraint_name "
        "WHERE a.table_name IS NULL OR a.enforced<>'YES' "
        f"OR a.{hash_column}<>e.{hash_column} "
        "ORDER BY e.table_name,e.constraint_name LIMIT 1;"
    )
    drift = database.client.execute_readonly(
        prefix
        + "SELECT GROUP_CONCAT(CONCAT(e.table_name,'.',e.constraint_name,':',"
        f"e.{hash_column},'/',COALESCE(a.{hash_column},'missing')) "
        "ORDER BY e.table_name,e.constraint_name SEPARATOR '|') "
        "FROM expected_check e LEFT JOIN actual_check a "
        "USING(table_name,constraint_name) WHERE a.table_name IS NULL "
        f"OR a.enforced<>'YES' OR a.{hash_column}<>e.{hash_column};"
    )
    return "raw-first=" + raw_first + "; drift=" + drift


def _view_drift(database, exact) -> str:
    select_start = exact.rfind("SELECT IF(")
    if select_start < 0:
        return "outer-select-missing"
    database.client.execute("SET SESSION group_concat_max_len=65535;")
    return database.client.execute_readonly(
        exact[:select_start]
        + "SELECT GROUP_CONCAT(CONCAT(e.table_name,':columns=',"
        "IF(a.view_columns=e.view_columns,'ok','drift'),':security=',"
        "COALESCE(a.security_type,'missing'),':check=',"
        "COALESCE(a.check_option,'missing'),':updatable=',"
        "COALESCE(a.is_updatable,'missing'),':positions=',"
        "CASE e.table_name "
        "WHEN 'noon_ad_effective_report_batch' THEN CONCAT_WS(',',"
        "LOCATE('noon_ad_report_batch',a.view_definition),"
        "LOCATE('where',a.view_definition),LOCATE('not',a.view_definition),"
        "LOCATE('exists',a.view_definition),"
        "LOCATE('dp_pull_advertising_current_head',a.view_definition),"
        "LOCATE('unionall',a.view_definition),"
        "LOCATE('dp_pull_advertising_sealed_current_generation',a.view_definition)) "
        "WHEN 'noon_ad_effective_campaign_fact' THEN CONCAT_WS(',',"
        "LOCATE('noon_ad_campaign_fact',a.view_definition),"
        "LOCATE('where',a.view_definition),LOCATE('not',a.view_definition),"
        "LOCATE('exists',a.view_definition),"
        "LOCATE('dp_pull_advertising_current_head',a.view_definition),"
        "LOCATE('unionall',a.view_definition),"
        "LOCATE('dp_pull_advertising_campaign_fact',a.view_definition),"
        "LOCATE('=0',a.view_definition),LOCATE('isfalse',a.view_definition),"
        "LOCATE('isnottrue',a.view_definition),"
        "HEX(SUBSTRING(a.view_definition,"
        "GREATEST(1,LOCATE('unionall',a.view_definition)-100),100))) "
        "WHEN 'noon_ad_effective_query_fact' THEN CONCAT_WS(',',"
        "LOCATE('noon_ad_query_fact',a.view_definition),"
        "LOCATE('where',a.view_definition),LOCATE('not',a.view_definition),"
        "LOCATE('exists',a.view_definition),"
        "LOCATE('dp_pull_advertising_current_head',a.view_definition),"
        "LOCATE('unionall',a.view_definition),"
        "LOCATE('dp_pull_advertising_query_fact',a.view_definition)) "
        "ELSE 'n/a' END) "
        "ORDER BY e.table_name SEPARATOR '|') FROM expected_view e "
        "LEFT JOIN actual_view a USING(table_name);"
    )


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
