from __future__ import annotations

from schema_migrations.mysql_database import MySqlMigrationDatabase
from schema_migrations.mysql_support import MySqlExecutionError


ELIGIBILITY_TABLE = "product_forwarder_transport_eligibility"
ANCHOR_TABLE = "product_forwarder_eligibility_scope_anchor"
OLD_ELIGIBILITY_DDL = """
CREATE TABLE `product_forwarder_transport_eligibility` (
  `id` BIGINT NOT NULL, `owner_user_id` BIGINT NOT NULL,
  `product_master_id` BIGINT DEFAULT NULL, `product_variant_id` BIGINT NOT NULL,
  `logical_store_id` BIGINT DEFAULT NULL,
  `source_store_code` VARCHAR(100) DEFAULT NULL,
  `partner_sku` VARCHAR(100) DEFAULT NULL,
  `site_code` VARCHAR(20) NOT NULL, `forwarder_code` VARCHAR(80) NOT NULL,
  `transport_mode` VARCHAR(20) NOT NULL,
  `eligibility_status` VARCHAR(40) NOT NULL,
  `effective_from` DATE NOT NULL, `effective_to` DATE DEFAULT NULL,
  `version` INT NOT NULL DEFAULT 1, `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
  `active_scope_slot` VARCHAR(255) GENERATED ALWAYS AS
    (CASE WHEN `is_deleted`=b'0' AND `effective_to` IS NULL THEN
      CONCAT(CAST(`owner_user_id` AS CHAR),':',CAST(`product_variant_id` AS CHAR),
      ':',UPPER(TRIM(`site_code`)),':',UPPER(TRIM(`forwarder_code`)),':',
      UPPER(TRIM(`transport_mode`))) ELSE NULL END) STORED,
  `created_by` BIGINT DEFAULT NULL, `updated_by` BIGINT DEFAULT NULL,
  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_pfte_active_scope` (`active_scope_slot`),
  KEY `idx_pfte_owner_variant` (`owner_user_id`,`product_variant_id`,`is_deleted`),
  KEY `idx_pfte_forwarder_scope`
    (`owner_user_id`,`site_code`,`forwarder_code`,`transport_mode`,`is_deleted`),
  KEY `idx_pfte_effective` (`effective_from`,`effective_to`),
  CONSTRAINT `chk_pfte_status` CHECK
    (CAST(`eligibility_status` AS BINARY) IN
      (CAST('INQUIRY_REQUIRED' AS BINARY),CAST('UNSUPPORTED' AS BINARY))),
  CONSTRAINT `chk_pfte_version` CHECK (`version`>0),
  CONSTRAINT `chk_pfte_effective` CHECK
    (`effective_to` IS NULL OR `effective_to`>=`effective_from`),
  CONSTRAINT `chk_pfte_scope_codes` CHECK
    (CAST(`site_code` AS BINARY)=CAST(UPPER(TRIM(`site_code`)) AS BINARY)
      AND OCTET_LENGTH(TRIM(`site_code`))>0
      AND CAST(`forwarder_code` AS BINARY)=
        CAST(UPPER(TRIM(`forwarder_code`)) AS BINARY)
      AND OCTET_LENGTH(TRIM(`forwarder_code`))>0
      AND CAST(`transport_mode` AS BINARY)=
        CAST(UPPER(TRIM(`transport_mode`)) AS BINARY)
      AND CAST(`transport_mode` AS BINARY) IN
        (CAST('AIR' AS BINARY),CAST('SEA' AS BINARY)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;
"""

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
    "AND trigger_name LIKE 'trg_fq_numeric_adjustment%'),"
    "shape.engine,shape.table_collation,shape.row_format,"
    "(SELECT COUNT(*) FROM product_forwarder_transport_eligibility),"
    "COALESCE((SELECT SHA2(GROUP_CONCAT(CAST(id AS CHAR) ORDER BY id),256) "
    "FROM product_forwarder_transport_eligibility),'<EMPTY>'),"
    "COALESCE((SELECT SHA2(GROUP_CONCAT(CONCAT_WS('|',trigger_name,event_manipulation,"
    "action_timing,action_orientation,action_statement) ORDER BY trigger_name SEPARATOR '\\n'),"
    "256) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND "
    "event_object_table='product_forwarder_transport_eligibility'),'<ABSENT>'),"
    "COALESCE((SELECT SHA2(GROUP_CONCAT(CONCAT_WS('|',ordinal_position,column_name,"
    "column_type,is_nullable,COALESCE(column_default,'<NULL>'),extra,"
    "COALESCE(generation_expression,''),COALESCE(collation_name,'-')) "
    "ORDER BY ordinal_position SEPARATOR '\\n'),256) FROM information_schema.columns "
    "WHERE table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility'),"
    "'<ABSENT>'),COALESCE((SELECT SHA2(GROUP_CONCAT(CONCAT_WS('|',index_name,"
    "non_unique,seq_in_index,column_name,COALESCE(sub_part,'-'),index_type,visible,"
    "COALESCE(expression,'-'),collation) ORDER BY index_name,seq_in_index "
    "SEPARATOR '\\n'),256) FROM information_schema.statistics "
    "WHERE table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility'),"
    "'<ABSENT>'),COALESCE((SELECT SHA2(GROUP_CONCAT(CONCAT_WS('|',constraint_name,enforced,check_clause) ORDER BY constraint_name SEPARATOR '\\n'),256) FROM information_schema.table_constraints JOIN information_schema.check_constraints USING (constraint_catalog,constraint_schema,constraint_name) WHERE constraint_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility'),'<ABSENT>'),COALESCE((SELECT SHA2(GROUP_CONCAT(CONCAT_WS('|',ordinal_position,"
    "column_name,column_type,is_nullable,COALESCE(column_default,'<NULL>'),extra,"
    "COALESCE(collation_name,'-')) ORDER BY ordinal_position SEPARATOR '\\n'),256) "
    "FROM information_schema.columns WHERE table_schema=DATABASE() "
    "AND table_name='product_forwarder_eligibility_scope_anchor'),'<ABSENT>'),"
    "(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
    "AND table_name='procurement_shipping_order_line' "
    "AND column_name='eligibility_status_snapshot') "
    "FROM forwarder_quote_version old_version "
    "JOIN forwarder_quote_route_template route ON route.route_code='YT-SAU-SEA-FBN-RUH' "
    "JOIN forwarder_quote_route_template_segment segment "
    "ON segment.route_code=route.route_code AND segment.segment_role='HEADHAUL' "
    "JOIN information_schema.tables shape ON shape.table_schema=DATABASE() "
    "AND shape.table_name='product_forwarder_transport_eligibility' "
    "WHERE old_version.id=904002;"
)
OLD_ELIGIBILITY_FACT_STATE_SQL = (
    "SET SESSION group_concat_max_len=16777216;SELECT COUNT(*),COALESCE(SHA2(GROUP_CONCAT("
    "CAST(JSON_ARRAY(id,owner_user_id,product_master_id,product_variant_id,logical_store_id,"
    "source_store_code,partner_sku,site_code,forwarder_code,transport_mode,eligibility_status,"
    "DATE_FORMAT(effective_from,'%Y-%m-%d'),DATE_FORMAT(effective_to,'%Y-%m-%d'),version,"
    "HEX(is_deleted),active_scope_slot,created_by,updated_by,DATE_FORMAT(gmt_create,"
    "'%Y-%m-%d %H:%i:%s'),DATE_FORMAT(gmt_updated,'%Y-%m-%d %H:%i:%s')) AS CHAR) ORDER BY id "
    "SEPARATOR '\\n'),256),'<EMPTY>') FROM product_forwarder_transport_eligibility;"
)

