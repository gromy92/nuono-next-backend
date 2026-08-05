from __future__ import annotations

import os
import sys
import unittest
from pathlib import Path


SCRIPT_ROOT = Path(__file__).resolve().parents[1]
REPOSITORY_ROOT = SCRIPT_ROOT.parent
if str(SCRIPT_ROOT) not in sys.path:
    sys.path.insert(0, str(SCRIPT_ROOT))

from schema_migrations.mysql_database import MySqlMigrationDatabase  # noqa: E402
from schema_migrations.mysql_support import MySqlExecutionError  # noqa: E402
from ci.dp_pull_runtime_cleanup_marker_scenario import (  # noqa: E402
    run_cleanup_marker_scenario,
)
from ci.dp_pull_runtime_scope_binding_scenario import (  # noqa: E402
    insert_valid_scope_binding_scenario,
)
from ci.dp_pull_runtime_successor_scenario import (  # noqa: E402
    drop_successor_objects,
    run_successor_schema_scenario,
)


MIGRATION_KEY = "243_dp_pull_runtime.sql"
RUNTIME_TABLES = (
    "dp_pull_task",
    "dp_pull_runtime_leader",
    "dp_pull_backoff_hold",
    "dp_pull_emergency_claim_hold",
    "dp_pull_schedule_cutover",
    "dp_pull_scope_admission",
    "dp_pull_scope_binding_epoch",
    "dp_pull_schedule_anchor",
    "dp_pull_scope_progress",
    "dp_pull_snapshot_stage",
    "dp_pull_snapshot_stage_page",
    "dp_pull_snapshot_stage_item",
    "dp_pull_snapshot_apply",
    "dp_pull_dp10_stage_page",
    "dp_pull_dp10_stage_item",
    "dp_pull_dp10_stage_fingerprint_count",
    "dp_pull_dp10_stage_identity",
    "dp_pull_dp10_stage_cleanup",
    "dp_pull_report_artifact",
    "dp_pull_report_download_locator",
    "dp_pull_report_apply",
)
SEQUENCE_TABLE = "noon_pull_id_sequence"
SEQUENCE_NAME = "dp_pull_task"
TASK_ID = 500001
SCOPE_BINDING_TASK_ID = TASK_ID + 10
EXTRA_COLUMN = "ci_additive_note"
EXTRA_INDEX = "idx_dp_pull_task_ci_additive_note"


