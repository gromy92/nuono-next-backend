from __future__ import annotations

from schema_migrations.mysql_support import MySqlExecutionError


SOURCE_AND_TARGET_STATE_SQL = (
    "SELECT (SELECT active_for_mvp+0 FROM forwarder_quote_service_line "
    "WHERE quote_version_id=904002 AND service_code='YT-SAU-SEA-FBN-RUH'),"
    "(SELECT unit_price FROM forwarder_quote_base_price "
    "WHERE id=912020 AND quote_version_id=904002),"
    "(SELECT effective_to FROM forwarder_quote_version WHERE id=904002),"
    "route.quote_version_code,segment.service_code,route.updated_by,segment.updated_by,"
    "(SELECT COUNT(*) FROM forwarder_quote_version WHERE version_no='YT-SAU-20260728')+"
    "(SELECT COUNT(*) FROM forwarder_quote_service_line "
    "WHERE service_code='YT-SAU-SEA-FBN-RUH-20260728')+"
    "(SELECT COUNT(*) FROM forwarder_quote_cargo_category "
    "WHERE service_code='YT-SAU-SEA-FBN-RUH-20260728')+"
    "(SELECT COUNT(*) FROM forwarder_quote_base_price "
    "WHERE service_code='YT-SAU-SEA-FBN-RUH-20260728')+"
    "(SELECT COUNT(*) FROM forwarder_quote_transport_fee "
    "WHERE service_code='YT-SAU-SEA-FBN-RUH-20260728') "
    "FROM forwarder_quote_route_template route "
    "JOIN forwarder_quote_route_template_segment segment "
    "ON segment.route_code=route.route_code AND segment.segment_role='HEADHAUL' "
    "WHERE route.route_code='YT-SAU-SEA-FBN-RUH';"
)


def _assert_guard_failure(test_case, database, migration):
    state_before = database.client.execute(SOURCE_AND_TARGET_STATE_SQL)
    with test_case.assertRaises(MySqlExecutionError) as caught:
        database.run_script(migration)
    test_case.assertEqual(3819, caught.exception.error_code)
    test_case.assertEqual(state_before, database.client.execute(SOURCE_AND_TARGET_STATE_SQL))


def verify_forwarder_source_drift_guard(test_case, database, migration):
    database.client.execute(
        "UPDATE forwarder_quote_route_template SET updated_by=306 "
        "WHERE route_code='YT-SAU-SEA-FBN-RUH';"
        "UPDATE forwarder_quote_route_template_segment SET updated_by=306 "
        "WHERE route_code='YT-SAU-SEA-FBN-RUH' AND segment_role='HEADHAUL';"
        "UPDATE forwarder_quote_service_line SET active_for_mvp=b'0' "
        "WHERE quote_version_id=904002 AND service_code='YT-SAU-SEA-FBN-RUH';"
    )
    _assert_guard_failure(test_case, database, migration)
    test_case.assertEqual(
        "0",
        database.client.execute(
            "SELECT active_for_mvp+0 FROM forwarder_quote_service_line "
            "WHERE quote_version_id=904002 AND service_code='YT-SAU-SEA-FBN-RUH';"
        ),
    )
    database.client.execute(
        "UPDATE forwarder_quote_service_line SET active_for_mvp=b'1' "
        "WHERE quote_version_id=904002 AND service_code='YT-SAU-SEA-FBN-RUH';"
        "UPDATE forwarder_quote_base_price SET unit_price=1191 "
        "WHERE id=912020 AND quote_version_id=904002;"
    )
    _assert_guard_failure(test_case, database, migration)
    test_case.assertEqual(
        "1191.0000",
        database.client.execute(
            "SELECT unit_price FROM forwarder_quote_base_price "
            "WHERE id=912020 AND quote_version_id=904002;"
        ),
    )

    database.client.execute(
        "UPDATE forwarder_quote_base_price SET unit_price=1190 "
        "WHERE id=912020 AND quote_version_id=904002;"
    )
    database.run_script(migration)
    test_case.assertTrue(database.postcheck(migration))
    test_case.assertEqual(
        "307\t307",
        database.client.execute(
            "SELECT route.updated_by,segment.updated_by "
            "FROM forwarder_quote_route_template route "
            "JOIN forwarder_quote_route_template_segment segment "
            "ON segment.route_code=route.route_code AND segment.segment_role='HEADHAUL' "
            "WHERE route.route_code='YT-SAU-SEA-FBN-RUH';"
        ),
    )