def verify_forwarder_wrong_shape_fail_before_writes(test_case, database, migration):
    _verify_wrong_eligibility_shape(test_case, database, migration)
    _verify_known_old_nonempty_rejected(test_case, database, migration)
    _verify_known_old_extra_trigger_rejected(test_case, database, migration)
    _verify_wrong_anchor_shape(test_case, database, migration)
    _verify_known_old_drop_contention(test_case, database)
    _verify_known_old_empty_upgrade_and_replay(test_case, database, migration)

def _verify_wrong_eligibility_shape(test_case, database, migration):
    database.client.execute(
        f"CREATE TABLE {ELIGIBILITY_TABLE} (id BIGINT NOT NULL) "
        "ENGINE=MyISAM DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;"
    )
    try:
        _assert_failed_unchanged(test_case, database, migration)
    finally:
        _drop_scope_tables(database)

def _verify_known_old_nonempty_rejected(test_case, database, migration):
    database.client.execute(
        OLD_ELIGIBILITY_DDL
        + "INSERT INTO product_forwarder_transport_eligibility "
        "(id,owner_user_id,product_master_id,product_variant_id,logical_store_id,"
        "source_store_code,partner_sku,site_code,forwarder_code,transport_mode,"
        "eligibility_status,effective_from,effective_to,version,is_deleted,created_by,"
        "updated_by,gmt_create,gmt_updated) VALUES (379998,307,379900,379998,108065,"
        "'STR108065-NSA','SKU|#1','SA','ET','AIR','UNSUPPORTED','2026-08-01',NULL,7,"
        "b'0',901,902,'2026-08-01 12:34:56','2026-08-01 12:35:57');"
    )
    try:
        _assert_failed_unchanged(test_case, database, migration, full_old=True)
        test_case.assertEqual(
            "379998", database.client.execute(
                "SELECT id FROM product_forwarder_transport_eligibility;"
            )
        )
    finally:
        _drop_scope_tables(database)