@unittest.skipUnless(
    os.environ.get("NUONO_MIGRATION_MYSQL_DEFAULTS_FILE"),
    "requires an isolated MySQL schema",
)
class DpPullRuntimeSchemaMySqlTest(unittest.TestCase):
    def test_243_exact_livecheck_idempotency_and_constraints(self):
        defaults = Path(os.environ["NUONO_MIGRATION_MYSQL_DEFAULTS_FILE"])
        schema = os.environ.get(
            "NUONO_MIGRATION_EXPECTED_SCHEMA", "nuono_schema_migration_ci"
        )
        database = MySqlMigrationDatabase(
            defaults,
            expected_schema=schema,
            expected_host="127.0.0.1",
            expected_port=int(
                os.environ.get("NUONO_MIGRATION_EXPECTED_PORT", "3306")
            ),
        )
        self.addCleanup(database.close)
        sequence_preexisting = self.table_exists(database, SEQUENCE_TABLE)
        self.ensure_sequence_table(database)
        self.addCleanup(
            self.drop_fixture, database, sequence_preexisting
        )
        drop_successor_objects(database)
        self.drop_runtime_tables(database)
        database.client.execute(
            f"DELETE FROM `{SEQUENCE_TABLE}` WHERE sequence_name='{SEQUENCE_NAME}';"
        )

        resources = REPOSITORY_ROOT / "src/main/resources/db"
        migration = (resources / "init" / MIGRATION_KEY).read_text(
            encoding="utf-8"
        )
        exact = (resources / "postcheck" / MIGRATION_KEY).read_text(
            encoding="utf-8"
        )
        live = (resources / "livecheck" / MIGRATION_KEY).read_text(
            encoding="utf-8"
        )

        database.client.execute(migration)
        self.assert_leader_seed(database)
        self.insert_valid_task(database)
        self.insert_valid_cutover(database)
        insert_valid_scope_binding_scenario(database, SCOPE_BINDING_TASK_ID)
        database.client.execute(migration)

        self.assertEqual("1", database.client.execute_readonly(exact))
        self.assertEqual("1", database.client.execute_readonly(live))
        self.assert_sequence_floor(database)
        self.assert_auth_wait_absent(database)
        self.assert_key_checks_reject_invalid_rows(database)
        run_cleanup_marker_scenario(self, database, exact, live)

        database.client.execute(
            f"ALTER TABLE dp_pull_task ADD COLUMN `{EXTRA_COLUMN}` "
            "VARCHAR(32) NULL, "
            f"ADD KEY `{EXTRA_INDEX}` (`{EXTRA_COLUMN}`);"
        )
        self.assertEqual("0", database.client.execute_readonly(exact))
        self.assertEqual("1", database.client.execute_readonly(live))

        database.client.execute(
            f"ALTER TABLE dp_pull_task DROP INDEX `{EXTRA_INDEX}`, "
            f"DROP COLUMN `{EXTRA_COLUMN}`;"
        )
        self.assertEqual("1", database.client.execute_readonly(exact))
        self.assertEqual("1", database.client.execute_readonly(live))
        run_successor_schema_scenario(self, database, resources, exact, live, SCOPE_BINDING_TASK_ID)

    @staticmethod
    def table_exists(database, table_name):
        return database.client.execute_readonly(
            "SELECT COUNT(*) FROM information_schema.tables "
            "WHERE table_schema=DATABASE() "
            f"AND table_name='{table_name}';"
        ) == "1"

    @staticmethod
    def ensure_sequence_table(database):
        database.client.execute(
            "CREATE TABLE IF NOT EXISTS `noon_pull_id_sequence` ("
            "`sequence_name` VARCHAR(100) NOT NULL,"
            "`next_id` BIGINT NOT NULL,"
            "`gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
            "`gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP "
            "ON UPDATE CURRENT_TIMESTAMP,"
            "PRIMARY KEY (`sequence_name`)"
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 "
            "COLLATE=utf8mb4_unicode_ci;"
        )

    @staticmethod
    def drop_runtime_tables(database):
        database.client.execute(
            "SET FOREIGN_KEY_CHECKS=0;"
            + "".join(
                f"DROP TABLE IF EXISTS `{table}`;"
                for table in reversed(RUNTIME_TABLES)
            )
            + "SET FOREIGN_KEY_CHECKS=1;"
        )

    @classmethod
    def drop_fixture(cls, database, sequence_preexisting):
        cls.drop_runtime_tables(database)
        if sequence_preexisting:
            database.client.execute(
                f"DELETE FROM `{SEQUENCE_TABLE}` "
                f"WHERE sequence_name='{SEQUENCE_NAME}';"
            )
        else:
            database.client.execute(
                f"DROP TABLE IF EXISTS `{SEQUENCE_TABLE}`;"
            )

    @staticmethod
    def insert_valid_task(database):
        database.client.execute(
            "INSERT INTO dp_pull_task ("
            "id,operation_code,provider_channel,owner_user_id,account_key,"
            "scope_key,schedule_slot,business_window_key,state,step_code,"
            "attempt,fence_epoch,version_no,gmt_create,gmt_updated"
            ") VALUES ("
            f"{TASK_ID},'DP04','NOON',307,'307::STORE','STORE::SA',"
            "'2026-08-03 03:00:00.000','2026-08-03','QUEUED','FETCH_PAGE',"
            "0,0,0,NOW(3),NOW(3));"
        )

    @staticmethod
    def insert_valid_cutover(database):
        database.client.execute(
            "INSERT INTO dp_pull_scope_admission ("
            "scope_key,scope_namespace,owner_user_id,account_key,admission_kind,"
            "source_binding_sha256,cutover_key,gmt_create"
            ") VALUES ("
            "'NOON_SCOPE-ci','NOON_SCOPE',307,'307::STORE','CUTOVER_EXISTING',"
            f"'{('a' * 64)}','cutover-ci',NOW(3));"
            "INSERT INTO dp_pull_schedule_cutover ("
            "operation_code,cutover_key,state,expected_scope_count,"
            "anchor_manifest_sha256,activated_at_utc,version_no,gmt_create,gmt_updated"
            ") VALUES ("
            f"'DP04','cutover-ci','ACTIVE',1,'{('b' * 64)}',NOW(3),0,NOW(3),NOW(3));"
            "INSERT INTO dp_pull_schedule_anchor ("
            "operation_code,scope_key,cutover_key,anchor_kind,reconcile_after_utc,"
            "anchor_evidence_sha256,gmt_create"
            ") VALUES ("
            f"'DP04','NOON_SCOPE-ci','cutover-ci','CUTOVER_RECONCILED',NOW(3),"
            f"'{('c' * 64)}',NOW(3));"
        )

    def assert_leader_seed(self, database):
        self.assertEqual(
            "1/1",
            database.client.execute_readonly(
                "SELECT CONCAT(COUNT(*),'/',COALESCE(SUM("
                "runtime_name='daily_pull' AND leader_owner IS NULL "
                "AND leader_epoch=0 AND lease_until IS NULL),0)) "
                "FROM dp_pull_runtime_leader;"
            ),
        )

    def assert_sequence_floor(self, database):
        self.assertEqual(
            f"1/{SCOPE_BINDING_TASK_ID}/{SCOPE_BINDING_TASK_ID}",
            database.client.execute_readonly(
                "SELECT CONCAT(COUNT(*),'/',MIN(next_id),'/',"
                "(SELECT MAX(id) FROM dp_pull_task)) "
                "FROM noon_pull_id_sequence "
                "WHERE sequence_name='dp_pull_task';"
            ),
        )

    def assert_auth_wait_absent(self, database):
        self.assertEqual(
            "0",
            database.client.execute_readonly(
                "SELECT COUNT(*) FROM information_schema.tables "
                "WHERE table_schema=DATABASE() "
                "AND table_name='dp_pull_auth_wait';"
            ),
        )

    def assert_key_checks_reject_invalid_rows(self, database):
        self.assert_mysql_rejects(
            database,
            "INSERT INTO dp_pull_task ("
            "id,operation_code,provider_channel,owner_user_id,account_key,"
            "scope_key,schedule_slot,business_window_key,state,step_code,"
            "attempt,fence_epoch,version_no,gmt_create,gmt_updated"
            ") VALUES ("
            f"{TASK_ID + 1},'DP09','NOON',307,'307::STORE','STORE::SA',"
            "'2026-08-03 03:00:00.000','2026-08-03-invalid','QUEUED',"
            "'FETCH_PAGE',0,0,0,NOW(3),NOW(3));",
        )
        self.assert_mysql_rejects(
            database,
            "INSERT INTO dp_pull_runtime_leader "
            "(runtime_name,leader_owner,leader_epoch,lease_until,gmt_create,gmt_updated) "
            "VALUES ('another_runtime',NULL,0,NULL,NOW(3),NOW(3));",
        )
        self.assert_mysql_rejects(
            database,
            "INSERT INTO dp_pull_backoff_hold ("
            "hold_key,share_level,provider_channel,account_key,operation_code,"
            "scope_key,egress_key,blocked_until,sanitized_code,gmt_create,gmt_updated"
            ") VALUES ("
            "'not-a-sha256','EXACT','NOON','307::STORE','DP04','STORE::SA',"
            "NULL,DATE_ADD(NOW(3),INTERVAL 5 MINUTE),'RATE_LIMITED',NOW(3),NOW(3));",
        )
        self.assert_mysql_rejects(
            database,
            "INSERT INTO dp_pull_snapshot_stage ("
            "task_id,active_fence_epoch,authority_kind,version_no,gmt_create,gmt_updated"
            ") VALUES ("
            f"{TASK_ID},1,'PAGED_GENERATION',0,NOW(3),NOW(3));",
        )
        self.assert_mysql_rejects(
            database,
            "INSERT INTO dp_pull_snapshot_apply ("
            "task_id,operation_code,scope_key,business_window_key,applied_fence_epoch,"
            "authority_kind,authority_token_sha256,declared_collection_count,"
            "source_item_count,applied_item_count,identity_skipped_item_count,"
            "business_skipped_item_count,last_page,applied_at,gmt_create"
            ") VALUES ("
            f"{TASK_ID},'DP04','STORE::SA','2026-08-03',1,'PAGED_GENERATION',"
            f"'{('d' * 64)}',1,1,0,0,0,1,NOW(3),NOW(3));",
        )

    def assert_mysql_rejects(self, database, sql, expected_error=3819):
        with self.assertRaises(MySqlExecutionError) as caught:
            database.client.execute(sql)
        self.assertEqual(expected_error, caught.exception.error_code)



if __name__ == "__main__":
    unittest.main()
