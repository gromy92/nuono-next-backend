from __future__ import annotations

import re

from schema_migrations.mysql_support import MySqlExecutionError
from ci.release_schema_mysql_forwarder_source_contract import assert_source_contract


MIGRATION_KEY = "237_warehouse_forwarder_quote_and_transport_eligibility.sql"
ADJUSTMENT_HASH = "025a8cfa78920deaff035819431e45742a6ee2830f1c1e010ef36383f5c82db2"
ADJUSTMENT_LOG_HASH = "83caf487c8953f0eff04ef719e5482a65158a4e3773f1176802838baf9e03245"
LEGACY_SCHEMA_HASH = "9cf247aea2f146265c979b3467bcfb6e41a2a864f7da226ef4789171b82bd444"

ADJUSTMENT_HASH_SQL = (
    "SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(id,quote_version_id,target_type,"
    "target_id,field_name,original_value,adjusted_value,currency,reason,"
    "adjustment_status,created_by,updated_by,DATE_FORMAT(gmt_create,"
    "'%Y-%m-%d %H:%i:%s'),DATE_FORMAT(gmt_updated,'%Y-%m-%d %H:%i:%s')) "
    "AS CHAR) ORDER BY id SEPARATOR '\\n'),256) "
    "FROM forwarder_quote_numeric_adjustment;"
)
ADJUSTMENT_LOG_HASH_SQL = (
    "SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(id,adjustment_id,quote_version_id,"
    "target_type,target_id,field_name,before_value,after_value,action_type,"
    "reason,operated_by,DATE_FORMAT(gmt_create,'%Y-%m-%d %H:%i:%s')) AS CHAR) "
    "ORDER BY id SEPARATOR '\\n'),256) "
    "FROM forwarder_quote_numeric_adjustment_log;"
)
LEGACY_SCHEMA_HASH_SQL = (
    "SELECT SHA2(GROUP_CONCAT(CONCAT_WS('|',table_name,ordinal_position,"
    "column_name,column_type,is_nullable,COALESCE(column_default,'<NULL>'),"
    "extra,COALESCE(generation_expression,'')) ORDER BY table_name,"
    "ordinal_position SEPARATOR '\\n'),256) FROM information_schema.columns "
    "WHERE table_schema=DATABASE() AND table_name IN "
    "('forwarder_quote_numeric_adjustment',"
    "'forwarder_quote_numeric_adjustment_log');"
)
FACT_SIGNATURE_SQL = (
    "SELECT SHA2(CONCAT("
    "(SELECT GROUP_CONCAT(CONCAT(id,':',marker) ORDER BY id SEPARATOR ',') "
    "FROM product_forwarder_channel_quote),'|',"
    "(SELECT GROUP_CONCAT(CONCAT(id,':',marker) ORDER BY id SEPARATOR ',') "
    "FROM product_logistics_current_cost),'|',"
    "(SELECT GROUP_CONCAT(CONCAT(id,':',marker) ORDER BY id SEPARATOR ',') "
    "FROM procurement_purchase_order_logistics_quote_line),'|',"
    "(SELECT GROUP_CONCAT(CONCAT(id,':',quote_line_id,':',marker) "
    "ORDER BY id SEPARATOR ',') FROM procurement_shipping_order_line)),256);"
)


