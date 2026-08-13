from __future__ import annotations

import os
import sys
import unittest
from pathlib import Path

SCRIPT_ROOT = Path(__file__).resolve().parents[1]
REPOSITORY_ROOT = SCRIPT_ROOT.parent
if str(SCRIPT_ROOT) not in sys.path:
    sys.path.insert(0, str(SCRIPT_ROOT))

from noon_auth_owner_scope_takeover import build_manifest, load_snapshot  # noqa: E402
from noon_auth_owner_scope_takeover_sql import build_apply_sql  # noqa: E402
from schema_migrations.catalog import load_catalog  # noqa: E402
from schema_migrations.mysql_database import MySqlMigrationDatabase  # noqa: E402
from schema_migrations.mysql_support import MySqlExecutionError  # noqa: E402


@unittest.skipUnless(os.environ.get("NUONO_MIGRATION_MYSQL_DEFAULTS_FILE"),
                     "requires an isolated MySQL schema")
class NoonAuthOwnerScopeTakeoverMySqlTest(unittest.TestCase):
    PREDECESSOR = 9100877
    SOURCE = 9100882
    IDENTITY = "t" * 64

    def test_owner_takeover_preserves_non_scope_waiter_and_budget(self):
        database = self._database()
        self.addCleanup(database.close)
        resources = REPOSITORY_ROOT / "src/main/resources"
        self._prepare_schema(database, resources)
        migration = next(item for item in load_catalog(resources) if item.order == 251)
        database.client.execute(migration.script_sql)
        self._prepare_rows(database)

        snapshot = load_snapshot(database.client, self.PREDECESSOR, self.SOURCE, 307)
        manifest = build_manifest(snapshot, 307, ("PRJ0", "PRJ1", "PRJ2", "PRJ3"), "ci", self._provenance())
        database.client.execute(build_apply_sql(manifest, "ci"))

        self.assertEqual(
            "FAILED_FINAL:WAITING_PREDECESSOR:0:0:1:COALESCING:5:4:4:REAUTH_REQUIRED:9100882",
            database.client.execute_readonly(
                "SELECT CONCAT((SELECT status FROM noon_auth_identity_recovery WHERE id=9100877),':',"
                "source.status,':',source.send_budget_epoch,':',source.send_attempt_count,':',"
                "(SELECT COUNT(*) FROM noon_auth_identity_recovery_item WHERE recovery_id=9100882),':',"
                "scoped.status,':',(SELECT COUNT(*) FROM noon_auth_identity_recovery_item WHERE recovery_id=scoped.id),':',"
                "(SELECT COUNT(*) FROM noon_pull_task WHERE id IN (93001,93002,93003,93004) AND status='CANCELLED'),':',"
                "(SELECT COUNT(*) FROM noon_pull_plan WHERE id IN (94001,94002,94003,94004) AND paused=b'1'),':',"
                "state.status,':',state.active_recovery_id) FROM noon_auth_identity_recovery source "
                "JOIN noon_auth_identity_recovery scoped ON scoped.identity_key=source.identity_key "
                "AND scoped.status='COALESCING' JOIN noon_project_auth_state state ON state.owner_user_id=308 "
                "AND state.project_code='PRJ8' WHERE source.id=9100882;"
            ),
        )
        self.assertTrue(database.livecheck(migration))

    def test_scope_drift_fails_before_any_pause_or_move(self):
        database = self._database()
        self.addCleanup(database.close)
        resources = REPOSITORY_ROOT / "src/main/resources"
        self._prepare_schema(database, resources)
        migration = next(item for item in load_catalog(resources) if item.order == 251)
        database.client.execute(migration.script_sql)
        self._prepare_rows(database)

        snapshot = load_snapshot(database.client, self.PREDECESSOR, self.SOURCE, 307)
        manifest = build_manifest(snapshot, 307, ("PRJ0", "PRJ1", "PRJ2", "PRJ3"), "ci", self._provenance())
        manifest["predecessor"]["versionNo"] += 1
        with self.assertRaises(MySqlExecutionError):
            database.client.execute(build_apply_sql(manifest, "ci"))
        self.assertEqual(
            "MANUAL_HOLD:WAITING_PREDECESSOR:4:5",
            database.client.execute_readonly(
                "SELECT CONCAT((SELECT status FROM noon_auth_identity_recovery WHERE id=9100877),':',"
                "(SELECT status FROM noon_auth_identity_recovery WHERE id=9100882),':',"
                "(SELECT COUNT(*) FROM noon_pull_plan WHERE id IN (94001,94002,94003,94004) AND paused=b'0'),':',"
                "(SELECT COUNT(*) FROM noon_auth_identity_recovery_item WHERE recovery_id=9100882 AND owner_user_id=307));"
            ),
        )

    def _database(self):
        return MySqlMigrationDatabase(Path(os.environ["NUONO_MIGRATION_MYSQL_DEFAULTS_FILE"]),
            expected_schema=os.environ.get("NUONO_MIGRATION_EXPECTED_SCHEMA", "nuono_schema_migration_ci"),
            expected_host="127.0.0.1", expected_port=int(os.environ.get("NUONO_MIGRATION_EXPECTED_PORT", "3306")))

    def _prepare_schema(self, database, resources):
        database.client.execute("SET FOREIGN_KEY_CHECKS=0;DROP TABLE IF EXISTS noon_auth_owner_scope_audit,"
            "noon_auth_owner_scope_manifest_item,noon_auth_owner_scope_manifest,noon_auth_identity_send_ledger,"
            "noon_auth_identity_recovery_item,noon_project_auth_state,noon_auth_identity_recovery,noon_pull_task,"
            "noon_pull_plan,noon_pull_id_sequence,noon_http_call_log;SET FOREIGN_KEY_CHECKS=1;")
        for order in (53, 58, 190, 238):
            database.client.execute(next((resources / "db/init").glob(f"{order:03d}_*.sql")).read_text())
        warehouse_sql = (resources / "db/init/134_official_warehouse_asn.sql").read_text()
        start = warehouse_sql.index("CREATE TABLE IF NOT EXISTS `noon_http_call_log`")
        end = warehouse_sql.index(";\n\n", start) + 1
        database.client.execute(warehouse_sql[start:end])

    def _prepare_rows(self, database):
        database.client.execute(
            "INSERT INTO noon_pull_plan (id,owner_user_id,store_code,site_code,pull_type,data_domain,trigger_mode,"
            "schedule_expression,enabled,paused,is_deleted) VALUES "
            "(94001,307,'STR0','SA','REPORT','SALES','SCHEDULED_DAILY','daily',b'1',b'0',b'0'),"
            "(94002,307,'STR1','SA','REPORT','ORDER','SCHEDULED_DAILY','daily',b'1',b'0',b'0'),"
            "(94003,307,'STR2','SA','REPORT','SALES','SCHEDULED_DAILY','daily',b'1',b'0',b'0'),"
            "(94004,307,'STR3','SA','REPORT','FINANCE_TRANSACTION','SCHEDULED_DAILY','daily',b'1',b'0',b'0');"
            "INSERT INTO noon_auth_identity_recovery (id,identity_key,status,generation_no,send_budget_epoch,"
            "send_attempt_count,coalesce_until,next_attempt_at,version_no,config_fingerprint,requested_at) VALUES "
            f"({self.PREDECESSOR},'{self.IDENTITY}','MANUAL_HOLD',0,0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),4,'{'d'*64}',UTC_TIMESTAMP(3)),"
            f"({self.SOURCE},'{self.IDENTITY}','WAITING_PREDECESSOR',0,0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),3,'{'d'*64}',UTC_TIMESTAMP(3));"
            f"UPDATE noon_auth_identity_recovery SET predecessor_recovery_id={self.PREDECESSOR} WHERE id={self.SOURCE};"
            f"UPDATE noon_auth_identity_recovery SET failure_code='IDENTITY_AUTH_FAILED' WHERE id={self.PREDECESSOR};"
            "INSERT INTO noon_project_auth_state (owner_user_id,project_code,identity_key,status,active_recovery_id,auth_version) VALUES "
            f"(307,'PRJ0','{self.IDENTITY}','REAUTH_REQUIRED',{self.SOURCE},1),(307,'PRJ1','{self.IDENTITY}','REAUTH_REQUIRED',{self.SOURCE},2),"
            f"(307,'PRJ2','{self.IDENTITY}','REAUTH_REQUIRED',{self.SOURCE},3),(307,'PRJ3','{self.IDENTITY}','REAUTH_REQUIRED',{self.SOURCE},4),"
            f"(308,'PRJ8','{self.IDENTITY}','REAUTH_REQUIRED',{self.SOURCE},8);"
        )
        values = []
        for item, owner, project, task, domain, version in (
                (92001, 307, "PRJ0", 93001, "SALES", 1), (92002, 307, "PRJ1", None, "STORE_BINDING", 2),
                (92003, 307, "PRJ1", 93002, "ORDER", 2), (92004, 307, "PRJ2", 93003, "SALES", 3),
                (92005, 307, "PRJ3", 93004, "FINANCE_TRANSACTION", 4), (92006, 308, "PRJ8", None, "STORE_BINDING", 8)):
            policy = "NONE" if task is None else "AUTO_RESUME"
            checkpoint = "PROJECT_BINDING" if task is None else "PERSISTED_TASK_STATE"
            values.append(f"({item},{self.SOURCE},{owner},'{project}','STR{project[3:]}','SA',{task or 'NULL'},'{domain}','{checkpoint}','{policy}',{version},'PENDING',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))")
        database.client.execute("INSERT INTO noon_auth_identity_recovery_item (id,recovery_id,owner_user_id,project_code,store_code,site_code,source_task_id,source_domain,source_checkpoint,resume_policy,expected_auth_version,status,gmt_create,gmt_updated) VALUES " + ",".join(values) + ";")
        database.client.execute("INSERT INTO noon_pull_task (id,plan_id,owner_user_id,store_code,site_code,pull_type,data_domain,trigger_mode,target_identity,active_lock_key,auth_recovery_id,status,retry_action,retryable,requires_manual_action,readiness_state,queued_at,started_at,is_deleted) VALUES " + ",".join(
            f"({task},{94000 + index},307,'STR{index - 1}','SA','REPORT','{domain}','SCHEDULED_DAILY','target-{task}','lock-{task}',{self.SOURCE},'BLOCKED_AUTH','WAIT_FOR_AUTH',b'1',b'0','auth_recovery_queued',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),b'0')"
            for index, (task, domain) in enumerate(((93001, "SALES"), (93002, "ORDER"), (93003, "SALES"), (93004, "FINANCE_TRANSACTION")), 1)) + ";")

    @staticmethod
    def _provenance():
        return {"manifestSha256": "a" * 64, "commit": "b" * 40, "runId": 1,
                "artifactName": "ci", "operationBundleSha256": "c" * 64}


if __name__ == "__main__":
    unittest.main()
