from __future__ import annotations

import os
import sys
import unittest
from pathlib import Path

SCRIPT_ROOT = Path(__file__).resolve().parents[1]
REPOSITORY_ROOT = SCRIPT_ROOT.parent
if str(SCRIPT_ROOT) not in sys.path:
    sys.path.insert(0, str(SCRIPT_ROOT))

from noon_auth_owner_scope_split import build_manifest, load_snapshot  # noqa: E402
from noon_auth_owner_scope_split_sql import build_apply_sql, build_rollback_sql  # noqa: E402
from noon_auth_manual_hold_retry import (  # noqa: E402
    build_manifest as build_retry_manifest,
    load_snapshot as load_retry_snapshot,
)
from noon_auth_manual_hold_retry_sql import build_apply_sql as build_retry_sql  # noqa: E402
from schema_migrations.catalog import load_catalog  # noqa: E402
from schema_migrations.mysql_database import MySqlMigrationDatabase  # noqa: E402


@unittest.skipUnless(
    os.environ.get("NUONO_MIGRATION_MYSQL_DEFAULTS_FILE"),
    "requires an isolated MySQL schema",
)
class NoonAuthOwnerScopeMySqlTest(unittest.TestCase):
    PREDECESSOR = 9100741
    SOURCE = 9100743
    IDENTITY = "c" * 64

    def test_split_rollback_retry_and_exact_successor_promotion(self):
        database = self._database()
        self.addCleanup(database.close)
        resources = REPOSITORY_ROOT / "src/main/resources"
        migration = next(item for item in load_catalog(resources) if item.order == 251)
        self._prepare_schema(database, resources)
        database.client.execute(migration.script_sql)
        self.assertTrue(database.postcheck(migration))

        self._prepare_rows(database)
        first = self._manifest(database, "ci-first")
        database.client.execute(build_apply_sql(first, "ci-first"))
        first_scoped = self._scoped_id(database, first["manifestKey"])
        self._assert_split(database, first_scoped)

        database.client.execute(build_rollback_sql(first, "ci-rollback"))
        self.assertEqual(
            "ROLLED_BACK:CANCELLED:3:3",
            database.client.execute_readonly(
                "SELECT CONCAT(manifest.status,':',scoped.status,':',"
                "(SELECT COUNT(*) FROM noon_auth_identity_recovery_item WHERE recovery_id="
                f"{self.SOURCE}),':',(SELECT COUNT(*) FROM noon_project_auth_state "
                f"WHERE active_recovery_id={self.SOURCE})) FROM noon_auth_owner_scope_manifest manifest "
                "JOIN noon_auth_identity_recovery scoped ON scoped.id=manifest.scoped_recovery_id "
                f"WHERE manifest.manifest_key='{first['manifestKey']}';"
            ),
        )

        second = self._manifest(database, "ci-second")
        self.assertNotEqual(first["manifestKey"], second["manifestKey"])
        database.client.execute(build_apply_sql(second, "ci-second"))
        second_scoped = self._scoped_id(database, second["manifestKey"])
        self._assert_split(database, second_scoped)

        retry = build_retry_manifest(
            load_retry_snapshot(database.client, self.PREDECESSOR, 307, "PRJ0"),
            self.PREDECESSOR, 307, "PRJ0", 60, "ci-retry",
            {"manifestSha256": "a" * 64, "commit": "b" * 40, "runId": 1,
             "artifactName": "ci", "operationBundleSha256": "e" * 64},
        )
        database.client.execute(build_retry_sql(retry, "ci-retry"))
        self.assertEqual(
            "WAITING_COOLDOWN:1:0:0:11:REAUTH_REQUIRED:8:PENDING:8:2",
            database.client.execute_readonly(
                "SELECT CONCAT(recovery.status,':',recovery.send_budget_epoch,':',"
                "recovery.generation_no,':',recovery.send_attempt_count,':',recovery.version_no,':',"
                "state.status,':',state.auth_version,':',item.status,':',item.expected_auth_version,':',"
                "(SELECT COUNT(*) FROM noon_auth_identity_send_ledger WHERE recovery_id="
                f"{self.PREDECESSOR})) FROM noon_auth_identity_recovery recovery "
                "JOIN noon_project_auth_state state ON state.active_recovery_id=recovery.id "
                "AND state.project_code='PRJ0' JOIN noon_auth_identity_recovery_item item "
                "ON item.recovery_id=recovery.id AND item.project_code=state.project_code "
                f"WHERE recovery.id={self.PREDECESSOR};"
            ),
        )

        database.client.execute(
            "UPDATE noon_auth_identity_recovery SET status='COMPLETED',"
            "completed_at=UTC_TIMESTAMP(3),version_no=version_no+1 "
            f"WHERE id={self.PREDECESSOR} AND status='WAITING_COOLDOWN';"
            "UPDATE noon_auth_identity_recovery successor "
            "JOIN noon_auth_identity_recovery predecessor "
            "ON predecessor.id=successor.predecessor_recovery_id "
            "LEFT JOIN noon_auth_identity_recovery active ON active.identity_key=successor.identity_key "
            "AND active.active_identity_slot IS NOT NULL "
            "LEFT JOIN noon_auth_owner_scope_manifest owner_scope "
            "ON owner_scope.predecessor_recovery_id=predecessor.id AND owner_scope.status='ACTIVE' "
            "SET successor.status='COALESCING',successor.version_no=successor.version_no+1 "
            "WHERE successor.status='WAITING_PREDECESSOR' "
            "AND predecessor.status IN ('COMPLETED','FAILED_FINAL','CANCELLED') "
            "AND (owner_scope.id IS NULL OR owner_scope.scoped_recovery_id=successor.id) "
            "AND active.id IS NULL;"
        )
        self.assertEqual(
            f"WAITING_PREDECESSOR:0:0:0:COALESCING:{second_scoped}",
            database.client.execute_readonly(
                "SELECT CONCAT(source.status,':',source.generation_no,':',"
                "source.send_budget_epoch,':',source.send_attempt_count,':',scoped.status,':',scoped.id) "
                "FROM noon_auth_identity_recovery source "
                "JOIN noon_auth_owner_scope_manifest manifest ON manifest.source_recovery_id=source.id "
                "AND manifest.status='ACTIVE' JOIN noon_auth_identity_recovery scoped "
                "ON scoped.id=manifest.scoped_recovery_id;"
            ),
        )
        self.assertTrue(database.livecheck(migration))

    def _database(self):
        return MySqlMigrationDatabase(
            Path(os.environ["NUONO_MIGRATION_MYSQL_DEFAULTS_FILE"]),
            expected_schema=os.environ.get("NUONO_MIGRATION_EXPECTED_SCHEMA", "nuono_schema_migration_ci"),
            expected_host="127.0.0.1",
            expected_port=int(os.environ.get("NUONO_MIGRATION_EXPECTED_PORT", "3306")),
        )

    def _prepare_schema(self, database, resources):
        database.client.execute(
            "SET FOREIGN_KEY_CHECKS=0;"
            "DROP TABLE IF EXISTS noon_auth_owner_scope_audit,noon_auth_owner_scope_manifest_item,"
            "noon_auth_owner_scope_manifest,noon_auth_identity_send_ledger,"
            "noon_auth_identity_recovery_item,noon_project_auth_state,noon_auth_identity_recovery,"
            "noon_http_call_log,noon_pull_task,noon_pull_plan,noon_pull_id_sequence,sales_sync_task;"
            "SET FOREIGN_KEY_CHECKS=1;"
        )
        # Reuse the exact production foundations that 190/238 evolve.  Hand-made
        # placeholder tables hide column/index drift in the owner-scope scenario.
        for order in (53, 58, 190, 238):
            path = next((resources / "db/init").glob(f"{order:03d}_*.sql"))
            database.client.execute(path.read_text(encoding="utf-8"))
        warehouse_sql = (resources / "db/init/134_official_warehouse_asn.sql").read_text(
            encoding="utf-8"
        )
        start = warehouse_sql.index("CREATE TABLE IF NOT EXISTS `noon_http_call_log`")
        end = warehouse_sql.index(";\n\n", start) + 1
        database.client.execute(warehouse_sql[start:end])

    def _prepare_rows(self, database):
        database.client.execute(
            "SET FOREIGN_KEY_CHECKS=0;"
            "DELETE FROM noon_auth_owner_scope_audit;DELETE FROM noon_auth_owner_scope_manifest_item;"
            "DELETE FROM noon_auth_owner_scope_manifest;DELETE FROM noon_auth_identity_send_ledger;"
            "DELETE FROM noon_auth_identity_recovery_item;DELETE FROM noon_project_auth_state;"
            "DELETE FROM noon_auth_identity_recovery;SET FOREIGN_KEY_CHECKS=1;"
            "INSERT INTO noon_auth_identity_recovery "
            "(id,identity_key,status,generation_no,send_budget_epoch,send_attempt_count,coalesce_until,"
            "next_attempt_at,version_no,config_fingerprint,requested_at,gmt_create,gmt_updated) VALUES "
            f"({self.PREDECESSOR},'{self.IDENTITY}','MANUAL_HOLD',2,0,2,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),10,'{'d'*64}',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3));"
            "UPDATE noon_auth_identity_recovery SET failure_code='SEND_RESULT_UNKNOWN',"
            "first_send_at=UTC_TIMESTAMP(3)-INTERVAL 10 MINUTE,"
            f"second_send_at=UTC_TIMESTAMP(3)-INTERVAL 5 MINUTE WHERE id={self.PREDECESSOR};"
            "INSERT INTO noon_auth_identity_recovery "
            "(id,predecessor_recovery_id,identity_key,status,generation_no,send_budget_epoch,"
            "send_attempt_count,coalesce_until,next_attempt_at,version_no,config_fingerprint,"
            "requested_at,gmt_create,gmt_updated) VALUES "
            f"({self.SOURCE},{self.PREDECESSOR},'{self.IDENTITY}','WAITING_PREDECESSOR',0,0,0,"
            f"UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),4,'{'d'*64}',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3));"
            "INSERT INTO noon_project_auth_state "
            "(owner_user_id,project_code,identity_key,status,active_recovery_id,auth_version,gmt_create,gmt_updated) VALUES "
            f"(307,'PRJ0','{self.IDENTITY}','MANUAL_HOLD',{self.PREDECESSOR},7,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),"
            f"(307,'PRJ1','{self.IDENTITY}','REAUTH_REQUIRED',{self.SOURCE},3,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),"
            f"(307,'PRJ2','{self.IDENTITY}','REAUTH_REQUIRED',{self.SOURCE},8,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),"
            f"(308,'PRJ3','{self.IDENTITY}','REAUTH_REQUIRED',{self.SOURCE},5,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3));"
            "INSERT INTO noon_auth_identity_recovery_item "
            "(id,recovery_id,owner_user_id,project_code,store_code,site_code,source_task_id,source_domain,"
            "source_checkpoint,resume_policy,expected_auth_version,status,gmt_create,gmt_updated) VALUES "
            f"(91999,{self.PREDECESSOR},307,'PRJ0','STR0','SA',NULL,'STORE_BINDING',NULL,'AUTO_RESUME',7,'PENDING',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),"
            f"(92001,{self.SOURCE},307,'PRJ1','STR1','SA',NULL,'STORE_BINDING',NULL,'AUTO_RESUME',3,'PENDING',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),"
            f"(92002,{self.SOURCE},307,'PRJ2','STR2','SA',NULL,'STORE_BINDING',NULL,'AUTO_RESUME',8,'PENDING',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),"
            f"(92003,{self.SOURCE},308,'PRJ3','STR3','SA',NULL,'STORE_BINDING',NULL,'AUTO_RESUME',5,'PENDING',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3));"
            "INSERT INTO noon_auth_identity_send_ledger "
            "(identity_key,recovery_id,config_fingerprint,send_budget_epoch,generation_no,send_intent_at,gmt_create) "
            f"SELECT identity_key,id,config_fingerprint,0,1,first_send_at,first_send_at FROM noon_auth_identity_recovery WHERE id={self.PREDECESSOR};"
            "INSERT INTO noon_auth_identity_send_ledger "
            "(identity_key,recovery_id,config_fingerprint,send_budget_epoch,generation_no,send_intent_at,gmt_create) "
            f"SELECT identity_key,id,config_fingerprint,0,2,second_send_at,second_send_at FROM noon_auth_identity_recovery WHERE id={self.PREDECESSOR};"
        )

    def _manifest(self, database, actor):
        snapshot = load_snapshot(database.client, self.PREDECESSOR, self.SOURCE)
        return build_manifest(snapshot, 307, ("PRJ1", "PRJ2"), actor)

    @staticmethod
    def _scoped_id(database, manifest_key):
        return int(database.client.execute_readonly(
            f"SELECT scoped_recovery_id FROM noon_auth_owner_scope_manifest WHERE manifest_key='{manifest_key}';"
        ))

    def _assert_split(self, database, scoped_id):
        self.assertEqual(
            f"WAITING_PREDECESSOR:4:0:0:0:1:2:2:1:{scoped_id}",
            database.client.execute_readonly(
                "SELECT CONCAT(source.status,':',source.version_no,':',source.generation_no,':',"
                "source.send_budget_epoch,':',source.send_attempt_count,':',"
                f"(SELECT COUNT(*) FROM noon_auth_identity_recovery_item WHERE recovery_id={self.SOURCE}),':',"
                f"(SELECT COUNT(*) FROM noon_auth_identity_recovery_item WHERE recovery_id={scoped_id}),':',"
                "manifest.item_count,':',manifest.source_remaining_item_count,':',manifest.scoped_recovery_id) "
                "FROM noon_auth_identity_recovery source JOIN noon_auth_owner_scope_manifest manifest "
                f"ON manifest.source_recovery_id=source.id WHERE source.id={self.SOURCE} AND manifest.status='ACTIVE';"
            ),
        )


if __name__ == "__main__":
    unittest.main()
