from __future__ import annotations

import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[2]
SCRIPT_ROOT = ROOT / "scripts"
if str(SCRIPT_ROOT) not in sys.path:
    sys.path.insert(0, str(SCRIPT_ROOT))

from ci.competitor_business_date_mysql_fixture import (  # noqa: E402
    FENCE_GENERATION,
    FENCE_TABLE,
    RELEASE_PROVENANCE,
    RUN_ID,
    assert_empty_schema,
    build_manifest,
    correction_state,
    create_contract_tables,
    drop_contract_tables,
    insert_snapshot,
    run_migrations,
    snapshot_row,
)
from ci.competitor_business_date_mysql_sessions import (  # noqa: E402
    SharedFenceLock,
)
from ci.competitor_business_date_mysql_schema import (  # noqa: E402
    MIGRATIONS,
    run_migration,
)
from competitor_business_date.execution import execute_manifest  # noqa: E402
from competitor_business_date.governance import MysqlAdvisoryLock  # noqa: E402
from competitor_business_date.mysql_cli import MysqlCli, MysqlCliError  # noqa: E402
from competitor_business_date.persistent_fence import (  # noqa: E402
    activate_fence,
    assert_fence_active,
    read_fence,
    reopen_fence,
)
from competitor_business_date.preflight import (  # noqa: E402
    read_schema_fingerprint,
    server_identity,
)


DEFAULTS_FILE = os.environ.get("COMPETITOR_MYSQL_DEFAULTS_FILE")
SCHEMA = os.environ.get("COMPETITOR_MYSQL_SCHEMA", "nuonuoai")


