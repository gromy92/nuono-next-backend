from __future__ import annotations

import os
import sys
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from schema_migrations.mysql_database import MySqlMigrationDatabase  # noqa: E402
from schema_migrations.mysql_support import MySqlExecutionError  # noqa: E402


MIGRATION_KEY = "242_file_management_parse_retirement.sql"
TABLES = (
    "file_mgmt_parse_task",
    "file_mgmt_parse_target_plan_scope",
    "file_mgmt_parse_target_plan",
    "user_menu",
    "role_menu",
    "menu",
)


@unittest.skipUnless(
    os.environ.get("NUONO_MIGRATION_MYSQL_DEFAULTS_FILE"),
    "requires an isolated MySQL schema",
)
class FileParseRetirementMySqlTest(unittest.TestCase):
    def test_same_session_drain_ack_blocker_and_idempotent_retirement(self):
        defaults = Path(os.environ["NUONO_MIGRATION_MYSQL_DEFAULTS_FILE"])
        schema = os.environ.get(
            "NUONO_MIGRATION_EXPECTED_SCHEMA", "nuono_schema_migration_ci"
        )
        database = MySqlMigrationDatabase(
            defaults,
            expected_schema=schema,
            expected_host="127.0.0.1",
            expected_port=int(os.environ.get("NUONO_MIGRATION_EXPECTED_PORT", "3306")),
        )
        self.addCleanup(database.close)
        self.addCleanup(self.drop_fixture, database)
        self.create_fixture(database)
        resources = SCRIPT_DIR.parent / "src/main/resources/db"
        migration = (resources / "init" / MIGRATION_KEY).read_text(encoding="utf-8")
        postcheck = (resources / "postcheck" / MIGRATION_KEY).read_text(
            encoding="utf-8"
        )

        with self.assertRaises(MySqlExecutionError):
            self.run_locked(database, migration, acknowledge=False)
        self.assertEqual("5", self.active_entry_count(database))

        with self.assertRaises(MySqlExecutionError):
            self.run_locked(database, migration, acknowledge=True)
        self.assertEqual("5", self.active_entry_count(database))

        database.client.execute(
            "UPDATE file_mgmt_parse_task SET status='published', "
            "locked_by=NULL, locked_at=NULL, started_at=NOW(), "
            "finished_at=NOW(), next_run_at=NULL WHERE id=1;"
        )
        self.run_locked(database, migration, acknowledge=True)
        self.assertEqual("1", database.client.execute_readonly(postcheck))
        self.assertEqual("0", self.active_entry_count(database))
        self.assertEqual(
            "1/published",
            database.client.execute_readonly(
                "SELECT CONCAT(COUNT(*),'/',MIN(status)) "
                "FROM file_mgmt_parse_task;"
            ),
        )

        self.run_locked(database, migration, acknowledge=True)
        self.assertEqual("1", database.client.execute_readonly(postcheck))

    @staticmethod
    def run_locked(database, migration, *, acknowledge):
        database.acquire_lock(5)
        try:
            if acknowledge:
                database.acknowledge_runtime_drain(MIGRATION_KEY)
            database.client.execute(migration)
        finally:
            database.release_lock()

    @staticmethod
    def active_entry_count(database):
        return database.client.execute_readonly(
            "SELECT "
            "(SELECT COUNT(*) FROM menu WHERE is_deleted=b'0') + "
            "(SELECT COUNT(*) FROM role_menu WHERE is_deleted=b'0') + "
            "(SELECT COUNT(*) FROM user_menu WHERE is_deleted=b'0') + "
            "(SELECT COUNT(*) FROM file_mgmt_parse_target_plan "
            " WHERE status='active' AND is_deleted=b'0') + "
            "(SELECT COUNT(*) FROM file_mgmt_parse_target_plan_scope "
            " WHERE status='active' AND is_deleted=b'0');"
        )

    @staticmethod
    def create_fixture(database):
        FileParseRetirementMySqlTest.drop_fixture(database)
        database.client.execute(
            "CREATE TABLE menu (id BIGINT PRIMARY KEY,url_path VARCHAR(255),"
            "is_deleted BIT(1) NOT NULL,gmt_updated DATETIME);"
            "CREATE TABLE role_menu (menu_id BIGINT,is_deleted BIT(1) NOT NULL,"
            "gmt_updated DATETIME);"
            "CREATE TABLE user_menu (menu_id BIGINT,status INT NOT NULL,"
            "is_deleted BIT(1) NOT NULL,gmt_updated DATETIME);"
            "CREATE TABLE file_mgmt_parse_target_plan (id BIGINT PRIMARY KEY,"
            "status VARCHAR(32),is_deleted BIT(1) NOT NULL,gmt_updated DATETIME);"
            "CREATE TABLE file_mgmt_parse_target_plan_scope (id BIGINT PRIMARY KEY,"
            "status VARCHAR(32),is_deleted BIT(1) NOT NULL,gmt_updated DATETIME);"
            "CREATE TABLE file_mgmt_parse_task (id BIGINT PRIMARY KEY,"
            "status VARCHAR(32),is_deleted BIT(1) NOT NULL,locked_by VARCHAR(128),"
            "locked_at DATETIME,started_at DATETIME,finished_at DATETIME,"
            "next_run_at DATETIME);"
            "INSERT INTO menu VALUES (9301,'/system/file-management',b'0',NOW());"
            "INSERT INTO role_menu VALUES (9301,b'0',NOW());"
            "INSERT INTO user_menu VALUES (9301,1,b'0',NOW());"
            "INSERT INTO file_mgmt_parse_target_plan VALUES "
            "(1,'active',b'0',NOW());"
            "INSERT INTO file_mgmt_parse_target_plan_scope VALUES "
            "(1,'active',b'0',NOW());"
            "INSERT INTO file_mgmt_parse_task VALUES "
            "(1,'running',b'0','worker',NOW(),NOW(),NULL,NOW());"
        )

    @staticmethod
    def drop_fixture(database):
        database.client.execute(
            "SET FOREIGN_KEY_CHECKS=0;"
            + "".join(f"DROP TABLE IF EXISTS `{table}`;" for table in TABLES)
            + "SET FOREIGN_KEY_CHECKS=1;"
        )


if __name__ == "__main__":
    unittest.main()