def prepare_forwarder_fixture(database, resources):
    bootstrap = (resources / "db/init/000_local_dev_bootstrap.sql").read_text(
        encoding="utf-8"
    )
    core_tables = (
        "forwarder",
        "quote_source_bundle",
        "quote_source_file",
        "quote_source_note",
        "forwarder_quote_version",
    )
    database.client.execute("\n".join(
        _create_table_statement(bootstrap, table) for table in core_tables
    ))
    database.client.execute(
        (resources / "db/init/030_logistics_quote_operations_v1.sql").read_text(
            encoding="utf-8"
        )
    )
    database.client.execute(
        (resources / "db/init/128_procurement_logistics_route_cost_components.sql").read_text(
            encoding="utf-8"
        )
    )
    database.client.execute(
        "UPDATE forwarder_quote_route_template_segment "
        "SET display_name=CONVERT(0xC3A6C2B5C2B7C3A8C2BFC290C3A5C28FC592C3A6C2B8E280A6C3A5C592E280A6C3A7C2A8C5BDC3A5C290C2ABC3A9E282ACC281C3A4C2BBE2809C USING utf8mb4) "
        "WHERE route_code='YT-SAU-SEA-FBN-RUH' AND segment_no=1;"
    )
    reason = (
        "义特通知：2026-07-28 00:00起入仓货物执行新价；"
        "批次YITE-SA-SEA-RATE-REVISION-20260728"
    )
    adjustment_values = ",".join(
        f"({930001 + offset},904002,'BASE_PRICE',{912020 + offset},"
        f"'unit_price',{before},{after},'RMB','{reason}','ACTIVE',307,307,"
        "'2026-07-29 12:05:50','2026-07-29 12:05:50')"
        for offset, (before, after) in enumerate(
            ((1190, 1540), (1640, 1900), (1740, 2040), (2140, 2290))
        )
    )
    log_values = ",".join(
        f"({940001 + offset},{930001 + offset},904002,'BASE_PRICE',"
        f"{912020 + offset},'unit_price',{before},{after},'CREATE','{reason}',"
        "307,'2026-07-29 12:05:50')"
        for offset, (before, after) in enumerate(
            ((1190, 1540), (1640, 1900), (1740, 2040), (2140, 2290))
        )
    )
    database.client.execute(
        "INSERT INTO forwarder_quote_numeric_adjustment "
        "(id,quote_version_id,target_type,target_id,field_name,original_value,"
        "adjusted_value,currency,reason,adjustment_status,created_by,updated_by,"
        f"gmt_create,gmt_updated) VALUES {adjustment_values};"
        "INSERT INTO forwarder_quote_numeric_adjustment_log "
        "(id,adjustment_id,quote_version_id,target_type,target_id,field_name,"
        "before_value,after_value,action_type,reason,operated_by,gmt_create) "
        f"VALUES {log_values};"
    )
    _insert_high_id_sentinels(database)
    _create_untouched_fact_fixture(database)
    database.client.execute("SET SESSION group_concat_max_len=1048576;")
    assert_source_contract(database)
    assert database.client.execute(ADJUSTMENT_HASH_SQL) == ADJUSTMENT_HASH
    assert database.client.execute(ADJUSTMENT_LOG_HASH_SQL) == ADJUSTMENT_LOG_HASH
    assert database.client.execute(LEGACY_SCHEMA_HASH_SQL) == LEGACY_SCHEMA_HASH
    return database.client.execute(FACT_SIGNATURE_SQL)


