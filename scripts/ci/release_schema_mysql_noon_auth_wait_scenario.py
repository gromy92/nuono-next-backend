from __future__ import annotations


def prepare_noon_auth_wait_fixture(database):
    database.client.execute(
        "CREATE TABLE noon_auth_identity_recovery_item ("
        "id BIGINT NOT NULL AUTO_INCREMENT, recovery_id BIGINT NOT NULL, "
        "owner_user_id BIGINT NOT NULL, "
        "project_code VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL, "
        "store_code VARCHAR(100) DEFAULT NULL, site_code VARCHAR(32) DEFAULT NULL, "
        "source_task_id BIGINT DEFAULT NULL, "
        "source_task_slot BIGINT GENERATED ALWAYS AS (COALESCE(source_task_id,0)) STORED, "
        "source_domain VARCHAR(64) DEFAULT NULL, expected_auth_version BIGINT NOT NULL, "
        "status VARCHAR(32) NOT NULL DEFAULT 'PENDING', PRIMARY KEY (id), "
        "UNIQUE KEY uk_noon_auth_recovery_item_source "
        "(recovery_id,owner_user_id,project_code,source_task_slot)"
        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;"
        "CREATE TABLE sales_sync_task ("
        "id BIGINT NOT NULL, trigger_type VARCHAR(32) NOT NULL, "
        "failure_reason VARCHAR(1000) DEFAULT NULL, PRIMARY KEY (id)"
        ") ENGINE=InnoDB;"
        "CREATE TABLE product_listing_reauthentication_attempt ("
        "id BIGINT NOT NULL, PRIMARY KEY (id)"
        ") ENGINE=InnoDB;"
    )


def verify_noon_auth_wait_migration(test_case, database, migration):
    test_case.assertTrue(database.postcheck(migration))
    database.run_script(migration)
    test_case.assertTrue(database.postcheck(migration))
    database.client.execute(
        "INSERT INTO noon_auth_identity_recovery_item "
        "(recovery_id,owner_user_id,project_code,source_task_id,source_domain,expected_auth_version) "
        "VALUES (1,307,'PRJ108065',42,'PRODUCT_DELETE',1),"
        "(1,307,'PRJ108065',42,'PRODUCT_IMAGE_SUITE',1);"
    )
    test_case.assertEqual(
        "PRODUCT_DELETE:42,PRODUCT_IMAGE_SUITE:42",
        database.client.execute(
            "SELECT GROUP_CONCAT(source_task_key ORDER BY source_task_key) "
            "FROM noon_auth_identity_recovery_item;"
        ),
    )
    test_case.assertEqual(
        "2",
        database.client.execute(
            "SELECT COUNT(*) FROM information_schema.COLUMNS "
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sales_sync_task' "
            "AND COLUMN_NAME IN ('auth_recovery_id','listing_coverage_mode');"
        ),
    )
    test_case.assertEqual(
        "0",
        database.client.execute(
            "SELECT COUNT(*) FROM information_schema.TABLES "
            "WHERE TABLE_SCHEMA=DATABASE() "
            "AND TABLE_NAME='product_listing_reauthentication_attempt';"
        ),
    )