def _verify_known_old_extra_trigger_rejected(test_case, database, migration):
    database.client.execute(OLD_ELIGIBILITY_DDL + "CREATE TRIGGER trg_ci_pfte_extra "
                            "BEFORE INSERT ON product_forwarder_transport_eligibility "
                            "FOR EACH ROW SET NEW.updated_by=NEW.updated_by;")
    try:
        _assert_failed_unchanged(test_case, database, migration, full_old=True)
        test_case.assertEqual("1", database.client.execute("SELECT COUNT(*) FROM "
            "information_schema.triggers WHERE trigger_schema=DATABASE() AND "
            "trigger_name='trg_ci_pfte_extra';"))
    finally:
        _drop_scope_tables(database)


def _verify_wrong_anchor_shape(test_case, database, migration):
    database.client.execute(
        OLD_ELIGIBILITY_DDL
        + "CREATE TABLE product_forwarder_eligibility_scope_anchor "
        "(owner_user_id BIGINT NOT NULL,PRIMARY KEY(owner_user_id)) ENGINE=InnoDB;"
    )
    try:
        _assert_failed_unchanged(test_case, database, migration, full_old=True)
    finally:
        _drop_scope_tables(database)


def _verify_known_old_drop_contention(test_case, database):
    grants = database.client.execute("SHOW GRANTS;").upper()
    test_case.assertTrue("ALL PRIVILEGES" in grants or "LOCK TABLES" in grants)
    first = _sibling_database(database)
    second = _sibling_database(database)
    test_case.addCleanup(first.close)
    test_case.addCleanup(second.close)
    database.client.execute(OLD_ELIGIBILITY_DDL)
    lock_name = "nuono:ci:forwarder-old-shape-drop"
    first.client.acquire_lock(lock_name, 1)
    try:
        test_case.assertEqual(
            "0",
            first.client.execute(
                "LOCK TABLES product_forwarder_transport_eligibility WRITE;"
                "SELECT COUNT(*) FROM product_forwarder_transport_eligibility;"
            ),
        )
        with test_case.assertRaises(MySqlExecutionError) as caught:
            second.client.execute(
                "SET SESSION lock_wait_timeout=1;"
                "INSERT INTO product_forwarder_transport_eligibility "
                "(id,owner_user_id,product_variant_id,site_code,forwarder_code,"
                "transport_mode,eligibility_status,effective_from) VALUES "
                "(379997,307,379997,'SA','ET','AIR','UNSUPPORTED','2026-08-01');",
                timeout_seconds=5,
            )
        test_case.assertEqual(1205, caught.exception.error_code)
        test_case.assertEqual(
            "0", first.client.execute(
                "SELECT COUNT(*) FROM product_forwarder_transport_eligibility;"
            )
        )
        first.client.execute(
            "DROP TABLE product_forwarder_transport_eligibility;UNLOCK TABLES;"
        )
    finally:
        first.client.release_lock(lock_name)
    test_case.assertEqual(
        "0", database.client.execute(
            "SELECT COUNT(*) FROM information_schema.tables "
            "WHERE table_schema=DATABASE() "
            "AND table_name='product_forwarder_transport_eligibility';"
        )
    )

    database.client.execute(OLD_ELIGIBILITY_DDL)
    failure_lock = "nuono:ci:forwarder-old-shape-failure"
    first.client.acquire_lock(failure_lock, 1)
    first.client.execute("LOCK TABLES product_forwarder_transport_eligibility WRITE;")
    first.close()
    test_case.assertEqual(
        "0",
        second.client.execute(
            "SET SESSION lock_wait_timeout=1;"
            "LOCK TABLES product_forwarder_transport_eligibility WRITE;"
            "SELECT COUNT(*) FROM product_forwarder_transport_eligibility;"
            "UNLOCK TABLES;"
        ),
    )
    _drop_scope_tables(database)