def verify_forwarder_migration(test_case, database, migrations, fact_signature):
    migration = next(item for item in migrations if item.key == MIGRATION_KEY)
    test_case.assertTrue(database.postcheck(migration))
    test_case.assertEqual(
        "1\t1\t10\t10\t1",
        database.client.execute(
            "SELECT COUNT(DISTINCT version.id),COUNT(DISTINCT service.id),"
            "COUNT(DISTINCT category_row.id),COUNT(DISTINCT price.id),"
            "COUNT(DISTINCT fee.id) FROM forwarder_quote_version version "
            "LEFT JOIN forwarder_quote_service_line service "
            "ON service.quote_version_id=version.id "
            "LEFT JOIN forwarder_quote_cargo_category category_row "
            "ON category_row.quote_version_id=version.id "
            "LEFT JOIN forwarder_quote_base_price price "
            "ON price.quote_version_id=version.id "
            "LEFT JOIN forwarder_quote_transport_fee fee "
            "ON fee.quote_version_id=version.id "
            "WHERE version.version_no='YT-SAU-20260728';"
        ),
    )
    test_case.assertEqual(
        "1540.0000,1900.0000,2040.0000,2290.0000",
        database.client.execute(
            "SELECT GROUP_CONCAT(CAST(price.unit_price AS CHAR) "
            "ORDER BY price.cargo_category_code) "
            "FROM forwarder_quote_base_price price "
            "JOIN forwarder_quote_version version "
            "ON version.id=price.quote_version_id "
            "WHERE version.version_no='YT-SAU-20260728' "
            "AND RIGHT(price.cargo_category_code,3) IN ('020','021','022','023');"
        ),
    )
    test_case.assertEqual(
        "YT-SAU-20260728\tYT-SAU-SEA-FBN-RUH-20260728",
        database.client.execute(
            "SELECT route.quote_version_code,segment.service_code "
            "FROM forwarder_quote_route_template route "
            "JOIN forwarder_quote_route_template_segment segment "
            "ON segment.route_code=route.route_code AND segment.segment_role='HEADHAUL' "
            "WHERE route.route_code='YT-SAU-SEA-FBN-RUH';"
        ),
    )
    test_case.assertEqual(
        "1540.0000,1900.0000,2040.0000,2290.0000",
        database.client.execute(
            "SELECT GROUP_CONCAT(CAST(COALESCE(adjustment.adjusted_value,"
            "price.unit_price) AS CHAR) ORDER BY price.cargo_category_code) "
            "FROM forwarder_quote_base_price price "
            "JOIN forwarder_quote_version version ON version.id=price.quote_version_id "
            "LEFT JOIN forwarder_quote_numeric_adjustment adjustment "
            "ON adjustment.target_type='BASE_PRICE' AND adjustment.target_id=price.id "
            "AND adjustment.field_name='unit_price' "
            "AND adjustment.adjustment_status='ACTIVE' "
            "WHERE version.version_no='YT-SAU-20260728' "
            "AND RIGHT(price.cargo_category_code,3) IN ('020','021','022','023') "
            "AND adjustment.id IS NULL;"
        ),
    )
    _assert_legacy_and_untouched(test_case, database, fact_signature)
    test_case.assertEqual(
        "0\t23",
        database.client.execute(
            "SELECT (SELECT COUNT(*) FROM product_forwarder_transport_eligibility),"
            "(SELECT COUNT(*) FROM procurement_shipping_order_line "
            "WHERE eligibility_status_snapshot IS NULL);"
        ),
    )
    _assert_legacy_write_fences(test_case, database)
    database.run_script(migration)
    test_case.assertTrue(database.postcheck(migration))
    _assert_legacy_and_untouched(test_case, database, fact_signature)


def _assert_legacy_and_untouched(test_case, database, fact_signature):
    test_case.assertEqual(ADJUSTMENT_HASH, database.client.execute(ADJUSTMENT_HASH_SQL))
    test_case.assertEqual(
        ADJUSTMENT_LOG_HASH, database.client.execute(ADJUSTMENT_LOG_HASH_SQL)
    )
    test_case.assertEqual(
        LEGACY_SCHEMA_HASH, database.client.execute(LEGACY_SCHEMA_HASH_SQL)
    )
    test_case.assertEqual(fact_signature, database.client.execute(FACT_SIGNATURE_SQL))


def _assert_legacy_write_fences(test_case, database):
    test_case.assertEqual(
        "6",
        database.client.execute(
            "SELECT COUNT(*) FROM information_schema.triggers "
            "WHERE trigger_schema=DATABASE() AND trigger_name LIKE "
            "'trg_fq_numeric_adjustment%retired_b_';"
        ),
    )
    statements = (
        "INSERT INTO forwarder_quote_numeric_adjustment "
        "(id,quote_version_id,target_type,target_id,field_name,original_value,"
        "adjusted_value,currency,reason,adjustment_status) VALUES "
        "(939999,904002,'BASE_PRICE',912020,'unit_price',1190,1540,'RMB','CI','ACTIVE');",
        "UPDATE forwarder_quote_numeric_adjustment SET reason=reason WHERE id=930001;",
        "DELETE FROM forwarder_quote_numeric_adjustment WHERE id=930001;",
        "INSERT INTO forwarder_quote_numeric_adjustment_log "
        "(id,adjustment_id,quote_version_id,target_type,target_id,field_name,"
        "before_value,after_value,action_type,reason) VALUES "
        "(949999,930001,904002,'BASE_PRICE',912020,'unit_price',1190,1540,'CREATE','CI');",
        "UPDATE forwarder_quote_numeric_adjustment_log SET reason=reason WHERE id=940001;",
        "DELETE FROM forwarder_quote_numeric_adjustment_log WHERE id=940001;",
    )
    for statement in statements:
        _assert_mysql_rejects(test_case, database, statement, 1644)


