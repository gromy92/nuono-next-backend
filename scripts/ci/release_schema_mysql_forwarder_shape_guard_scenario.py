from __future__ import annotations

from schema_migrations.mysql_support import MySqlExecutionError


PRE_DML_STATE_SQL = (
    "SELECT COALESCE(CAST(old_version.effective_to AS CHAR),'<NULL>'),"
    "(SELECT COUNT(*) FROM forwarder_quote_version WHERE version_no='YT-SAU-20260728')+"
    "(SELECT COUNT(*) FROM forwarder_quote_service_line "
    "WHERE service_code='YT-SAU-SEA-FBN-RUH-20260728')+"
    "(SELECT COUNT(*) FROM forwarder_quote_cargo_category "
    "WHERE service_code='YT-SAU-SEA-FBN-RUH-20260728')+"
    "(SELECT COUNT(*) FROM forwarder_quote_base_price "
    "WHERE service_code='YT-SAU-SEA-FBN-RUH-20260728')+"
    "(SELECT COUNT(*) FROM forwarder_quote_transport_fee "
    "WHERE service_code='YT-SAU-SEA-FBN-RUH-20260728'),"
    "route.quote_version_id,route.quote_version_code,segment.service_code,"
    "(SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() "
    "AND trigger_name LIKE 'trg_fq_numeric_adjustment%'),shape.engine,shape.table_collation "
    "FROM forwarder_quote_version old_version "
    "JOIN forwarder_quote_route_template route "
    "ON route.route_code='YT-SAU-SEA-FBN-RUH' "
    "JOIN forwarder_quote_route_template_segment segment "
    "ON segment.route_code=route.route_code AND segment.segment_role='HEADHAUL' "
    "JOIN information_schema.tables shape ON shape.table_schema=DATABASE() "
    "AND shape.table_name='product_forwarder_transport_eligibility' "
    "WHERE old_version.id=904002;"
)


def verify_forwarder_wrong_shape_fail_before_writes(test_case, database, migration):
    database.client.execute(
        "CREATE TABLE product_forwarder_transport_eligibility (id BIGINT NOT NULL) "
        "ENGINE=MyISAM DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;"
    )
    try:
        before = database.client.execute(PRE_DML_STATE_SQL)
        test_case.assertEqual(
            "<NULL>\t0\t904002\tYT-SAU-UNDATED-001\tYT-SAU-SEA-FBN-RUH\t0\tMyISAM\tutf8mb4_0900_ai_ci",
            before,
        )
        with test_case.assertRaises(MySqlExecutionError) as caught:
            database.run_script(migration)
        test_case.assertEqual(3819, caught.exception.error_code)
        test_case.assertEqual(before, database.client.execute(PRE_DML_STATE_SQL))
    finally:
        database.client.execute(
            "DROP TABLE IF EXISTS product_forwarder_transport_eligibility;"
        )
