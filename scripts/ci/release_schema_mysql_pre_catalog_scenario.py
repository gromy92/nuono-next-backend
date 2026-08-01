from __future__ import annotations

from schema_migrations.catalog import sha256_bytes
from schema_migrations.mysql_support import MySqlExecutionError


PUBLISHED_PRE_CATALOG_SHA256 = {
    "223_product_site_offer_active_state_evidence.sql": (
        "3e69492bdc3665c7a7609704c6ce4d82e90ac26347766639fd321d3dbf9b6742"
    ),
    "224_product_master_psku_zcode_lookup.sql": (
        "feefcabb4fc3a352e9b59103fd9ca7a835ffce49023f90cfc12b0d0ce0d206e8"
    ),
}


def verify_pre_catalog_bootstrap(
    test_case,
    database,
    resources,
    migrations,
    runner,
):
    """Recreate the production boundary before the 227+ catalog existed."""
    database.client.execute(
        "DROP TABLE IF EXISTS product_site_offer;"
        "DROP TABLE IF EXISTS product_master;"
        "CREATE TABLE product_site_offer ("
        "id BIGINT NOT NULL, logical_store_id BIGINT NOT NULL, "
        "site_id BIGINT NOT NULL, maintenance_enabled BIT(1) NOT NULL, "
        "is_active BIT(1) NOT NULL, PRIMARY KEY (id)) ENGINE=InnoDB;"
        "CREATE TABLE product_master ("
        "id BIGINT NOT NULL, logical_store_id BIGINT NOT NULL, "
        "partner_sku VARCHAR(100) DEFAULT NULL, "
        "current_z_code VARCHAR(100) DEFAULT NULL, "
        "sku_parent VARCHAR(100) NOT NULL, "
        "is_deleted BIT(1) NOT NULL DEFAULT b'0', "
        "PRIMARY KEY (id), "
        "UNIQUE KEY uk_product_master_store_partner_sku "
        "(logical_store_id, partner_sku), "
        "UNIQUE KEY uk_product_master_store_sku_parent "
        "(logical_store_id, sku_parent)"
        ") ENGINE=InnoDB;"
    )

    published_sql = {}
    for key, expected_sha256 in PUBLISHED_PRE_CATALOG_SHA256.items():
        content = (resources / "db/init" / key).read_bytes()
        test_case.assertEqual(
            expected_sha256,
            sha256_bytes(content),
            f"published migration {key} no longer matches production evidence",
        )
        published_sql[key] = content.decode("utf-8")

    database.client.execute(
        "DROP TABLE nuono_schema_migration_attempt;"
        "DROP TABLE nuono_schema_migration;"
    )
    test_case.assertEqual({}, database.load_states())
    approvals = [
        migration.key
        for migration in migrations
        if migration.kind == "MANAGED"
    ]
    with test_case.assertRaises(MySqlExecutionError) as caught:
        runner.apply(approved_managed=approvals)
    test_case.assertEqual(3819, caught.exception.error_code)

    database.client.execute(
        published_sql["223_product_site_offer_active_state_evidence.sql"]
    )
    with test_case.assertRaises(MySqlExecutionError) as caught:
        runner.apply(approved_managed=approvals)
    test_case.assertEqual(3819, caught.exception.error_code)

    published_224 = published_sql["224_product_master_psku_zcode_lookup.sql"]
    database.client.execute(published_224)
    database.client.execute(published_224)

    _verify_product_site_offer_contract(test_case, database)
    _verify_product_master_index_contract(test_case, database)
    database.client.execute(
        "INSERT INTO product_master "
        "(id,logical_store_id,partner_sku,current_z_code,sku_parent,is_deleted) "
        "VALUES "
        "(1001,307,'PSKU-SHARED-A','Z-SHARED','Z-SHARED',b'0'),"
        "(1002,307,'PSKU-SHARED-B','Z-SHARED','Z-SHARED',b'0');"
    )
    with test_case.assertRaises(MySqlExecutionError) as caught:
        database.client.execute(
            "INSERT INTO product_master "
            "(id,logical_store_id,partner_sku,current_z_code,sku_parent,is_deleted) "
            "VALUES "
            "(1003,307,'PSKU-SHARED-A','Z-OTHER','Z-OTHER',b'0');"
        )
    test_case.assertEqual(1062, caught.exception.error_code)
    test_case.assertEqual(
        "2",
        database.client.execute(
            "SELECT COUNT(*) FROM product_master "
            "WHERE logical_store_id=307 AND current_z_code='Z-SHARED' "
            "AND sku_parent='Z-SHARED' AND is_deleted=b'0';"
        ),
    )

    test_case.assertEqual(
        [migration.key for migration in migrations[1:]],
        runner.apply(approved_managed=approvals),
    )

    _verify_product_site_offer_contract(test_case, database)
    _verify_product_master_index_contract(test_case, database)

    states = database.load_states()
    test_case.assertEqual(
        {migration.key for migration in migrations},
        set(states),
    )
    for pre_catalog_key in PUBLISHED_PRE_CATALOG_SHA256:
        test_case.assertNotIn(pre_catalog_key, states)