@unittest.skipUnless(DEFAULTS_FILE, "real MySQL fixture is CI-only")
class CompetitorBusinessDateMysqlTest(unittest.TestCase):
    def test_migrations_and_correction_lifecycle(self):
        mysql = MysqlCli(Path(DEFAULTS_FILE), SCHEMA, timeout_seconds=60)
        assert_empty_schema(mysql)
        try:
            create_contract_tables(mysql, ROOT)
            run_migrations(mysql, ROOT)
            self._assert_exact_empty_fence_recovers(mysql)
            self._assert_writer_lock_blocks_activation(mysql)
            self._assert_writer_fence_cas(mysql)
            self._assert_active_uniqueness(mysql)
            self._assert_correction_cas_resume_rollback(mysql)
            self._assert_writer_fence_reopen(mysql)
            self._assert_migration_drift_fails_closed(mysql)
        finally:
            drop_contract_tables(mysql)

    def _assert_exact_empty_fence_recovers(self, mysql: MysqlCli) -> None:
        mysql.run_script(f"DELETE FROM `{FENCE_TABLE}`;\n")
        run_migration(mysql, ROOT, MIGRATIONS[1])
        state = read_fence(mysql)
        self.assertEqual("OPEN", state["fence_status"])
        self.assertEqual(0, state["generation"])

    def _assert_writer_lock_blocks_activation(self, mysql: MysqlCli) -> None:
        with SharedFenceLock(mysql) as writer:
            writer.assert_held()
            with self.assertRaises(subprocess.TimeoutExpired):
                activate_fence(
                    mysql,
                    generation=FENCE_GENERATION,
                    run_id=RUN_ID,
                    actor="ci",
                    timeout_seconds=2,
                )
        state = read_fence(mysql)
        self.assertEqual("OPEN", state["fence_status"])
        self.assertEqual(0, state["generation"])

    def _assert_writer_fence_cas(self, mysql: MysqlCli) -> None:
        activate_fence(
            mysql,
            generation=FENCE_GENERATION,
            run_id=RUN_ID,
            actor="ci",
        )
        with self.assertRaises(MysqlCliError):
            activate_fence(
                mysql,
                generation=FENCE_GENERATION,
                run_id="stale-run",
                actor="ci",
            )
        assert_fence_active(
            mysql,
            generation=FENCE_GENERATION,
            run_id=RUN_ID,
        )

    def _assert_writer_fence_reopen(self, mysql: MysqlCli) -> None:
        reopen_fence(
            mysql,
            generation=FENCE_GENERATION,
            run_id=RUN_ID,
            actor="ci",
        )
        with self.assertRaises(MysqlCliError):
            reopen_fence(
                mysql,
                generation=FENCE_GENERATION,
                run_id=RUN_ID,
                actor="ci",
            )
        state = read_fence(mysql)
        self.assertEqual("OPEN", state["fence_status"])
        self.assertEqual(FENCE_GENERATION, state["generation"])
        self.assertEqual(RUN_ID, state["operation_run_id"])

    def _assert_migration_drift_fails_closed(self, mysql: MysqlCli) -> None:
        mysql.run_script(
            "ALTER TABLE `operations_competitor_product_snapshot` "
            "ADD INDEX `idx_unexpected_ci_drift` (`title_en`);\n"
        )
        with self.assertRaises(MysqlCliError):
            run_migration(mysql, ROOT, MIGRATIONS[0])
        mysql.run_script(
            "ALTER TABLE `operations_competitor_product_snapshot` "
            "DROP INDEX `idx_unexpected_ci_drift`;\n"
            f"ALTER TABLE `{FENCE_TABLE}` "
            "DROP CHECK `chk_ops_comp_cwf_active_audit`;\n"
        )
        with self.assertRaises(MysqlCliError):
            run_migration(mysql, ROOT, MIGRATIONS[1])

    def _assert_active_uniqueness(self, mysql: MysqlCli) -> None:
        first = snapshot_row(
            20,
            watch_product_id=700,
            noon_product_code="UNIQUE-CI",
        )
        insert_snapshot(mysql, first)
        duplicate = dict(first)
        duplicate["id"] = 21
        with self.assertRaises(MysqlCliError):
            insert_snapshot(mysql, duplicate)
        duplicate["is_deleted"] = 1
        insert_snapshot(mysql, duplicate)

    def _assert_correction_cas_resume_rollback(self, mysql: MysqlCli) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            evidence = Path(temporary)
            manifest, backup, manifest_sha = build_manifest(mysql, evidence)
            with patch(
                "competitor_business_date.execution.assert_database_writer_fence"
            ):
                applied = self._execute(
                    mysql,
                    manifest,
                    backup,
                    manifest_sha,
                    evidence / "apply.jsonl",
                    "apply",
                )
                resumed = self._execute(
                    mysql,
                    manifest,
                    backup,
                    manifest_sha,
                    evidence / "apply.jsonl",
                    "resume",
                )
                self.assertEqual(
                    {
                        "snapshot_1_date": "2026-07-29",
                        "snapshot_1_deleted": 1,
                        "snapshot_2_date": "2026-07-29",
                        "snapshot_2_deleted": 0,
                        "event_count": 1,
                        "sequence_next": 270001,
                        "keyword_captured": "2026-07-28 02:30:00",
                        "rank_time": "2026-07-28 02:30:00",
                        "rank_date": "2026-07-28",
                    },
                    correction_state(mysql),
                )
                rolled_back = self._execute(
                    mysql,
                    manifest,
                    backup,
                    manifest_sha,
                    evidence / "rollback.jsonl",
                    "rollback",
                )
                self.assertEqual(3, applied["executed_batches"])
                self.assertEqual(3, resumed["resumed_batches"])
                self.assertEqual("PRE_WITH_SEQUENCE_POST", rolled_back["final_state"])
                self.assertEqual(
                    {
                        "snapshot_1_date": "2026-07-28",
                        "snapshot_1_deleted": 0,
                        "snapshot_2_date": "2026-07-29",
                        "snapshot_2_deleted": 0,
                        "event_count": 0,
                        "sequence_next": 270001,
                        "keyword_captured": "2026-07-27 18:30:00",
                        "rank_time": "2026-07-27 18:30:00",
                        "rank_date": "2026-07-27",
                    },
                    correction_state(mysql),
                )

                mysql.run_script(
                    "UPDATE `operations_competitor_product_snapshot` "
                    "SET `title_en` = 'outside-manifest' WHERE `id` = 1;\n"
                )
                with self.assertRaises(MysqlCliError):
                    self._execute(
                        mysql,
                        manifest,
                        backup,
                        manifest_sha,
                        evidence / "conflict.jsonl",
                        "apply",
                    )

    def _execute(
        self,
        mysql: MysqlCli,
        manifest: Path,
        backup: Path,
        manifest_sha: str,
        journal: Path,
        operation: str,
    ):
        with MysqlAdvisoryLock(mysql) as advisory:
            return execute_manifest(
                mysql,
                advisory,
                assert_outer_fence_held=lambda: assert_fence_active(
                    mysql,
                    generation=FENCE_GENERATION,
                    run_id=RUN_ID,
                ),
                manifest=manifest,
                manifest_sha256=manifest_sha,
                backup=backup,
                fence_generation=FENCE_GENERATION,
                expected_run_id=RUN_ID,
                expected_server_uuid=server_identity(
                    read_schema_fingerprint(mysql)[0]
                )["server_uuid"],
                release_provenance=RELEASE_PROVENANCE,
                journal=journal,
                operation=operation,
                batch_size=10,
                timeout_seconds=120,
            )


if __name__ == "__main__":
    unittest.main()
