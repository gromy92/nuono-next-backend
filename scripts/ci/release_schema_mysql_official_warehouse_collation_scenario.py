from __future__ import annotations

from ci.release_schema_mysql_noon_auth_wait_scenario import (
    approve_noon_auth_wait,
    prepare_noon_auth_wait_fixture,
    verify_noon_auth_wait_migration,
)
from schema_migrations.core import MigrationError
from schema_migrations.mysql_support import MySqlExecutionError


MIGRATION_KEY = "239_official_warehouse_scope_collation_alignment.sql"


def prepare_release_tail_fixture(test_case, database):
    prepare_noon_auth_wait_fixture(database)
    database.client.execute(
        "ALTER TABLE official_warehouse_asn "
        "DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci, "
        "MODIFY COLUMN store_code VARCHAR(100) CHARACTER SET utf8mb4 "
        "COLLATE utf8mb4_0900_ai_ci NOT NULL, "
        "MODIFY COLUMN site_code VARCHAR(20) CHARACTER SET utf8mb4 "
        "COLLATE utf8mb4_0900_ai_ci NOT NULL;"
        "CREATE TABLE official_warehouse_asn_line ("
        "id BIGINT NOT NULL, asn_id BIGINT NOT NULL, owner_user_id BIGINT NOT NULL, "
        "store_code VARCHAR(100) NOT NULL, site_code VARCHAR(20) NOT NULL, "
        "is_deleted BIT(1) NOT NULL DEFAULT b'0', PRIMARY KEY (id)"
        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;"
        "CREATE TABLE official_warehouse_asn_shipping_batch_link ("
        "id BIGINT NOT NULL, asn_id BIGINT NOT NULL, asn_line_id BIGINT NOT NULL, "
        "owner_user_id BIGINT NOT NULL, store_code VARCHAR(100) NOT NULL, "
        "site_code VARCHAR(20) NOT NULL, product_variant_id BIGINT DEFAULT NULL, "
        "is_deleted BIT(1) NOT NULL DEFAULT b'0', PRIMARY KEY (id), "
        "KEY idx_official_warehouse_asn_shipping_product "
        "(owner_user_id,store_code,site_code,product_variant_id,is_deleted)"
        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;"
        "INSERT INTO official_warehouse_asn "
        "(id,owner_user_id,store_code,site_code,is_deleted) VALUES "
        "(900,307,'STORE-A','SA',b'0'),(901,307,'متجر-A','AE',b'0');"
        "INSERT INTO official_warehouse_asn_line "
        "(id,asn_id,owner_user_id,store_code,site_code,is_deleted) VALUES "
        "(910,900,307,'store-a','sa',b'0'),"
        "(911,901,307,'متجر-A','ae',b'0');"
        "INSERT INTO official_warehouse_asn_shipping_batch_link "
        "(id,asn_id,asn_line_id,owner_user_id,store_code,site_code,"
        "product_variant_id,is_deleted) VALUES "
        "(920,900,910,307,'STORE-A','SA',1001,b'0'),"
        "(921,901,911,307,'متجر-A','AE',1002,b'0');"
    )
    with test_case.assertRaises(MySqlExecutionError) as caught:
        database.client.execute(_original_scope_query())
    test_case.assertEqual(1267, caught.exception.error_code)
    return _fixture_signature(database)


def approve_release_tail(test_case, runner, approvals, migrations):
    noon_auth_wait = approve_noon_auth_wait(
        test_case, runner, approvals, migrations
    )
    migration = next(item for item in migrations if item.key == MIGRATION_KEY)
    with test_case.assertRaisesRegex(MigrationError, "missing " + migration.key):
        runner.apply(approved_managed=approvals)
    approvals.append(migration.key)
    return noon_auth_wait, migration


def verify_release_tail(
    test_case,
    database,
    noon_auth_wait,
    migration,
    fixture_signature,
):
    verify_noon_auth_wait_migration(test_case, database, noon_auth_wait)
    test_case.assertTrue(database.postcheck(migration))
    test_case.assertEqual("2", database.client.execute(_original_scope_query()))
    test_case.assertEqual(fixture_signature, _fixture_signature(database))

    database.run_script(migration)
    database.run_script(migration)
    test_case.assertTrue(database.postcheck(migration))
    test_case.assertEqual(fixture_signature, _fixture_signature(database))

    database.client.execute(
        "ALTER TABLE official_warehouse_asn_shipping_batch_link "
        "DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    )
    test_case.assertFalse(database.postcheck(migration))
    with test_case.assertRaises(MySqlExecutionError) as caught:
        database.run_script(migration)
    test_case.assertEqual(3819, caught.exception.error_code)
    database.client.execute(
        "ALTER TABLE official_warehouse_asn_shipping_batch_link "
        "DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
    )
    database.run_script(migration)
    test_case.assertTrue(database.postcheck(migration))
    test_case.assertEqual(fixture_signature, _fixture_signature(database))


def _fixture_signature(database):
    return (
        database.client.execute(
            "SELECT CONCAT(COUNT(*),':',SHA2(GROUP_CONCAT(CONCAT_WS(':',"
            "id,HEX(store_code),HEX(site_code),COALESCE(product_variant_id,0),"
            "HEX(is_deleted)) ORDER BY id SEPARATOR '|'),256)) "
            "FROM official_warehouse_asn_shipping_batch_link;"
        ),
        database.client.execute(
            "SELECT GROUP_CONCAT(CONCAT(seq_in_index,':',column_name) "
            "ORDER BY seq_in_index SEPARATOR ',') "
            "FROM information_schema.statistics WHERE table_schema=DATABASE() "
            "AND table_name='official_warehouse_asn_shipping_batch_link' "
            "AND index_name='idx_official_warehouse_asn_shipping_product';"
        ),
    )


def _original_scope_query():
    return (
        "SELECT COUNT(*) FROM official_warehouse_asn_shipping_batch_link link "
        "WHERE link.is_deleted=b'0' AND EXISTS (SELECT 1 FROM "
        "official_warehouse_asn parent_asn JOIN official_warehouse_asn_line "
        "parent_line ON parent_line.id=link.asn_line_id "
        "AND parent_line.asn_id=parent_asn.id "
        "AND parent_line.owner_user_id=parent_asn.owner_user_id "
        "AND UPPER(parent_line.store_code)=UPPER(parent_asn.store_code) "
        "AND UPPER(parent_line.site_code)=UPPER(parent_asn.site_code) "
        "AND parent_line.is_deleted=b'0' WHERE parent_asn.id=link.asn_id "
        "AND parent_asn.owner_user_id=link.owner_user_id "
        "AND UPPER(parent_asn.store_code)=UPPER(link.store_code) "
        "AND UPPER(parent_asn.site_code)=UPPER(link.site_code) "
        "AND parent_asn.is_deleted=b'0');"
    )
