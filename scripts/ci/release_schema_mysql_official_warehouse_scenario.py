from __future__ import annotations

from schema_migrations.mysql_support import MySqlExecutionError


def verify_appointment_concurrency_migration(test_case, database, migrations):
    migration = next(
        item for item in migrations
        if item.key == "234_official_warehouse_appointment_concurrency.sql"
    )
    test_case.assertTrue(database.postcheck(migration))
    database.run_script(migration)
    database.run_script(migration)
    database.client.execute(
        "ALTER TABLE official_warehouse_appointment MODIFY COLUMN "
        "active_asn_slot BIGINT GENERATED ALWAYS AS ("
        "CASE WHEN COALESCE(is_deleted,b'0') = b'0' "
        "AND status <> 'CAN\\\\CELED' THEN asn_id ELSE NULL END"
        ") STORED;"
    )
    test_case.assertFalse(database.postcheck(migration))
    database.client.execute(
        "ALTER TABLE official_warehouse_appointment MODIFY COLUMN "
        "active_asn_slot BIGINT GENERATED ALWAYS AS ("
        "CASE WHEN COALESCE(is_deleted,b'0') = b'0' "
        "AND status <> '`CANCELED`' THEN asn_id ELSE NULL END"
        ") STORED;"
    )
    test_case.assertFalse(database.postcheck(migration))
    database.client.execute(
        "ALTER TABLE official_warehouse_appointment MODIFY COLUMN "
        "active_asn_slot BIGINT GENERATED ALWAYS AS ("
        "CASE WHEN COALESCE(is_deleted,b'0') = b'0' "
        "AND status <> _latin1'CANCELED' THEN asn_id ELSE NULL END"
        ") STORED;"
    )
    test_case.assertFalse(database.postcheck(migration))
    database.client.execute(
        "ALTER TABLE official_warehouse_appointment MODIFY COLUMN "
        "active_asn_slot BIGINT GENERATED ALWAYS AS ("
        "CASE WHEN COALESCE(is_deleted,b'0') = b'0' "
        "AND status <> 'CANCELED' THEN asn_id ELSE NULL END"
        ") STORED;"
    )
    test_case.assertTrue(database.postcheck(migration))
    database.client.execute(
        "ALTER TABLE official_warehouse_appointment MODIFY COLUMN "
        "active_remote_slot VARCHAR(384) CHARACTER SET utf8mb4 "
        "COLLATE utf8mb4_bin GENERATED ALWAYS AS ("
        "CASE WHEN COALESCE(is_deleted,b'0') = b'0' "
        "AND status <> 'CANCELED' THEN CONCAT("
        "CHAR_LENGTH(UPPER(TRIM(COALESCE('project_code','')))),':',"
        "UPPER(TRIM(COALESCE('project_code',''))),'|',"
        "CHAR_LENGTH(UPPER(TRIM(COALESCE(partner_id,'')))),':',"
        "UPPER(TRIM(COALESCE(partner_id,''))),'|',"
        "CHAR_LENGTH(UPPER(TRIM(COALESCE(site_code,'')))),':',"
        "UPPER(TRIM(COALESCE(site_code,''))),'|',"
        "CHAR_LENGTH(UPPER(TRIM(COALESCE(noon_asn_nr,'')))),':',"
        "UPPER(TRIM(COALESCE(noon_asn_nr,'')))"
        ") ELSE NULL END) STORED;"
    )
    test_case.assertFalse(database.postcheck(migration))
    database.client.execute(
        "ALTER TABLE official_warehouse_appointment MODIFY COLUMN "
        "active_remote_slot VARCHAR(384) CHARACTER SET utf8mb4 "
        "COLLATE utf8mb4_bin GENERATED ALWAYS AS ("
        "CASE WHEN COALESCE(is_deleted,b'0') = b'0' "
        "AND status <> 'CANCELED' THEN CONCAT("
        "CHAR_LENGTH(UPPER(TRIM(COALESCE(project_code,'')))),':',"
        "UPPER(TRIM(COALESCE(project_code,''))),'|',"
        "CHAR_LENGTH(UPPER(TRIM(COALESCE(partner_id,'')))),':',"
        "UPPER(TRIM(COALESCE(partner_id,''))),'|',"
        "CHAR_LENGTH(UPPER(TRIM(COALESCE(site_code,'')))),':',"
        "UPPER(TRIM(COALESCE(site_code,''))),'|',"
        "CHAR_LENGTH(UPPER(TRIM(COALESCE(noon_asn_nr,'')))),':',"
        "UPPER(TRIM(COALESCE(noon_asn_nr,'')))"
        ") ELSE NULL END) STORED;"
    )
    test_case.assertTrue(database.postcheck(migration))
    database.client.execute(
        "ALTER TABLE official_warehouse_appointment "
        "MODIFY COLUMN asn_id BIGINT NULL;"
    )
    test_case.assertFalse(database.postcheck(migration))
    database.client.execute(
        "ALTER TABLE official_warehouse_appointment "
        "MODIFY COLUMN asn_id BIGINT NOT NULL;"
    )
    test_case.assertTrue(database.postcheck(migration))
    database.client.execute(
        "ALTER TABLE official_warehouse_appointment "
        "MODIFY COLUMN status VARCHAR(40) NULL DEFAULT 'PENDING';"
    )
    test_case.assertFalse(database.postcheck(migration))
    database.client.execute(
        "ALTER TABLE official_warehouse_appointment "
        "MODIFY COLUMN status VARCHAR(40) NOT NULL DEFAULT 'PENDING';"
    )
    test_case.assertTrue(database.postcheck(migration))
    schema_drifts = (
        ("official_warehouse_appointment", "id INT NOT NULL", "id BIGINT NOT NULL"),
        ("official_warehouse_appointment", "owner_user_id BIGINT NULL", "owner_user_id BIGINT NOT NULL"),
        ("official_warehouse_appointment", "store_code VARCHAR(99) NOT NULL", "store_code VARCHAR(100) NOT NULL"),
        ("official_warehouse_appointment", "attempt_count INT NULL DEFAULT 0", "attempt_count INT NOT NULL DEFAULT 0"),
        ("official_warehouse_asn", "id INT NOT NULL", "id BIGINT NOT NULL"),
        ("official_warehouse_asn", "owner_user_id BIGINT NULL", "owner_user_id BIGINT NOT NULL"),
        ("official_warehouse_asn", "store_code VARCHAR(99) NOT NULL", "store_code VARCHAR(100) NOT NULL"),
        ("official_warehouse_asn", "site_code VARCHAR(19) NOT NULL", "site_code VARCHAR(20) NOT NULL"),
        ("official_warehouse_asn", "is_deleted BIT(2) DEFAULT b'0'", "is_deleted BIT(1) NOT NULL DEFAULT b'0'"),
    )
    for table_name, drift_definition, restored_definition in schema_drifts:
        database.client.execute(
            f"ALTER TABLE {table_name} MODIFY COLUMN {drift_definition};"
        )
        test_case.assertFalse(database.postcheck(migration))
        database.client.execute(
            f"ALTER TABLE {table_name} MODIFY COLUMN {restored_definition};"
        )
        test_case.assertTrue(database.postcheck(migration))
    database.client.execute(
        "INSERT INTO official_warehouse_asn "
        "(id,owner_user_id,store_code,site_code,is_deleted) VALUES "
        "(1,307,'STORE-A','SA',b'0'),(2,307,'STORE-A','SA',b'0'),"
        "(3,307,'STORE-A','SA',b'0'),(4,307,'STORE-A','SA',b'0');"
        "INSERT INTO official_warehouse_appointment "
        "(id,asn_id,owner_user_id,store_code,site_code,project_code,"
        "partner_id,noon_asn_nr,status,attempt_count,is_deleted) VALUES "
        "(100,1,307,'STORE-A','SA','P1','PARTNER','ASN-1','PENDING',0,b'0');"
    )
    duplicate_local = (
        "INSERT INTO official_warehouse_appointment "
        "(id,asn_id,owner_user_id,store_code,site_code,project_code,"
        "partner_id,noon_asn_nr,status,attempt_count,is_deleted) VALUES "
        "(101,1,307,'STORE-A','SA','P2','PARTNER','ASN-2','PENDING',0,b'0');"
    )
    with test_case.assertRaises(MySqlExecutionError) as caught:
        database.client.execute(duplicate_local)
    test_case.assertEqual(1062, caught.exception.error_code)
    database.client.execute(
        "UPDATE official_warehouse_appointment SET status='CANCELED' WHERE id=100;"
        "INSERT INTO official_warehouse_appointment "
        "(id,asn_id,owner_user_id,store_code,site_code,project_code,"
        "partner_id,noon_asn_nr,status,attempt_count,is_deleted) VALUES "
        "(102,1,307,'STORE-A','SA','P1','PARTNER','ASN-1','PENDING',0,b'0');"
    )
    duplicate_remote = duplicate_local.replace(
        "(101,1,307,'STORE-A','SA','P2','PARTNER','ASN-2'",
        "(103,2,307,'STORE-A','SA','P1','PARTNER','ASN-1'",
    )
    with test_case.assertRaises(MySqlExecutionError) as caught:
        database.client.execute(duplicate_remote)
    test_case.assertEqual(1062, caught.exception.error_code)
    database.client.execute(
        "INSERT INTO official_warehouse_appointment "
        "(id,asn_id,owner_user_id,store_code,site_code,project_code,"
        "partner_id,noon_asn_nr,status,attempt_count,is_deleted) VALUES "
        "(104,3,307,'STORE-A','SA','A|B','C','ASN-X','PENDING',0,b'0'),"
        "(105,4,307,'STORE-A','SA','A','B|C','ASN-X','PENDING',0,b'0');"
    )
    test_case.assertTrue(database.postcheck(migration))