def _assert_mysql_rejects(test_case, database, sql, error_code):
    with test_case.assertRaises(MySqlExecutionError) as caught:
        database.client.execute(sql)
    test_case.assertEqual(error_code, caught.exception.error_code)


def _create_table_statement(sql, table):
    match = re.search(
        rf"CREATE TABLE IF NOT EXISTS `{re.escape(table)}` \(.*?\) "
        r"ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;",
        sql,
        re.DOTALL,
    )
    if match is None:
        raise AssertionError(f"historical table definition missing: {table}")
    return match.group(0)


def _insert_high_id_sentinels(database):
    database.client.execute(
        "INSERT INTO forwarder_quote_version "
        "(id,forwarder_id,bundle_id,version_no,status) "
        "VALUES (904005,900001,901001,'CI-HIGH-ID','PUBLISHED');"
        "INSERT INTO forwarder_quote_service_line "
        "(id,quote_version_id,quote_version_code,forwarder_code,service_code,"
        "service_name,transport_mode) VALUES "
        "(910032,904005,'CI-HIGH-ID','CI','CI-HIGH-SERVICE','CI','SEA');"
        "INSERT INTO forwarder_quote_cargo_category "
        "(id,quote_version_id,quote_version_code,forwarder_code,service_code,"
        "cargo_category_code,cargo_category_name) VALUES "
        "(911085,904005,'CI-HIGH-ID','CI','CI-HIGH-SERVICE','CI-HIGH-CAT','CI');"
        "INSERT INTO forwarder_quote_base_price "
        "(id,price_rule_code,quote_version_id,quote_version_code,service_code,"
        "pricing_model) VALUES "
        "(912132,'CI-HIGH-PRICE',904005,'CI-HIGH-ID','CI-HIGH-SERVICE','FIXED');"
        "INSERT INTO forwarder_quote_transport_fee "
        "(id,fee_rule_code,quote_version_id,quote_version_code,service_code,fee_name) "
        "VALUES (913032,'CI-HIGH-FEE',904005,'CI-HIGH-ID','CI-HIGH-SERVICE','CI');"
    )


def _create_untouched_fact_fixture(database):
    database.client.execute(
        "CREATE TABLE product_management_id_sequence (sequence_name VARCHAR(80) "
        "NOT NULL,next_id BIGINT NOT NULL,gmt_create DATETIME DEFAULT CURRENT_TIMESTAMP,"
        "gmt_updated DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
        "PRIMARY KEY(sequence_name)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
        "CREATE TABLE product_forwarder_channel_quote "
        "(id BIGINT NOT NULL,marker VARCHAR(40) NOT NULL,PRIMARY KEY(id)) ENGINE=InnoDB;"
        "CREATE TABLE product_logistics_current_cost "
        "(id BIGINT NOT NULL,marker VARCHAR(40) NOT NULL,PRIMARY KEY(id)) ENGINE=InnoDB;"
        "CREATE TABLE procurement_purchase_order_logistics_quote_line "
        "(id BIGINT NOT NULL,marker VARCHAR(40) NOT NULL,PRIMARY KEY(id)) ENGINE=InnoDB;"
        "CREATE TABLE procurement_shipping_order_line "
        "(id BIGINT NOT NULL,quote_line_id BIGINT DEFAULT NULL,marker VARCHAR(40) NOT NULL,"
        "PRIMARY KEY(id)) ENGINE=InnoDB;"
        + _insert_markers("product_forwarder_channel_quote", 287, "QUOTE")
        + _insert_markers("product_logistics_current_cost", 249, "COST")
        + _insert_markers("procurement_purchase_order_logistics_quote_line", 23, "LINE")
        + "INSERT INTO procurement_shipping_order_line (id,quote_line_id,marker) VALUES "
        + ",".join(f"({index},{index},'ORDER-{index}')" for index in range(1, 24))
        + ";"
    )


def _insert_markers(table, count, prefix):
    values = ",".join(
        f"({index},'{prefix}-{index}')" for index in range(1, count + 1)
    )
    return f"INSERT INTO {table} (id,marker) VALUES {values};"