def _verify_known_old_empty_upgrade_and_replay(test_case, database, migration):
    database.client.execute(OLD_ELIGIBILITY_DDL)
    database.run_script(migration)
    test_case.assertTrue(database.postcheck(migration))
    test_case.assertEqual(
        "YES\tNO\tNO\t512\tutf8mb4_bin\t1",
        database.client.execute(
            "SELECT "
            "(SELECT is_nullable FROM information_schema.columns WHERE "
            "table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility' "
            "AND column_name='product_variant_id'),"
            "(SELECT is_nullable FROM information_schema.columns WHERE "
            "table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility' "
            "AND column_name='logical_store_id'),"
            "(SELECT is_nullable FROM information_schema.columns WHERE "
            "table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility' "
            "AND column_name='partner_sku'),"
            "(SELECT character_maximum_length FROM information_schema.columns WHERE "
            "table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility' "
            "AND column_name='active_scope_slot'),"
            "(SELECT collation_name FROM information_schema.columns WHERE "
            "table_schema=DATABASE() AND table_name='product_forwarder_transport_eligibility' "
            "AND column_name='active_scope_slot'),"
            "(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() "
            "AND table_name='product_forwarder_eligibility_scope_anchor');"
        ),
    )
    state = database.client.execute(PRE_DML_STATE_SQL)
    database.run_script(migration)
    test_case.assertTrue(database.postcheck(migration))
    test_case.assertEqual(state, database.client.execute(PRE_DML_STATE_SQL))


def _assert_failed_unchanged(test_case, database, migration, full_old=False):
    before = _pre_dml_state(database, full_old)
    with test_case.assertRaises(MySqlExecutionError) as caught:
        database.run_script(migration)
    test_case.assertEqual(3819, caught.exception.error_code)
    test_case.assertEqual(before, _pre_dml_state(database, full_old))


def _pre_dml_state(database, full_old):
    state = database.client.execute(PRE_DML_STATE_SQL)
    return state + "\n" + database.client.execute(OLD_ELIGIBILITY_FACT_STATE_SQL) \
        if full_old else state


def _drop_scope_tables(database):
    database.client.execute(
        "DROP TABLE IF EXISTS product_forwarder_eligibility_scope_anchor;"
        "DROP TABLE IF EXISTS product_forwarder_transport_eligibility;"
    )


def _sibling_database(database):
    client = database.client
    return MySqlMigrationDatabase(
        client.source_defaults_file,
        expected_schema=client.expected_schema,
        expected_host=client.expected_host,
        expected_port=client.expected_port,
        execution_timeout_seconds=10,
    )