def _verify_product_site_offer_contract(test_case, database):
    test_case.assertEqual(
        "1",
        database.client.execute(
            "SELECT COUNT(*)=2 FROM information_schema.columns "
            "WHERE table_schema=DATABASE() "
            "AND table_name='product_site_offer' AND ("
            "(column_name='active_state_source' "
            "AND data_type='varchar' AND column_type='varchar(80)' "
            "AND is_nullable='YES' AND column_default IS NULL AND extra='') "
            "OR (column_name='active_state_synced_at' "
            "AND data_type='datetime' AND column_type='datetime' "
            "AND is_nullable='YES' AND column_default IS NULL AND extra=''));"
        ),
    )
    test_case.assertEqual(
        "1",
        database.client.execute(
            "SELECT IF(COUNT(*)=4 AND MIN(non_unique)=1 "
            "AND MAX(non_unique)=1 AND MIN(index_type)='BTREE' "
            "AND MAX(index_type)='BTREE' AND SUM(sub_part IS NULL)=4 "
            "AND SUM(collation='A')=4 AND SUM(is_visible='YES')=4 "
            "AND SUM(expression IS NULL)=4 AND GROUP_CONCAT("
            "CONCAT(seq_in_index,':',column_name) "
            "ORDER BY seq_in_index SEPARATOR ',')="
            "'1:logical_store_id,2:site_id,3:maintenance_enabled,4:is_active',"
            "1,0) FROM information_schema.statistics "
            "WHERE table_schema=DATABASE() "
            "AND table_name='product_site_offer' "
            "AND index_name='idx_product_site_offer_replenishment_coverage';"
        ),
    )


def _verify_product_master_index_contract(test_case, database):
    test_case.assertEqual(
        "2",
        database.client.execute(
            "SELECT COUNT(*) FROM information_schema.statistics "
            "WHERE table_schema=DATABASE() AND table_name='product_master' "
            "AND index_name='uk_product_master_store_partner_sku';"
        ),
    )
    test_case.assertEqual(
        "2",
        database.client.execute(
            "SELECT COUNT(*) FROM information_schema.statistics "
            "WHERE table_schema=DATABASE() AND table_name='product_master' "
            "AND index_name='uk_product_master_store_partner_sku' "
            "AND non_unique=0 AND index_type='BTREE' AND is_visible='YES' "
            "AND expression IS NULL AND sub_part IS NULL AND collation='A' "
            "AND ((seq_in_index=1 AND column_name='logical_store_id') "
            "OR (seq_in_index=2 AND column_name='partner_sku'));"
        ),
    )
    test_case.assertEqual(
        "3",
        database.client.execute(
            "SELECT COUNT(*) FROM information_schema.statistics "
            "WHERE table_schema=DATABASE() AND table_name='product_master' "
            "AND index_name='idx_product_master_store_sku_parent_lookup';"
        ),
    )
    test_case.assertEqual(
        "3",
        database.client.execute(
            "SELECT COUNT(*) FROM information_schema.statistics "
            "WHERE table_schema=DATABASE() AND table_name='product_master' "
            "AND index_name='idx_product_master_store_sku_parent_lookup' "
            "AND non_unique=1 AND index_type='BTREE' AND is_visible='YES' "
            "AND expression IS NULL AND sub_part IS NULL AND collation='A' "
            "AND ((seq_in_index=1 AND column_name='logical_store_id') "
            "OR (seq_in_index=2 AND column_name='sku_parent') "
            "OR (seq_in_index=3 AND column_name='is_deleted'));"
        ),
    )
    test_case.assertEqual(
        "0",
        database.client.execute(
            "SELECT COUNT(*) FROM information_schema.statistics "
            "WHERE table_schema=DATABASE() AND table_name='product_master' "
            "AND index_name='uk_product_master_store_sku_parent';"
        ),
    )
